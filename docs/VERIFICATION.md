# Verification record — Rajpoot edition

Date: 2026-10-23. Upstream base: `MM-482/pov-android-tv` at `c5a64e92232fa90889507114721e736c37450f2d`.

## Packaged app

- Version: `0.2.4-offcloud`, versionCode `9`, application ID `dev.pov.tv.offcloud`.
- Release build installed over the preview on a physical Android TV through ADB. Package flags confirm debugging is disabled.
- The installed build displays **Made by Rajpoot**. About and open-source notices credit Rajpoot's fork, Mark Mateo's original Android TV application, and kodifitzwell's original POV Kodi add-on.
- Existing Offcloud account data survived the release update using the same signing certificate.

## Service checks

Read-only instrumentation verified a saved key, account authentication, cloud-history retrieval, and the current cache API on the physical TV. These checks passed before and after a non-debuggable release update. No API key, account email, library filenames, or playback URLs are included in the public diagnostic record. No transfers are created by the diagnostic.

## Automated checks

- Offcloud protocol and integration suite: 248 tests passed with zero failures including the branded build.
- Connection-fix debug lint: zero errors / 57 warnings.
- Connection-fix release lint: zero errors / 51 warnings.
- Branded release assembly: successful; branded regression tests passed. GitHub CI results are available in Actions once published.

## Screenshots and limits

The new screenshots are actual physical-TV captures from the branded app. Temporary guest presentation hides viewing history without clearing saved data. Keys, personal cloud filenames, emails, and private playback links are excluded.

The account/API checks and screenshots do not certify every title, codec, device, HDR/audio path, or authenticated playback combination. Upstream's historical Sony playback audit is preserved separately and is not presented as a new test of this fork.
