package dev.pov.tv

import android.app.Instrumentation
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Bundle
import android.view.Display
import org.json.JSONArray
import org.json.JSONObject

/** Device codec/display capabilities only; no account data, history or stream addresses. */
class CodecCapabilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filterNot { it.isEncoder }
            for ((label, mime) in listOf("dolby_vision" to "video/dolby-vision", "hevc" to "video/hevc", "avc" to "video/avc")) {
                val matches = codecs.filter { codec -> codec.supportedTypes.any { it.equals(mime, true) } }
                report.putInt("${label}_decoder_count", matches.size)
                report.putString("${label}_decoders", JSONArray(matches.map { codec ->
                    val capabilities = codec.getCapabilitiesForType(mime)
                    JSONObject().put("name", codec.name).put("supports_4k", capabilities.videoCapabilities?.isSizeSupported(3840, 2160) == true)
                        .put("profiles", JSONArray(capabilities.profileLevels.map { it.profile }.distinct()))
                }).toString())
            }
            val display = (targetContext.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val hdr = display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            report.putBoolean("display_dolby_vision", Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdr)
            report.putBoolean("display_hdr10", Display.HdrCapabilities.HDR_TYPE_HDR10 in hdr)
            report.putBoolean("passed", true)
            finish(-1, report)
        } catch (error: Throwable) {
            report.putString("error_type", error.javaClass.simpleName)
            report.putBoolean("passed", false)
            finish(0, report)
        }
    }
}
