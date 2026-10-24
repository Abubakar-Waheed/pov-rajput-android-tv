# POV Rajput for Android TV

### By Rajput

[![Android APKs](https://github.com/husnainahmedrana/pov-rajput-android-tv/actions/workflows/apks.yml/badge.svg)](https://github.com/husnainahmedrana/pov-rajput-android-tv/actions/workflows/apks.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**A native Android TV app with Offcloud and TorBox cloud playback, Trakt tracking, five themes, anime browsing, and a Media3 player.** Kodi is not required.

This is **Rajput's Android TV edition**, forked from [MM-482/pov-android-tv](https://github.com/MM-482/pov-android-tv). The original Android TV port was created by **Mark Mateo**, based on the original POV Kodi add-on by **[kodifitzwell](https://github.com/kodifitzwell/repo)**. Their work, attribution, and GPL-3.0 license are preserved.

**[Download release APK](https://github.com/husnainahmedrana/pov-rajput-android-tv/releases/latest)** · **[Setup guide](docs/OFFCLOUD.md)** · **[Build and release](docs/BUILD_AND_RELEASE.md)** · **[Release notes](docs/RELEASE_NOTES.md)**

## Rajput's contributions

- Added Offcloud API-key connection with Android Keystore encryption.
- Updated account validation to the current account API and Bearer authorization, replacing the retired proxy endpoint.
- Added JSON cache checks, cloud transfer preparation, download-status polling, cloud-history browsing, and exact file selection.
- Added combined Offcloud/TorBox cache searches and selectable debrid priority, safe episode matching, duplicate-transfer prevention, and retry reuse for pending downloads.
- Improved the connection dialog with visible progress/errors and retained input when validation fails.
- Added account-change/cancellation guards and read-only ADB diagnostics that do not expose keys or playback links.
- Added transparent backgrounds, bold text, and Small/Medium/Large subtitle sizes for fullscreen and mini-player playback.
- Added a saved subtitle language preference, selected automatically when a matching track is available.
- Added a saved preferred audio language, with automatic selection and fallback when unavailable.
- Added a live subtitle-position slider in Settings and the player subtitle-style menu.
- Added one-second D-pad seeking with a target-time preview and seek on button release.
- Added direct cached playback: Offcloud avoids cloud transfers; TorBox resolves direct links and removes newly created cached entries before playback. Uncached downloads remain in the library.
- Added numbered debrid priorities with Move up / Move down controls.
- Added verified in-app updates from published GitHub releases and optional startup checks.
- Added long-press removal from Continue watching across all five themes and the Trakt page.
- Added automated debug/release APK builds, tests, lint reports, checksums, and signed APK publication to tagged GitHub Releases.

## Screenshots

Screenshots of POV Rajput for Android TV.

### Home

![POV Rajput home with Made by Rajput branding](docs/screenshots/home.png)

### Offcloud settings

![Offcloud connection and playback-service settings](docs/screenshots/settings.png)

### Appearance

![Five selectable appearances in POV Rajput](docs/screenshots/appearance.png)

## Features

| Area | Included |
| --- | --- |
| Offcloud | Bring-your-own API key, direct cached playback, cloud downloads, library, status, file selection |
| TorBox | Account connection, cloud libraries, cached playback with temporary-entry removal, direct links |
| Catalog | Movie/show discovery, search, details, seasons, episodes, and anime browsing |
| Player | Native Media3, fullscreen/mini player, audio/subtitle choices, transparent subtitle backgrounds, bold subtitles, three subtitle sizes, resume, compatibility guard |
| Trakt | Original device linking, watchlists, progress and watched tracking; own Trakt Client ID required |
| Appearance | Cinematic, Editorial, Neon, Cinema, and Dashboard |
| TV | Android TV launcher, D-pad navigation, Android 8.0/API 26 or later |

Raw cloud files can play without a catalog identity. They are not silently assigned a guessed movie/episode for Trakt tracking. Offcloud account plans, limits, availability, and source-provider results remain controlled by their respective services.

## Install and connect

1. Download `POV-Rajput-release.apk` from [Releases](https://github.com/husnainahmedrana/pov-rajput-android-tv/releases).
2. Sideload it on Android TV 8.0 or later. The launcher name is **POV Rajput**; package ID: `dev.pov.tv.rajput`.
3. Open **Settings → Connect Offcloud with API key**, enter your own key, and select **Connect**.
4. Select a movie or episode and choose a source. A multi-video transfer opens an explicit file/version choice.
5. Browse completed downloads through **Library**. If a transfer is pending, wait and select the same source again; its saved request is reused.

Connect TorBox as well to search both caches together. Open **Settings → Debrid priority** to see the numbered order. Select a service, then use **Move up** or **Move down**; number **1** is preferred. Shared cached sources appear once with both services listed; playback uses your preferred cached service, or the other service when only it has a cached copy. Library lets you browse either connected service. No Offcloud key, TorBox key, Trakt token, or personal account is bundled. The app can coexist with the original POV package.

From version 0.2.6, the app is named **POV Rajput**, with package ID `dev.pov.tv.rajput`. It installs separately from earlier POV packages. Connect your accounts once in POV Rajput; earlier installations retain their own settings.

## Cached playback and cloud downloads

All connected debrid services are checked together. A source cached on both uses your priority order. A source cached only on the second service plays through that service without switching Settings. A failed resolver can try another confirmed cached service, and cached failures never silently start an uncached download.

Offcloud cached sources resolve directly without adding a cloud-dashboard entry. If no connected service can resolve a cached source, the app reports an error instead of silently creating a download. Uncached sources still ask for confirmation before adding a cloud download, which stays in your library. Existing downloads are retained.

TorBox resolves direct links and removes newly created cached entries before playback begins. Cached packs let you choose a video before its playback link is resolved. A temporary entry can appear during resolution. Existing library entries are reused and retained. Uncached downloads remain in your library. If cleanup fails, the app reports an error; a temporary entry may need removal from the dashboard.

![Numbered debrid priority](docs/screenshots/debrid-priority.png)

## Subtitle style

Open **Settings → Subtitle style** or **Subtitles → Subtitle style** during playback. Enable **Transparent background** and/or **Bold text**. Choices are saved on-device and work with text subtitles in fullscreen and the mini player. Background transparency and bold text default to off. Size defaults to **Medium**, with **Small** and **Large** available. Image-based and burned-in subtitles retain their original appearance.

Text subtitle appearance using a sample caption:

| Original style | Transparent background + bold |
| --- | --- |
| ![Original subtitle style](docs/screenshots/subtitles-original.png) | ![Transparent bold subtitles](docs/screenshots/subtitles-transparent-bold.png) |

| Small | Medium | Large |
| --- | --- | --- |
| ![Small subtitles](docs/screenshots/subtitles-small.png) | ![Medium subtitles](docs/screenshots/subtitles-medium.png) | ![Large subtitles](docs/screenshots/subtitles-large.png) |

## Playback languages and seeking

Open **Settings → Subtitle language** to choose a preferred language. New playback selects a matching subtitle track when available. **Automatic (device preference)** follows Android's caption preferences; **Off** disables subtitles. You can still choose a different track for the current video from the player's subtitle menu.

Open **Settings → Audio language** to choose the preferred audio language. New playback selects a matching audio track when available, with normal player fallback when it is unavailable. **Automatic (player default)** restores the usual audio selection. Manual audio-track choices remain available in the player.

Choose **Settings → Subtitle position** or **Subtitles → Subtitle style → Subtitle position** during playback. Move the slider left to lower the text or right to raise it. Changes preview immediately on the video, or on a sample in Settings. Choose **Done** to keep the position, **Cancel** to restore the previous choice, or **Reset** to restore the original placement. Image-based and burned-in subtitles retain their original position.

While watching, press **Left/Right** to preview a one-second seek. Hold the button to move further; the overlay shows the target time and seconds moved. During the same hold, steps increase to five seconds after moving a minute, ten seconds after five minutes, and thirty seconds after fifteen minutes. Releasing or reversing direction restores one-second precision. Release to seek and resume if the video was playing. A paused video stays paused. **Back** cancels the preview. With the controls visible, focus the timeline to seek; Left/Right on the action buttons still navigates between them.

| Preferred subtitle language | Seek preview |
| --- | --- |
| ![Subtitle language preference](docs/screenshots/subtitle-language.png) | ![Seek target and seconds moved](docs/screenshots/seek-preview.png) |

![Live subtitle position adjustment](docs/screenshots/subtitle-position.png)

## App updates

Open **Settings → Check for updates** to view the latest compatible release and its notes, then choose **Download and install**. If Android asks, allow POV Rajput to install apps; return to the updater and select **Install update**. Android asks you to confirm installation. You can also enable **Check for updates on startup** in Settings; it defaults to off.

The updater downloads only published stable releases from this repository and checks the APK checksum, package, newer version code, and matching signing certificate. Existing POV Rajput accounts and settings are preserved during a normal update. Earlier POV packages need the initial POV Rajput installation separately; subsequent POV Rajput updates keep the same package.

![App update screen](docs/screenshots/updates.png)

## Continue watching

Hold **OK/Select** on a Continue watching card, then choose **Remove from Continue watching**. This works in all five themes and on the My Trakt page. Removal is local to this app and preserves resume positions and Trakt history. Playing the same movie or episode again makes it visible again.

![Long-press Continue watching menu](docs/screenshots/continue-watching.png)

## Builds and releases

Pushes to `main`, pull requests, and manual runs build APK artifacts after tests and lint. When release signing secrets are configured, version tags such as `v0.2.6-rajput` publish the signed release APK, checksums, update metadata, license, and release notes automatically. Release signing is configured through private GitHub Actions secrets. The private key is never committed or included in download assets. If signing secrets are absent, Actions saves an installable debug APK and a clearly labeled unsigned release artifact, and skips automatic release publication. Unsigned release artifacts must be signed before installation. Pull requests cannot access release signing credentials. See [build and release instructions](docs/BUILD_AND_RELEASE.md).

## Validation and limits

Automated tests and Android lint run before every release. Build results and reports are available in [GitHub Actions](https://github.com/husnainahmedrana/pov-rajput-android-tv/actions).

The screenshots and connection checks do not establish every TV, codec, HDR/audio path, or authenticated playback combination. This is an independent community fork.

## Source and acknowledgments

**Thank you to Mark Mateo for creating and sharing the standalone Android TV application that makes this fork possible.** His work provides this edition's core TV interface, remote navigation, themes, catalog and anime browsing, Media3 playback, and original TorBox and Trakt integrations. Rajput's edition builds on that foundation; it does not claim authorship of the original Android TV application.

- Original Android TV source: **[Mark Mateo / MM-482 — pov-android-tv](https://github.com/MM-482/pov-android-tv)**.
- Fork base revision: [`c5a64e9`](https://github.com/MM-482/pov-android-tv/commit/c5a64e92232fa90889507114721e736c37450f2d), retaining the upstream Git history.
- Original POV Kodi add-on: **[kodifitzwell/repo](https://github.com/kodifitzwell/repo)**, including the credited POV visual identity and original fanart.

- **Rajput** — this Offcloud edition, integration work, connection fixes, branding, and release automation.
- **Mark Mateo / MM-482** — original standalone Android TV application, TV interface, themes, catalog integration, Media3 player, TorBox and Trakt support. [Upstream source](https://github.com/MM-482/pov-android-tv).
- **kodifitzwell** — original POV Kodi add-on and POV visual identity. [Original project](https://github.com/kodifitzwell/repo).
- **Android Open Source Project / AndroidX Media3**, **JetBrains / Kotlin**, and the original project's credited metadata and artwork providers.

This project is not endorsed by Offcloud, TorBox, Trakt, Kodi, or the upstream authors. Product names and artwork remain with their respective owners. See the in-app open-source notices and [original project documentation](docs/UPSTREAM_README.md).

## License

GNU General Public License version 3. See [LICENSE](LICENSE). Source accompanies each release through this repository and GitHub's source archives. Original notices and credits are retained.
