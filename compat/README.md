# Compatibility Model

This directory is the repo-controlled compatibility source of truth consumed by
verification tasks and generated TestKit fixtures.

Files:

- `matrix.tsv`: rows for build pins, declared consumer support, forward-tested
  canaries, unsupported combinations, and fixture profiles.
- `sources.tsv`: official sources used by matrix rows.
- `unsafe-pattern-allowlist.tsv`: temporary allowlist for known unsafe patterns.

TSV rules:

- The first non-comment line is the header.
- Lines starting with `#` are comments.
- Keep cells single-line and tab-separated.
- Use `-` for intentionally absent values.

