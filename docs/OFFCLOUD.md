# POV Rajput

POV Rajput is based on the [Android TV application by Mark Mateo / MM-482](https://github.com/MM-482/pov-android-tv), with Offcloud integration by Rajput.

## Install and connect

1. Sideload the APK on Android TV 8.0 or later. It appears as **POV Rajput** and uses application ID `dev.pov.tv.rajput`, so it can coexist with the original POV app.
2. Open **Settings → Connect Offcloud with API key**.
3. Enter your own API key from your Offcloud account settings. Offcloud can be used alone or alongside TorBox.
4. Browse a movie or episode, open its sources, and select a source. For an uncached transfer, confirm adding it to your Offcloud account.
5. If the download needs longer than the brief preparation window, wait and select the same source again. Its saved request ID is reused. Completed downloads can also be opened through **Library**; choose **Offcloud cloud** when both accounts are connected.
6. When several eligible videos are available, choose the exact video version. Catalog episode sources are filtered against episode filename markers; ambiguous episode packs require manual file selection from the cloud library.

Connect both accounts to search both caches. **Settings → Debrid priority** shows numbered ranks: select a service and choose **Move up** or **Move down**. Number **1** is preferred when both have a cached copy; a source cached only on the other service plays there automatically. Shared sources show both services in one result. Both use their own credentials. The Offcloud integration does not require a TorBox account. Cloud files opened without a catalog identity do not produce guessed Trakt watched events.

## Privacy and account behavior

The APK and source contain no personal API key. Keys and locally remembered transfer IDs use the existing Android Keystore-backed encrypted account storage; Android backup is disabled. Keys are sent only to the fixed HTTPS Offcloud API origin. The source provider receives the catalog identifier, but never the Offcloud key. API redirects are rejected. Error messages do not include raw server responses, request URLs, or credentials.

Cached sources use the direct cache/download endpoint and do not create cloud transfers. Uncached downloads remain in your library. Existing account downloads are retained. Disconnecting Offcloud removes its locally saved key and request map. It does not delete downloads from the Offcloud account. Changing to another API key clears the local request map and invalidates previously prepared file choices. The cloud-library screen can still retrieve existing account downloads after reconnecting.

Selecting a source may create an Offcloud cloud download and consume the account's storage or usage allowance. Cache checks and cloud-library browsing do not create transfers. Service availability and account limits depend on Offcloud. This integration does not bypass those limits.

## API and scope

Historical API reference: https://github.com/Offcloud/offcloud-api . Its proxy endpoint is no longer available. Current protocol is also reflected in https://github.com/TheBeastLT/node-offcloud and https://github.com/MunifTanjim/stremthru/tree/main/store/offcloud .

Implemented: API-key validation through `GET /api/account/info`, Bearer authorization headers, JSON cache checks through `/api/cache/info`, cloud submission, status polling, archive/file exploration, and cloud history. Simple URL arrays and detailed file responses are supported. Playback uses the existing Media3 player. No torrent client runs on the TV. Offcloud Instant and Remote destination uploads are outside this build's scope.

Release APKs are signed and built with debugging disabled. Automated tests and lint reports are available in GitHub Actions.

## Build from source

The original `scripts/bootstrap.ps1` and `scripts/build.ps1` provision and use Java 17, Gradle 8.14.3, and Android SDK 35. Alternatively, use installed Java 17 or 21, Gradle 8.14, and SDK platform/build-tools 35:

```powershell
gradle assembleDebug testDebugUnitTest lintDebug --console=plain --no-daemon
```

Set `JAVA_HOME` and `ANDROID_HOME` to the installed tools first. The APK is under `app/build/outputs/apk/debug/` for a normal Gradle build. The repository's build script uses its own output directory, recorded in `.tooling/build-output-path.txt`.
