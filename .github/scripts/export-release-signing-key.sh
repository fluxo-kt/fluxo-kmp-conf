#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

escape_workflow_command() {
  local value="$1"
  value="${value//%/%25}"
  value="${value//$'\r'/%0D}"
  value="${value//$'\n'/%0A}"
  printf '%s' "${value}"
}

new_delimiter() {
  local suffix
  suffix="$(openssl rand -hex 16 2>/dev/null || uuidgen 2>/dev/null || printf '%s_%s' "$$" "${RANDOM}")"
  suffix="${suffix//[^A-Za-z0-9_]/_}"
  printf 'FKC_SIGNING_KEY_%s' "${suffix}"
}

[[ -n "${GITHUB_ENV:-}" ]] || fail "GITHUB_ENV is not set"
[[ -n "${SIGNING_KEY:-}" ]] || fail "SIGNING_KEY is not set"

begin="-----BEGIN PGP PRIVATE KEY BLOCK-----"
end="-----END PGP PRIVATE KEY BLOCK-----"
normalized="${SIGNING_KEY//\\n/$'\n'}"
normalized="$(printf '%s' "${normalized}" | perl -0pe 's/\A\s+//; s/\s+\z//')"

[[ "${normalized}" == *"${begin}"* ]] ||
  fail "SIGNING_KEY must contain an ASCII-armored private PGP key block."
[[ "${normalized}" == *"${end}"* ]] ||
  fail "SIGNING_KEY must contain a complete ASCII-armored private PGP key block."

key="${normalized#*"${begin}"}"
key="${begin}${key}"
key="${key%%"${end}"*}${end}"

if command -v gpg >/dev/null 2>&1; then
  gnupg_home="$(mktemp -d)"
  chmod 700 "${gnupg_home}"
  trap 'rm -rf "${gnupg_home}"' EXIT
  GNUPGHOME="${gnupg_home}" gpg --batch --quiet --import \
    <(printf '%s\n' "${key}") >/dev/null ||
    fail "SIGNING_KEY is not importable as a PGP secret key."
else
  echo "::notice::gpg is unavailable; Gradle signing will validate the normalized key."
fi

echo "::add-mask::$(escape_workflow_command "${key}")"
delimiter="$(new_delimiter)"
while [[ "${key}" == *"${delimiter}"* ]]; do
  delimiter="$(new_delimiter)"
done

{
  echo "ORG_GRADLE_PROJECT_signingInMemoryKey<<${delimiter}"
  printf '%s\n' "${key}"
  echo "${delimiter}"
} >> "${GITHUB_ENV}"
