# POV Standalone for Android TV 0.2.0 preview

Standalone app made by **Mark Mateo**, based on the original [POV Kodi add-on by kodifitzwell](https://github.com/kodifitzwell/repo). Distributed under GNU GPL version 3.

This update rebuilds the interface around the five saved design concepts. Cinematic is the default, with full-width featured artwork, a slim icon navigation rail, cyan focus, and landscape collections. Settings → Appearance also offers Editorial, Neon, Cinema, and Dashboard. Each changes its navigation, feature composition, typography, palette, and card layout. Theme selection persists across restarts and app updates.

The Anime section adds Kitsu discovery, anime series and film filters, search, and pagination. Titles are resolved using explicit catalog identifiers and then opened with the canonical movie or season/episode picker. Anime season and episode numbering is never guessed. A manual matching option is available when a mapping is missing or ambiguous. Catalog listings do not guarantee that a video source is available.

TorBox playback and the existing Trakt connection, watchlist, tracking, and encrypted account storage are retained. The selected Neon POV launcher icon remains available as the app's identity.

The supplied APK is a locally signed debug preview for sideload testing. Keep the same signing key for compatible future updates; a hosted CI debug build has its own key and cannot replace this installed APK without a signing plan. Do not uninstall the working app to resolve a signing mismatch, because uninstalling removes its account data.

See README for the completed validation results and docs/ANIME_CATALOG.md for research, service references, identity handling, and known limitations. The GitHub Actions workflow is included but will need a first hosted run after publication.
