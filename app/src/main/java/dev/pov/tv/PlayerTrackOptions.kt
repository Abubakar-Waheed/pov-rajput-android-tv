package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import java.util.Locale

internal enum class TrackChoiceKind { AUTOMATIC, OFF, TRACK }

internal data class PlayerTrackChoice(
    val label: String,
    val type: Int,
    val kind: TrackChoiceKind,
    val checked: Boolean,
    val group: TrackGroup? = null,
    val index: Int = -1,
    val selectable: Boolean = true,
    val selected: Boolean = false,
    val unavailableReason: String? = null,
)

/** Track overrides affect only the requested type; selecting subtitles never resets audio. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal object PlayerTrackOptions {
    fun choices(tracks: Tracks, parameters: TrackSelectionParameters, type: Int): List<PlayerTrackChoice> {
        require(type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_TEXT)
        val disabled = type in parameters.disabledTrackTypes
        val overrides = parameters.overrides.values.filter { it.type == type }
        val result = mutableListOf(
            PlayerTrackChoice("Automatic", type, TrackChoiceKind.AUTOMATIC, !disabled && overrides.isEmpty())
        )
        if (type == C.TRACK_TYPE_TEXT) result += PlayerTrackChoice("Off", type, TrackChoiceKind.OFF, disabled)
        tracks.groups.filter { it.type == type }.forEach { group ->
            for (index in 0 until group.length) {
                val checked = !disabled && overrides.any { it.mediaTrackGroup == group.mediaTrackGroup && index in it.trackIndices }
                val selected = group.isTrackSelected(index)
                val reason = unsupportedReason(group.getTrackSupport(index))
                val trackLabel = label(group.getTrackFormat(index), result.count { it.kind == TrackChoiceKind.TRACK } + 1)
                result += PlayerTrackChoice(
                    trackLabel + buildString {
                        if (selected) append(" · Selected for playback")
                        if (reason != null) append(" · Manual selection unavailable: $reason")
                        else if (checked && !selected) append(" · Requested")
                    },
                    type, TrackChoiceKind.TRACK, checked, group.mediaTrackGroup, index,
                    selectable = reason == null, selected = selected, unavailableReason = reason
                )
            }
        }
        return result
    }

    fun apply(parameters: TrackSelectionParameters, choice: PlayerTrackChoice): TrackSelectionParameters {
        require(choice.type == C.TRACK_TYPE_AUDIO || choice.type == C.TRACK_TYPE_TEXT)
        require(choice.kind != TrackChoiceKind.OFF || choice.type == C.TRACK_TYPE_TEXT)
        require(choice.selectable) { "This device cannot select the track's format" }
        val builder = parameters.buildUpon().clearOverridesOfType(choice.type)
            .setTrackTypeDisabled(choice.type, choice.kind == TrackChoiceKind.OFF)
        if (choice.kind == TrackChoiceKind.TRACK) {
            val group = requireNotNull(choice.group)
            require(choice.index in 0 until group.length && group.type == choice.type)
            builder.setOverrideForType(TrackSelectionOverride(group, choice.index))
        }
        return builder.build()
    }

    fun label(format: Format, ordinal: Int): String {
        val rawLanguage = format.language.orEmpty().trim().replace('_', '-')
        val language = if (rawLanguage.isBlank() || rawLanguage.equals("und", true)) "Unknown language" else {
            val locale = Locale.forLanguageTag(rawLanguage)
            val display = locale.getDisplayName(Locale.getDefault()).ifBlank { rawLanguage }
            val native = locale.getDisplayName(locale).ifBlank { display }
            (if (display.equals(native, true)) display else "$display / $native") + " [${rawLanguage.take(24)}]"
        }
        val parts = mutableListOf(language)
        format.label?.takeIf { it.isNotBlank() && !it.equals(language, true) }?.let { parts += it.take(70) }
        val codec = when (format.sampleMimeType) {
            "audio/mp4a-latm" -> "AAC"
            "audio/ac3" -> "AC-3"
            "audio/eac3" -> "E-AC-3"
            "audio/eac3-joc" -> "E-AC-3 JOC"
            "audio/vnd.dts" -> "DTS"
            "audio/vnd.dts.hd" -> "DTS-HD"
            "audio/true-hd" -> "TrueHD"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "text/vtt" -> "WebVTT"
            "application/ttml+xml" -> "TTML"
            "application/x-subrip" -> "SRT"
            "text/x-ssa" -> "SSA/ASS"
            "application/pgs" -> "PGS"
            "application/vobsub" -> "VobSub"
            else -> format.codecs?.take(28) ?: format.sampleMimeType?.substringAfter('/')?.take(28)
        }
        codec?.takeIf(String::isNotBlank)?.let {
            val profile = format.codecs?.takeIf { value -> value.isNotBlank() && !value.equals(it, true) }?.take(28)
            parts += if (profile == null) it else "$it ($profile)"
        }
        if (format.channelCount > 0) parts += "${format.channelCount} ch"
        if (format.bitrate > 0) parts += "${format.bitrate / 1000} kb/s"
        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) parts += "Forced"
        if (format.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) parts += "Commentary"
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) parts += "SDH"
        return "$ordinal. " + parts.distinct().joinToString(" · ")
    }

    fun description(choices: List<PlayerTrackChoice>, type: Int, loading: Boolean): String {
        val tracks = choices.filter { it.kind == TrackChoiceKind.TRACK }
        val noun = if (type == C.TRACK_TYPE_AUDIO) "audio" else "subtitle"
        if (tracks.isEmpty()) return if (loading) {
            "Tracks are not loaded yet. This menu refreshes as the stream loads."
        } else {
            "This file exposes no embedded $noun tracks. The menu lists tracks from the open file."
        }
        val supported = tracks.count { it.selectable }
        val count = if (tracks.size == 1) "This file exposes only 1 $noun track." else "${tracks.size} $noun tracks in this file."
        val support = if (supported < tracks.size)
            " $supported manually selectable · ${tracks.size - supported} unavailable for manual selection." else ""
        return "$count$support ✓ marks your preference. Active tracks say “Selected for playback”."
    }

    private fun unsupportedReason(support: Int): String? = when (support) {
        C.FORMAT_HANDLED -> null
        C.FORMAT_EXCEEDS_CAPABILITIES -> "exceeds reported capabilities"
        C.FORMAT_UNSUPPORTED_DRM -> "unsupported copy protection"
        C.FORMAT_UNSUPPORTED_SUBTYPE -> "decoder does not support this codec"
        C.FORMAT_UNSUPPORTED_TYPE -> "unsupported media type"
        else -> "unsupported format"
    }
}

