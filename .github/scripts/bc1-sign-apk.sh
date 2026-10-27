#!/usr/bin/env bash
# Re-signs the CI APK with the retained BC-1 Preview key so it installs over an existing BC-1 Preview
# and keeps its app data. The key comes only from repository secrets; it is decoded into the runner's
# temp directory and removed when this script exits. Without the secrets, signing is skipped (the
# debug-signed APK is still uploaded) and a warning explains why.
#
# Usage: bc1-sign-apk.sh <input.apk> <output.apk>
# Env:   KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (optional; defaults to the store password)
set -euo pipefail
in="$1"; out="$2"
# Certificate of the retained preview key (docs: v1.1.0/QA.md). A different key cannot update installs.
expected="3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d"

if [ -z "${KEYSTORE_BASE64:-}" ] || [ -z "${KEYSTORE_PASSWORD:-}" ] || [ -z "${KEY_ALIAS:-}" ]; then
  echo "::warning::Preview signing secrets are not set; only the debug-signed APK was produced. It will not install over an existing BC-1 Preview. See docs/SIGNING.md."
  echo "signed=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

apksigner=$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)
keystore="${RUNNER_TEMP:-/tmp}/bc1-preview.keystore"
trap 'rm -f "$keystore"' EXIT
umask 077
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore"
export KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

"$apksigner" sign --ks "$keystore" --ks-pass env:KEYSTORE_PASSWORD --ks-key-alias "$KEY_ALIAS" --key-pass env:KEY_PASSWORD --out "$out" "$in"
"$apksigner" verify --verbose "$out" | grep -E '^Verified using|^Verifies' || true

digest=$("$apksigner" verify --print-certs "$out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)
echo "Signer certificate SHA-256: $digest"
if [ "$digest" != "$expected" ]; then
  echo "::error::The APK was signed, but not with the BC-1 Preview key (got $digest, expected $expected). Installs would still refuse it as an update; check the BC1_KEYSTORE_* secrets."
  exit 1
fi
{
  echo "### Preview-signed APK"
  echo "Signed with the retained BC-1 Preview certificate \`$digest\`. Installs over an existing BC-1 Preview and keeps its data."
} >> "$GITHUB_STEP_SUMMARY"
echo "signed=true" >> "$GITHUB_OUTPUT"
