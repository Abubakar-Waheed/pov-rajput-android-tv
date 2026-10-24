# POV Rajput privacy

POV Rajput is maintained by Rajput, based on Mark Mateo’s Android TV application and the original POV project by kodifitzwell. This policy describes the current source implementation. The app has no developer operated account service, advertising SDK, analytics SDK, or developer telemetry endpoint. It communicates directly with the services below. Those services receive normal connection information, including your public IP address, and apply their own policies.

## Connections and information sent

| Service | Information used and sent | Purpose |
| --- | --- | --- |
| Offcloud (`offcloud.com`) | Your API key, selected torrent hashes/magnets, cloud request IDs and file choices. | Validate your account, check cached sources, obtain direct cached links, and manage transfers explicitly requested in the app. Cached direct playback does not create a cloud transfer. |
| TorBox (`api.torbox.app`) | Device authorization codes or the API key you enter; an account token; cloud folder and file identifiers; torrent hashes; the source you explicitly select. The download link endpoint requires your TorBox token in its HTTPS query. | Link your own account, display its cloud library, check cached sources, add a selected transfer, and obtain playback links. Browsing source results does not add transfers. |
| Trakt (`auth.trakt.tv`, `api.trakt.tv`) | Your registered application Client ID; device codes; OAuth access and refresh tokens; matched movie/show identifiers and episode numbers; watchlist changes and watched status you request; playback start, pause, stop, and progress for identified titles when connected. | Link your account, display lists and progress, maintain the watchlist, and synchronize identified playback. Public external-ID lookups send the application Client ID without an OAuth bearer token. |
| Cinemeta (`v3-cinemeta.strem.io`, `cinemeta-catalogs.strem.io`) | Catalog/search requests, search text, and IMDb movie/show identifiers. | Discover titles and obtain descriptions, artwork links, and episode lists. These requests do not include your TorBox or Trakt account tokens. |
| Kitsu (`kitsu.app`) and community Anime Kitsu metadata (`anime-kitsu.strem.fun`) | Anime search text, catalog, relationship and episode requests, and Kitsu identifiers. | Discover anime and verify catalog mappings. These public requests do not include your TorBox or Trakt account tokens. Matching can also use the public Trakt ID lookup above. |
| Artwork hosts, including `images.metahub.space`, and hosts named in catalog artwork | Artwork URLs and ordinary HTTPS connection information. | Display title artwork. Artwork requests do not deliberately attach your TorBox or Trakt account tokens. |
| Your configured source provider (default `torrentio.strem.fun`) | The requested IMDb title and, for a series, season and episode. Any provider configuration you include in its URL is sent to that provider. | Obtain source choices. POV does not add TorBox or Trakt authorization headers to these requests. |
| The selected HTTPS playback host | The resolved playback URL, which may itself contain a temporary authorization value, and video requests made by AndroidX Media3. | Stream the source you select. A direct provider URL can stream from its host rather than TorBox. |

Account information is shared with the service it authorizes for the functions above. Viewing progress and watched status are sent to Trakt when that account is connected and the playback has a matched identity. The app does not send your account tokens or viewing history to a Mark Mateo server. The app does not control third party logging, retention, or subsequent use.

## Information kept on the device

- Account and connection settings are stored in the app's private preferences encrypted using AES-GCM with an Android Keystore key. This includes TorBox tokens, Trakt tokens and Client ID, callback configuration, and a custom source provider address. The key is not exported by the app. This is not a guarantee against a compromised device or operating system.
- Recent playback keeps up to 40 records containing title, media identity when available, local source key, playback position, and duration. Failed Trakt updates are queued locally for retry. **These playback preferences are private to the app but are not additionally encrypted by POV.** They can contain cloud file titles or viewing history.
- The chosen theme is saved as a normal, nonsecret device preference. Catalog/relationship and artwork caches used by this implementation are held in memory. Playback uses normal Media3 buffering; this preview has no offline video download feature.
- The active playback URL is used in memory and the private player Activity's intent. Recent playback records do not store the resolved streaming URL. Android manages Activity/process state independently of POV.

The manifest sets `android:allowBackup="false"`. It also supplies explicit exclusion rules for all supported app storage domains: credential protected and device protected files, databases, preferences and root storage, plus app specific external storage. On Android 12 and later, both the standard cloud backup and device transfer sections exclude all of this app data. A separate legacy rules file applies on Android 11 and earlier. This covers encrypted account settings, local playback history, pending Trakt updates and appearance preferences through the standard Android backup mechanisms. Android documents why device transfer rules are needed in addition to the manifest flag on some manufacturers' devices. [Android backup configuration](https://developer.android.com/identity/data/autobackup), [Android 12 backup changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore).

These settings do not control a manufacturer's nonstandard migration tools, debugging access, a compromised or rooted device, operating system behavior outside the standard backup rules, or separately captured screenshots/logs. The preview build is intended for sideloading and may be debug signed; do not treat a development device as a vault.

## App updates

Manual update checks, and optional startup checks when enabled, contact GitHub for this repository’s published releases. Downloading an update contacts GitHub’s release asset servers. Requests contain no Offcloud, TorBox, or Trakt credentials or viewing history. GitHub receives normal network information such as your IP address. Update APKs are held in the app’s private cache and shared with Android’s installer through a temporary read grant.

Continue watching removals are stored locally. They hide the selected movie or episode without deleting its resume position or remote Trakt history. New playback restores that item’s visibility.

## Removing information

Settings can disconnect Offcloud, TorBox, and Trakt. Disconnecting invalidates in-flight connection attempts; disconnecting Trakt or changing its Client ID also discards unsent Trakt updates through the app UI. Disconnecting does not erase your local recent playback list, delete transfers from TorBox, erase remote Trakt history, or revoke every authorization held by a remote service. Manage remote access and account history in those services.

Android's app storage controls can clear POV's local app data, and uninstalling normally removes its local preferences. Reinstalling, restoring, and manufacturer behavior can vary. Never share account tokens, activation codes, private cloud/download URLs, or screenshots of personal viewing history in public bug reports.

## Source and release preparation

Release source packaging uses an explicit file allowlist. TV verification captures, inspection material, tooling, local machine settings, runtime app preferences, and private signing keys are excluded. The offline public-artifact checker examines archive entries and detectable strings; it does not read a TV's account data. A passing static check cannot prove the absence of every secret, inspect visual content in images/video, verify third party behavior, or replace review of the exact files being published.

Report a privacy concern through the issue tracker of the repository distributing your build, with a sanitized description. Do not attach credentials or private playback links. Publication remains a separate review action from preparing a local release bundle.
