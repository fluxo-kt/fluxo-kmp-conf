#!/usr/bin/env bash
# Fast-forward `main` to a freshly-published release tag.
#
# Why this exists: `main` is the repo's JitPack `main-SNAPSHOT` surface and the
# branch `main`-tracking consumers follow, but releases are cut from `dev` and
# tagged there. Without automation, `main` silently rots behind the published
# release (it was 2 releases / 255 commits behind before the 0.14.1 catch-up).
#
# Why it is not a plain `git push`: `main` is hard-protected — enforce_admins,
# strict required status checks (the 6 build-matrix contexts, which are
# `cancelled` on a release commit because the release workflow supersedes them),
# required signatures, and linear history. A direct push is rejected even for an
# admin. The only safe path (validated manually during the 0.14.1 promotion) is:
# temporarily relax enforce_admins, fast-forward-push the already-signed tag
# commit (signatures + linear history still hold), then ALWAYS re-enable
# enforce_admins — including on push failure, via the EXIT trap below.
#
# Requires GH_TOKEN = a token whose identity is allowed by main's push
# restrictions (i.e. owned by the repo admin) AND carries Administration:write
# (to toggle enforce_admins) + Contents:write (to push). The default
# GITHUB_TOKEN CANNOT edit branch protection, so a dedicated secret is needed.
set -euo pipefail

fail() {
  echo "::error::$*"
  exit 1
}

: "${GH_TOKEN:?GH_TOKEN (admin-capable promotion token) is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
TAG="${TAG:?TAG (released tag name, e.g. v0.14.1) is required}"

branch="main"
protection_api="repos/${GITHUB_REPOSITORY}/branches/${branch}/protection"

tag_sha="$(git rev-list -n 1 "${TAG}" 2>/dev/null)" ||
  fail "Tag ${TAG} is not present in the checkout (need fetch-depth: 0)"

git fetch --no-tags origin "${branch}" ||
  fail "Could not fetch origin/${branch}"
main_sha="$(git rev-parse FETCH_HEAD)"

# Idempotent: nothing to do if main already contains the tag commit.
if git merge-base --is-ancestor "${tag_sha}" "${main_sha}"; then
  echo "::notice::origin/${branch} (${main_sha}) already contains ${TAG} (${tag_sha}); nothing to promote."
  exit 0
fi

# Fast-forward-only guard: refuse to promote unless main is a strict ancestor of
# the tag commit. NEVER force-push — divergence is a human-investigate event.
if ! git merge-base --is-ancestor "${main_sha}" "${tag_sha}"; then
  fail "origin/${branch} (${main_sha}) is not an ancestor of ${TAG} (${tag_sha}); refusing non-fast-forward promotion."
fi

# Capture and restore protection. The trap guarantees enforce_admins is
# re-enabled on EVERY exit path (success, push failure, or error).
admins_were_enabled="$(gh api "${protection_api}/enforce_admins" --jq '.enabled' 2>/dev/null || echo "true")"

restore_protection() {
  if [[ "${admins_were_enabled}" == "true" ]]; then
    if gh api -X POST "${protection_api}/enforce_admins" >/dev/null 2>&1; then
      echo "::notice::Re-enabled enforce_admins on ${branch}."
    else
      # Loud, non-fatal: the branch is briefly unprotected and a human must act.
      echo "::error::FAILED to re-enable enforce_admins on ${branch} — restore it manually NOW."
    fi
  fi
}
trap restore_protection EXIT

echo "::notice::Relaxing enforce_admins on ${branch} to fast-forward ${main_sha} -> ${tag_sha} (${TAG})."
gh api -X DELETE "${protection_api}/enforce_admins" >/dev/null ||
  fail "Could not disable enforce_admins on ${branch}"

git push "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" \
  "${tag_sha}:refs/heads/${branch}" ||
  fail "Fast-forward push of ${TAG} to ${branch} failed"

# Verify the promotion actually landed before trusting it.
landed_sha="$(gh api "repos/${GITHUB_REPOSITORY}/branches/${branch}" --jq '.commit.sha')"
[[ "${landed_sha}" == "${tag_sha}" ]] ||
  fail "Post-push ${branch} is ${landed_sha}, expected ${tag_sha}"

echo "::notice::Promoted ${branch} to ${TAG} (${tag_sha})."
