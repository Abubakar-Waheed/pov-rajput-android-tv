# POV Rajput 0.2.12

- Continue Watching reuses the last successfully played link for up to 24 hours and searches for fresh sources when that link is unavailable.
- Long-press a Continue Watching item for **Open Media** or **Remove from Continue Watching**. Details include watchlist, watched-status, playlist and fresh-source actions.
- Home now offers Cinemeta catalogs in this order: **New Movies, New Shows, Featured Movies, Featured Shows, Popular Movies, Popular Shows**. These catalogs work without a WeTrakr account. WeTrakr Continue Watching and optional liked lists remain available.
- Improved D-pad navigation across Home, Movies, Shows, Anime, Library, WeTrakr and Settings. Shelf headings are selectable, entering a shelf starts at its first poster, and scrolling back up reveals the top content.
- Returning from details and grids preserves Home position. Removing Continue Watching items updates the row without rebuilding the whole page.
- Search supports voice input and submits directly from the keyboard Search/Enter action.
- Recognized cloud-library movies and shows display posters from Cinemeta.
- Editorial is the default theme for new installations.

Add your preferred scraper URL in **Settings → Source provider address** to find playback sources. Use an HTTPS Stremio-compatible add-on URL.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

**Thanks to [WeTrakr](https://wetrakr.com/) and its developer [alberdev](https://www.reddit.com/user/alberdev/)** for building the tracking service, providing the API and logo, and enabling developer access for this integration.

Users supply their own registered WeTrakr Client ID in Settings. No developer Client ID, client secret or account tokens are included in the source or release APK.

WeTrakr API documentation: [api.wetrakr.com](https://api.wetrakr.com/). See [0.2.11 release notes](RELEASE_NOTES_0.2.11.md) for earlier changes.
