"""Restore an offline-signed APK from an exact CI payload and public ZIP/signature metadata.
No private signing material is stored or used here. Both complete files are hash-checked.
"""
import base64
import hashlib
import json
import sys
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


if __name__ == '__main__':
    manifest = json.loads(Path(sys.argv[1]).read_text())
    source = Path(sys.argv[2]).read_bytes()
    if digest(source) != manifest['ci_apk_sha256']:
        raise ValueError('CI APK hash does not match the qualified build')
    result = bytearray()
    for segment in manifest['segments']:
        if 'copy' in segment:
            start, length = segment['copy']
            if start < 0 or length < 0 or start + length > len(source):
                raise ValueError('Invalid copy range')
            result.extend(source[start:start + length])
        else:
            result.extend(base64.b64decode(segment['data'], validate=True))
    if digest(result) != manifest['signed_apk_sha256']:
        raise ValueError('Restored APK differs from the verified offline-signed file')
    Path(sys.argv[3]).write_bytes(result)
    print('Restored verified APK:', manifest['signed_apk_sha256'])
