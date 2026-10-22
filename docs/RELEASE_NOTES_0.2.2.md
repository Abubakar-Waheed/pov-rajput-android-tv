# POV Standalone 0.2.2 preview — local change notes

**Standalone app made by Mark Mateo. Based on the original POV Kodi add-on by [kodifitzwell](https://github.com/kodifitzwell/repo).**

Version: **0.2.2-preview** · Android version code **5** · Android TV 8.0 or later.

This revision addresses compact playback controls, complete language lists, source selection, smoother mini-player handoff, and unsupported video. Current validation and exact packaged-artifact results are recorded in the [audit record](AUDIT_0.2.2.md).

Validation: **227 passing unit tests**, zero failures/errors/skips; lint **0 errors / 56 warnings**. The Sony combined playback audit, visible mini/fullscreen motion checks, repeated handoff check, final synthetic source/file navigation check, and episode focus/Back check passed.

- Compact bottom playback controls with a slim timeline and consistent audio, subtitle, and boost selection panels.
- Full enumeration of audio/subtitle tracks reported by the file, with actual selected states, language/format details, and explanations for unsupported tracks.
- Sequential remote focus through source versions and cloud files, including all middle rows.
- A file chooser for transfers with multiple eligible videos, preserving actual TorBox file IDs and safe episode matching.
- Decoded PCM software volume boost replaces the native effect implicated in Sony audio-system crashes. Off/+3/+6/+9/+12 dB use bounded sample processing; encoded passthrough playback is preserved and explains why boost is unavailable on that route. New sessions default to Off, and end/error resets the gain.
- Fullscreen returns retain the browsing screen, scroll/focus state, and mini-player view. The player surface switches without Activity slide/scale animation or rebuilding the catalog. Horizontal poster rows leave room for the focus outline.
- A video compatibility check pauses unsupported/excess-capability video and shows a safe codec/resolution message with **Choose another source**. It uses the TV's reported support, preserving supported HEVC, 4K, and adaptive video. Leaving a blocked source releases its session; it does not add new local history or Trakt progress for that blocked playback.
- Existing five themes, generic anime seasons/movies/specials separation, shared mini player, account protections, privacy exclusions, and Mark Mateo/kodifitzwell credits remain included.

Install as an update over the existing app with its compatible signing key. Do not uninstall or clear storage as a routine update. Bring your own service accounts; credentials and personal viewing history are not part of the distributable source or APK.

Languages depend on the chosen copy of a video. If it contains only one audio language, the app cannot create other language tracks. Unsupported embedded tracks can be described without being playable on a particular TV. External subtitle downloads and additional debrid services remain outside the current preview scope.

The Sony's decoder reported an 8K HEVC copy at 7680×4320 as exceeding its capabilities during a frozen-picture/continuing-audio report. This guard addresses that reported incompatibility; it does not convert unsupported video or establish the cause of every possible freeze. Choose a compatible version when needed.

This debug-signed sideload preview is approved for publication to [MM-482/pov-android-tv](https://github.com/MM-482/pov-android-tv) and the [v0.2.2-preview release](https://github.com/MM-482/pov-android-tv/releases/tag/v0.2.2-preview), with the signing and validation limits described above.
