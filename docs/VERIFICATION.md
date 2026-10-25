# Build checks

Release builds run unit tests and Android debug/release lint before APK assembly. Signed APKs are verified with Android apksigner before publication.

[Actions runs and reports](https://github.com/husnainahmedrana/pov-rajput-android-tv/actions/workflows/apks.yml)

Checks cover Offcloud protocol handling, player controls and track selection, subtitle preferences, and existing application behavior. Device and media format compatibility can vary.

## Unreleased: named source providers

366 unit tests and debug/release lint pass. Provider checks cover preserving the existing address, configuration validation, independent enable/disable state, parallel retrieval, partial failures, combined cache checks, exact duplicate merging, separate torrent files and signed URLs, and episode filtering. Android TV instrumentation exercises adding, editing, toggling and confirmed removal using temporary provider fixtures; the original encrypted source configuration is restored afterward.

## POV Rajput 0.2.12

353 unit tests and debug/release lint pass. Android TV instrumentation verifies all six Cinemeta shelves, header selection and first-poster navigation across all five themes, returning to the top of every main tab, and retaining Home when returning from grids and details. Device checks also cover cached playback and expired-link fallback, Continue Watching actions, keyboard and voice-result search submission, and shared-player link caching. Live Cinemeta catalog requests pass. Tracking writes use isolated fixtures during automated tests.

## POV Rajput 0.2.7

313 unit tests pass, including subtitle preference persistence, text-only track overrides, and one-second seek state, cancellation, boundaries, and long-video timing. Subtitle position checks cover authored text regions, persistence, restoring default placement, and retaining image subtitles. Audio preference tests preserve subtitle/video settings and overrides; accelerated seeking tests cover all speed thresholds, reversals, new holds, and end bounds. Debug and release lint pass.

On an onn 4K running Android 14, public multilingual DASH playback selects the saved French preference automatically. Switching to Off and English also changes the selected text tracks. D-pad repeat events stage one-second positions without seeking during the hold; release commits once and preserves the previous playing/paused state. Hidden controls, Back cancellation, and native D-pad action-row navigation are verified. The seek overlay contains only times and seconds moved. Native D-pad input adjusts the subtitle-position slider in Settings and during playback, the rendered text moves immediately, and Cancel restores the saved position. The test restores the original language and position preferences and confirms personal playback history is unchanged.

Preferred French audio is selected automatically on fresh playback. Switching to German preserves the subtitle language; unavailable-language fallback and Automatic are exercised. A longer public video verifies five-second and ten-second held-seek increments, commit on release, and restoration of one-second precision. Settings is visible and opens using the native remote keys in all five themes.

## POV Rajput 0.2.6

302 unit tests cover cache routing and safety, numbered priority ordering, subtitle preferences, update metadata and download restrictions, and Continue watching persistence. Debug and release lint pass.

TV verification uses a synthetic Continue watching item across all five themes. Correctly signed update APK validation succeeds; mismatched checksum, version metadata, and signing-certificate fixtures are rejected. Public screenshots exclude personal viewing history and API keys.

These checks cover the exercised paths and do not certify every Android TV model or service response.
