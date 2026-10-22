#!/usr/bin/env python3
"""Offline APK/source ZIP leakage checks. Never prints matched content or credentials.

Usage: python scripts/verify-public-artifacts.py --apk dist/app.apk --source dist/source.zip
       python scripts/verify-public-artifacts.py --self-test
Only Python's standard library is used. Archives are inspected without extraction.
"""
import argparse
import hashlib
import io
import ipaddress
import json
import math
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import string
import sys
import unittest
import zipfile

MAX_ENTRY = 64 * 1024 * 1024
MAX_TOTAL = 350 * 1024 * 1024
ROOT_FILES = {'.gitignore', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
              'app/build.gradle.kts', 'LICENSE', 'README.md'}
ROOT_DIRS = ('app/src/', 'scripts/', '.github/', 'docs/', 'design/')
PRIVATE_DIRS = {'.git', '.tooling', '.gradle', '.kotlin', 'inspection', 'verification',
                'captures', 'shared_prefs', 'databases', 'cache', 'code_cache', 'generated', 'build', '__pycache__'}
PRIVATE_FILES = {'local.properties', 'pov_accounts_encrypted.xml', 'playback.xml', 'pov_appearance.xml'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.p12', '.pfx', '.key', '.log', '.hprof', '.pcap', '.pcapng', '.db', '.sqlite', '.pyc'}
IPV4 = re.compile(r'(?<![\w.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![\w.])')
OPAQUE = re.compile(r'[A-Za-z0-9_\-]{32,256}')
QUOTED = re.compile(r'''["']([A-Za-z0-9_\-]{32,256})["']''')
JWT = re.compile(r'eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}')
KNOWN_TOKEN = re.compile(r'(?:github_pat_|gh[pousr]_)[A-Za-z0-9_]{20,}')
CIPHER = re.compile(r'v1:[A-Za-z0-9+/]{12,}={0,2}:[A-Za-z0-9+/]{20,}={0,2}')
PEM = re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----')
AUTH_VALUE = re.compile(r'''(?ix)(?:bearer\s+|[?&](?:token|access_token|refresh_token|api_key|client_secret|client_id)=)([A-Za-z0-9_\-%]{8,})''')
CONFIG_VALUE = re.compile(r'''(?ix)["'](?:trakt\.|torbox\.)?(?:client_id|access_token|refresh_token|api_key|client_secret|token)["']\s*(?:[:,=]|\bto\b)\s*["']([^"'\r\n]+)["']''')
XML_CONFIG = re.compile(r'''(?ix)<string\b[^>]*\bname\s*=\s*["'](?:trakt[._]|torbox[._])?(?:client_id|access_token|refresh_token|api_key|client_secret|token)["'][^>]*>\s*([^<]+)</string>''')
URL = re.compile(r'https?://[^\s<>"\x27]+')
PUBLIC_FIXTURE = re.compile(r'(?i)^(?:test[-_]|synthetic[-_]|fixture[-_]|example[-_]|fake[-_])')
SAFE_TEST_VALUES = {'synthetic', 'private-value', 'do-not-expose', 'access-refreshed'}
PUBLIC_ALPHABETS = {string.ascii_uppercase + string.ascii_lowercase + string.digits + '-_',
                    string.ascii_uppercase + string.ascii_lowercase + string.digits,
                    string.digits + string.ascii_lowercase + string.ascii_uppercase,
                    string.ascii_lowercase + string.ascii_uppercase + string.digits}


def safe_name(name):
    name = IPV4.sub('[address]', name)
    return OPAQUE.sub('[long-segment]', name)[:240]


def finding(rule, name, line=None):
    result = {'rule': rule, 'entry': safe_name(name)}
    if line is not None:
        result['line'] = line
    return result


def entropy(value):
    return -sum((value.count(c) / len(value)) * math.log2(value.count(c) / len(value)) for c in set(value))


def opaque_value(value, public_digests, sensitive=False):
    if PUBLIC_FIXTURE.match(value) or value in public_digests or value in PUBLIC_ALPHABETS:
        return False
    if not OPAQUE.fullmatch(value):
        return False
    if re.fullmatch(r'[a-fA-F0-9]{40}|[a-fA-F0-9]{64}', value):
        return entropy(value) >= 3.2
    classes = sum(bool(re.search(pattern, value)) for pattern in ('[a-z]', '[A-Z]', '[0-9]'))
    return classes == 3 and entropy(value) >= (4.1 if sensitive else 4.5)


def scan_text(text, name, public_digests, dex=False, binary=False):
    issues = []
    # The API and preference *field names* are public implementation details, not secrets.
    for number, line in enumerate(text.splitlines(), 1):
        rules = set()
        for match in IPV4.finditer(line):
            try:
                address = ipaddress.ip_address(match.group())
                # Dependency DEX contains literal wildcard/localhost defaults. These exact
                # standalone defaults do not identify the owner's LAN or account.
                dependency_default = dex and line == match.group() and (address.is_loopback or address.is_unspecified)
                if (address.is_private or address.is_loopback or address.is_link_local) and not dependency_default:
                    rules.add('private-network-address')
            except ValueError:
                pass
        if PEM.search(line):
            rules.add('private-key-material')
        if JWT.search(line) or KNOWN_TOKEN.search(line):
            rules.add('recognized-secret-format')
        if CIPHER.search(line):
            rules.add('runtime-encrypted-account-value')
        if any(len(m.group(1)) >= 24 and not PUBLIC_FIXTURE.match(m.group(1)) and m.group(1) not in SAFE_TEST_VALUES and '$' not in m.group(1) for m in AUTH_VALUE.finditer(line)):
            rules.add('literal-authorization-value')
        for match in list(CONFIG_VALUE.finditer(line)) + list(XML_CONFIG.finditer(line)):
            value = match.group(1)
            if len(value) >= 24 and not PUBLIC_FIXTURE.match(value) and '$' not in value and '{' not in value:
                if opaque_value(value, public_digests, sensitive=True):
                    rules.add('embedded-account-configuration')
        candidates = [line] if dex else [m.group() for m in OPAQUE.finditer(line)] if binary else [m.group(1) for m in QUOTED.finditer(line)]
        if any(opaque_value(value, public_digests) for value in candidates):
            rules.add('opaque-literal-needs-review')
        for match in URL.finditer(line):
            if any(opaque_value(part, public_digests) for part in re.split(r'[/?:&=]', match.group())):
                rules.add('opaque-url-configuration')
        issues.extend(finding(rule, name, number) for rule in sorted(rules))
    return issues


def dex_strings(data):
    """Read the DEX string table; string literals remain visible without a decompiler."""
    if len(data) < 112 or not data.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header')
    count, offset = struct.unpack_from('<II', data, 56)
    if count > 2_000_000 or offset + count * 4 > len(data):
        raise ValueError('Invalid DEX string table')
    # Class descriptors, prototype shorties and member names are compiler metadata,
    # not application configuration. Excluding their exact indices avoids treating
    # long camelCase methods with numeric suffixes as random account credentials.
    metadata = set()
    for size_offset, width, string_offset in ((64, 4, 0), (72, 12, 0), (80, 8, 4), (88, 8, 4)):
        size, table = struct.unpack_from('<II', data, size_offset)
        if size > 2_000_000 or table + size * width > len(data):
            raise ValueError('Invalid DEX metadata table')
        for index in range(size):
            metadata.add(struct.unpack_from('<I', data, table + index * width + string_offset)[0])
    values = []
    for index in range(count):
        cursor, = struct.unpack_from('<I', data, offset + index * 4)
        for _ in range(5):
            if cursor >= len(data):
                raise ValueError('Invalid DEX string offset')
            value = data[cursor]
            cursor += 1
            if value < 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.find(b'\0', cursor)
        if end < 0:
            raise ValueError('Unterminated DEX string')
        if index not in metadata:
            values.append(data[cursor:end].decode('utf-8', errors='replace'))
    return values


def entry_issues(name, kind):
    clean = name.replace('\\', '/')
    path = PurePosixPath(clean)
    issues = []
    if clean.startswith('/') or '..' in path.parts or re.match(r'^[A-Za-z]:', clean):
        issues.append(finding('unsafe-archive-path', name))
    parts = {part.lower() for part in path.parts}
    forbidden_parts = PRIVATE_DIRS - {'build'} if kind == 'apk' else PRIVATE_DIRS
    if parts & forbidden_parts or path.name.lower() in PRIVATE_FILES or path.suffix.lower() in PRIVATE_SUFFIXES or path.name.startswith('.env'):
        issues.append(finding('private-or-runtime-file', name))
    if kind == 'source' and clean not in ROOT_FILES and not clean.startswith(ROOT_DIRS):
        issues.append(finding('outside-source-allowlist', name))
    return issues


def public_hashes(source):
    # These are explicit tool download integrity pins, not a blanket exemption for hex secrets.
    values = set()
    with zipfile.ZipFile(source) as archive:
        for name in archive.namelist():
            if name.replace('\\', '/') == 'scripts/bootstrap.ps1':
                if archive.getinfo(name).file_size > MAX_ENTRY:
                    continue
                text = archive.read(name).decode('utf-8-sig', errors='replace')
                for line in text.splitlines():
                    if re.match(r"^\s*Get-VerifiedFile\s+'https://", line):
                        values.update(re.findall(r"'([a-fA-F0-9]{64})'", line))
    return values


def audit_archive(path, kind, public_digests=()):
    raw = path.getvalue() if isinstance(path, io.BytesIO) else Path(path).read_bytes()
    name = 'synthetic.zip' if isinstance(path, io.BytesIO) else Path(path).name
    result = {'kind': kind, 'file': safe_name(name), 'sha256': hashlib.sha256(raw).hexdigest(),
              'entries': 0, 'dex_strings': 0, 'issues': []}
    public_digests = set(public_digests)
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names = [info.filename.replace('\\', '/') for info in infos if not info.is_dir()]
            result['entries'] = len(names)
            if len(set(names)) != len(names):
                result['issues'].append(finding('duplicate-archive-entry', '[archive]'))
            if sum(info.file_size for info in infos) > MAX_TOTAL:
                result['issues'].append(finding('archive-too-large-to-audit', '[archive]'))
                return result
            required = {'AndroidManifest.xml', 'classes.dex'} if kind == 'apk' else {'app/src/main/AndroidManifest.xml', 'build.gradle.kts', 'LICENSE', 'docs/PRIVACY.md'}
            for name in sorted(required - set(names)):
                result['issues'].append(finding('required-entry-missing', name))
            for info in infos:
                if info.is_dir():
                    continue
                name = info.filename
                result['issues'].extend(entry_issues(name, kind))
                if stat.S_ISLNK(info.external_attr >> 16):
                    result['issues'].append(finding('archive-symlink', name))
                if info.flag_bits & 1 or info.file_size > MAX_ENTRY:
                    result['issues'].append(finding('entry-cannot-be-audited', name))
                    continue
                data = archive.read(info)
                if name.lower().endswith('.dex'):
                    try:
                        values = dex_strings(data)
                        result['dex_strings'] += len(values)
                        for value in values:
                            result['issues'].extend(scan_text(value, name, public_digests, dex=True))
                    except (ValueError, struct.error):
                        result['issues'].append(finding('invalid-dex-cannot-be-audited', name))
                else:
                    binary = b'\0' in data
                    # ASCII strings also cover binary XML/resource UTF-8 string pools and metadata.
                    for block in re.findall(rb'[\x20-\x7e\r\n\t]{8,}', data):
                        result['issues'].extend(scan_text(block.decode('ascii'), name, public_digests, binary=binary))
                    # Android binary resources can use UTF-16LE string pools.
                    for block in re.findall(rb'(?:[\x20-\x7e]\x00){8,}', data):
                        result['issues'].extend(scan_text(block.decode('utf-16le'), name, public_digests, binary=True))
    except (zipfile.BadZipFile, RuntimeError, OSError, ValueError):
        result['issues'].append(finding('archive-could-not-be-fully-read', '[archive]'))
    # Content can appear in several encodings/string tables. Report each location/rule once.
    result['issues'] = list({json.dumps(issue, sort_keys=True): issue for issue in result['issues']}.values())
    return result


class SyntheticTests(unittest.TestCase):
    def test_field_names_dynamic_values_and_public_fixtures_are_allowed(self):
        source = 'store.get("trakt.access_token"); headers["Authorization"] = "Bearer $token"\n' + '"trakt.client_id" to "synthetic-client-identifier-for-tests"'
        self.assertEqual([], scan_text(source, 'example.kt', set()))

    def test_private_address_is_detected_without_exposing_the_value(self):
        address = '.'.join(map(str, [192, 168, 17, 42]))
        issues = scan_text('connect ' + address, 'example.txt', set())
        self.assertTrue(any(i['rule'] == 'private-network-address' for i in issues))
        self.assertNotIn(address, json.dumps(issues))

    def test_random_client_id_and_hex_account_key_need_review(self):
        for value in [''.join(['aB7cD9eF2gH4jK6m', 'N8pQ0rS3tU5vW1xY']) * 2, hashlib.sha256(b'not-a-real-account').hexdigest()]:
            issues = scan_text('"trakt.client_id" to "' + value + '"', 'example.kt', set())
            self.assertTrue(issues)
            self.assertNotIn(value, json.dumps(issues))

    def test_runtime_preferences_and_signing_keys_are_rejected(self):
        for name in ['shared_prefs/pov_accounts_encrypted.xml', 'local.properties', 'private.keystore', 'verification/screen.png']:
            self.assertTrue(entry_issues(name, 'source'))

    def test_apk_signature_public_certificate_is_not_a_private_key(self):
        self.assertEqual([], entry_issues('META-INF/CERT.RSA', 'apk'))

    def test_literal_bearer_and_query_secrets_are_detected(self):
        for value in ['Bearer ' + 'opaque-' * 8, 'https://example.invalid/file?token=' + 'opaque-' * 8]:
            self.assertTrue(scan_text(value, 'example.txt', set()))

    def test_public_download_digest_has_a_narrow_explicit_exemption(self):
        digest = hashlib.sha256(b'public-download-integrity').hexdigest()
        self.assertEqual([], scan_text('"' + digest + '"', 'bootstrap.ps1', {digest}))
        self.assertTrue(scan_text('"' + digest + '"', 'unexpected.kt', set()))

    def test_binary_utf16_private_configuration_is_scanned(self):
        contents = io.BytesIO()
        with zipfile.ZipFile(contents, 'w') as archive:
            archive.writestr('app/src/main/assets/unexpected.xml', ('.'.join(map(str, [10, 7, 8, 9]))).encode('utf-16le'))
        contents.seek(0)
        self.assertTrue(any(i['rule'] == 'private-network-address' for i in audit_archive(contents, 'source')['issues']))

    def test_source_xml_literal_configuration_is_detected(self):
        value = ''.join(['aB7cD9eF2gH4jK6m', 'N8pQ0rS3tU5vW1xY']) * 2
        text = '<string name="trakt_client_id">' + value + '</string>'
        self.assertTrue(any(i['rule'] == 'embedded-account-configuration' for i in scan_text(text, 'strings.xml', set())))

    def test_binary_resource_string_is_detected_without_quote_context(self):
        value = ''.join(['aB7cD9eF2gH4jK6m', 'N8pQ0rS3tU5vW1xY']) * 2
        self.assertTrue(scan_text(value, 'resources.arsc', set(), binary=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--report', type=Path, help='Optional JSON report path; contains no matched values.')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        return 0 if unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SyntheticTests)).wasSuccessful() else 1
    if not args.apk or not args.source:
        parser.error('--apk and --source are both required unless --self-test is selected')
    if not args.apk.is_file() or not args.source.is_file():
        parser.error('Both artifact files must exist')
    try:
        hashes = public_hashes(args.source)
    except (zipfile.BadZipFile, OSError):
        hashes = set()
    archives = [audit_archive(args.apk, 'apk', hashes), audit_archive(args.source, 'source', hashes)]
    report = {'passed': all(not item['issues'] for item in archives), 'network_used': False, 'archives': archives,
              'limitations': ['Heuristic static checks cannot prove every secret is absent.',
                              'No OCR or visual review of images/video; no encrypted payload inspection.',
                              'Does not prove APK/source equivalence or third-party service behavior.',
                              'Recognized public synthetic fixtures and explicit bootstrap SHA256 pins are allowed.']}
    output = json.dumps(report, indent=2)
    print(output)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output + '\n', encoding='utf-8')
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
