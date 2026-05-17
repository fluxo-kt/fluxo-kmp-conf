# Latest-Build Compatibility SOP

This SOP is the source of truth for the latest-build, multi-version runtime
compatibility, and verification system for `io.github.fluxo-kt.fluxo-kmp-conf`.
Keep it prescriptive and current. Update the implementation state as each slice
lands.

## Intent

Build the plugin against the latest reasonable stable toolchain so development
uses current APIs, warnings, and compiler checks, while preserving declared
consumer support through explicit compatibility adapters, feature detection, and
graceful fallback. Prove compile-time safety and real consumer behavior with
Gradle TestKit-generated builds across Gradle, Kotlin/KGP, AGP, Compose, and JDK
combinations.

## Ground Rules

- Compile against latest stable pins selected by the compatibility matrix.
- Define runtime support with documented minimum supported versions per
  integration.
- Do not directly call an API newer than the declared runtime floor unless the
  call is behind a compatibility adapter.
- Prefer typed public APIs when available across the supported range. Use
  reflection only when a type or member is absent, renamed, removed, or has
  incompatible signatures across supported versions.
- Unsupported combinations fail fast with actionable messages, not linkage
  crashes or vague Gradle stacktraces.
- Docs, KDoc, CI, release checks, and tests consume the same compatibility
  source of truth.

## Compatibility Model

Maintain a repo-controlled model under `compat/`.

Concepts:

- `buildPin`: versions used to compile and publish this plugin.
- `declaredSupported`: versions promised to consumers and blocked by CI.
- `forwardTested`: latest versions tested as canaries but not promised when
  upstream compatibility tables do not yet declare support.
- `unsupported`: impossible, untested, or upstream-incompatible combinations.

Axes:

- Gradle runtime version.
- JDK running Gradle.
- KGP version.
- Kotlin language/API defaults.
- AGP version and path: non-KMP Android, AGP 8 KMP legacy, AGP 9 KMP-aware.
- Compose Multiplatform version and Kotlin Compose compiler version.
- KSP, Detekt, Vanniktech, BCV, Dokka where plugin code touches their APIs.
- Fixture type and required tasks.

Rules:

- Kotlin/KGP compatibility comes from official Kotlin docs.
- AGP/Gradle compatibility comes from official Android docs.
- Compose/Kotlin compatibility comes from official Compose Multiplatform docs.
- The model distinguishes KGP version from Kotlin language/API version.

## Adapter Architecture

Create explicit compatibility modules by integration instead of scattering
version checks:

- `gradleCompat`: Gradle-version gates, task/configuration-cache-safe helpers,
  wrapper/runtime checks.
- `kotlinCompat`: KGP version parsing, compiler option APIs, source-set APIs,
  KMP target APIs, language/API decisions.
- `androidCompat`: AGP version detection, AGP 8 vs 9 extension routing, setter
  wrappers, lint APIs, Android KMP plugin path.
- `composeCompat`: Compose plugin APIs, Compose compiler plugin alignment,
  desktop packaging/shrinker hooks.
- `publicationCompat`: Vanniktech/Gradle publish APIs, signing, local publish,
  release preflights.
- `verificationCompat`: Detekt/BCV/Dokka/report APIs.

Adapter contract:

- Public entry points accept typed Gradle `Project` and extension objects only
  when those types exist across the supported runtime floor.
- Newer or unstable API calls live in narrow helpers with version/capability
  checks.
- If a class may not exist in older supported versions, do not reference it in
  public signatures, top-level properties, field types, or eager initializers.
  Use reflection or isolated implementation classes loaded only after capability
  checks.
- `NoSuchMethodError` swallowing is allowed only for known forward-compat setter
  removals and must be per setter, not whole function.
- Every behavior-changing fallback logs at most one concise actionable message.

## Test Harness

Add a `compatibilityTest` JVM test suite using Gradle TestKit.

Harness requirements:

- Register `compatibilityTest` as a `JvmTestSuite`.
- Add `gradleTestKit()` and JUnit/Kotlin test dependencies.
- Generate temporary consumer projects under build output.
- Use `GradleRunner.withGradleVersion(...)` for the Gradle runtime axis.
- Generate literal plugin versions for KGP/AGP/Compose in fixture
  `plugins {}` blocks.
- Use plugin-under-test metadata for source-level tests.
- Add local Maven publication smoke mode for release-like marker/POM
  consumption.
- Run with isolated Gradle user home per test or per matrix row.
- Capture build output and assert absence of known crash signatures, not only
  success.

Profiles:

- `compat.profile=pr`: small blocking set: declared minimum, build pin, AGP 8
  boundary, AGP 9 boundary, Compose path, publication smoke.
- `compat.profile=full`: all declared-supported rows plus latest stable
  canaries.
- `compat.profile=release`: PR profile plus local Maven marker consumption,
  docs invariant checks, release preflight fixtures.
- `compat.profile=canary`: latest upstream versions outside full support; never
  required for release unless promoted.

Required fixtures:

- Kotlin JVM consumer: Kotlin JVM plugin plus this plugin; run `help`,
  `compileKotlin`, `test`, and `check`; verify JVM target alignment and no
  signing/publication noise on `help`.
- KMP consumer: applies KMP and this plugin; verifies
  `fkcSetupMultiplatform(config = {})`, target filtering, disabled targets, and
  source-set graph behavior.
- AGP 8 Android/KMP consumer: legacy `com.android.library` plus KMP path;
  verifies namespace, SDKs, lint, and absence of AGP 9-only assumptions.
- AGP 9 Android/KMP consumer: `com.android.kotlin.multiplatform.library`;
  verifies auto-created Android target handling, lint, namespace/SDK
  propagation, and clear failure for unsupported KMP Android app path.
- Non-KMP Android consumer: AGP 9 built-in Kotlin path; verifies
  `setupAndroidCommon`, lint, compile options, and setter wrappers.
- Compose Desktop consumer: Compose Multiplatform plus Kotlin Compose plugin;
  runs compile/test/package tasks relevant to the current OS; verifies shrinker
  hooks and desktop main-class behavior.
- Publication consumer: publishes to a temporary local Maven repository and
  consumes by plugin id/version through plugin marker metadata.

## Version Selection

For each update wave:

1. Pull upstream metadata and official compatibility docs.
2. Compute candidate rows from compatibility rules.
3. Classify each row as `declaredSupported`, `forwardTested`, or
   `unsupported`.
4. Run `compatibilityTest` for candidate rows before changing public claims.
5. Promote a latest version to build pin only if declared-supported rows pass,
   docs remain truthful, compile-only dependencies do not leak into runtime, and
   generated consumer builds pass.

Immediate candidates:

- Gradle wrapper `9.5.1`, classified as forward-tested until upstream
  KGP/Gradle docs allow declared support if necessary.
- AGP `9.2.1`, requiring Gradle `9.4.1+`; do not combine with a KGP row that
  officially tops at lower Gradle unless classified as canary.
- Kotlin/KGP `2.3.21`, KSP `2.3.8`, Compose `1.11.0` as a coupled tranche.
- Keep Kotlin language/API default separate from KGP build pin.

## Static Drift Prevention

Add verification tasks that fail before release:

- `verifyCompatibilityMatrix`: validates rule consistency and forbids
  impossible declared-supported rows.
- `verifyCompatibilityDocs`: README, CHANGELOG, AGENTS, and KDoc cannot claim
  unsupported ranges.
- `verifyLatestActionPins`: runs `actions-up` excluding Claude Code action.
- `verifyNoUnsafeCompatPatterns`: scans for raw `System.getenv`, raw exec,
  unguarded `taskGraph.whenReady`, production `resolvedConfiguration`, direct
  newer API calls outside compat packages, and unpinned workflow actions.
- `verifyReleaseDocs`: checks README Plugin Portal/JitPack snippets, changelog
  section/date policy, and version/tag consistency.

Keep allowlists small and justified at the call site.

## CI And Release

- PR gate: normal build matrix, existing integration checks,
  `compatibilityTest -Pcompat.profile=pr`, and static drift checks.
- Scheduled/manual: `compatibilityTest -Pcompat.profile=full`,
  `compatibilityTest -Pcompat.profile=canary`, and dependency update report.
- Release: `compatibilityTest -Pcompat.profile=release`, local Maven marker
  consumption, release preflight docs/version checks, tag-scoped concurrency,
  and repair-safe GitHub release creation after partial external publication.
- Branch protection must require either every integration/compat check or one
  aggregate required check that depends on them.

## Existing Defects To Fix Under This SOP

- Remove unconditional IDE-sync marking; sync-only setup must not run during
  normal builds.
- Replace `taskGraph.whenReady` mutation with configuration-time predicates or
  earlier target filtering.
- Stop configuration-time dependency resolution for dynamic plugin loading
  unless explicitly opted in.
- Move signing warnings and SCM probing to publish tasks only.
- Make local publish from `check` self-check/opt-in.
- Fix `fkcSetupMultiplatform(config = {})` behavior or docs, then lock it with
  TestKit.
- Fix stale README JitPack commit/Kotlin version and Kotlin floor
  contradictions.
- Harden CI permissions and checkout credential persistence.
- Add release workflow concurrency and repair-safe release mode.
- Update GitHub Actions with `actions-up`, excluding Claude action.
- Close stale Dependabot PRs after branch state is clean, then rerun security
  updates.

## Acceptance Criteria

- A generated consumer project proves every declared-supported matrix row.
- Unsupported combinations fail with explicit messages, not linkage/runtime
  crashes.
- Latest stable build pins are either declared-supported or documented as
  forward-tested with passing canary evidence.
- Docs cannot drift from the compatibility model.
- `./gradlew compatibilityTest -Pcompat.profile=pr` passes locally and in CI.
- Release profile consumes the plugin through a local Maven marker, not only
  `includeBuild`.
- `./gradlew help --warning-mode all` has no plugin-owned avoidable warnings.
- `./gradlew build assemble check --continue --stacktrace` and all existing
  `checks/*` paths pass.
- Dependency/security alerts are resolved or mapped to concrete top-level
  owners.
- Every implementation slice is committed with focused Conventional Commits;
  no accumulated dirty state.

## Implementation State

- 2026-05-17: SOP created. No compatibility model, generated TestKit harness,
  adapter refactors, static drift tasks, CI wiring, or defect fixes have landed
  yet.
- 2026-05-17: SOP committed as
  `e31b4b5 docs(compat): add compatibility system SOP`.

## Living Implementation Queue

Do not remove unfinished entries. Mark entries complete only after code/docs are
updated, verified, and committed. Add newly discovered work as separate entries.

- [ ] Update this SOP with every implementation slice, finding, verification
  command, failure, and commit hash that affects the compatibility system.
- [ ] Independently map the existing build layout, version catalog, test suites,
  workflows, docs, and plugin compatibility code paths before changing behavior.
- [ ] Verify current upstream compatibility data from official sources before
  writing version claims or changing build pins.
- [ ] Create the first repo-controlled compatibility model under `compat/` with
  explicit `buildPin`, `declaredSupported`, `forwardTested`, and `unsupported`
  concepts.
- [ ] Add typed model loading for Gradle tasks without configuration-time
  dependency resolution.
- [ ] Add `verifyCompatibilityMatrix` to reject internally inconsistent or
  impossible declared-supported rows.
- [ ] Add `verifyCompatibilityDocs` to compare README, CHANGELOG, AGENTS, and
  selected KDoc compatibility claims against the model.
- [ ] Add `verifyNoUnsafeCompatPatterns` with a small justified allowlist for
  raw env access, raw exec, `taskGraph.whenReady`, production
  `resolvedConfiguration`, direct variant API calls, and workflow action pins.
- [ ] Add `verifyReleaseDocs` for README Plugin Portal/JitPack snippets,
  changelog policy, and version/tag consistency.
- [ ] Add `verifyLatestActionPins` or a documented local wrapper around
  `actions-up`, excluding Claude Code action.
- [ ] Register `compatibilityTest` as a Gradle JVM Test Suite with
  `gradleTestKit()` and JUnit/Kotlin dependencies.
- [ ] Build generated TestKit fixture infrastructure with isolated Gradle user
  homes and captured output assertions for known crash signatures.
- [ ] Add Kotlin JVM consumer fixture rows for `help`, `compileKotlin`, `test`,
  and `check`.
- [ ] Add KMP consumer fixture rows for `fkcSetupMultiplatform(config = {})`,
  target filtering, all-targets-disabled configuration, and source-set behavior.
- [ ] Add AGP 8 Android/KMP fixture rows for the legacy `com.android.library`
  plus KMP path.
- [ ] Add AGP 9 Android/KMP fixture rows for
  `com.android.kotlin.multiplatform.library`.
- [ ] Add non-KMP Android fixture rows for the AGP 9 built-in Kotlin path.
- [ ] Add Compose Desktop fixture rows with matching Compose Multiplatform and
  Kotlin Compose plugin versions.
- [ ] Add publication fixture rows that consume the plugin through a temporary
  local Maven plugin marker.
- [ ] Remove unconditional IDE-sync marking so sync-only setup cannot run during
  normal builds.
- [ ] Replace `taskGraph.whenReady` mutation with configuration-time predicates
  or earlier target filtering.
- [ ] Stop configuration-time dependency resolution for dynamic plugin loading
  unless explicitly opted in.
- [ ] Move signing warnings and SCM probing to publish tasks only.
- [ ] Make local publish from `check` self-check/opt-in.
- [ ] Fix or document `fkcSetupMultiplatform(config = {})`, then lock behavior
  with TestKit.
- [ ] Fix stale README JitPack commit/Kotlin version and Kotlin floor
  contradictions using the compatibility model.
- [ ] Harden CI permissions and checkout credential persistence.
- [ ] Add release workflow concurrency and repair-safe release mode.
- [ ] Update GitHub Actions with `actions-up`, excluding Claude Code action.
- [ ] Close stale Dependabot PRs after branch state is clean, then rerun
  security updates if GitHub access is available and the branch state permits.
- [ ] Run `./gradlew compatibilityTest -Pcompat.profile=pr` and record evidence.
- [ ] Run `./gradlew help --warning-mode all` and record plugin-owned warnings.
- [ ] Run `./gradlew build assemble check --continue --stacktrace` and record
  evidence.
- [ ] Run each existing `checks/*` build, including `checks/intellij-platform`
  only when local prerequisites are acceptable, and record evidence or blocker.
- [ ] Perform adversarial self-audit against every SOP acceptance criterion
  before final response.
