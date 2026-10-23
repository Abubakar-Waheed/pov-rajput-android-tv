# Build checks

Release builds run unit tests and Android debug/release lint before APK assembly. Signed APKs are verified with Android apksigner before publication.

[Actions runs and reports](https://github.com/husnainahmedrana/pov-offcloud-android-tv/actions/workflows/apks.yml)

Checks cover Offcloud protocol handling, player controls and track selection, subtitle preferences, and existing application behavior. Device and media format compatibility can vary.
