# POV Rajput 0.2.7

- Added **Settings → Subtitle language**, with common languages and a full language list. Matching subtitles are selected automatically on new playback. Automatic follows Android caption preferences; Off disables subtitles. Manual track choices remain available during playback.
- Added **Settings → Audio language**, with automatic selection of a matching preferred track, normal fallback if unavailable, and an Automatic option. Manual audio choices remain available during playback.
- Replaced duration-based D-pad timeline jumps with one-second steps. Hold Left/Right to preview a target time and seconds moved, then release to seek. Playback resumes if it was playing; paused videos stay paused. Back cancels the preview.
- Left/Right still navigates between player action buttons when those buttons have focus.
- Long held seeks accelerate after moving a minute: five-second steps, then ten seconds after five minutes, and thirty seconds after fifteen minutes. Releasing or reversing restores one-second steps for precise adjustments.
- Removed home-screen account-status strips and reserved header space, keeping Settings and the other navigation tabs clear in every theme. Account controls remain in Settings.
- Removed the decorative author label from the navigation footer.
- Added a live text subtitle-position slider in Settings and the player's subtitle-style menu, with Reset and Cancel controls. The seek overlay shows only times and seconds moved.

## Install

Android TV 8.0 or later. Install the signed `POV-Rajput-release.apk` over an existing POV Rajput installation to retain accounts and settings.

## Credits and source

Maintained by **Rajput**. Original Android TV application: **Mark Mateo / MM-482** ([source](https://github.com/MM-482/pov-android-tv)). Original POV Kodi add-on: **kodifitzwell** ([source](https://github.com/kodifitzwell/repo)). GPL-3.0; original history, license, and acknowledgments are preserved.

See [0.2.6 release notes](RELEASE_NOTES_0.2.6.md) for earlier changes.
