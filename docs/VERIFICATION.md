# Build checks

Release builds run unit tests and Android debug/release lint before APK assembly. Signed APKs are verified with Android apksigner before publication.

[Actions runs and reports](https://github.com/husnainahmedrana/pov-rajput-android-tv/actions/workflows/apks.yml)

Checks cover Offcloud protocol handling, player controls and track selection, subtitle preferences, and existing application behavior. Device and media format compatibility can vary.

## POV Rajput 0.2.7

309 unit tests pass, including subtitle preference persistence, text-only track overrides, and one-second seek state, cancellation, boundaries, and long-video timing. Subtitle position checks cover authored text regions, persistence, restoring default placement, and retaining image subtitles. Debug and release lint pass.

On an onn 4K running Android 14, public multilingual DASH playback selects the saved French preference automatically. Switching to Off and English also changes the selected text tracks. D-pad repeat events stage one-second positions without seeking during the hold; release commits once and preserves the previous playing/paused state. Hidden controls, Back cancellation, and native D-pad action-row navigation are verified. The seek overlay contains only times and seconds moved. Native D-pad input adjusts the subtitle-position slider in Settings and during playback, the rendered text moves immediately, and Cancel restores the saved position. The test restores the original language and position preferences and confirms personal playback history is unchanged.

## POV Rajput 0.2.6

302 unit tests cover cache routing and safety, numbered priority ordering, subtitle preferences, update metadata and download restrictions, and Continue watching persistence. Debug and release lint pass.

TV verification uses a synthetic Continue watching item across all five themes. Correctly signed update APK validation succeeds; mismatched checksum, version metadata, and signing-certificate fixtures are rejected. Public screenshots exclude personal viewing history and API keys.

These checks cover the exercised paths and do not certify every Android TV model or service response.
