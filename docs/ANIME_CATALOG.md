# Anime catalog research and implementation

Public metadata checks were performed on October 22, 2026. This document describes the **0.2.1-preview** source. All **42 focused anime/catalog cases passed** within the completed 158-test central build, with no failures, errors, or skipped tests; lint reported 0 errors and 52 warnings. The hardware audit remains incomplete, as recorded in the [audit record](AUDIT_0.2.1.md).

Anime browsing and search default to series, with separate movie and special/OVA filters. Selecting a season or cour shows only that entry's explicitly mapped episodes. Catalog-linked story parts, films, OVAs, and spinoffs are grouped separately. The implementation is generic: it has no Attack on Titan, Frieren, My Hero Academia, or other franchise-ID branch. Discovery and episode metadata never require a Kitsu login or bundle an account credential.

## Provider choice

| Option reviewed | Evidence and tradeoff | Decision |
| --- | --- | --- |
| Kitsu public JSON:API | The official documentation describes public GET access, text search, type filters, pagination, and related-resource inclusion. Live requests returned English/Japanese titles, posters, cover images, category data, and explicit Trakt/TVDB relations. | Primary anime discovery provider. |
| AniList GraphQL | Excellent anime discovery, but its Media schema does not provide IMDb/TVDB identities required by this app. An additional mapping service is still needed. Its current documentation warns of a reduced limit of 30 requests per minute, and its terms restrict bulk collection. | Not added as another dependency for this category. |
| TheBeastLT Anime Kitsu metadata | Its `kitsu:{id}` metadata supplies explicit IMDb root IDs and per-video IMDb season/episode numbers. Live checks covered several unrelated series, sequel cours, long final presentations, noncontiguous OVAs, and films. Uptime and mapping coverage are independent of POV. | Required per-selection episode mapping for the scoped anime details workflow. A show-root relationship alone is insufficient to choose an anime season's episodes. |
| Fribb anime-lists and related databases | The primary repository describes cross-service IDs and season/episode offsets. Many-to-many mappings and different season boundaries need careful handling; a whole downloaded database is unnecessary for the current category. | No database is bundled or downloaded by the app. |
| TMDB | Its official API requires an application API key or read-access token. It would add another credential setup requirement. | Kept out of this implementation. |
| MyAnimeList/Jikan | These were considered as alternate anime discovery routes. Their catalog identities would still require an independently verified IMDb bridge. | Not added; one working discovery provider keeps behavior and requests predictable. |

Kitsu's official docs identify JSON:API page limits, inclusion, and search semantics; the live `kitsu.app` endpoint was verified separately. It currently serves unauthenticated requests directly with HTTP 200. The older documented `kitsu.io` endpoint was also checked and returned HTTP 200. The app uses the current `kitsu.app` host, HTTPS, a fixed service URL, and properly encoded query parameters.

## Identity and episode behavior

Anime catalogs often split a show into individual seasons, cours, OVAs, or films. Trakt/IMDb may group those entries into one show. The app keeps discovery identity (`AnimeTitle.kitsuId`) separate from the existing IMDb playback identity so a catalog ID cannot accidentally be submitted to TorBox sources or Trakt as an IMDb ID.

When an anime is selected, `AnimeCatalogApi.details()`:

1. Requests one Anime Kitsu metadata resource for the exact selected Kitsu ID and movie/series type. The returned Kitsu identity, playback type, and supplied anime subtype must match the selection. All supplied IMDb roots must agree.
2. Retrieves canonical Cinemeta details for that root. The returned IMDb root and media type must match. A film stays a film, even when its anime metadata contains a single video numbered 1.
3. For a series, reads each video's explicit `imdb_id`, `imdbSeason`, and `imdbEpisode`. The local catalog episode must agree with the video's own ID. A root-only video ID is accepted only for a single presentation with explicit local episode 1, as used by some long specials. The canonical show must contain that exact season/episode pair.
4. Returns only the verified episodes from the selected anime entry. Missing or conflicting rows are counted and reported. Duplicate local episode mappings, two local episodes mapped to the same canonical episode, invalid numbers, foreign title IDs, and unavailable canonical episodes are excluded rather than replaced.

`catalogEpisode` is the local number displayed within that selected anime season or part. `absoluteEpisode` is optional and is displayed only when the metadata explicitly supplies it. `PlaybackIdentity` retains the canonical IMDb root and season/episode values used by sources and Trakt. Neither a season number nor an overall episode number is inferred from a title, array position, previous season length, or franchise-specific table.

The earlier root-only handoff could expose the entire parent show's episode list, including unrelated season-zero extras, after selecting a single anime season. The new scoped workflow does not use that handoff. `resolve()` remains a root/overview helper; its result must not replace the selected-entry episodes returned by `details()`.

An unavailable or ambiguous root produces a useful error. A partially mapped series shows only the verified choices with a missing-mapping notice. If no episodes can be verified, the overview remains available with an explanation and no substituted episode choices. Manual matching is an explicit user choice.

## Series, films, and related story parts

The `series` browse filter requests Kitsu TV and ONA entries. `movie` requests movie entries. `special` requests OVA and special entries. The parser checks those categories again locally; movies cannot enter a series page merely because a provider returns mixed data. The optional `all` filter must be selected explicitly.

`related(title)` follows only Kitsu-declared relationships. It walks a bounded chain of explicit prequel/sequel story links and returns chronologically ordered main-story seasons/parts, movies, special extras/OVAs, and other series such as spinoffs. It does not guess a franchise from matching title words. A large or paginated relationship chain is labeled incomplete.

Catalog format and story placement are separate. Attack on Titan's Final Chapters are typed as specials by Kitsu but explicitly linked as main-story sequels, so they appear with the story parts while preserving S4E29/S4E30. Compilation films remain in Movies; OVAs keep their noncontiguous season-zero identities under special extras. A selected OVA cannot become a TV season merely because it is the starting entry.

The category does not claim a title is playable because it appears in a catalog. Existing source discovery and TorBox availability still determine playback. Existing Trakt watchlist/history behavior consumes the canonical IMDb movie or selected show episode identity. No Kitsu account tracking or automatic anime-list synchronization is implemented.

## Live checks

The exact encoded app request with `page[limit]=20`, `page[offset]=0`, `include=mappings,categories`, `sort=popularityRank`, and `filter[subtype]=movie` returned HTTP 200 with 20 movie records and a next-page link. Separate requests verified the series subtype filter and a `Frieren` text search. Responses were inspected without account credentials.

| Selected entry | Kitsu ID | Verified selected scope and canonical identity |
| --- | --- | --- |
| Attack on Titan season 1 | `7442` | 25 choices; `tt2560140`, S1E1–25; overall 1–25. |
| Attack on Titan season 2 | `8671` | 12 choices; same show, S2E1–12; overall 26–37. |
| Attack on Titan season 3 part 1 | `13569` | 12 choices; S3E1–12; overall 38–49. |
| Attack on Titan season 3 part 2 | `41982` | Local episodes 1–10 map to S3E13–22; overall 50–59. |
| Attack on Titan Final Season part 1 | `42422` | 16 choices; S4E1–16; overall 60–75. |
| Attack on Titan Final Season part 2 | `44240` | 12 choices; S4E17–28; overall 76–87. |
| Attack on Titan Final Chapters 1 / 2 | `46038` / `47132` | One long presentation each; S4E29 / S4E30; overall 88 / 89. |
| Attack on Titan OVA | `7694` | Local episodes 1–3 map to S0E7, E12, E13. No overall number is invented. |
| Attack on Titan: No Regrets | `8674` | Local episodes 1–2 map to S0E15, E17. |
| Attack on Titan: Lost Girls | `13718` | Local episodes 1–3 map to S0E20, E22, E23. |
| Crimson Bow and Arrow / The Last Attack | `8672` / `49129` | Separate movie roots `tt3646944` / `tt33175825`; no TV episode selector. |
| Frieren season 1 | `46474` | 28 choices; `tt22248376`, S1E1–28; overall 1–28; later seasons excluded. |
| My Hero Academia season 2 | `12268` | 25 choices; `tt5626028`, S2E1–25; overall 14–38; other seasons excluded. |
| Your Name | `11614` | Movie `tt5311514`; no TV episode choices despite a numbered video in the anime metadata. |

An actual Kitsu relationship walk from Attack on Titan season 1 returned the main-story order `7442 → 8671 → 13569 → 41982 → 42422 → 44240 → 46038 → 47132`. The same responses linked compilation films separately and identified Junior High as a spinoff rather than another season of the parent show.

Episode lists can include announced or unaired episodes. These checks establish catalog access and identity/numbering behavior; they do not establish video source availability or completed playback.

AniList's live public `Media` introspection was also checked: 55 fields were returned, with identity fields `id` and `idMal`, and no IMDb or TVDB field. This confirmed that adding AniList would still require a separate cross-service identity bridge.

## Requests, caching, and errors

- Browse and search retrieve one 20-record page per action. The next offset advances by raw records, so omitted unsupported/explicit-adult records do not repeat a page.
- Public pages are cached in memory for ten minutes, bounded to twelve pages. Root/overview identities are bounded to 128 entries. Relationship pages have a ten-minute cache bounded to 24 entries; a related-story walk visits at most twelve entries and one relationship page per entry. The app does not build an offline copy of the provider database.
- HTTP 429 observes `Retry-After` with a minimum 30-second retry delay; requests during that delay fail visibly without repeated network calls. Network, forbidden, missing metadata, and service errors are shown without raw server response bodies, credentials, or request URLs.
- Explicit `nsfw` or `R18` records are omitted. Music videos and unknown formats are omitted because this app's source and tracking contracts support movies and TV series.
- Included mapping records are joined only through each anime's matching JSON:API relationships. Duplicate included identities, mismatched Kitsu responses, wrong movie/show types, and multiple conflicting IMDb roots fail validation.

## Public implementation interface

```kotlin
val anime = AnimeCatalogApi(trakt)
val page: AnimePage = anime.popular(type = "series", skip = 0)
val matches: AnimePage = anime.search(query = "Frieren", type = "series", skip = 0)
val selected: AnimeEpisodeCatalog = anime.details(selectedAnimeTitle)
val linked: AnimeRelated = anime.related(selectedAnimeTitle)
val episode: AnimeEpisodeChoice? = selected.groups.firstOrNull()?.episodes?.firstOrNull()
val identity: PlaybackIdentity? = episode?.identity // Original IMDb show/season/episode, if verified.
```

`type` accepts `series` (default), `movie`, `special`, or explicit `all`. `AnimePage` exposes `items`, `hasMore`, and `nextOffset`. `AnimeTitle` exposes `kitsuId`, `title`, playback `type`, display `category`, `year`, `startDate`, `poster`, `background`, `description`, `subtype`, `genres`, `relationRole`, and `mappingNotice`.

`AnimeEpisodeCatalog` exposes `anime`, `media`, `groups`, `missingMappings`, and `notice`. Its media episode list is scoped to the selection; replacing it with a fresh full-show list would discard that scope. `AnimeEpisodeGroup` exposes `title`, canonical `season`, and `episodes`. Each choice exposes `catalogEpisode`, optional `absoluteEpisode`, `title`, `released`, `thumbnail`, and canonical `identity`.

`AnimeRelated` exposes `seasons`, `movies`, `specials`, `otherSeries`, `truncated`, and `notice`. Fetching related titles is a separate operation so a relationship-service failure need not prevent already-verified episode choices from being shown. All metadata/network calls run on a worker thread.

The root/overview helper can use `TraktApi.lookupCatalogId(idType, id, type)`, which accepts `trakt` or `tvdb`, a numeric ID, and `movie` or `series`. That public lookup sends an application key without an OAuth token. The scoped `details()` workflow does not depend on a Trakt account or use a root-only lookup as an episode mapping.

## Regression coverage and limits

All forty-two focused anime/catalog cases passed in the central 0.2.2 test run: 19 in `AnimeEpisodeCatalogTest`, three in `GenericAnimeEpisodeCatalogTest`, 16 in `AnimeCatalogApiTest`, and four in `CatalogDetailsIdentityTest`. They are a subset of the complete suite recorded in the [current audit](AUDIT_0.2.2.md). Four compact public-data fixtures preserve representative episode and relationship facts. The non-Attack-on-Titan cases exercise the same production `details()` API and parser. These offline regression results do not prove every provider record correct; actual device results are recorded separately in that audit.

These examples establish the generic algorithm and cover important format differences. They do not establish perfect data for every anime. Third-party records, alternate cuts, anime recaps, regional episode orders, special numbering, missing external IDs, and video availability can still differ. Unsupported or conflicting mappings remain visible instead of being silently guessed.

## Attribution and primary references

Anime discovery metadata and images are supplied by **Kitsu**. The explicit IMDb mapping fallback is the independent **Anime Kitsu** service by **TheBeastLT**. Canonical movie/show details come from **Cinemeta**. Original artwork and title rights remain with their respective owners. The standalone app is independent of these services.

This implementation calls published metadata interfaces; no provider source code, full mapping database, or shared provider credential is copied into the app. The Anime Kitsu repository's LICENSE file currently contains Apache 2.0 while its package metadata says MIT. No source is incorporated here; if code or data is bundled in a future release, that discrepancy should be resolved first.

- [Kitsu official JSON:API documentation](https://hummingbird-me.github.io/api-docs/) — public access, headers, pagination, filters, resource inclusion, and schema.
- [Kitsu official API documentation repository](https://github.com/hummingbird-me/api-docs).
- [Kitsu server MediaResource](https://github.com/hummingbird-me/kitsu-server/blob/the-future/app/resources/media_resource.rb) — subtype/text querying and mappings relationships.
- [Kitsu server MappingResource](https://github.com/hummingbird-me/kitsu-server/blob/the-future/app/resources/mapping_resource.rb) — explicit external-site and external-ID attributes.
- [AniList GraphQL getting started](https://docs.anilist.co/guide/graphql/), [rate limiting](https://docs.anilist.co/guide/rate-limiting), and [terms](https://docs.anilist.co/guide/terms-of-use).
- [Anime Kitsu primary repository](https://github.com/TheBeastLT/stremio-kitsu-anime), [IMDb mapping source](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/static/data/imdb_mapping.json), and [repository LICENSE](https://github.com/TheBeastLT/stremio-kitsu-anime/blob/master/LICENSE).
- [Anime Kitsu live manifest](https://anime-kitsu.strem.fun/manifest.json).
- [Fribb anime-lists primary repository and mapping descriptions](https://github.com/Fribb/anime-lists).
- [TMDB official application authentication](https://developer.themoviedb.org/docs/authentication-application).
- [Trakt developer documentation](https://developer.trakt.tv/) — the official OpenAPI saved in `inspection/reference/trakt-openapi.json` specifies public `/search/{id_type}/{id}` lookups with optional movie/show filtering and application-key authentication.
- [Stremio official Cinemeta source](https://github.com/Stremio/cinemeta).
