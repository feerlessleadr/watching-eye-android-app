#!/usr/bin/env bash
# Fails unless the APK is signed by exactly one certificate whose SHA-256 fingerprint equals the expected one.
# This stops a release from being published with the wrong key (for example a debug or a new key), because such an
# APK could not be installed over the existing app.
#
#   tools/verify-apk-signature.sh <apk> <expected SHA-256 fingerprint, with or without colons>
#
# APKSIGNER may point to the apksigner executable; otherwise "apksigner" from the PATH is used.
#
# Exit codes: 0 = signed by the expected certificate
#             1 = signed by a DIFFERENT certificate (wrong key)
#             2 = no signature could be read (APK missing/unsigned, or apksigner could not run); the output is printed
set -uo pipefail

apk="${1:?usage: verify-apk-signature.sh <apk> <expected-sha256>}"
expected="$(printf '%s' "${2:?usage: verify-apk-signature.sh <apk> <expected-sha256>}" | tr -d ': \r\n' | tr 'A-F' 'a-f')"
apksigner="${APKSIGNER:-apksigner}"

# Both stdout and stderr are kept so that a failure explains itself.
output="$("$apksigner" verify --print-certs "$apk" 2>&1 | tr -d '\r')"
status=$?

# apksigner prints "Signer #1 certificate ..." or, for v3 signatures, "Signer (minSdkVersion=..., maxSdkVersion=...) certificate ...".
lines="$(printf '%s\n' "$output" | grep -E '^Signer .*certificate SHA-256 digest:' || true)"
signers="$(printf '%s' "$lines" | grep -c . || true)"
actual="$(printf '%s\n' "$lines" | head -n 1 | sed 's/.*digest: *//' | tr 'A-F' 'a-f' | tr -d ' ')"

if [ "$signers" != "1" ] || [ -z "$actual" ]; then
  echo "ERROR: could not read exactly one signer from '$apk' (found $signers; apksigner exit status $status)." >&2
  echo "----- apksigner: $apksigner" >&2
  printf '%s\n' "$output" | head -n 40 >&2
  echo "-----" >&2
  exit 2
fi
if [ "$actual" != "$expected" ]; then
  echo "ERROR: signing certificate mismatch." >&2
  echo "  expected: $expected" >&2
  echo "  actual:   $actual" >&2
  exit 1
fi
echo "OK: APK is signed by the expected certificate ($actual)"
