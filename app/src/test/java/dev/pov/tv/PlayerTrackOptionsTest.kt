package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerTrackOptionsTest {
    private val audio = TrackGroup("audio", Format.Builder().setSampleMimeType("audio/mp4a-latm")
        .setLanguage("en").setChannelCount(2).build())
    private val subtitle = TrackGroup("text", Format.Builder().setSampleMimeType("application/x-subrip")
        .setLanguage("fr").setSelectionFlags(C.SELECTION_FLAG_FORCED).build())
    private fun defaults() = TrackSelectionParameters.Builder(RuntimeEnvironment.getApplication()).build()
    private fun group(tracks: TrackGroup, supported: Boolean = true, selected: Set<Int> = emptySet()) = Tracks.Group(
        tracks, false, IntArray(tracks.length) { if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE },
        BooleanArray(tracks.length) { it in selected }
    )
    private fun available() = Tracks(listOf(group(audio), group(subtitle)))

    @Test fun `supported choices expose language codec channels and forced flags`() {
        val audioChoices = PlayerTrackOptions.choices(available(), defaults(), C.TRACK_TYPE_AUDIO)
        assertEquals(listOf(TrackChoiceKind.AUTOMATIC, TrackChoiceKind.TRACK), audioChoices.map { it.kind })
        assertTrue(audioChoices.first().checked)
        assertTrue(audioChoices.last().label.contains("English"))
        assertTrue(audioChoices.last().label.contains("AAC"))
        assertTrue(audioChoices.last().label.contains("2 ch"))
        val text = PlayerTrackOptions.choices(available(), defaults(), C.TRACK_TYPE_TEXT).last()
        assertTrue(text.label.contains("French"))
        assertTrue(text.label.contains("SRT"))
        assertTrue(text.label.contains("Forced"))
    }

    @Test fun `unsupported tracks remain visible with reason but cannot be selected`() {
        val choices = PlayerTrackOptions.choices(Tracks(listOf(group(audio, false))), defaults(), C.TRACK_TYPE_AUDIO)
        assertEquals(2, choices.size)
        assertEquals(TrackChoiceKind.AUTOMATIC, choices.first().kind)
        assertFalse(choices.last().selectable)
        assertEquals("unsupported media type", choices.last().unavailableReason)
        assertTrue(choices.last().label.contains("Manual selection unavailable"))
        try {
            PlayerTrackOptions.apply(defaults(), choices.last())
            fail("An unsupported format must not create an override")
        } catch (_: IllegalArgumentException) { }
        val emptyText = PlayerTrackOptions.choices(Tracks.EMPTY, defaults(), C.TRACK_TYPE_TEXT)
        assertEquals(listOf(TrackChoiceKind.AUTOMATIC, TrackChoiceKind.OFF), emptyText.map { it.kind })
    }

    @Test fun `audio override preserves subtitle override and unrelated preferences`() {
        val textChoice = PlayerTrackOptions.choices(available(), defaults(), C.TRACK_TYPE_TEXT).last()
        val textSelected = PlayerTrackOptions.apply(defaults().buildUpon().setMaxVideoBitrate(1_000_000).build(), textChoice)
        val audioChoice = PlayerTrackOptions.choices(available(), textSelected, C.TRACK_TYPE_AUDIO).last()
        val both = PlayerTrackOptions.apply(textSelected, audioChoice)
        assertEquals(setOf(audio, subtitle), both.overrides.keys)
        assertEquals(1_000_000, both.maxVideoBitrate)
        val checked = PlayerTrackOptions.choices(available(), both, C.TRACK_TYPE_AUDIO)
        assertFalse(checked.first().checked)
        assertTrue(checked.last().checked)
    }

    @Test fun `automatic clears only the matching type override`() {
        var parameters = defaults()
        for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            parameters = PlayerTrackOptions.apply(parameters, PlayerTrackOptions.choices(available(), parameters, type).last())
        }
        val automatic = PlayerTrackOptions.choices(available(), parameters, C.TRACK_TYPE_AUDIO).first()
        val changed = PlayerTrackOptions.apply(parameters, automatic)
        assertEquals(setOf(subtitle), changed.overrides.keys)
        assertTrue(PlayerTrackOptions.choices(available(), changed, C.TRACK_TYPE_AUDIO).first().checked)
    }

    @Test fun `subtitle Off disables text without disabling audio and Automatic re-enables it`() {
        val audioSelected = PlayerTrackOptions.apply(defaults(),
            PlayerTrackOptions.choices(available(), defaults(), C.TRACK_TYPE_AUDIO).last())
        val off = PlayerTrackOptions.choices(available(), audioSelected, C.TRACK_TYPE_TEXT).first { it.kind == TrackChoiceKind.OFF }
        val disabled = PlayerTrackOptions.apply(audioSelected, off)
        assertTrue(C.TRACK_TYPE_TEXT in disabled.disabledTrackTypes)
        assertFalse(C.TRACK_TYPE_AUDIO in disabled.disabledTrackTypes)
        assertEquals(setOf(audio), disabled.overrides.keys)
        assertTrue(PlayerTrackOptions.choices(available(), disabled, C.TRACK_TYPE_TEXT).first { it.kind == TrackChoiceKind.OFF }.checked)
        val automatic = PlayerTrackOptions.choices(available(), disabled, C.TRACK_TYPE_TEXT).first()
        assertFalse(C.TRACK_TYPE_TEXT in PlayerTrackOptions.apply(disabled, automatic).disabledTrackTypes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched audio and text override is rejected`() {
        PlayerTrackOptions.apply(defaults(), PlayerTrackChoice("invalid", C.TRACK_TYPE_AUDIO,
            TrackChoiceKind.TRACK, false, subtitle, 0))
    }

    @Test fun `unknown languages still have stable readable track labels`() {
        val label = PlayerTrackOptions.label(Format.Builder().setSampleMimeType("audio/ac3").setChannelCount(6).build(), 2)
        assertTrue(label.startsWith("2. Unknown language"))
        assertTrue(label.contains("AC-3"))
        assertTrue(label.contains("6 ch"))
    }

    @Test fun `all language and codec variants are retained including unknown and unsupported`() {
        val formats = listOf(
            Format.Builder().setSampleMimeType("audio/mp4a-latm").setLanguage("en").setCodecs("mp4a.40.2").setChannelCount(2).build(),
            Format.Builder().setSampleMimeType("audio/ac3").setLanguage("en").setLabel("Surround").setChannelCount(6).build(),
            Format.Builder().setSampleMimeType("audio/mp4a-latm").setLanguage("ja").setChannelCount(2).build(),
            Format.Builder().setSampleMimeType("audio/flac").setLanguage("fr").setChannelCount(2).build(),
            Format.Builder().setSampleMimeType("audio/opus").setLanguage("und").setChannelCount(2).build()
        )
        val tracks = Tracks(formats.mapIndexed { index, format -> group(TrackGroup("variant$index", format), supported = index != 3) })
        val choices = PlayerTrackOptions.choices(tracks, defaults(), C.TRACK_TYPE_AUDIO).filter { it.kind == TrackChoiceKind.TRACK }
        assertEquals(5, choices.size)
        assertEquals(2, choices.count { it.label.contains("[en]") })
        assertTrue(choices[0].label.contains("AAC (mp4a.40.2)"))
        assertTrue(choices[1].label.contains("AC-3") && choices[1].label.contains("6 ch") && choices[1].label.contains("Surround"))
        assertTrue(choices[2].label.contains("[ja]") && choices[2].label.contains("日本語"))
        assertTrue(choices[3].label.contains("[fr]") && !choices[3].selectable)
        assertTrue(choices[4].label.contains("Unknown language") && !choices[4].label.contains("English"))
    }

    @Test fun `automatic mode and renderer selected track are separate from a pending manual request`() {
        val second = TrackGroup("second", Format.Builder().setSampleMimeType("audio/ac3").setLanguage("ja").build())
        val tracks = Tracks(listOf(group(audio, selected = setOf(0)), group(second)))
        val automatic = PlayerTrackOptions.choices(tracks, defaults(), C.TRACK_TYPE_AUDIO)
        assertTrue(automatic.first().checked)
        assertTrue(automatic[1].selected)
        assertFalse(automatic[1].checked)
        assertTrue(automatic[1].label.contains("Selected for playback"))
        val requested = PlayerTrackOptions.apply(defaults(), automatic.last())
        val pending = PlayerTrackOptions.choices(tracks, requested, C.TRACK_TYPE_AUDIO)
        assertFalse(pending.first().checked)
        assertTrue(pending[1].selected)
        assertFalse(pending.last().selected)
        assertTrue(pending.last().checked)
        assertTrue(pending.last().label.contains("Requested"))
        val switched = PlayerTrackOptions.choices(Tracks(listOf(group(audio), group(second, selected = setOf(0)))), requested, C.TRACK_TYPE_AUDIO)
        assertTrue(switched.last().selected && switched.last().checked)
        assertTrue(switched.last().label.contains("Selected for playback"))
        assertFalse(switched.last().label.contains("Requested"))
    }

    @Test fun `all unsupported capability reasons are visible without disappearing from the menu`() {
        val supports = listOf(C.FORMAT_EXCEEDS_CAPABILITIES, C.FORMAT_UNSUPPORTED_DRM,
            C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_UNSUPPORTED_TYPE)
        val tracks = Tracks(supports.mapIndexed { index, support -> Tracks.Group(
            TrackGroup("unsupported$index", audio.getFormat(0)), false, intArrayOf(support), booleanArrayOf(index == 0)) })
        val choices = PlayerTrackOptions.choices(tracks, defaults(), C.TRACK_TYPE_AUDIO).drop(1)
        assertEquals(4, choices.size)
        assertTrue(choices.all { !it.selectable && it.label.contains("Manual selection unavailable") })
        assertEquals(4, choices.map { it.unavailableReason }.distinct().size)
        assertTrue(choices.first().selected)
        assertTrue(choices.first().label.contains("Selected for playback"))
        assertTrue(choices.first().label.contains("exceeds reported capabilities"))
    }

    @Test fun `every format inside a group and every subtitle language stays visible`() {
        val audioVariants = TrackGroup("variants",
            Format.Builder().setSampleMimeType("audio/mp4a-latm").setLanguage("en").setChannelCount(2).build(),
            Format.Builder().setSampleMimeType("audio/mp4a-latm").setLanguage("en").setChannelCount(6).build())
        val japanese = TrackGroup("ja-text", Format.Builder().setSampleMimeType("text/vtt").setLanguage("ja").build())
        val unknown = TrackGroup("unknown-text", Format.Builder().setSampleMimeType("application/pgs").build())
        val tracks = Tracks(listOf(group(audioVariants), group(japanese), group(unknown, false)))
        val audioChoices = PlayerTrackOptions.choices(tracks, defaults(), C.TRACK_TYPE_AUDIO).drop(1)
        assertEquals(listOf(0, 1), audioChoices.map { it.index })
        assertTrue(audioChoices[0].label.contains("2 ch") && audioChoices[1].label.contains("6 ch"))
        val textChoices = PlayerTrackOptions.choices(tracks, defaults(), C.TRACK_TYPE_TEXT).drop(2)
        assertEquals(2, textChoices.size)
        assertTrue(textChoices[0].label.contains("[ja]"))
        assertTrue(textChoices[1].label.contains("Unknown language") && textChoices[1].label.contains("PGS"))
        assertFalse(textChoices[1].selectable)
    }

    @Test fun `track count and loading details reflect only the currently open file`() {
        val empty = PlayerTrackOptions.choices(Tracks.EMPTY, defaults(), C.TRACK_TYPE_AUDIO)
        assertTrue(PlayerTrackOptions.description(empty, C.TRACK_TYPE_AUDIO, true).contains("not loaded yet"))
        assertTrue(PlayerTrackOptions.description(empty, C.TRACK_TYPE_AUDIO, false).contains("no embedded audio tracks"))
        val single = PlayerTrackOptions.choices(Tracks(listOf(group(audio))), defaults(), C.TRACK_TYPE_AUDIO)
        assertTrue(PlayerTrackOptions.description(single, C.TRACK_TYPE_AUDIO, false).contains("only 1 audio track"))
        val unsupported = PlayerTrackOptions.choices(Tracks(listOf(group(audio, false))), defaults(), C.TRACK_TYPE_AUDIO)
        assertTrue(PlayerTrackOptions.description(unsupported, C.TRACK_TYPE_AUDIO, false).contains("0 manually selectable · 1 unavailable for manual selection"))
    }
}

