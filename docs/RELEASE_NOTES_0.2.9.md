# POV Rajput 0.2.9

- Added automatic Dolby Vision compatibility handling for clear Profile 7/8 video with a verified HEVC Main10 HDR10 or HLG base picture. Native supported Dolby Vision remains preferred. Settings also offers Prefer HDR10 / HLG and Standard player modes for subsequent playback.
- Base-layer playback drops Dolby Vision metadata and enhancement-layer data. It does not reconstruct Profile 7 FEL or convert Profile 7 to Dolby Vision Profile 8.1. The device decoder and display must support the base format. Encrypted, unverified, and unsupported formats are not forced through this path.
- Profile 5 is excluded from base-layer fallback, with a clearer explanation when unsupported: it has no standard HDR10 base picture.
- Added Offcloud and TorBox logos to the playback controls, identifying the service that actually resolved the stream. The badge follows the session through browsing and fullscreen, hides with the controls, and is absent for direct streams.

Playback still depends on the device decoder, connected display, and video profile. Updating the player does not add universal Dolby Vision decoding. Unsupported video remains blocked to avoid audio-only playback or incorrect-format output.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

Service logo sources and ownership are listed in [Service logos](SERVICE_LOGOS.md). See [0.2.8 release notes](RELEASE_NOTES_0.2.8.md) for earlier changes.
