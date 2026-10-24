"""Create public updater metadata for the signed release APK."""
import hashlib
import json
import re
import sys
from pathlib import Path

apk = Path(sys.argv[1])
source = Path("app/build.gradle.kts").read_text()
metadata = {
    "schema": 1,
    "packageName": re.search(r'applicationId\s*=\s*"([^"]+)"', source).group(1),
    "versionCode": int(re.search(r'versionCode\s*=\s*(\d+)', source).group(1)),
    "versionName": re.search(r'versionName\s*=\s*"([^"]+)"', source).group(1),
    "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
}
apk.with_name("update.json").write_text(json.dumps(metadata, indent=2) + "\n")
