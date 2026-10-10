#!/usr/bin/env bash
# Fails unless the APK is signed by exactly one certificate whose SHA-256 fingerprint equals the expected one.
# This stops a release from being published with the wrong key (for example a debug or a new key), because such an
# APK could not be installed over the existing app.
#
#   tools/verify-apk-signature.sh <apk> <expected SHA-256 fingerprint, with or without colons>
#
# APKSIGNER may point to the apksigner executable; otherwise "apksigner" from the PATH is used.
set -euo pipefail

apk="${1:?usage: verify-apk-signature.sh <apk> <expected-sha256>}"
expected="$(printf '%s' "${2:?usage: verify-apk-signature.sh <apk> <expected-sha256>}" | tr -d ': \r\n' | tr 'A-F' 'a-f')"
apksigner="${APKSIGNER:-apksigner}"

output="$("$apksigner" verify --print-certs "$apk" 2>/dev/null | tr -d '\r' || true)"
signers="$(printf '%s\n' "$output" | grep -c '^Signer #[0-9]* certificate SHA-256 digest:' || true)"
actual="$(printf '%s\n' "$output" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 | tr 'A-F' 'a-f')"

if [ "$signers" != "1" ] || [ -z "$actual" ]; then
  echo "ERROR: expected exactly one signer, found $signers (is the APK signed?)" >&2
  exit 1
fi
if [ "$actual" != "$expected" ]; then
  echo "ERROR: signing certificate mismatch." >&2
  echo "  expected: $expected" >&2
  echo "  actual:   $actual" >&2
  exit 1
fi
echo "OK: APK is signed by the expected certificate ($actual)"
