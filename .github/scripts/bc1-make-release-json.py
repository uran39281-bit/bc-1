"""Build a publication manifest (release.json) from the qualified CI APK and its offline-signed copy.

Run this on the private signing machine after signing the CI APK with the retained key:

    python bc1-make-release-json.py CI.apk SIGNED.apk vX.Y.Z/release.json \
        --version 1.4.6 --version-code 40 --source-commit <sha> --workflow-run <id> \
        --candidate-artifact-id <id> --qa-artifact-id <id>

Entry payloads identical in both files become "copy" ranges into the CI APK; everything else
(local headers, alignment, the APK signing block, central directory) is stored as public base64
"data". The manifest contains no private key material. The result is verified by reconstruction
exactly as .github/scripts/bc1-restore-signed-apk.py does in the publish workflow.
"""
import argparse
import base64
import hashlib
import json
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

CERTIFICATE = '3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d'
PACKAGE = 'com.blackice.bc1.preview'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def payloads(path, data):
    """Name -> (payload start, payload length) for every stored entry."""
    result = {}
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            start = info.header_offset
            if data[start:start + 4] != b'PK\x03\x04':
                raise ValueError('Missing local header for ' + info.filename)
            name_length, extra_length = struct.unpack('<HH', data[start + 26:start + 30])
            result[info.filename] = (start + 30 + name_length + extra_length, info.compress_size)
    return result


def segments(source, source_path, signed, signed_path):
    available = payloads(source_path, source)
    copies = []
    for name, (start, length) in payloads(signed_path, signed).items():
        if name in available and length > 0:
            origin, origin_length = available[name]
            if origin_length == length and source[origin:origin + length] == signed[start:start + length]:
                copies.append((start, origin, length))
    copies.sort()
    result, cursor = [], 0
    for start, origin, length in copies:
        if start < cursor:
            continue
        if start > cursor:
            result.append({'data': base64.b64encode(signed[cursor:start]).decode()})
        result.append({'copy': [origin, length]})
        cursor = start + length
    if cursor < len(signed):
        result.append({'data': base64.b64encode(signed[cursor:]).decode()})
    return result


def restore(manifest, source):
    out = bytearray()
    for segment in manifest['segments']:
        if 'copy' in segment:
            start, length = segment['copy']
            out.extend(source[start:start + length])
        else:
            out.extend(base64.b64decode(segment['data'], validate=True))
    return bytes(out)


def certificate(path):
    """SHA-256 of the signer certificate, via apksigner when it is on PATH."""
    try:
        output = subprocess.run(['apksigner', 'verify', '--print-certs', str(path)], check=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        print('apksigner unavailable or verification failed; certificate not checked:', error, file=sys.stderr)
        return None
    for line in output.splitlines():
        if 'certificate SHA-256 digest:' in line:
            return line.rsplit(':', 1)[1].strip()
    return None


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('ci_apk')
    parser.add_argument('signed_apk')
    parser.add_argument('output')
    parser.add_argument('--version', required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--workflow-run', type=int, required=True)
    parser.add_argument('--candidate-artifact-id', type=int, required=True)
    parser.add_argument('--qa-artifact-id', type=int, required=True)
    parser.add_argument('--allow-unverified-certificate', action='store_true',
                        help='skip the apksigner certificate check (not recommended)')
    args = parser.parse_args()

    source, signed = Path(args.ci_apk).read_bytes(), Path(args.signed_apk).read_bytes()
    found = certificate(args.signed_apk)
    if found != CERTIFICATE and not args.allow_unverified_certificate:
        raise SystemExit('Signed APK certificate is %s, expected the retained Preview certificate %s' % (found, CERTIFICATE))
    manifest = {
        'version': args.version,
        'version_code': args.version_code,
        'package': PACKAGE,
        'source_commit': args.source_commit,
        'workflow_run': args.workflow_run,
        'candidate_artifact_id': args.candidate_artifact_id,
        'qa_artifact_id': args.qa_artifact_id,
        'ci_apk_sha256': digest(source),
        'signed_apk_sha256': digest(signed),
        'certificate_sha256': CERTIFICATE,
        'segments': segments(source, args.ci_apk, signed, args.signed_apk),
    }
    if digest(restore(manifest, source)) != manifest['signed_apk_sha256']:
        raise SystemExit('Reconstruction check failed; manifest not written')
    Path(args.output).write_text(json.dumps(manifest, indent=2) + '\n')
    copied = sum(s['copy'][1] for s in manifest['segments'] if 'copy' in s)
    print('Wrote %s: %d segments, %.1f%% of the signed APK copied from the CI APK' % (
        args.output, len(manifest['segments']), 100.0 * copied / len(signed)))
    print('%s  %s' % (manifest['signed_apk_sha256'], 'Black-Ice-Editor-v%s-Preview-build%d.apk' % (args.version, args.version_code)))
