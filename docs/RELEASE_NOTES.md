# POV Rajput 0.2.10

- Source results now display video quality and provider-reported file size. Missing metadata is shown as unavailable rather than estimated.
- Added larger bold badges with rounded corners for quality, file size, and explicit release tags such as DV, HDR, WEB-DL, REMUX, and codecs. Quality colors are yellow for 4K, red for 1080p, and purple for 720p. Badges wrap when needed and retain their colors during selection.
- Added source sorting in Settings and on the results screen: choose highest or lowest quality first, then smallest or largest file first within each quality. The default is highest quality first and smallest file first.
- Sources with unavailable quality or size appear after known values. Changing the sort order reuses the fetched results without creating a transfer or resolving a stream.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

See [0.2.9 release notes](RELEASE_NOTES_0.2.9.md) for earlier changes.
