# POV Rajpoot 0.2.6

- Renamed the app to **POV Rajpoot**, with package ID `dev.pov.tv.rajpoot`.
- Added direct cached-file resolution for Offcloud without creating a cloud-dashboard entry. Cached resolution failures do not silently create downloads.
- TorBox resolves direct cached-file links and removes newly created cached entries before playback. Existing library items are reused and retained.
- Cached TorBox packs show file/version choices first, then resolve only the selected video.
- Uncached downloads remain in the cloud library.
- Added **Small (80%)**, **Medium (100%)**, and **Large (130%)** text subtitle sizes. Medium preserves the original size.
- Transparent subtitle backgrounds and bold text remain available in Settings and the player subtitle menu.

## Install

Android TV 8.0 or later. Download `POV-Rajpoot-release.apk`.

The new package installs separately from POV Offcloud. Connect your debrid and tracking accounts in POV Rajpoot; existing account settings remain with the old app.

TorBox may briefly show a temporary entry while cached links are resolved. If removal fails, an error is shown and the entry may need removal from the dashboard. Offcloud uses its direct cached-download endpoint without creating a cloud entry.

## Credits and source

Maintained by **Rajpoot**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.