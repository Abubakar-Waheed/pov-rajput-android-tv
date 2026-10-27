package dev.pov.tv

import android.media.MediaCodecList

/**
 * The tallest standard 16:9 video this TV's video decoders report they can play, so sources above it
 * (for example 8K on a 4K or 1080p TV) can be ranked last and skipped by auto-play before they fail.
 * Unknown capabilities never hide anything.
 */
internal object DecoderLimits {
    @Volatile private var cached: Int? = null

    fun maxHeight(): Int = cached ?: compute().also { cached = it }

    fun label(height: Int): String = when {
        height >= 4320 -> "8K"
        height >= 2160 -> "4K"
        else -> "${height}p"
    }

    private fun compute(): Int {
        val video = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.flatMap { info ->
                info.supportedTypes.filter { it.startsWith("video/") }
                    .mapNotNull { type -> runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull() }
            }
        } catch (_: Exception) { emptyList() }
        if (video.isEmpty()) return Int.MAX_VALUE
        return listOf(4320, 2160, 1440, 1080, 720).firstOrNull { height -> video.any { it.isSizeSupported(height * 16 / 9, height) } }
            ?: Int.MAX_VALUE
    }
}
