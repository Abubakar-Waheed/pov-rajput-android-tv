package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SoftwarePcmGainAudioProcessorTest {
    private fun processor(channels: Int = 1) = SoftwarePcmGainAudioProcessor().also {
        assertEquals(AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT),
            it.configure(AudioFormat(48_000, channels, C.ENCODING_PCM_16BIT)))
        it.flush()
        assertTrue(it.isActive)
    }

    private fun input(vararg values: Int) = ByteBuffer.allocateDirect(values.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it.toShort()) }; flip() }

    private fun result(processor: SoftwarePcmGainAudioProcessor): List<Int> {
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        return buildList { while (output.remaining() >= 2) add(output.short.toInt()) }
    }

    @Test fun `Off is bit exact for signed extremes silence and stereo ordering`() {
        val processor = processor(channels = 2)
        val samples = intArrayOf(-32768, 32767, -1, 0, 1, 12345, -23456, 256)
        processor.queueInput(input(*samples))
        assertEquals(samples.toList(), result(processor))
        assertEquals(4L, processor.processedPcmFrames)
    }

    @Test fun `all four dB choices apply logarithmic amplitude to both signs`() {
        val processor = processor()
        for ((db, expected) in listOf(3 to 1413, 6 to 1995, 9 to 2818, 12 to 3981)) {
            processor.setGainDb(db)
            processor.queueInput(input(-1000, 0, 1000))
            assertEquals("$db dB", listOf(-expected, 0, expected), result(processor))
        }
    }

    @Test fun `positive and negative overload saturate without wraparound`() {
        val processor = processor()
        for (db in listOf(3, 6, 9, 12)) {
            processor.setGainDb(db)
            processor.queueInput(input(32767, -32768, 30000, -30000))
            assertEquals(listOf(32767, -32768, 32767, -32768), result(processor))
        }
    }

    @Test fun `input positions and little endian samples survive arbitrary buffer order`() {
        val processor = processor()
        val bytes = ByteBuffer.wrap(byteArrayOf(99, 0x34, 0x12, 0xcc.toByte(), 0xed.toByte(), 99))
            .order(ByteOrder.BIG_ENDIAN).apply { position(1); limit(5) }
        processor.queueInput(bytes)
        assertEquals(5, bytes.position())
        assertEquals(listOf(0x1234, -0x1234), result(processor))
    }

    @Test fun `gain changes apply to next buffer without reconfigure or flush`() {
        val processor = processor()
        processor.queueInput(input(1000)); assertEquals(listOf(1000), result(processor))
        processor.setGainDb(6)
        processor.queueInput(input(1000)); assertEquals(listOf(1995), result(processor))
        processor.setGainDb(0)
        processor.queueInput(input(1000)); assertEquals(listOf(1000), result(processor))
        assertTrue(processor.isActive)
        assertEquals(3L, processor.processedPcmFrames)
    }

    @Test fun `split samples are buffered and emitted once with no shifted bytes`() {
        val processor = processor()
        processor.queueInput(ByteBuffer.wrap(byteArrayOf(0x34)))
        assertTrue(result(processor).isEmpty())
        processor.queueInput(ByteBuffer.wrap(byteArrayOf(0x12, 0xcc.toByte(), 0xed.toByte())))
        assertEquals(listOf(0x1234, -0x1234), result(processor))
        assertEquals(2L, processor.processedPcmFrames)
    }

    @Test fun `empty input does not claim processed PCM or emit samples`() {
        val events = mutableListOf<PcmGainRoute>()
        val processor = SoftwarePcmGainAudioProcessor { route, _, _ -> events += route }
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.queueInput(ByteBuffer.allocate(0))
        assertTrue(result(processor).isEmpty())
        assertFalse(events.contains(PcmGainRoute.PROCESSING))
        assertEquals(0L, processor.processedPcmFrames)
    }

    @Test fun `flush discards an incomplete sample and preserves selected gain`() {
        val processor = processor()
        processor.setGainDb(6)
        processor.queueInput(ByteBuffer.wrap(byteArrayOf(0x34))); result(processor)
        processor.flush()
        processor.queueInput(input(1000))
        assertEquals(listOf(1995), result(processor))
    }

    @Test fun `gain changes away and back emit a fresh applied request`() {
        val revisions = mutableListOf<Long>()
        val processor = SoftwarePcmGainAudioProcessor { route, _, revision ->
            if (route == PcmGainRoute.PROCESSING) revisions += revision
        }
        processor.configure(AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.setGainDb(3); processor.queueInput(input(100)); result(processor)
        processor.setGainDb(0); processor.setGainDb(3)
        processor.queueInput(input(100)); result(processor)
        assertEquals(2, revisions.size)
        assertTrue(revisions[1] > revisions[0])
    }

    @Test fun `unsupported input is explicit and an encoded route never claims processing`() {
        val events = mutableListOf<PcmGainRoute>()
        val processor = SoftwarePcmGainAudioProcessor { route, _, _ -> events += route }
        try {
            processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
            fail("The sink must convert PCM to 16 bit before custom processing")
        } catch (_: UnhandledAudioFormatException) { }
        assertEquals(PcmGainRoute.BYPASSED, events.last())
        processor.onEncodedPassthrough()
        assertFalse(events.contains(PcmGainRoute.PROCESSING))
    }

    @Test fun `queue end of stream drains complete PCM and ends without an orphan byte`() {
        val processor = processor()
        processor.queueInput(ByteBuffer.wrap(byteArrayOf(1, 0, 2)))
        processor.queueEndOfStream()
        assertFalse(processor.isEnded)
        assertEquals(listOf(1), result(processor))
        assertTrue(processor.isEnded)
    }

    @Test fun `invalid gains cannot change a valid selected multiplier`() {
        val processor = processor()
        processor.setGainDb(3)
        for (db in listOf(-3, 1, 13, 60)) {
            try { processor.setGainDb(db); fail("Gain must be bounded") }
            catch (_: IllegalArgumentException) { }
        }
        processor.queueInput(input(1000))
        assertEquals(listOf(1413), result(processor))
    }
}
