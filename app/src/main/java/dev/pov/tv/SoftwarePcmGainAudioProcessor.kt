package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class PcmGainRoute { WAITING, PROCESSING, BYPASSED }

/**
 * Always-present PCM16 stage, including at 0 dB, so gain changes need no player rebuild.
 * DefaultAudioSink converts decoded PCM to PCM16 before this processor. Encoded passthrough
 * remains available for TV-supported formats and is explicitly reported as bypassed.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class SoftwarePcmGainAudioProcessor(
    private val onRouteChanged: (PcmGainRoute, Int?, Long) -> Unit = { _, _, _ -> },
) : BaseAudioProcessor() {
    private data class GainRequest(val db: Int, val revision: Long)
    @Volatile private var request = GainRequest(0, 0)
    @Volatile var processedPcmFrames: Long = 0
        private set
    private var channelCount = 1
    private var partialFrameSamples = 0
    private var pcmRoute = false
    private var pendingLowByte = -1
    private var reportedRoute: PcmGainRoute? = null
    private var reportedGain: Int? = null
    private var reportedRevision = -1L

    @Synchronized fun setGainDb(db: Int): Long {
        require(db in SessionVolumeBoost.STEPS) { "Unsupported volume boost" }
        if (request.db != db) request = GainRequest(db, request.revision + 1)
        return request.revision
    }

    /** Called on the playback/audio thread when the sink chooses compressed passthrough. */
    fun onEncodedPassthrough() {
        pcmRoute = false
        pendingLowByte = -1
        partialFrameSamples = 0
        report(PcmGainRoute.BYPASSED, null)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            pcmRoute = false
            report(PcmGainRoute.BYPASSED, null)
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        pcmRoute = true
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        partialFrameSamples = 0
        report(PcmGainRoute.WAITING, null)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val samples = (inputBuffer.remaining() + if (pendingLowByte >= 0) 1 else 0) / 2
        val output = replaceOutputBuffer(samples * 2)
        val selection = request
        val gain = selection.db
        val multiplier = MULTIPLIERS.getValue(gain)

        fun writeSample(low: Int, high: Int) {
            val sample = ((high shl 8) or low).toShort().toInt()
            val adjusted = if (gain == 0) sample else
                (sample * multiplier).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.put((adjusted and 0xff).toByte())
            output.put((adjusted shr 8).toByte())
        }

        if (pendingLowByte >= 0 && inputBuffer.hasRemaining()) {
            writeSample(pendingLowByte, inputBuffer.get().toInt())
            pendingLowByte = -1
        }
        while (inputBuffer.remaining() >= 2) {
            val low = inputBuffer.get().toInt() and 0xff
            writeSample(low, inputBuffer.get().toInt())
        }
        // A split sample survives across buffer boundaries without dropping or shifting bytes.
        if (inputBuffer.hasRemaining()) pendingLowByte = inputBuffer.get().toInt() and 0xff
        output.flip()
        if (samples > 0) {
            val frameSamples = samples + partialFrameSamples
            processedPcmFrames += frameSamples / channelCount
            partialFrameSamples = frameSamples % channelCount
            report(PcmGainRoute.PROCESSING, gain, selection.revision)
        }
    }

    override fun onFlush() {
        pendingLowByte = -1
        partialFrameSamples = 0
        report(if (pcmRoute) PcmGainRoute.WAITING else PcmGainRoute.BYPASSED, null)
    }

    override fun onReset() {
        pcmRoute = false
        pendingLowByte = -1
        partialFrameSamples = 0
        report(PcmGainRoute.WAITING, null)
    }

    override fun onQueueEndOfStream() {
        // An orphan byte is not a PCM sample; all complete samples have already been emitted.
        pendingLowByte = -1
    }

    private fun report(route: PcmGainRoute, gain: Int?, revision: Long = request.revision) {
        if (reportedRoute == route && reportedGain == gain && reportedRevision == revision) return
        reportedRoute = route
        reportedGain = gain
        reportedRevision = revision
        onRouteChanged(route, gain, revision)
    }

    private companion object {
        val MULTIPLIERS = SessionVolumeBoost.STEPS.associateWith { 10.0.pow(it / 20.0) }
    }
}
