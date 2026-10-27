# Install POV Rajput

POV Rajput requires Android TV or Google TV running Android 8.0 or later. Kodi is not required.

1. Download **POV-Rajput-release.apk** from the [latest published release](https://github.com/husnainahmedrana/pov-rajput-android-tv/releases/latest).
2. Transfer the APK to your TV and open it with a file browser. Allow installation from that browser if Android asks, then select **Install**.
3. Open **POV Rajput**. Connect your own Offcloud and/or TorBox account in Settings. Trakt is optional.
4. Open **Settings → Debrid Services → Debrid priority** to reorder connected services. Number 1 is preferred when multiple services have the same cached source.

POV Rajput uses package ID `dev.pov.tv.rajput` and installs separately from earlier POV packages. Connect your accounts once in POV Rajput; earlier installations retain their own account settings.

## Future updates

Use **Settings → Updates → Check for updates**. View the release notes, choose **Download and install**, and confirm the Android installation prompt. Android may first require allowing **Install unknown apps** for POV Rajput. Return to the updater and select **Install update** after enabling that setting.

Optional startup update checks can be enabled in Settings. Published stable releases are checked; drafts and prereleases are excluded. The updater verifies the APK checksum, package, newer version code, and signing certificate. Updating the existing POV Rajput installation preserves its settings and accounts.

If Android reports a signing conflict, use an APK signed by the same distributor. Avoid uninstalling if you want to keep local settings. Checksums are provided in **SHA256SUMS.txt** for manual download verification; **LICENSE.txt** contains the GPL license.

## Android TV controls

Use the D-pad to browse and **OK/Select** to open items. **Back** returns to the previous page. Hold **OK/Select** on a Continue watching item to remove it from that list. Subtitle background, bold text, and Small/Medium/Large size controls are available in Settings and during playback.

See the [setup guide](OFFCLOUD.md) for account and playback details.
