#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "::error::GH_TOKEN is required to create or update a GitHub release."
  exit 1
fi

release_tag="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
if [[ -z "$release_tag" ]]; then
  echo "::error::RELEASE_TAG or GITHUB_REF_NAME is required."
  exit 1
fi

version="${release_tag#v}"
target_commit="$(git rev-list -n 1 "$release_tag")"
notes_file="$(mktemp)"
trap 'rm -f "$notes_file"' EXIT

awk -v version="$version" '
  $0 == "## [" version "]" || index($0, "## [" version "] - ") == 1 {
    emit = 1
    next
  }
  emit && /^## \[/ {
    exit
  }
  emit {
    print
  }
' CHANGELOG.md > "$notes_file"

if [[ ! -s "$notes_file" ]]; then
  echo "::error::CHANGELOG.md has no release notes for $version."
  exit 1
fi

title="${release_tag}${RELEASE_SUFFIX:-}"
release_flags=(--draft --title "$title" --notes-file "$notes_file" --target "$target_commit" --verify-tag)
if [[ "${RELEASE_PRE:-false}" == "true" ]]; then
  release_flags+=(--prerelease)
fi

if gh release view "$release_tag" >/dev/null 2>&1; then
  gh release edit "$release_tag" "${release_flags[@]}"
else
  gh release create "$release_tag" "${release_flags[@]}"
fi
