# POV Offcloud 0.2.4

POV Offcloud by **Rajpoot** adds Offcloud account support to the original Android TV port by **Mark Mateo**, based on **kodifitzwell's** POV Kodi add-on.

- Connect Offcloud using your own API key, encrypted on your TV with Android Keystore.
- Current Bearer-authenticated Offcloud account, JSON cache, cloud submission, status, history, and file-exploration APIs.
- Offcloud and TorBox playback-service selection; native Media3 playback and existing Trakt integration.
- Explicit video-version selection and episode filename matching.
- Pending downloads reuse request IDs; overlapping selections avoid duplicate submissions.
- Connection errors remain visible in the key-entry dialog so you can correct or retry without retyping.
- Rajpoot branding with original-author credits and GPL-3.0 notices preserved.
- Automated APK builds, unit tests, lint reports, tagged releases, and SHA-256 checksums.

## Install

Android TV 8.0 or later. Install `POV-Offcloud-release.apk`, then open Settings → Connect Offcloud with API key. No personal API key or account is included. Keep the same signing certificate for future upgrades to preserve local settings.

## Verification

248 unit tests passed, including the branded build. Account authentication, cloud-history access, and cache checks passed on a physical Android TV through ADB, including after a non-debuggable release update. These checks do not establish every codec, device, or authenticated playback combination. See `docs/VERIFICATION.md` for the final build record.

## Credits and license

Fork and Offcloud edition: Rajpoot. Original Android TV app: Mark Mateo / MM-482. Original POV Kodi add-on and visual identity: kodifitzwell. GNU GPL version 3; source and license are available in this repository. This independent app is not endorsed by Offcloud, TorBox, Trakt, or the original authors.
