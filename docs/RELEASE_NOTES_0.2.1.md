# POV Standalone 0.2.1 preview — local change notes

**Standalone app made by Mark Mateo. Based on the original POV Kodi add-on by [kodifitzwell](https://github.com/kodifitzwell/repo).**

Version: **0.2.1-preview** · Android version code: **4** · Android TV 8.0 or later.

These notes describe the current source changes for local app verification. App/test APK assembly passed; **158 tests passed with 0 failures, 0 errors, and 0 skipped**. Lint reported **0 errors and 52 warnings**. APK v2 signature verification passed with the existing certificate, and the same-signature update installed on the Sony Android 8 TV. The hardware audit is incomplete after its controller-geometry stop; packaged checksums and guest previews are not final. GitHub publication, Reddit sharing, and publishing review are held until the app is right and the owner requests those steps. These notes are not a request to publish.

## Changes

- **Clearer remote navigation:** visible focus rings and stable selection/scroll restoration when returning with Back. TV details show one selected season at a time and prefer a regular season initially, with Specials separate.
- **Correctly scoped anime episodes:** selecting a season or cour shows only its verified episodes. Local and supplied overall numbers are displayed separately from the canonical IMDb S/E identity used for sources and Trakt. Missing/conflicting mappings produce a message rather than a substituted episode list.
- **Separate anime formats:** series, movies, and specials/OVAs have distinct browse filters. Catalog-declared relationships group story seasons/parts, compilation films, extras, and spinoff shows. The code is generic; Attack on Titan, Frieren, My Hero Academia, and Your Name are regression examples rather than hardcoded exceptions.
- **Browse while playing:** one Media3 session moves between fullscreen and a mini view. Back returns to POV browsing while the session continues. Select the mini view or use Return to video to reattach it; Stop playback saves progress and ends it. Browsing reserves extra space and reveals focused content above the mini overlay.
- **Playback options:** supported embedded audio/subtitle selection, subtitle-off controls, and Android session volume boost at Off/+3/+6/+9/+12 dB. Boost resets when the session ends and reports waiting/unavailable states.
- **Unified bottom controller:** title, loading status, timeline, transport buttons, and option actions share the same hidden controller root. Focusing Audio or Boost does not pin the controls during playback; option sheets stay available until Done/Back.
- **Account and source guards:** additional cancellation/disconnect/refresh protections for TorBox and Trakt, revision-safe queued tracking, source-action debouncing, and stable cloud-file resume identification.

## Playback behavior

Browse while playing works inside POV. Select the mini view or Now playing navigation to return directly to fullscreen; hold Select on the mini view for Return/Stop options when the TV reserves MENU for system settings. Pressing Home, switching to another app, or otherwise moving every POV screen out of the foreground pauses the session; returning resumes only playback that was running before leaving. This preview has no background playback service or system picture-in-picture mode. Leaving POV from its root screen offers Stop and leave when a session is active.

Audio/subtitle choices depend on tracks supported by the selected file and TV. External subtitle downloads are not included. Volume boost uses Android's session loudness effect; HDMI passthrough, offload, or unsupported routes can bypass it. An accepted gain setting does not establish louder physical output on every route.

## Accounts and updates

Bring your own TorBox and optional Trakt accounts. No passwords, API keys, account tokens, Trakt application Client ID, or client secret are bundled. This preview still requires a user-owned Trakt application registration. Tokens remain in encrypted device storage; changing/disconnecting the account clears or invalidates the relevant pending state.

Update over the existing `dev.pov.tv` installation using a compatible signing key and the normal update flow or `adb install -r`. Do not uninstall or clear app storage as a routine update step. Debug APKs built on different machines can have incompatible signatures; see [installation and update instructions](INSTALL.md).

## Validation and known limits

The current build/test/lint result is recorded in the [audit record](AUDIT_0.2.1.md), including the 42-case anime/catalog subset. The 52 lint warnings remain visible rather than broadly suppressed; they include localization, dependency/tool update, platform, layout/drawing, and resource recommendations. **The hardware audit has not yet been marked passed.** A muted public sample played with track switching and accepted/reset boost settings; the runner then stopped at controller geometry before remaining checks. No audible-gain, authenticated TorBox, or Trakt-sync result is established by that sample. Public anime data checks and focused fixtures cover important episode formats, but do not prove perfect mappings for every anime or video availability.

Service availability, account permissions, source readiness, multi-file packs, alternate episode orders, codec/HDR support, subtitles, and audio routes can still vary. Authenticated playback and Trakt synchronization require their own live checks. A linked badge, a catalog card, or a guest screenshot is not proof of those workflows.

The manifest disables backup and supplies explicit legacy and Android 12+ cloud/device-transfer exclusions across all nine supported storage domains. See [Privacy](PRIVACY.md) for provider requests, local history/storage, removal, and standard-platform limits.

The five appearances—Cinematic, Editorial, Neon, Cinema, and Dashboard—and the Mark Mateo/kodifitzwell credits remain included. Finish the app behavior and device checks first. Guest previews and checksums can document the verified local build without initiating any publication or social-sharing step.
