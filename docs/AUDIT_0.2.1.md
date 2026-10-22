# POV Standalone 0.2.1 audit record

Source version: **0.2.1-preview**, Android version code **4**. This record describes implemented fixes and the evidence needed to assess them. It does not claim the app is free of defects or that every third-party catalog, source, account, audio route, and TV has been verified.

**Current status: the central build/test/lint run passed, APK v2 signature verification passed, and the same-signature update installed successfully. The hardware audit is incomplete.** The automated device runner played a public sample and exercised track/boost controls, then stopped at its `unified_controller_geometry` check; that result is being diagnosed. No full device-audit pass or completed preview set is recorded. The owner has asked to get the app right before any GitHub/Reddit work; uploads, releases, sharing, and publishing review remain held.

## Findings and implemented resolutions

| Area | Problem addressed | Resolution in the current source | Remaining evidence |
| --- | --- | --- | --- |
| TV focus | A screen rebuild or Back navigation could lose the user's selection, and the focused item was hard to distinguish. | Stable focus identifiers and restoration preserve selection/scroll when available. Focused navigation, posters, and actions have a visible ring. | Exercise D-pad movement, Back, scrolling, asynchronous loads, and all five themes on the target TV. |
| General TV episodes | Rendering every season together exposed season-zero extras first and made regular episode selection cumbersome. | A season selector shows one season's episode list at a time, preferring a regular season initially and offering Specials explicitly. | Verify first entry, changing season, returning from sources, and missing episode metadata. |
| Anime season scope | Selecting an anime season/cour could open the entire parent show's episodes and unrelated extras. | Generic per-video mapping limits choices to the selected entry, with canonical IMDb root/S/E validation and visible missing/ambiguous mapping errors. Local and supplied overall episode numbers are presentation fields; source/Trakt identities stay canonical. | Central regression run and device navigation through representative anime entries. |
| Anime formats | Films, OVAs, long story specials, and spinoffs could be presented as interchangeable TV choices. | Series, movie, and special/OVA discovery filters are separate. Explicit catalog relationships group main-story seasons/parts, movies, extras, and spinoffs. Final-story specials can remain in the story sequence while compilation films stay movies. | Verify filters, relationship loading, alternate cuts, and provider errors. |
| Single long presentations | Some one-video specials use a root-only Kitsu video ID rather than an episode suffix. Rejecting that shape hid otherwise valid Final Chapters episodes. | Accept the exact root-only ID only for one video with explicit local episode 1; retain all IMDb root/type/S/E checks. Multi-episode/root-conflict regressions cover the rejection path. | Final regression run after this parser fix. |
| Fullscreen/mini playback | Leaving fullscreen could terminate or recreate playback instead of allowing browsing. | One in-app Media3 session is shared between fullscreen and mini views. Back browses while the session continues; Return reattaches it; Stop saves progress and releases it. | Test view handoff, seeking, error/retry, repeated Return/Back, and source replacement with real playback. |
| Mini overlay visibility | Lower-right browsing selections could be covered by the mini video. | Extra content space is reserved while the mini is present. Focus reveal accounts for its global bounds and moves covered content above it; the mini surface cannot own descendant focus. | Device checks of lower rows, D-pad entry/return, and preserved scroll position remain incomplete. |
| Foreground lifecycle | In-app Activity handoffs and leaving the entire app need different playback behavior. | A foreground gate coalesces in-app transitions. All POV screens leaving the foreground pauses the session; return resumes only a previously playing session. There is no background playback service. | Verify Home/app switch, fullscreen/browse transitions, screen recreation, paused-state preservation, and recovery. |
| Player controls | Audio/subtitle choices and volume adjustment needed clear, usable TV controls. | Supported embedded track selection has visible states; Android session loudness boost offers Off/+3/+6/+9/+12 dB and resets at session end. Unsupported/pending audio routes have explanatory messages. | Check actual tracks, subtitle-off behavior, gain reset, passthrough/offload, and the physical audio output. |
| Controller hiding | A thin timeline, detached option buttons, or loading status could remain after other controls disappeared. | Title, buffering status, timeline, transport, and option actions share one bottom Media3 controller. Its intermediate animation is disabled; merely focusing an option does not pin it open. Option sheets remain dismissible with Done/Back. | Confirm shared auto-hide, focused-option timeout, paused controls, and sheet behavior during playback. |
| Account cancellation/replacement | Late authorization or refresh responses could save state after cancellation, disconnect, or account replacement. | TorBox and Trakt use guarded account/auth state and cancellation paths. Trakt queued updates remain associated with the intended account revision. | Central race regressions plus authorized live cancel/disconnect/reconnect/refresh checks. |
| Rapid source actions | Repeated source selection could start overlapping resolutions. | Source actions are debounced and stale work is prevented from replacing the intended session. | Exercise repeated Select/Back/Retry with ready and pending sources. |
| Cloud resume | Raw cloud playback and later catalog identification could lose consistent resume identity. | Cloud playback uses a stable source key and preserves explicit canonical media identification for resume/tracking. | Reopen an identified cloud file, verify position, and confirm the intended Trakt episode. |
| Backup and transfer scope | The manifest flag alone does not describe every standard Android cloud/device-transfer path. | `allowBackup=false` is paired with explicit legacy backup rules and Android 12+ cloud/device-transfer exclusions for all nine supported app-storage domains. | XML is present and the lint run has no errors; manufacturer-specific migration, debugging, or compromised-device access is outside those standard rules. See [Privacy](PRIVACY.md). |

## Anime evidence

Public metadata and canonical episode facts were checked on October 22, 2026. The production runtime has no franchise-name or sample-ID conditional. The same `AnimeCatalogApi.details()` and parser support the regression examples below.

| Example | Scope checked |
| --- | --- |
| Attack on Titan seasons/cours | S1: 25 episodes; S2: 12; S3 part 2: local 1–10 maps to S3E13–22; Final Season part 2: S4E17–28. |
| Attack on Titan Final Chapters | One long presentation each at S4E29 and S4E30, with supplied overall numbers 88 and 89. |
| Attack on Titan OVAs | Explicit noncontiguous S0 mappings, such as No Regrets at E15/E17; not renumbered to S1. |
| Attack on Titan films/relationships | Compilation films retain movie roots; the eight-entry main-story prequel/sequel chain keeps Final Chapters with the story, apart from movie compilations and Junior High. |
| Frieren | Selected first season has exactly 28 S1 choices; later parent-show seasons are excluded. |
| My Hero Academia season 2 | Exactly 25 S2 choices, supplied overall numbers 14–38, canonical show identity retained. |
| Your Name | Movie identity retained; no TV episode picker despite the metadata's single numbered video. |

The central run passed all forty-two focused anime/catalog cases across `AnimeEpisodeCatalogTest` (19), `GenericAnimeEpisodeCatalogTest` (3), `AnimeCatalogApiTest` (16), and `CatalogDetailsIdentityTest` (4). Four compact public-data fixtures preserve the checked facts. This is a subset of the 158 passing unit tests.

See [the anime research and API notes](ANIME_CATALOG.md) for exact IDs, provider choices, mapping rules, public references, and limitations. A mapping check does not establish playable video availability.

## Current validation record

| Check | Current result for 0.2.1 |
| --- | --- |
| App and instrumentation APK assembly | Passed `assembleDebug` and `assembleAndroidTest` in the completed central run. |
| Central unit tests | **158 passed; 0 failures, 0 errors, 0 skipped**, including the 42-case anime/catalog subset. |
| Lint current source | **0 errors / 52 warnings**. No broad suppression was added to obtain this result. |
| APK signature | APK v2 signature verification passed with the existing signing certificate. |
| Same-signature update | Android accepted the same-package `install -r` update on the Sony Android 8 TV. |
| Public sample playback | A muted public DASH sample played on the TV. This was not authenticated TorBox playback. |
| Track selection | The partial device audit reported 10 supported audio tracks and 4 subtitle tracks, changed audio, selected subtitles, turned them off, and restored Automatic selection. |
| Volume boost | The Android effect accepted the requested gain and reset to Off. Audible gain and passthrough/offload output were not established by the muted sample. |
| Local history | The partial public-sample audit reported history unchanged after cleanup. |
| Unified controller geometry | Runner stopped at `unified_controller_geometry`; diagnosis is in progress. This is not yet classified as an app-layout defect or a harness-timing defect. |
| Remaining D-pad, mini lifecycle, controller hiding, Back, season-picker, and theme checks | Not yet completed in the current hardware run. |
| Authorized TorBox playback and Trakt synchronization | Remain unverified by this public-sample audit; connection badges alone are insufficient. |
| Public guest screenshots | Pending generation and review; account/device evidence is excluded. |
| GitHub/Reddit publication or publishing review | Held until the app is accepted and the owner requests that work; not performed. |

The 52 lint warnings consist of 35 English hardcoded/localization warnings, six library-update suggestions, one build-tool update suggestion, two future landscape-orientation platform warnings, and one each for target API, null-parent inflation, drawing allocation, overdraw, unused fanart, a redundant parent, icon shape, and a custom-view constructor. The blocking typography error was corrected. These warnings remain visible for follow-up.

As further results arrive, record the exact executed checks and device scope here. A unit test with a mocked transport or audio effect verifies the exercised state transitions, not a real server, stream, decoder, or HDMI gain. Instrumentation that dispatches a key directly verifies app handling, not a manufacturer's physical remote mapping. A guest screenshot verifies that rendered state, not connected account behavior. A successful local build does not establish a successful GitHub Actions run.

## Review and distribution boundary

The public source/package should include the app, build instructions, license/credits, documentation, focused test fixtures, and reviewed guest previews. It excludes `inspection/`, `.tooling/`, `verification/`, generated caches/outputs, signing material, private account/device evidence, tokens, API keys, user-entered application IDs, activation codes, and private stream URLs. No private device address is part of these instructions.

The standalone-app credit remains **Mark Mateo**, with attribution to **kodifitzwell** and the original POV project. Public material must distinguish generated design references from real guest screenshots. No upload, repository push, release publication, Reddit post, or publishing-review request is part of the current app fixes.
