# Build checks

Release builds run unit tests and Android debug/release lint before APK assembly. Signed APKs are verified with Android apksigner before publication.

[Actions runs and reports](https://github.com/husnainahmedrana/pov-rajpoot-android-tv/actions/workflows/apks.yml)

Checks cover Offcloud protocol handling, player controls and track selection, subtitle preferences, and existing application behavior. Device and media format compatibility can vary.

## POV Rajpoot 0.2.6

302 unit tests cover cache routing and safety, numbered priority ordering, subtitle preferences, update metadata and download restrictions, and Continue watching persistence. Debug and release lint pass.

TV verification uses a synthetic Continue watching item across all five themes. Correctly signed update APK validation succeeds; mismatched checksum, version metadata, and signing-certificate fixtures are rejected. Public screenshots exclude personal viewing history and API keys.

These checks cover the exercised paths and do not certify every Android TV model or service response.
