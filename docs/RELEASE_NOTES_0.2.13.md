# POV Rajput 0.2.13

- Added **Settings → Source providers** to manage multiple named scraper addresses.
- Turn providers on or off independently without removing their saved configuration. Edit names and addresses, or remove providers when no longer needed.
- Search combines results from enabled providers and displays their names instead of endpoint addresses.
- Matching torrent files and identical direct links appear once, with the contributing provider names. Different torrent files and signed playback URLs remain separate.
- Source requests run concurrently. If one provider is unavailable, results from the others remain accessible.
- Existing quality/file-size sorting and debrid priority apply to combined results. Returning to results after changing provider settings refreshes the search.
- Existing source addresses are preserved as **Source 1** and can be renamed. Provider addresses stay encrypted on the device.

Add your preferred scraper URLs in **Settings → Source providers** and choose a name for each. Use HTTPS Stremio-compatible add-on URLs.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

**Thanks to [WeTrakr](https://wetrakr.com/) and its developer [alberdev](https://www.reddit.com/user/alberdev/)** for building the tracking service, providing the API and logo, and enabling developer access for this integration.

Users supply their own registered WeTrakr Client ID in Settings. No developer Client ID, client secret or account tokens are included in the source or release APK.

WeTrakr API documentation: [api.wetrakr.com](https://api.wetrakr.com/). See [0.2.12 release notes](RELEASE_NOTES_0.2.12.md) for earlier changes.
