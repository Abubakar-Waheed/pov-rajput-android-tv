# POV Standalone 0.2.2 audit record

Source version: **0.2.2-preview**, Android version code **5**. The final source passed **227 unit tests**, with **0 failures, 0 errors, and 0 skipped**. Android lint reported **0 errors and 56 warnings**. The combined playback audit, repeated mini-player audit, and final source/file-choice audit passed on the Sony Android 8 TV.

## Scope of this revision

- A slimmer integrated player controller with title, timeline, transport, audio, subtitles, boost, browse action, and loading state under one visibility root.
- Every audio/subtitle track exposed by the loaded file, including unknown languages and explanatory unsupported entries. Available languages belong to the selected file; the app does not invent additional tracks.
- Reliable sequential source/version and cloud-file focus, including middle choices, tall rows, scrolling, selection visibility, and Back restoration.
- Explicit file choice when a selected transfer has multiple eligible videos. A provider file index is never treated as a TorBox file ID.
- Decoded PCM software gain replaces the native Android loudness effect implicated in two Sony process crashes. Gain is bounded to Off/+3/+6/+9/+12 dB with signed sample saturation; encoded passthrough remains playable and reports boost as unavailable. Hardware audio offload is disabled. Normal system volume is unchanged.
- Fullscreen/mini handoffs retain the browsing root, catalog, scroll/focus state, and existing mini view. Activity slide/scale animations are disabled. Incoming video attachment waits for a visible, attached, valid surface, with cancellation of stale callbacks. Right at the last card stays scoped to that horizontal row and reaches the mini instead of chasing offscreen cards in another row.
- Unsupported video is paused with a safe format/resolution message and a choice to select another source. The generic check preserves supported formats and avoids creating new history/Trakt progress for blocked playback.

## Evidence requirements

| Check | Result |
| --- | --- |
| App and instrumentation APK assembly | Passed. |
| Complete unit suite | 227 passed; 0 failures, errors, or skipped. Includes 21 software gain/session cases, 42 anime/catalog cases, 18 compatibility cases, and 10 surface-readiness cases. |
| Android lint | 0 errors / 56 warnings. |
| Exact APK signature, package, and version | APK v2 signature verified; same local debug certificate as the installed app. `dev.pov.tv`, version 0.2.2-preview/code 5, minimum API 26. |
| Sony same-signature update without clearing data | Passed with install-as-update. Final normal Home/version check passed; TorBox and Trakt linked statuses remained true and both green dots were verified from screen pixels without exporting a screenshot. The audit companion package was removed. |
| Compact controller geometry and complete hide/reveal | Passed: 124 dp controller; title, timeline, transport, loading, and options share its visibility. D-pad restores controls; paused controls remain visible. |
| Real window-input D-pad navigation through all track choices | Passed: all 10 audio and 4 subtitle tracks listed; all 11 audio-menu rows reached and fully visible; audio switch, subtitle selection/off, and Automatic restored. |
| Rendered loading indicator hides with the controller | Passed with an explicitly rendered loading fixture; independent Media3 spinner remained hidden. |
| Decoded software gain and reset | Passed on the muted public AAC sample: 5,120 additional PCM frames processed with +3 dB, followed by Off/reset. This is processing evidence, not an audible HDMI measurement. |
| Home to mini player, Menu/hold Select, fullscreen return, pause preservation | Passed. Same player and browsing root; existing mini reused; Right-edge entry, Menu, long-click fallback, Center return, and Stop/release passed. Paused handoffs remained paused with 0 ms measured position drift. |
| Visible video after handoff | Passed with in-memory video-only pixel comparisons and decoder counters. Combined run: mini 26 changed grid cells / 41 rendered buffers; fullscreen 156 cells / 171 buffers. The separate repeat-mini run also passed. No motion-check pixels or hashes were exported. |
| All displayed source/file rows, including middle choices, on the TV | Final offline fixture passed: 7 rows in both directions, 5 middle sources opened, 25 middle-file selections, fully visible focused cards, exact source restoration on Back. No service transfer was created by this fixture. |
| Episode scrolling and exact selection on Back | Passed: 8 sequential, fully visible rows, S1E9 selection, exact Back restoration. Generic anime parsing/mapping cases also pass; this device case does not establish perfect metadata for all titles. |
| Reviewed public guest screenshots | Four actual Sony captures reviewed: Cinematic guest Home plus controls/audio/hidden public-sample playback. Five original generated concept images remain clearly labeled references; four other actual theme Home captures were not taken. |
| Exact APK/source ZIP privacy scan | Passed offline with no flagged issues: 400 APK entries, 39,392 DEX strings, and 129 source ZIP entries. Runtime account preferences, private device evidence/logs, caches, signing keys, and local tooling are excluded. Exact distributable hashes are supplied separately in SHA256SUMS.txt. |

The device runner injects keys through Android's window input system. Direct Activity key dispatch alone does not exercise Android's normal directional focus handling. Its loading check deliberately renders the indicator before timeout; that is a visibility fixture, not evidence of sustained network buffering. A muted public multilingual sample exercises track selection without adding personal viewing history. Positive processed PCM frame counts establish the software processing path, not an audible physical HDMI measurement. Encoded passthrough bypasses that processor.

Dialog bounds are translated from window-root coordinates into screen coordinates before checking their position against the display. The audit requires the whole sheet and focused rows to remain visible; the coordinate correction does not relax clipping checks.

The Sony decoder logs identified an 8K HEVC copy at 7680×4320 as exceeding reported capabilities during a frozen-picture/continuing-audio report. The new compatibility guard is covered by 18 unit cases, and the compatible public sample plays and renders on the device. The same private 8K file was not replayed after the guard update. This finding does not establish the cause of every freeze or add decoding capability to the TV. Wider codec, HDR, passthrough, network, and TV-model coverage remains necessary; physical HDMI gain and visual frame pacing were not measured.

Guest screenshots intentionally hide account indicators and viewing history. An interrupted early capture left that guest display visible; normal mode was restored and both linked-status booleans were verified without reading or exporting credentials. Local audit drivers now restore normal MainActivity in a finally block, including after failures. The app's real connection indicators use green for a stored linked account and gray when unlinked; they are not forced green to conceal a disconnected account.

Public source exports exclude local account preferences, device evidence, logs, caches, private signing material, and inspection/tooling folders. See [Privacy](PRIVACY.md). The owner has approved preview publication to [MM-482/pov-android-tv](https://github.com/MM-482/pov-android-tv) and the [v0.2.2-preview release](https://github.com/MM-482/pov-android-tv/releases/tag/v0.2.2-preview), with the validation scope and signing limits recorded here.

The public artifact checker also passed its 10 synthetic regression cases, and the shared export policy passed 15 filesystem fixtures. The final scan and source allowlist are safeguards for the reviewed archives, not proof of every possible secret's absence. The checker performs no image OCR or encrypted-payload inspection, does not establish APK/source equivalence, and does not verify third-party services. The four actual public screenshots were reviewed separately. Raw device diagnostics stay in excluded local verification storage.
