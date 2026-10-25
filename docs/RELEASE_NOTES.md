# POV Rajput 0.2.11

- Added WeTrakr device-code login, token renewal, watchlists, Continue watching, Up next, and personal lists.
- WeTrakr is the only tracking service. Its official transparent logo replaces My tracking in navigation; old Trakt linking, settings and playback updates are removed.
- Home Continue Watching loads WeTrakr progress across devices. Long-press removal cancels exactly that movie or episode's WeTrakr playback session without deleting watched history.
- Liked WeTrakr lists appear as Home rows, with a Settings toggle and access to full lists.
- Added a scrobbling toggle for automatic playback updates and queued retries. Library reads and manual actions remain available while it is off.
- Added separate WeTrakr account controls and an application Client ID setting. No client secret is required or bundled; account tokens are encrypted on the device.
- Library reads are cached for two minutes. Requests respect server rate-limit backoff, and failed final playback updates are retried for their original account with their original timestamp.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

WeTrakr API documentation: [api.wetrakr.com](https://api.wetrakr.com/). See [0.2.10 release notes](RELEASE_NOTES_0.2.10.md) for earlier changes.
