# POV Rajput 0.2.14

The biggest update yet: a new **Flix** interface, a much smoother Home on TV hardware, TMDB catalogs, and a reworked player.

## Flix interface

- **Flix** is the new default theme: a living backdrop that follows your focus, title logos, a feature that rotates through the Top 10 every five seconds, and numbered **Top 10 Movies** and **Top 10 Shows** shelves.
- The menu stays hidden until you press **Left** from the first item of a row or **Back**. Back from any menu page returns to Home; Back again asks to leave.
- **Hold Back for one second** from anywhere, including the player, to open Settings.
- Returning to Home through the menu is instant.
- Movie details sit at the foot of the screen with the backdrop open above; show details add season tabs and episode stills (anime too).
- Details show an IMDb badge, year, runtime, type and genre chips, and a single **Play** (shows: **Play S1 E3**).
- Settings are grouped into cards with switches that change in place; every dialog, picker and input box has a matching dark design; notices replace system toasts.
- Your **Library** appears as poster cards matched from download filenames.
- A full-screen search with in-app **voice search** that shows your words as you speak.
- An animated POV launch splash.
- Editorial remains available; the Cinematic, Neon, Cinema and Dashboard themes were retired. Their users move to Flix.

## Faster everywhere

- Home scrolling: off-screen shelves are no longer drawn, artwork is sized to its card, backdrops are one pre-shaded image and image decoding runs at background priority. Janky frames fell from 55% to under 10% on an onn 4K box.
- Source results: debrid cache checks are batched (100 per Offcloud request) and run in parallel.
- WeTrakr Continue Watching appears instantly from your last visit, then refreshes; movies and episodes load side by side.
- Images for the page in front of you load first.

## Catalogs

- Add your own free **TMDB** key in Settings for Trending Today, Popular and Now Playing in Cinemas (choose your country) movies, and Trending Today, Popular and Airing Today shows. Without a key, Home uses Cinemeta.

## Playback

- **Play** searches fresh links, starts the first playable one (with Auto-play on) and resumes your position. **Continue Watching** on Home resumes instantly from the last link when it is under 24 hours old.
- New **Auto-play first result** switch. Sources above your TV's decoder limit (such as 8K on a 4K TV) are labelled, moved last and never auto-played.
- Seeking: one-second steps for the first minute of a hold, then 10 s, 30 s, 1 min and 2 min jumps.
- **Ends at** clock in the top-right corner, a Stremio-style loading and buffering splash, a new **Stop** button, and a restyled control strip.
- New installs default to bold subtitles on a transparent background, 3% above the bottom, with English subtitles and audio.

## Notes

- The microphone is used only while the voice-search screen is open; Android asks for permission the first time.
- All keys, tokens and Client IDs stay encrypted on your TV. None are included in the app or source.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license and acknowledgments are preserved.

**Thanks to [WeTrakr](https://wetrakr.com/) and its developer [alberdev](https://www.reddit.com/user/alberdev/)** for the tracking service, API, logo and developer access. This product uses the TMDB API but is not endorsed or certified by TMDB.

See [0.2.13 release notes](RELEASE_NOTES_0.2.13.md) for earlier changes.
