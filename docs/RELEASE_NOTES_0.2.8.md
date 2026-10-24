# POV Rajput 0.2.8

- Search movies and TV shows together, with type labels and instant All, Movies, and TV shows filters. If one catalog is unavailable, results from the other remain available with a retry option.
- Updated the Media3/ExoPlayer libraries from 1.8.0 to stable 1.11.1, including upstream playback and Dolby Vision fallback fixes.
- Enabled decoder initialization fallback and adapted the custom audio sink to the current Media3 API, preserving software volume boost and encoded-audio bypass.
- Updated the build SDK to 36. Android TV 8.0/API 26 remains the minimum supported version.

Playback still depends on the device decoder, connected display, and video profile. Updating the player does not add universal Dolby Vision decoding. Unsupported video remains blocked to avoid audio-only playback or incorrect-format output.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

See [0.2.7 release notes](RELEASE_NOTES_0.2.7.md) for earlier changes.
