# Build and release

Use Java 17, Gradle 8.14.3, and Android SDK platform 36 and build-tools 35. The original Windows bootstrap scripts remain available. GitHub Actions provisions these tools on Ubuntu.

## Local builds

```sh
gradle testDebugUnitTest lintDebug lintRelease
gradle assembleDebug
gradle assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/`. Release APK: `app/build/outputs/apk/release/`. A normal release build is unsigned unless the signing environment below is configured.

For device audits against a signed release, build `assembleReleaseAndroidTest` with `-PpovTestBuildType=release` and matching local signing configuration. This compiles the tests against release Kotlin internal symbols. Select an audit runner using `-PpovTestRunner=dev.pov.tv.PlaybackAuditInstrumentation`; the default debug test build remains available for debug app installs.

## Automated builds

The `Android APKs` workflow runs on pushes to `main`, pull requests, manual dispatch, and version tags. It tests and lints before building, saves separate debug/release artifacts, and retains the check reports. Pull requests receive no release signing credentials. When credentials are absent, ordinary builds save a clearly marked unsigned release artifact; tagged builds skip automatic release publication rather than publish an unsigned APK. Release signing is configured using private repository Actions secrets; the key is never included in the source or downloadable assets.

## Private signing configuration

Configure these repository Actions secrets. Do not commit a keystore, passwords, or a personal Offcloud key.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 encoding of the private APK signing keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing-key alias |
| `RELEASE_KEY_PASSWORD` | Signing-key password |

For local signing, use `POV_RELEASE_STORE_FILE`, `POV_RELEASE_STORE_PASSWORD`, `POV_RELEASE_KEY_ALIAS`, and `POV_RELEASE_KEY_PASSWORD` environment variables. Keep a private backup of the signing key. Changing certificates requires a separate app ID or reinstall, which can remove local account data. The Offcloud API key belongs on the TV and is never a build secret.

## Publish a release

1. Update `versionCode`, `versionName`, release notes, and verification details.
2. Commit to `main` and let the build pass.
3. Push a tag equal to `v` plus `versionName`, for example `v0.2.7-rajput`.

```sh
git tag v0.2.7-rajput
git push origin v0.2.7-rajput
```

The tagged workflow validates the version, builds and verifies the signed APK, then creates a GitHub Release containing the release APK, SHA-256 checksums, `update.json` updater metadata, license, release notes, and GitHub's automatic source archives. Only the publish job receives repository-contents write permission. No upstream repository is changed.

For the updater, every signed release must retain `POV-Rajput-release.apk` and `update.json`. Actions generates the manifest from the app version and the final signed APK checksum. Increase `versionCode` for each update and keep the package ID and signing certificate consistent. Drafts, prereleases, debug APKs, and older version codes are not offered by the updater.
