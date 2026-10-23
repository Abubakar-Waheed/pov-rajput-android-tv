# POV Offcloud for Android TV

### By Rajpoot

[![Android APKs](https://github.com/husnainahmedrana/pov-offcloud-android-tv/actions/workflows/apks.yml/badge.svg)](https://github.com/husnainahmedrana/pov-offcloud-android-tv/actions/workflows/apks.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**A native Android TV app with Offcloud and TorBox cloud playback, Trakt tracking, five themes, anime browsing, and a Media3 player.** Kodi is not required.

This is **Rajpoot's Offcloud edition**, forked from [MM-482/pov-android-tv](https://github.com/MM-482/pov-android-tv). The original Android TV port was created by **Mark Mateo**, based on the original POV Kodi add-on by **[kodifitzwell](https://github.com/kodifitzwell/repo)**. Their work, attribution, and GPL-3.0 license are preserved.

**[Download release APK](https://github.com/husnainahmedrana/pov-offcloud-android-tv/releases/latest)** · **[Setup guide](docs/OFFCLOUD.md)** · **[Build and release](docs/BUILD_AND_RELEASE.md)** · **[Release notes](docs/RELEASE_NOTES.md)**

## Rajpoot's contributions

- Added Offcloud API-key connection with Android Keystore encryption.
- Updated account validation to the current account API and Bearer authorization, replacing the retired proxy endpoint.
- Added JSON cache checks, cloud transfer preparation, download-status polling, cloud-history browsing, and exact file selection.
- Added Offcloud/TorBox service selection, safe episode matching, duplicate-transfer prevention, and retry reuse for pending downloads.
- Improved the connection dialog with visible progress/errors and retained input when validation fails.
- Added account-change/cancellation guards and read-only ADB diagnostics that do not expose keys or playback links.
- Added the Rajpoot app footer and About attribution, while crediting Mark Mateo and kodifitzwell.
- Added automated debug/release APK builds, tests, lint reports, checksums, and signed APK publication to tagged GitHub Releases.

## Screenshots

Actual screenshots from the branded app running on a physical Android TV. The home capture uses a temporary guest presentation to hide personal viewing history; saved accounts and history are not changed. Account keys, emails, personal cloud filenames, and private playback links are excluded.

### Home

![POV Offcloud home with Made by Rajpoot branding](docs/screenshots/home.png)

### Offcloud settings

![Offcloud connection and playback-service settings](docs/screenshots/settings.png)

### Appearance

![Five selectable appearances in POV Offcloud](docs/screenshots/appearance.png)

## Features

| Area | Included |
| --- | --- |
| Offcloud | Bring-your-own API key, cache checks, cloud downloads, library, status, file selection |
| TorBox | Original account connection, cloud libraries, cache and playback-link support |
| Catalog | Movie/show discovery, search, details, seasons, episodes, and anime browsing |
| Player | Native Media3, fullscreen/mini player, audio/subtitle choices, resume, compatibility guard |
| Trakt | Original device linking, watchlists, progress and watched tracking; own Trakt Client ID required |
| Appearance | Cinematic, Editorial, Neon, Cinema, and Dashboard |
| TV | Android TV launcher, D-pad navigation, Android 8.0/API 26 or later |

Raw cloud files can play without a catalog identity. They are not silently assigned a guessed movie/episode for Trakt tracking. Offcloud account plans, limits, availability, and source-provider results remain controlled by their respective services.

## Install and connect

1. Download `POV-Offcloud-release.apk` from [Releases](https://github.com/husnainahmedrana/pov-offcloud-android-tv/releases).
2. Sideload it on Android TV 8.0 or later. The launcher name is **POV Offcloud**; package ID: `dev.pov.tv.offcloud`.
3. Open **Settings → Connect Offcloud with API key**, enter your own key, and select **Connect**.
4. Select a movie or episode and choose a source. A multi-video transfer opens an explicit file/version choice.
5. Browse completed downloads through **Library**. If a transfer is pending, wait and select the same source again; its saved request is reused.

Use **Settings → Playback service** to switch to TorBox. No Offcloud key, TorBox key, Trakt token, or personal account is bundled. The app can coexist with the original POV package. Keep the same signing certificate for upgrades to preserve local settings.

## Builds and releases

Pushes to `main`, pull requests, and manual runs build APK artifacts after tests and lint. When release signing secrets are configured, version tags such as `v0.2.4-offcloud` publish the signed release APK, checksums, license, and release notes automatically. The first release is signed locally; its signing key has not been uploaded. Until signing secrets are configured, Actions saves an installable debug APK and a clearly labeled unsigned release artifact, and skips automatic release publication. Unsigned release artifacts must be signed before installation. Pull requests cannot access release signing credentials. See [build and release instructions](docs/BUILD_AND_RELEASE.md).

## Validation and limits

The Offcloud integration passed 248 unit tests, including the branded build. Live ADB checks on a physical Android TV verified saved-key authentication, cloud-history access, and cache API requests, including after an update to a non-debuggable release build. See the [final verification record](docs/VERIFICATION.md) for the packaged version and checks.

The screenshots and connection checks do not establish every TV, codec, HDR/audio path, or authenticated playback combination. This is an independent community fork.

## Source and acknowledgments

**Thank you to Mark Mateo for creating and sharing the standalone Android TV application that makes this fork possible.** His work provides this edition's core TV interface, remote navigation, themes, catalog and anime browsing, Media3 playback, and original TorBox and Trakt integrations. Rajpoot's edition builds on that foundation; it does not claim authorship of the original Android TV application.

- Original Android TV source: **[Mark Mateo / MM-482 — pov-android-tv](https://github.com/MM-482/pov-android-tv)**.
- Fork base revision: [`c5a64e9`](https://github.com/MM-482/pov-android-tv/commit/c5a64e92232fa90889507114721e736c37450f2d), retaining the upstream Git history.
- Original POV Kodi add-on: **[kodifitzwell/repo](https://github.com/kodifitzwell/repo)**, including the credited POV visual identity and original fanart.

- **Rajpoot** — this Offcloud edition, integration work, connection fixes, branding, and release automation.
- **Mark Mateo / MM-482** — original standalone Android TV application, TV interface, themes, catalog integration, Media3 player, TorBox and Trakt support. [Upstream source](https://github.com/MM-482/pov-android-tv).
- **kodifitzwell** — original POV Kodi add-on and POV visual identity. [Original project](https://github.com/kodifitzwell/repo).
- **Android Open Source Project / AndroidX Media3**, **JetBrains / Kotlin**, and the original project's credited metadata and artwork providers.

This project is not endorsed by Offcloud, TorBox, Trakt, Kodi, or the upstream authors. Product names and artwork remain with their respective owners. See the in-app open-source notices and [original project documentation](docs/UPSTREAM_README.md).

## License

GNU General Public License version 3. See [LICENSE](LICENSE). Source accompanies each release through this repository and GitHub's source archives. Original notices and credits are retained.
