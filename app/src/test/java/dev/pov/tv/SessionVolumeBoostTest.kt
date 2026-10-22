package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
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
class SessionVolumeBoostTest {
    private class Harness {
        val mainTasks = ArrayDeque<() -> Unit>()
        var notifications = 0
        val boost = SessionVolumeBoost({ mainTasks.addLast(it) }, { notifications++ })
        fun drainMain() { while (mainTasks.isNotEmpty()) mainTasks.removeFirst().invoke() }
        fun prepare(processor: SoftwarePcmGainAudioProcessor) {
            processor.configure(AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
            processor.flush()
            drainMain()
        }
        fun process(processor: SoftwarePcmGainAudioProcessor) {
            processor.queueInput(ByteBuffer.allocateDirect(2).order(ByteOrder.LITTLE_ENDIAN)
                .apply { putShort(1000); flip() })
            processor.output
        }
    }

    @Test fun `gain waits for real PCM and audio callbacks notify only through main dispatcher`() {
        val h = Harness()
        val processor = h.boost.createAudioProcessor()
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.setGain(6))
        h.prepare(processor)
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.state)
        val before = h.notifications
        h.process(processor)
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.state)
        assertEquals(before, h.notifications)
        h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
        assertTrue(h.notifications > before)
        assertEquals(1L, h.boost.processedPcmFrames)
    }

    @Test fun `selecting a changed gain waits for its own next processed buffer`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor(); h.prepare(processor)
        h.boost.setGain(3); h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.setGain(12))
        h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
    }

    @Test fun `stale gain callback cannot approve a later request with the same gain`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor(); h.prepare(processor)
        h.boost.setGain(3); h.process(processor)
        h.boost.setGain(0); h.boost.setGain(3)
        h.drainMain()
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.state)
        h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
    }

    @Test fun `encoded passthrough reports unavailable and returning PCM can recover`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor()
        h.boost.setGain(6); processor.onEncodedPassthrough(); h.drainMain()
        assertEquals(VolumeBoostState.UNAVAILABLE, h.boost.state)
        assertEquals(6, h.boost.gainDb)
        h.prepare(processor)
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.state)
        h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
    }

    @Test fun `Off is immediate on any route and gain never exceeds defined steps`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor()
        h.boost.setGain(9); processor.onEncodedPassthrough(); h.drainMain()
        assertEquals(VolumeBoostState.OFF, h.boost.setGain(0))
        assertEquals(0, h.boost.gainDb)
        for (db in listOf(-3, 1, 13, 60)) {
            try { h.boost.setGain(db); fail("Gain must be bounded") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(listOf(0, 3, 6, 9, 12), SessionVolumeBoost.STEPS)
        assertEquals(VolumeBoostState.OFF, h.boost.state)
    }

    @Test fun `close invalidates queued processing and later player starts at normal gain`() {
        val h = Harness(); val old = h.boost.createAudioProcessor(); h.prepare(old)
        h.boost.setGain(12); h.process(old); h.boost.close(); h.boost.close(); h.drainMain()
        assertEquals(VolumeBoostState.OFF, h.boost.state)
        assertEquals(0, h.boost.gainDb)
        assertEquals(0L, h.boost.processedPcmFrames)
        val next = h.boost.createAudioProcessor(); h.prepare(next); h.process(next); h.drainMain()
        assertEquals(VolumeBoostState.OFF, h.boost.state)
    }

    @Test fun `old sink route callbacks cannot alter a replacement sink`() {
        val h = Harness(); val old = h.boost.createAudioProcessor(); h.prepare(old)
        h.boost.setGain(6)
        val next = h.boost.createAudioProcessor(); h.prepare(next)
        h.process(next); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
        old.onEncodedPassthrough(); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
    }

    @Test fun `reset for end or error keeps the sink usable for replay and retry`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor(); h.prepare(processor)
        h.boost.setGain(9); h.process(processor); h.drainMain()
        h.boost.resetGain()
        assertEquals(VolumeBoostState.OFF, h.boost.state)
        h.boost.setGain(3); h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
        assertEquals(2L, h.boost.processedPcmFrames)
    }

    @Test fun `audio flush stops active claim until new PCM arrives without dropping selected gain`() {
        val h = Harness(); val processor = h.boost.createAudioProcessor(); h.prepare(processor)
        h.boost.setGain(6); h.process(processor); h.drainMain()
        processor.flush(); h.drainMain()
        assertEquals(VolumeBoostState.WAITING_FOR_AUDIO, h.boost.state)
        assertEquals(6, h.boost.gainDb)
        h.process(processor); h.drainMain()
        assertEquals(VolumeBoostState.REQUESTED, h.boost.state)
    }
}
