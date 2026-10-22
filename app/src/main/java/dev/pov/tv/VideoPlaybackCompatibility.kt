package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks

internal enum class VideoCompatibilityReason { EXCEEDS_CAPABILITIES, UNSUPPORTED, NO_VIDEO, NO_SELECTED_VIDEO }

/** Display only fixed codec names and numeric dimensions, never file labels or raw codec strings. */
internal data class VideoCompatibilityIssue(
    val reason: VideoCompatibilityReason,
    val codec: String? = null,
    val width: Int = Format.NO_VALUE,
    val height: Int = Format.NO_VALUE,
) {
    val message: String get() {
        val format = listOfNotNull(codec,
            if (width > 0 && height > 0) "${width}×${height}" else null).joinToString(" at ")
        val explanation = when (reason) {
            VideoCompatibilityReason.EXCEEDS_CAPABILITIES ->
                "The video format (${format.ifBlank { "unknown" }}) exceeds this TV's reported decoder capabilities."
            VideoCompatibilityReason.UNSUPPORTED ->
                "This TV cannot decode the video format (${format.ifBlank { "unknown" }})."
            VideoCompatibilityReason.NO_VIDEO -> "This copy has no video track available for playback."
            VideoCompatibilityReason.NO_SELECTED_VIDEO -> "This copy has no video track selected for playback."
        }
        return "$explanation Playback was paused to avoid playing only audio. Choose a compatible copy, such as 1080p H.264 (AVC), or another format supported by this TV."
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class VideoCompatibilityException(val issue: VideoCompatibilityIssue) : PlaybackException(
    issue.message, null,
    if (issue.reason == VideoCompatibilityReason.EXCEEDS_CAPABILITIES)
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES else PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)

/** Uses renderer capability reports, with no assumptions about TV manufacturer or video title. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal object VideoPlaybackCompatibility {
    private data class Candidate(val format: Format, val support: Int, val selected: Boolean)

    fun issue(tracks: Tracks, playbackState: Int, actualVideoFormat: Format? = null): VideoCompatibilityIssue? {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) return null
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).map { index ->
                Candidate(group.getTrackFormat(index), group.getTrackSupport(index), group.isTrackSelected(index))
            }
        }
        if (video.isEmpty()) {
            // Demuxers may report empty/audio-only tracks before the complete media is ready.
            return if (playbackState == Player.STATE_READY) VideoCompatibilityIssue(VideoCompatibilityReason.NO_VIDEO) else null
        }
        val selected = video.filter { it.selected }
        // An adaptive selection can contain several representations. Only an actual matching
        // unsupported format proves that its handled alternatives aren't currently being used.
        val actual = actualVideoFormat?.let { format -> selected.firstOrNull { it.format == format } }
        if (actual != null && actual.support != C.FORMAT_HANDLED) return failure(actual)
        if (selected.any { it.support == C.FORMAT_HANDLED }) return null
        if (selected.isNotEmpty()) return failure(selected.first())
        if (video.none { it.support == C.FORMAT_HANDLED }) {
            return failure(video.firstOrNull { it.support == C.FORMAT_EXCEEDS_CAPABILITIES } ?: video.first())
        }
        return if (playbackState == Player.STATE_READY) VideoCompatibilityIssue(VideoCompatibilityReason.NO_SELECTED_VIDEO) else null
    }

    private fun failure(candidate: Candidate): VideoCompatibilityIssue = VideoCompatibilityIssue(
        if (candidate.support == C.FORMAT_EXCEEDS_CAPABILITIES) VideoCompatibilityReason.EXCEEDS_CAPABILITIES
        else VideoCompatibilityReason.UNSUPPORTED,
        codecLabel(candidate.format.sampleMimeType), candidate.format.width, candidate.format.height,
    )

    private fun codecLabel(mime: String?): String = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264 (AVC)"
        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        "video/mpeg2" -> "MPEG-2"
        "video/mp4v-es" -> "MPEG-4"
        else -> "video format"
    }
}
