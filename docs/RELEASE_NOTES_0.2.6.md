# POV Rajput 0.2.6

- Renamed the app to **POV Rajput**, with package ID `dev.pov.tv.rajput`.
- Added direct cached-file resolution for Offcloud without creating a cloud-dashboard entry. Cached resolution failures do not silently create downloads.
- TorBox resolves direct cached-file links and removes newly created cached entries before playback. Existing library items are reused and retained.
- Cached TorBox packs show file/version choices first, then resolve only the selected video.
- Uncached downloads remain in the cloud library.
- Added **Small**, **Medium**, and **Large** text subtitle sizes, with simple labels. Medium preserves the original size.
- Added numbered debrid priorities with **Move up** and **Move down** controls. Number **1** is preferred.
- Added **Settings → Check for updates** with release notes, download progress, APK integrity and signing checks, and Android install confirmation. Optional startup checks default to off.
- Added long-press **Remove from Continue watching** in all five themes and My Trakt. Hiding an item preserves local resume data and Trakt history; replaying it makes it visible again.
- Connected Offcloud and TorBox accounts are searched together. Choose the order in **Settings → Debrid priority**. Shared cached sources use your preferred service; sources cached only on the other service use that service automatically.
- Both cloud libraries are available from Library. Cached results list every service that has the source.
- Transparent subtitle backgrounds and bold text remain available in Settings and the player subtitle menu.

## Install

Android TV 8.0 or later. Download `POV-Rajput-release.apk`.

The new `dev.pov.tv.rajput` package installs separately from earlier POV packages. Connect your debrid and tracking accounts once in POV Rajput; earlier installations retain their own settings.

TorBox may briefly show a temporary entry while cached links are resolved. If removal fails, an error is shown and the entry may need removal from the dashboard. Offcloud uses its direct cached-download endpoint without creating a cloud entry.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.