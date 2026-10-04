# Compatibility Model

This directory is the repo-controlled compatibility source of truth consumed by
verification tasks and generated TestKit fixtures.

Files:

- `matrix.tsv`: one row per consumer build the compatibility suite runs, either
  declared-supported or expected to fail (`unsupported`). A version column is a
  claim the suite must execute, so each is applied to the generated consumer
  build: `jdkVersion` and `kotlinLangVersion`/`kotlinApiVersion` centrally in
  `compatRunner` (`-` there means the consumer KGP's default), the tool
  versions by each fixture's build script.
  `status` must match the runner: an `unsupported` row's runner expects the
  build to fail, any other row's runner expects it to pass.
- `sources.tsv`: official sources used by matrix rows.
- `unsafe-pattern-allowlist.tsv`: temporary allowlist for known unsafe patterns.

TSV rules:

- The first non-comment line is the header.
- Lines starting with `#` are comments.
- Keep cells single-line and tab-separated.
- Use `-` for intentionally absent values.

