# AGENTS.md — fluxo-kmp-conf

This file targets **maintainers/contributors** of this Gradle plugin (`io.github.fluxo-kt.fluxo-kmp-conf`), not consumers. Consumer docs are in `README.md`.

The plugin lazily, target-aware, configures Kotlin/JVM, Android, KMP, Compose, IDEA-plugin, and Gradle-plugin modules from one root entry point. Built **with itself** via `includeBuild("self")`.

> Read the **Surprises** section first — most footguns here aren't grep-able.

## Vibe / core principles
- **Lazy on-demand**: nothing configured eagerly; modules with every target disabled must still configure cleanly. Prefer `tasks.named` / `withType` over eager creation.
- **Per-target filtering**: every target gate honours `KMP_TARGETS` (CSV of codes from `KmpTargetCode.kt`) or `KMP_TARGETS_ALL=true`. Don't assume a target is present.
- **Opt-in experimental**: defaults are conservative — check the relevant `FluxoConfigurationExtension*` KDoc and `CHANGELOG.md` before flipping any default.
- **Property/env flags only via `PropsAndEnv.kt`** (`envOrPropFlag` / `envOrPropValue`); for shelling out, the same file's `runCommand` helper. Never `System.getenv` / raw `Runtime.exec`.
- **Configuration-cache compatible**: no `Project` access at execution time, no eager task creation. CC is on, `problems=warn` — silent violations are possible, treat warnings as errors during review.
- **Public API is the contract**: KDoc on `fluxo/conf/dsl/FluxoConfigurationExtension*.kt` is authoritative. New flags need impl in `FluxoConfigurationExtensionImpl*` plus parent-inheritance fallback where the KDoc says so.

## Layout (only the non-obvious)
- `fluxo-kmp-conf/` — published plugin. Public DSL: top-level `FkcSetup*.kt` (all share `@JvmName("Fkc") @JvmMultifileClass`). Engine: `fluxo/conf/`. Public API dump: `api/plugin.api`. Verification baselines: `detekt-baseline.xml`, `lint-baseline.xml`. Generated `BuildConstants` → `fluxo.conf.data`.
- `self/` — sibling module sharing sources with `fluxo-kmp-conf/` via `kotlin.srcDir("../fluxo-kmp-conf/src/main/kotlin")` and `includeBuild`'d so the plugin self-applies. `buildSrc` is intentionally avoided.
- `checks/{kmp,gradle-plugin,compose-desktop}/` — three integration-test Gradle builds with their own `settings.gradle.kts` and `build.gradle.kts`, but `gradle/`, `gradlew`, `gradlew.bat`, `config/` are **symlinks to root** (one wrapper, one catalog). Two of the three (`gradle-plugin`, `compose-desktop`) additionally symlink `gradle.properties` to `self/gradle.properties` (CC=false). CI cd's into each.
- `gradle/libs.versions.toml` — single source of truth for versions; resource-copied at build time as `fluxo.versions.toml` (comments stripped) and exposed at runtime via `FluxoVersionCatalog`.
- `ROADMAP.md` = research/links scratchpad, **not** active TODOs. `CHANGELOG.md` follows Common Changelog.
- ProGuard/R8 keep rules live per-module in `<module>/pg/` (plugin's own at `fluxo-kmp-conf/pg/`).
- Optional integrations live in `fluxo/conf/feat/` (`Setup*.kt` / `Prepare*.kt` patterns); per-target KMP containers in `fluxo/conf/dsl/container/{,impl,target}`.

## Things that will surprise you (append new finds; prune outdated)
- **`self/build.gradle.kts` and `fluxo-kmp-conf/build.gradle.kts` carry mirrored `dependencies {}` and `buildConfig {}` blocks** because `:self` re-uses the plugin's main sources via `kotlin.srcDir(...)` and must declare the same buildscript classpath. The mirrored regions are delimited by `// MIRROR-START` / `// MIRROR-END` markers and a byte-identity invariant is enforced by the `verifyBuildScriptMirror` task on `:plugin:check`. Update both regions in lockstep; CI fails the build on drift.
- **`kotlin-stdlib` is explicitly excluded** from `implementation` in the plugin's build (`exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib*")`) plus `kotlin.stdlib.default.dependency=false`. Gradle injects its own stdlib at runtime; bundling ours triggers ClassLoader conflicts. **Don't "clean up" that block.**
- **Kotlin lang/api are pinned** in `libs.versions.toml` to a version older than the build Kotlin — this is the consumer-compatibility surface. Bumping is a breaking-change decision, not a routine bump.
- **Module rename trap**: `settings.gradle.kts` renames `:fluxo-kmp-conf` → `:plugin`. The string `plugin` is referenced by `jitpack.yml`; renaming breaks JitPack snapshot consumers (comment on the line warns).
- **Git symlinks required for development** (Windows: enable per the README link). Without them the shared-sources trick fails. `.aiexclude` is itself a symlink to `.cursorignore`.
- `org.gradle.vfs.watch=false` and `org.gradle.unsafe.watch-fs=false` are intentional — VFS watching errors with "same file via different paths" because of source sharing. Don't re-enable.
- `MAX_DEBUG=true` is committed in `gradle.properties` — verbose plugin logs at `lifecycle` level are the **plugin-developer** default, not a consumer recommendation.
- **Tests auto-disable** when `DISABLE_TESTS=true`, the build is an *included* build with no startup tasks, or `check`/`test` are excluded. If `prepareKotlinIdeaImport` runs but expected tasks don't, see `FluxoKmpConfContext.testsDisabled`.
- **IDE-sync-only config** uses `onProjectInSyncRun` (detected via `prepareKotlinIdeaImport` / `prepareKotlinBuildScriptModel` task names). Use it instead of `afterEvaluate` for IDE-import hooks.
- The plugin **applies only to root projects** (`checkIsRootProject`) and **requires a Kotlin plugin already on the classpath** before it's applied — order in `plugins {}` matters.
- Shrinker `replaceOutgoingJar = false` in the plugin's own build is intentional — chained R8/ProGuard tasks are *verification-only*, they don't replace the published jar.
- `afterEvaluate` is used sparingly and always carries a FIXME — prefer lazy `Provider` / `NamedDomainObjectContainer` APIs in new code.
- Conventional Commits are **convention only** — not CI-enforced. PR titles + commit messages rely on reviewer discipline.
- **Detekt's `CyclomaticComplexMethod` counts local-function complexity into the enclosing method's score.** Extracting a helper into a local `fun` does NOT reduce the host's CC. To shrink it, hoist the helper to a top-level extension (see the `applyReproducibleArchivePermissions` extraction).
- **Pure-Kotlin symbols that need unit tests must live in a file with zero KGP refs.** The Kotlin file-class `<clinit>` resolves every top-level symbol declared in the file, so a single `org.jetbrains.kotlin.gradle.*` reference (those types are `compileOnly`) explodes the file's tests with `NoClassDefFoundError`. Pattern: split the testable surface to its own file (e.g. `KotlinVersionTable.kt`, `DetektKotlinClamp.kt`); the file-level docstring documents the discipline.
- **`kotlin.KotlinVersion`'s `compareTo` is patch-inclusive.** `KotlinVersion(2, 0, 1) > KotlinVersion(2, 0, 0)` is `true` — be deliberate about whether you mean "later patch of the same minor" or "later minor". For minor-bracket membership use a `FIRST_UNTABULATED_<...>` constant compared with `>=`, not a `LATEST_TABULATED_<...>` compared with `>` (the latter false-fires on every patch above 0).
- **Never use `String.toFloat()` for version comparison.** `"1.10".toFloat() == 1.1f` ranks falsely below `1.9`; `"2.10".toFloat() == 2.1f` ranks falsely equal to `2.1`. Parse to `kotlin.KotlinVersion` (no KGP coupling) and compare lexicographically — see `parseDetektLangVersion`.
- **`setupAndroidCommon` and the AGP-8 `Library.setupAndroid` branch have no live `checks/*` coverage** as of the AGP-9 migration in 0.14.0. The production paths still work — the gap is in CI integration coverage. Adding a `checks/android/` non-KMP module (exercising `fkcSetupAndroidLibrary` / `fkcSetupAndroidApp` directly) covers `setupAndroidCommon` via `configureKotlinJvm` line 142. AGP-8 KMP+Android coverage requires either a sibling `checks/kmp-agp8/` or a build-property toggle that retargets `checks/kmp` between the two plugin ids. Pre-requisite: catalog gains a `kotlin-android` plugin alias (only `kotlin-jvm`/`kotlin-multiplatform` exist today). Tracked in the maintainer SOP as a follow-up.
- **AGP 8 vs AGP 9 KMP+Android plugin id is a hard split, not a version bump.** Legacy `com.android.library` + `kotlin("multiplatform")` co-application is hard-rejected by AGP 9; the AGP-9 KMP-aware `com.android.kotlin.multiplatform.library` plugin auto-creates the `android` KMP target via `kotlin { android { } }` and uses the disjoint `KotlinMultiplatformAndroidLibraryExtension` / `KotlinMultiplatformAndroidLibraryTarget` types (do NOT extend `LibraryExtension` / `KotlinAndroidTarget`). Detect the line with `AgpVersion.isAgp9OrLater(project)`. Routing in this codebase: `setupKmpAndroidExtension` (namespace/compileSdk/minSdk slot-fill, wired in `setupKotlin`) + `setupKmpAndroidLint` (Lint config, wired in `setupVerification`) handle the new path; the legacy `setupAndroidCommon` / `setupAndroidLint` handle the old. `TargetAndroidContainer.Library` runs both via the virtual `needsLegacyTargetCreation` property — under AGP 9 it skips `androidTarget()` (the auto-created target collides) and warns if `lazyAndroid` is non-empty (lambdas typed for the disjoint `LibraryExtension` cannot run on the new extension). `androidApp { }` fail-fasts under AGP 9: there is no KMP-aware AGP application plugin upstream.

## Common tasks
| Task | Command |
|---|---|
| Build & full check (root plugin) | `./gradlew build assemble check` |
| Run an integration check | `cd checks/<kmp\|gradle-plugin\|compose-desktop> && ./gradlew build check` |
| Update **all** baselines (api, detekt, lint, depGuard) | `./updateBaseline` — runs root + every `checks/*` |
| Per-area baselines | `./gradlew apiDump dependencyGuardBaseline detektBaselineMerge updateLintBaseline --continue` |
| Limit KMP targets | `KMP_TARGETS=<csv> ./gradlew …` (codes in `KmpTargetCode.kt`) or `KMP_TARGETS_ALL=true` |
| Disable shrinker | `DISABLE_R8=true …` (aliases in `PropsAndEnv.kt`) |
| Force release semantics | `RELEASE=true …` |

Tests are integration-style: `fluxo-kmp-conf/src/test/` is sparse (mostly shrinker via `kotlin-compile-testing` + ProGuard/R8). The real test surface is the three `checks/*` builds.

## Conventions
- **Conventional Commits** required (`feat(scope): …`); types and rules in `CONTRIBUTING.md`. PR titles follow the same. Merges are **`--ff-only`** (enforced by `pr-fast-forward.yml`); avoid merge commits except on hotfix branches.
- Public API: anything new needs `./gradlew apiDump` + CHANGELOG entry. Internal-only: annotate `@fluxo.annotation.InternalFluxoApi` (wired into BCV `nonPublicMarkers`).
- **CHANGELOG.md is strictly consumer-facing.** Each entry must answer *"what changes in the consumer's build?"*: new/changed/removed public DSL or ABI; new behaviours/warnings the plugin emits; consumer-compat-floor bumps; wrapped-plugin bumps consumers can observe; bug fixes consumers hit; doc that teaches a previously-undocumented consumer contract. **NOT**: CI, baselines, `verifyBuildScriptMirror`, internal refactors/renames/file-splits, test seeds, AGENTS/ROADMAP edits, regenerated `api/*.api`, SHA-pin / Dependabot grouping, internal-package decoupling that doesn't ship today. Failing the test → commit message, not CHANGELOG.
- Editorconfig: 4-space Kotlin / `.kts`, 100-col line length. ktlint-official enabled; `trailing-comma` and `no-consecutive-blank-lines` rules disabled.
- Diffs stay surgical: don't reformat untouched files, don't refactor during bug fixes.
- **Bumping the plugin's own version**: update `gradle/libs.versions.toml` (`version`) and the `README.md` example block (consumer-visible Gradle Plugin Portal version).
- **Adding a 3rd-party Gradle plugin we wrap**: mirror in `self/build.gradle.kts` *and* `fluxo-kmp-conf/build.gradle.kts` (`compileOnly`/`implementation` + `buildConfigField`). See *Surprises* — no guard.
- Release: `vanniktech-mvn-publish` + `release.yml`; SCM derived from `SCM_TAG` / `BUILD_NUMBER` env (see `PropsAndEnv.kt`).

## Where deep context lives (don't duplicate)
- Hierarchical KMP source-set diagram → `README.md` `## Hierarchical KMP project structure`.
- Commit / PR style + commit-message types → `CONTRIBUTING.md`.
- Future research / inspirations → `ROADMAP.md` (scratchpad — *not* active TODOs).
- Release history → `CHANGELOG.md`.
- Per-flag semantics → KDoc on `fluxo/conf/dsl/FluxoConfigurationExtension*.kt`.
- Per-feature integration code → `fluxo/conf/feat/Setup*.kt` / `Prepare*.kt`.

## When something surprises you
If you encounter behaviour, layout, config, or tooling that genuinely surprised you (hidden coupling, non-obvious flag, subtle CI requirement, footgun), **flag it to the user** and **append a one-liner to "Things that will surprise you"** so the next agent doesn't repeat the discovery cost.

Before appending: **scan the existing list and prune anything no longer true** (resolved FIXMEs, removed flags, fixed gotchas). The list must reflect *current* state, not history. When you fix or remove a coupling, prune the corresponding entry in the same change.
