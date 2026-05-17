# Latest-Build Compatibility SOP

This SOP is the live process guide for the latest-build, multi-version runtime
compatibility, and verification system for `io.github.fluxo-kt.fluxo-kmp-conf`.
The machine-readable compatibility truth belongs in `compat/`. README,
CHANGELOG, KDoc, CI, and release checks must be generated from or verified
against that model. Keep this SOP prescriptive, current, and useful for the next
implementation phase.
Required path:
`.ai/sop/2026-05-17-latest-build-compatibility-system.md`.

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
- Do not bump wrapper/catalog/plugin build pins until the `compat/` model,
  `verifyCompatibilityMatrix`, PR-profile TestKit rows, docs drift checks, and
  local Maven marker consumption pass.
- Each self-contained slice ends with verification evidence, a focused
  Conventional Commit, and `git status --short` evidence showing no accumulated
  uncommitted work except intentionally separate next-slice files.

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
- Android mode is a separate axis from AGP version. Non-KMP Android uses the
  legacy `com.android.library`/application extension path on both AGP 8 and 9;
  KMP+Android uses legacy `com.android.library` co-application on AGP 8, and
  the modern `com.android.kotlin.multiplatform.library` path on AGP 9.
- Compose Multiplatform version and Kotlin Compose compiler version.
- KSP, Detekt, Vanniktech, BCV, Dokka where plugin code touches their APIs.
- Fixture type and required tasks.

Rules:

- Kotlin/KGP compatibility comes from official Kotlin docs.
- AGP/Gradle compatibility comes from official Android docs.
- Compose/Kotlin compatibility comes from official Compose Multiplatform docs.
- The model distinguishes KGP version from Kotlin language/API version.
- Every row has a unique ID, source references, profile membership, fixture
  type, expected tasks, and either a promotion rationale or blocking rationale.
- `declaredSupported` and `unsupported` rows cannot overlap on the same
  normalized axis tuple.
- Every public compatibility claim maps to at least one model row.
- Generated fixture count must equal selected model row count for the active
  profile.

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
- Add `implementation(project())` for the non-default suite when tests need this
  plugin project's outputs; Gradle does not add project dependencies to custom
  suites automatically.
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
- Add negative rows for unsupported combinations. Assert exact actionable
  failure messages and absence of `NoSuchMethodError`, `ClassCastException`,
  `NoClassDefFoundError`, and `Could not initialize class`.

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
- Non-KMP Android consumer: legacy Android extension path under AGP 8 and AGP 9;
  verifies `setupAndroidCommon`, lint, compile options, and setter wrappers.
- Compose Desktop consumer: Compose Multiplatform plus Kotlin Compose plugin;
  runs compile/test/package tasks relevant to the current OS; verifies shrinker
  hooks and desktop main-class behavior.
- Publication consumer: publishes to a temporary local Maven repository and
  consumes by plugin id/version through plugin marker metadata.
- Runtime classpath leak gate: after local Maven marker consumption, inspect the
  consumer buildscript/runtime classpath and assert this plugin does not leak
  `org.jetbrains.kotlin:kotlin-compiler-embeddable` or
  `io.gitlab.arturbosch.detekt:detekt-core`.
- Configuration-cache gate: run key fixtures twice with configuration cache
  enabled and fail on plugin-owned cache problems. If an existing check disables
  CC for a specific diagnostic task, add a separate CC-compatible command.
- Wire `compatibilityTest` to `check` deliberately. Gradle creates a custom
  suite's `Test` task but does not add it to `check` by convention.

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

- Kotlin/KGP `2.3.21`: official Kotlin docs classify KGP `2.3.20-2.3.21`
  as fully supported with Gradle `7.6.3-9.3.0` and AGP `8.2.2-9.0.0`.
- Gradle wrapper `9.5.1`: Gradle current, but beyond the official KGP
  `2.3.21` fully-supported Gradle max; classify as `forwardTested` or canary
  until upstream tables align.
- AGP `9.2.1`: official Android docs require Gradle `9.4.1+`; do not combine
  with KGP `2.3.21` as `declaredSupported` because that exceeds KGP's official
  Gradle max.
- Compose Multiplatform `1.11.0`: official Compose docs say latest Compose is
  compatible with latest Kotlin, requires the matching Kotlin Compose compiler
  plugin version, and requires at least Kotlin `2.1.0`.
- KSP `2.3.8`: candidate coupled with KGP `2.3.21`; verify artifact/version
  availability before promoting.
- Keep Kotlin language/API default separate from KGP build pin.
- Freeze these build-pin candidates until the generated consumer harness and
  marker-consumption runtime classpath gate exist.

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

- Current SOP path is
  `.ai/sop/2026-05-17-latest-build-compatibility-system.md`.
- Landed slices: SOP creation/relocation, initial `compat/` model, static
  compatibility verifier, source-level Kotlin JVM TestKit smoke, lifecycle task
  coverage, profile row selection.
- In progress: release-like Kotlin JVM marker fixture that publishes to the
  repo localDev Maven repository and consumes by plugin id/version without
  `withPluginClasspath()`.
- Not landed: typed model loading beyond the local Gradle task, Android/KMP/
  Compose fixtures, negative rows, classpath leak checks, adapter refactors, CI
  wiring, and defect fixes.

## Current Findings And Constraints

- Current build pins are Kotlin `2.2.21`, KSP `2.2.21-2.0.5`, Gradle wrapper
  `9.3.1`, AGP `9.1.1`, Compose Multiplatform `1.10.3`, Detekt `1.23.8`,
  Vanniktech `0.36.0`, BCV `0.18.1`, Dokka `2.2.0`.
- Gradle wrapper currently uses `gradle-9.3.1-bin.zip`.
- Official-source constraint: KGP `2.3.21` fully supports Gradle only through
  `9.3.0`, while AGP `9.2` requires Gradle `9.4.1`; latest-candidate rows must
  stay `forwardTested`/canary until official tables align or the project chooses
  an explicit non-declared canary policy.
- Known unsafe-pattern call sites found by local scan:
  `FluxoKmpConfContext.kt` uses unconditional IDE-sync marking and
  `taskGraph.whenReady`; `DisableUnreachableTasks.kt` mutates task state from
  `taskGraph.whenReady`; `LoadAndApplyPluginIfNotApplied.kt` uses
  `resolvedConfiguration.resolvedArtifacts`; publication setup reads signing
  and SCM-related state during configuration.
- Minimal safe insertion points: register `compatibilityTest` in
  `fluxo-kmp-conf/build.gradle.kts` after the existing `tasks.test` block and
  before `buildConfig`; keep test-suite dependencies outside mirrored
  `MIRROR-START`/`MIRROR-END` regions because `self` does not need them. Add
  static verification tasks near `verifyBuildScriptMirror` and wire them to
  `check` there.
- Static verifier algorithms currently live in a repo-native
  `VerifyCompatibilityStaticTask` class inside `fluxo-kmp-conf/build.gradle.kts`.
  Do not use an undeclared external runtime for this gate. Do not put
  non-trivial verifier logic in `doLast` closures: Gradle script object capture
  breaks configuration-cache reuse. If the verifier grows beyond this local
  scope, graduate it to included build logic instead of adding ad hoc tooling.
- Static verification tasks must be build-script tasks in `:plugin`, not only
  plugin-runtime tasks, because runtime verification setup can disappear when
  tests are disabled, `check`/`test` are excluded, or the build is included with
  no startup tasks.
- Build-script wiring must tolerate included-build mode. Optional publication
  tasks, including localDev publication tasks used by marker tests, must be
  wired through lazy task collections rather than hard `tasks.named(...)` calls;
  otherwise every `checks/*` build can fail during configuration before it even
  reaches its own tasks.
- Check builds must respect `KMP_TARGETS` filtering too. Optional JS/Wasm/Yarn
  extension configuration must use presence checks, because an Android-only
  filtered run legitimately has no `kotlinYarn`/`kotlinWasmYarn` extensions.
- Existing `checks/*` builds consume the local plugin via composite
  `includeBuild("../../")`; they do not prove published plugin marker/POM
  behavior. Marker consumption must be a separate TestKit/release fixture.
- The repo localDev Maven repository is `_/local-repo`. It can contain stale
  artifacts, so marker fixtures must depend on
  `publishAllPublicationsToLocalDevRepository` and assert the exact marker POM
  plus runtime jar for the requested plugin id/version exist before running the
  consumer build. Do not infer marker coordinates from publication log lines.
- Generated TestKit consumers use isolated Gradle user homes and JUnit
  `CleanupMode.NEVER`; GradleRunner uses Tooling API and does not support
  `--no-daemon`, while daemon-held cache files can make JUnit temp cleanup fail
  after a successful consumer build.
- Signing-key checks must be pure during configuration. Missing signing is
  actionable only at remote publish task execution via
  `validateSignedReleaseBeforeRemotePublish`; non-publish tasks must not warn
  about unsigned publications.
- Detekt tasks generated for Gradle JVM Test Suite source sets must be
  classified as JVM when their source set name is a known JVM-only suite name.
  `detektCompatibilityTest` is the current coverage row for this rule.
- The first KMP fixture is intentionally JVM-filtered with `-PKMP_TARGETS=JVM`.
  It proves `fkcSetupMultiplatform(config = {})`, `allDefaultTargets()` target
  filtering, JVM source sets, `compileKotlinJvm`, `jvmTest`, and `check`
  without Android/JS/native downloads or task fanout.
- KMP source-set evidence must compile real `commonMain` and `commonTest`
  symbols from `jvmTest`; task-name assertions alone are not enough.
- TestKit consumer environments must scrub `KMP_TARGETS` and `KMP_TARGETS_ALL`.
  Environment variables win over Gradle properties; inherited `KMP_TARGETS_ALL`
  would silently defeat row-level target filtering.
- The all-targets-disabled KMP fixture must use `KMP_TARGETS=COMMON`, not empty
  `KMP_TARGETS`; empty target input currently means all default targets enabled.
- Intentional KMP target-filter skips must not emit Kotlin's no-target diagnostics
  or plugin warning noise. The fixture keeps KGP on the plugin classpath with
  `apply false` and verifies `fkcSetupMultiplatform` does not apply it.
- The first Android boundary fixture should cover AGP 9 KMP routing only:
  KGP and AGP KMP plugins available with `apply false`, `KMP_TARGETS=ANDROID`,
  plugin applies `com.android.kotlin.multiplatform.library`, does not apply
  legacy `com.android.library`, and propagates namespace/compileSdk/minSdk.
- The first AGP 8 KMP boundary fixture should mirror that routing-only shape:
  KGP and `com.android.library` available with `apply false`, `KMP_TARGETS=ANDROID`,
  plugin applies the legacy library plugin, creates a `KotlinAndroidTarget`, and
  propagates namespace/compileSdk/minSdk through `LibraryExtension`.
- Android KMP routing fixtures should start with `help assert...`, not `check`.
  `check` mixes SDK/lint/variant work into the routing signal; add it only in a
  later fixture that intentionally covers Android task execution.
- Do not add Kotlin DSL `kotlin { android { withHostTest {} } }` to the first
  AGP 9 routing fixture while Fluxo applies KMP dynamically; the script has no
  generated `android` accessor at compile time. Use the named KMP extension and
  an explicit `KotlinMultiplatformAndroidLibraryTarget` cast when the fixture
  must prove the actual AGP 9 target shape.
- Non-KMP Android library fixtures must cover both Kotlin wiring modes:
  AGP 8 with consumer-applied `org.jetbrains.kotlin.android`, and AGP 9 with
  built-in Kotlin where that legacy plugin remains unapplied. Both still use
  the legacy `com.android.library` extension path.
- Android execution fixtures must compile real Kotlin sources, enable
  verification, run variant/lint tasks, and reject noisy update-check or Detekt
  classification output. A task existing or running `NO-SOURCE` is not enough
  execution evidence.
- Non-KMP Android consumers with implicit JVM target expose a real compatibility
  invariant: Kotlin and AGP Java compile targets must be aligned to the same
  effective JRE target even when `jvmTarget` is not explicitly configured.
- Compose Desktop fixture rows must not overstate the Java runtime they
  exercise. Generated TestKit builds currently run on the compatibility test JVM;
  Compose Desktop packaging requires JDK 17+, so the PR fixture uses JDK 17
  evidence unless explicit Java launcher/JAVA_HOME wiring is added.
- Compose Desktop fixture evidence must compile a real `@Composable`, run at
  least one Kotlin test, and execute `createReleaseDistributable`, not
  `packageReleaseDistributionForCurrentOS`; OS package tasks can open installer
  UI on macOS and are too invasive for compatibility tests. Applying
  `org.jetbrains.kotlin.plugin.compose` only with `apply false` is acceptable
  only when the shape assertion proves Fluxo applied it.
- Source-level TestKit rows can still report the build-pin KGP version because
  their injected plugin-under-test classpath includes the build-pin Kotlin
  plugin. Marker rows are the classloader-fidelity evidence for exact consumer
  KGP/plugin DSL behavior.
- `checks/kmp/gradle.properties` disables configuration cache for its diagnostic
  graph task. Do not treat that as global KMP CC evidence; add a separate
  CC-compatible KMP fixture command.
- README has a stale JitPack example using Kotlin `2.0.21` while the current
  README floor says Kotlin `2.1+`; fix through model-backed docs verification,
  not an isolated text edit.
- External explorer agents are running for build-surface mapping and SOP gap
  audit; their outputs must be independently verified before becoming decisions.

## Living Implementation Queue

Do not remove unfinished entries. Mark entries complete only after code/docs are
updated, verified, and committed. Add newly discovered work as separate entries.
Current WIP limit: finish and commit one slice before starting the next. The
next optimal slice is to move marker-consumption evidence to a compatibility-owned
local Maven repository, then add local-Maven runtime classpath leak assertions.
Do not start build-pin bumps until PR-profile TestKit, marker consumption, and
static verifiers pass.

- [x] Correct this SOP to the required path
  `.ai/sop/2026-05-17-latest-build-compatibility-system.md`.
- [ ] Update this SOP with every implementation slice, finding, verification
  command, failure, and commit hash that affects the compatibility system.
- [ ] Keep this SOP process-oriented; do not turn it into a full status log.
  Durable constraints and next-phase tasks belong here. Volatile command output
  belongs in commits, CI artifacts, PR text, or release evidence.
- [ ] Independently map the existing build layout, version catalog, test suites,
  workflows, docs, and plugin compatibility code paths before changing behavior.
- [ ] Verify current upstream compatibility data from official sources before
  writing version claims or changing build pins.
- [ ] Verify KSP `2.3.8` candidate availability from primary artifact metadata
  before using it in build pins or declared model rows.
- [ ] Create the first repo-controlled compatibility model under `compat/` with
  explicit `buildPin`, `declaredSupported`, `forwardTested`, and `unsupported`
  concepts.
- [ ] Validate `compat/*.tsv` shape and current build-pin alignment before
  committing the initial model.
- [ ] Add the minimal static verifier implementation before TestKit: matrix
  shape/build-pin alignment, doc-claim checks, unsafe-pattern allowlist checks,
  and release-doc basics.
- [ ] Run only targeted static verifier tasks for the verifier slice; defer full
  `build assemble check` until the surface it is meant to validate is complete.
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
- [x] Register `compatibilityTest` as a Gradle JVM Test Suite with
  `gradleTestKit()` and JUnit dependencies.
- [x] Add `implementation(project())` for `compatibilityTest` if the suite needs
  direct project outputs, and wire the suite's test task to `check` explicitly.
- [x] Wire custom TestKit suites to the `pluginUnderTestMetadata` task output;
  Gradle's default `withPluginClasspath()` discovery does not see that metadata
  automatically from this repo's custom `JvmTestSuite` runtime classpath.
- [x] Build generated TestKit fixture infrastructure with isolated Gradle user
  homes and captured output assertions for known crash signatures.
- [ ] Keep apply-time capability checks free of direct `compileOnly` KGP type
  references; source-level TestKit fixtures must catch `NoClassDefFoundError`
  before a release profile can validate marker/POM consumption.
- [x] Distinguish source-level TestKit fixtures that use an explicit
  `buildscript` KGP classpath from release-like marker fixtures that use
  `plugins { kotlin(...) apply false }`; both paths must be proven because
  Gradle TestKit injected plugin classpaths can expose different classloader
  visibility from marker resolution.
- [x] Keep TestKit-only KGP classpath augmentation scoped to
  `compatibilityTest`; it models the consumer-supplied Kotlin plugin classpath
  without adding KGP to the published runtime dependency graph.
- [x] Add Kotlin JVM consumer fixture rows for `help`, `compileKotlin`, `test`,
  and `check`.
- [x] Seed the Kotlin JVM fixture with the `current-build` model row, real
  Kotlin source and test files, isolated TestKit state, required lifecycle task
  outcomes, and linkage-crash absence assertions.
- [x] Replace the seeded single-row fixture with profile-aware row selection
  from `compat/matrix.tsv`; source-level TestKit rows may prove source
  injection behavior, while local-Maven marker rows must separately prove
  marker/plugin-DSL classloader behavior.
- [x] Source-level Kotlin JVM rows currently run the plugin-under-test with the
  build-pin KGP on the injected plugin classpath even when the matrix row
  declares an older KGP; marker rows now separately prove exact consumer
  KGP/plugin-DSL classloader behavior for the Kotlin JVM PR row.
- [x] Add a KMP JVM-filtered consumer fixture row for
  `fkcSetupMultiplatform(config = {})`, target filtering, source-set behavior,
  `compileKotlinJvm`, `jvmTest`, and `check`.
- [x] Add KMP all-targets-disabled configuration evidence with
  `KMP_TARGETS=COMMON`; current empty `KMP_TARGETS` means all targets.
- [x] Add the first AGP 8 Android/KMP routing fixture for the legacy
  `com.android.library` plus KMP path.
- [x] Add AGP 8 Android/KMP execution fixture rows for lint/variant task
  coverage without weakening the routing fixture signal.
- [x] Add the first AGP 9 Android/KMP routing fixture for
  `com.android.kotlin.multiplatform.library`.
- [x] Add AGP 9 Android/KMP execution fixture rows for lint/variant task
  coverage without weakening the routing fixture signal.
- [x] Add non-KMP Android routing fixture rows for the legacy Android path
  under AGP 8 consumer-applied Kotlin Android and AGP 9 built-in Kotlin.
- [x] Add non-KMP Android execution fixture rows for lint/variant task coverage
  without weakening the routing fixture signal.
- [x] Add Compose Desktop fixture rows with matching Compose Multiplatform and
  Kotlin Compose plugin versions.
- [x] Add a Kotlin JVM marker fixture that consumes the plugin through the repo
  localDev Maven plugin marker without `withPluginClasspath()`.
- [x] Replace repo-local marker smoke with a configurable temporary local Maven
  repository or deliberately document why repo-local ignored `_/local-repo` is
  the chosen release evidence.
- [ ] Add publication fixture rows that consume the plugin through a temporary
  local Maven plugin marker.
- [ ] Add negative TestKit rows for Gradle below floor, Kotlin/KGP below floor,
  AGP 9 legacy `com.android.library` plus KMP co-application, AGP 8 attempting
  AGP-9 KMP plugin path, unsupported KMP Android app path, unknown
  `KMP_TARGETS`, and incompatible Compose/Kotlin rows.
- [ ] Add exact failure-message assertions for every negative row and assert
  absence of linkage/class-initialization crash signatures.
- [ ] Add runtime classpath leak verification for local Maven marker
  consumption, blocking leaks of `kotlin-compiler-embeddable` and `detekt-core`.
- [x] Add published marker/runtime metadata leak checks for
  `kotlin-compiler-embeddable` and `detekt-core` before running marker
  consumers.
- [ ] Add consumer buildscript classpath leak inspection after marker
  consumption if it can distinguish plugin leaks from Kotlin plugin's own
  classpath without false failures.
- [ ] Decide whether dependencyGuard baseline creation in generated consumers is
  acceptable fixture byproduct or plugin-owned noise; if noise, make the fixture
  suppress it through public DSL/config rather than ignoring output.
- [x] Classify `detektCompatibilityTest` as a JVM Detekt task so the new
  `compatibilityTest` suite does not produce plugin-owned configuration errors.
- [ ] Add configuration-cache fixture evidence for key rows, including a
  CC-compatible KMP command separate from diagnostics that intentionally disable
  CC.
- [ ] Remove unconditional IDE-sync marking so sync-only setup cannot run during
  normal builds.
- [ ] Replace `taskGraph.whenReady` mutation with configuration-time predicates
  or earlier target filtering.
- [ ] Stop configuration-time dependency resolution for dynamic plugin loading
  unless explicitly opted in.
- [ ] Keep `compatibilityTest` dependencies outside mirrored build-script
  regions unless `self` demonstrably needs the same dependency.
- [x] Remove configuration-time signing warnings from publication setup while
  keeping remote-publish task execution failure for unsigned non-snapshot
  releases.
- [ ] Move remaining SCM probing to publish tasks only.
- [ ] Locate every publication signing/SCM read path and classify whether it is
  configuration-time noise, task input, or release preflight before editing.
- [ ] Make local publish from `check` self-check/opt-in.
- [ ] Fix or document `fkcSetupMultiplatform(config = {})`, then lock behavior
  with TestKit.
- [ ] Define the canonical input for "all KMP targets disabled" or revise the
  README/AGENTS claim; then prove it configures without realizing target tasks.
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
- [ ] End each focused slice with a clean `git status --short` check, a focused
  Conventional Commit body, and only intentionally separate next-slice files
  left uncommitted.
- [ ] Verify CI YAML evidence: PR workflow runs `compat.profile=pr`, release
  workflow runs `compat.profile=release` before publishing, checkout credential
  persistence is disabled unless justified, and branch protection/aggregate
  required checks are verified by GitHub readback when access is available.
- [ ] Inventory and classify every `Class.forName`, `getMethod`,
  `getDeclaredMethod`, `noSuchMethodSafe`, `afterEvaluate`,
  `taskGraph.whenReady`, and `resolvedConfiguration` use against AGENTS
  layer-1/layer-2/layer-3 compatibility rules before adapter refactors.
- [ ] Perform adversarial self-audit against every SOP acceptance criterion
  before final response.
