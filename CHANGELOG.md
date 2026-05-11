# Changelog [^1]


## Unreleased

[//]: # (Sections: Removed, Added, Changed, Fixed, Updated. Common Changelog style.)
[//]: # (CONSUMER-FACING ONLY — see AGENTS.md "Conventions" for the strict scope rule.)


## [0.14.0] - 2026-05-01

### Removed
- **breaking** `FluxoPublicationConfig.sonatypeHost` — Vanniktech 0.34.0 removed `SonatypeHost` and all OSSRH support; Sonatype Central Portal (via `publishToMavenCentral`) is the sole publish target since OSSRH retired 2025-06-30. The field had no remaining semantic content and a soft-deprecation no-op would silently swallow consumer values; a clean removal forces consumers to confront the migration. **Migration**: drop `sonatypeHost = …` from your `FluxoPublicationConfig {}` block and switch publish credentials to a [Central Portal user-token](https://central.sonatype.org/publish/publish-portal-gradle/).
- **breaking** dropped Compose-legacy fallback path. With the consumer floor at Kotlin 2.1, the JetBrains Kotlin Compose Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) is the only configuration path. The previous `try/catch` fallback that injected `-P plugin:androidx.compose.compiler.plugins.kotlin:*` flags into `freeCompilerArgs` is gone, and the legacy `composeOptions.kotlinCompilerExtensionVersion = …` setter is no longer applied to AGP `composeOptions`. **Migration**: ensure your build resolves the Kotlin Compose plugin (it does automatically when `enableCompose = true` and consumer Kotlin ≥ 2.0).
- **breaking** `FluxoConfigurationExtensionKotlin.setupLegacyKotlinHierarchy` removed. With the consumer floor at KGP 2.0+, `applyDefaultHierarchyTemplate` (auto-applied since KGP 1.9.20) handles intermediate source-set wiring (`commonJvm`, `commonJs`, `native`, `apple`, etc.) unconditionally. The legacy manual path — gated on `pluginVersion < 1.9.20 && NO_MANUAL_HIERARCHY=false && setupLegacyKotlinHierarchy=true` — was permanently unreachable under the new floor. **Migration**: remove any `setupLegacyKotlinHierarchy = true` from your `fluxoConfiguration { }` blocks; the default KGP hierarchy already handles the same wiring.

### Changed
- **breaking** consumer-compat floor: Kotlin 1.9 → **2.1**, Gradle 8 → **9.0+**. Reasoning: Gradle 8.x daemons embed Kotlin 2.0.x at most; the build matrix in this release (Kotlin 2.2.21 + Gradle 9.3.1 + AGP 9.1.1) cannot guarantee 1.9 source-language compatibility across non-JVM KGP targets. The README badges, the example block in the Quick Start, and the `kotlinLangVersion`/`kotlinApiVersion`/`kotlinCoreLibraries` catalog defaults all advance accordingly.
- **breaking** `Provider<T?>.getValue(thisRef, property)` property-delegation operator removed. Kotlin 2.2's strict `T:Any` constraint on `Property<T>`/`Provider<T>` collapsed the nullable and non-null overloads to identical signatures; only the non-null variant survives. **Migration**: replace `val x: Foo? by fooProvider` with `val x: Foo? = fooProvider.orNull`.
- migrate publication archive permissions from the Gradle-9.0-removed `dirMode` / `fileMode` setters to `dirPermissions { unix("0755") }` / `filePermissions { unix("0644") }` (added in Gradle 8.3). Without this, consumers on Gradle 9 would hit `NoSuchMethodError` during publication archive setup.
- `FluxoPublicationConfig.repositoryUrl` defaults to `null` (was hard-coded to the now-retired Sonatype OSSRH staging/snapshot URLs). When unset, no extra Maven repository is registered — Vanniktech's Central Portal upload path is independent. Set explicitly when publishing to a custom mirror (Artifactory, internal Nexus, etc.).
- `androidBuildToolsVersion` now defaults to `null` unless explicitly set by the consumer or their version catalog. Modern AGP selects the compatible Build Tools version itself; keeping a bundled pin caused avoidable AGP-9 warnings as soon as the minimum accepted Build Tools version moved. Consumers that need a reproducible explicit pin can still set `androidBuildTools`, `buildToolsVersion`, or `androidBuildToolsVersion`.
- `fkcSetupIdeaPlugin`: `intellijVersion` now defaults to `""` (was a required parameter); existing callers that pass it explicitly continue to work. A deprecation warning is emitted at configuration time when the parameter is non-blank — in IntelliJ Platform Gradle Plugin v2 the IDE dependency is declared in `dependencies { intellijPlatform { intellijIdeaCommunity(version) } }` instead.

### Added
- recognise the AGP-9 KMP+Android plugin (`com.android.kotlin.multiplatform.library`, AGP `>= 8.8.0`, **required** from AGP `9.0`). The plugin replaces the legacy `com.android.library` + `kotlin("multiplatform")` co-application that AGP 9 hard-rejects, and auto-creates the `android` KMP target from `kotlin { android { } }`. Consumers can now drive AGP 9 KMP+Android modules end-to-end through `fkcSetupMultiplatform`.
- auto-apply `androidNamespace` / `androidCompileSdk` / `androidMinSdk` / `androidBuildToolsVersion` from `fluxoConfiguration { }` onto `KotlinMultiplatformAndroidLibraryExtension` when the AGP-9 KMP+Android plugin is on the classpath. Mirrors the behaviour the legacy `setupAndroidCommon` path already provided for `com.android.library` (the new extension does **not** extend `TestedExtension`, so the two paths are necessarily separate). Idempotent and defensive — explicit consumer-set values in `kotlin { android { } }` are never overwritten.
- Fluxo's preferred Android Lint configuration (sarif/html report toggles, CI-aware baseline, `lint.xml` discovery, `GradleDependency` / `NewerVersionAvailable` suppression in tests, lint-version reporting, `:mergeLintSarif` hookup) now applies to the AGP-9 KMP+Android plugin via `KotlinMultiplatformAndroidLibraryExtension.lint`. Previously this extension was unrecognised by the wrapper and consumers silently fell back to AGP defaults.
- `androidLibrary { }` inside `fkcSetupMultiplatform` is now AGP-version-aware: under AGP 8.x it applies `com.android.library` and runs the legacy configuration path (existing behaviour, untouched); under AGP 9.x it applies `com.android.kotlin.multiplatform.library` and skips the legacy `androidTarget()` create call (the AGP-9 plugin auto-creates the `android` KMP target, and post-extension config is delivered by the new `setupKmpAndroidExtension` / `setupKmpAndroidLint` paths). Empty `androidLibrary { }` blocks "just work" on both lines; non-empty `onAndroidExtension { }` blocks under AGP 9 are skipped with an explicit error-level log because their lambda receiver (`LibraryExtension`) is disjoint from the AGP-9 replacement type (`KotlinMultiplatformAndroidLibraryExtension`). `androidApp { }` retains a fail-fast under AGP 9+ — the AGP team has not shipped a KMP-aware application plugin, so there is no migration target.
- `setupRoom = true` now wires the project-level Room KSP args (`room.generateKotlin`, `room.incremental`, `room.schemaLocation`) under AGP-9 KMP+Android (`com.android.kotlin.multiplatform.library`), at parity with the existing AGP-8 `com.android.library` path. The legacy path's `sourceSets["androidTest"].assets.srcDir(roomSchemasDir)` cannot port — the AGP-9 KMP+Android extension drops the `NamedDomainObjectContainer<AndroidSourceSet>` collection, and KMP source sets (`androidDeviceTest`, etc.) expose `kotlin`/`resources` but not `assets`. If you run instrumented Room tests on this path, attach the schemas dir to the `androidDeviceTest` source set's resources manually (the schema directory location is logged at info level for that purpose).
- `fkcSetupAndroidLibrary` / `fkcSetupAndroidApp` (non-KMP entry points) keep working under AGP 9: `setupAndroidCommon`'s receiver is now the modern `com.android.build.api.dsl.CommonExtension` (was the legacy `com.android.build.gradle.TestedExtension`, which AGP-9 extension instances no longer implement at runtime). Subtype-aware dispatch routes `targetSdk` to `ApplicationExtension` only (AGP 9 dropped it from `LibraryBaseFlavor`), and the non-KMP path uses the AGP-9-built-in Kotlin support (`android.builtInKotlin = true` default) instead of the legacy `org.jetbrains.kotlin.android` plugin (which AGP 9 hard-rejects).
- `maxStackSize` task input on the bundled shrinker (`AbstractShrinkerTask`); defaults to `8m` (`-Xss8m`). Previous releases ran ProGuard with the worker-default thread stack, which overflowed deep optimisation passes on real-world Compose Multiplatform 1.10 inputs. Override per task when you need more headroom.
- self-warn (one-shot per JVM) at configuration time when the consumer's Kotlin plugin version is at or beyond the first untabulated minor in the Kotlin → max-JVM-target compatibility table. Surfaces silent JVM-target capping, which previously required maintainer attention to discover.
- self-warn (one-shot per JVM) at configuration time when the consumer's `kotlinLangVersion` exceeds Detekt's supported maximum, instead of silently clamping.
- KDoc for `FluxoPublicationConfig.projectName` explaining the Maven-artifact-ID character restriction.
- KDoc on `enableGradleDoctor` documents the consumer-side escape hatch: override Gradle Doctor defaults via the standard `doctor { ... }` extension on the root project (no wrapper API was added — the existing extension already covers it).
- README documents the four recognised version-catalog aliases (`androidx.compose.ui.tooling`, `square.leakcanary`, `square.plumber`, `pinned`) — previously a hidden consumer contract.
- `fkcSetupIdeaPlugin`: new `extension: (IntelliJPlatformExtension.() -> Unit)?` parameter for direct IntelliJ Platform extension configuration.

### Fixed
- `JRE_17` internal constant had the value `11` (copy-paste from `JRE_11 = 11`). Two consumer-visible effects: (1) when using `fkcSetupIdeaPlugin`, the plugin's JVM-target floor was enforced at JDK 11 instead of the required JDK 17, silently producing JDK-11-level bytecode in IDE-plugin builds that mandate JDK 17+; (2) the `-Xjdk-release` compiler flag was applied to JVM targets in the range `12..22` (should be `18..22`), meaning consumers requesting JVM target 12–17 received superfluous `-Xjdk-release` arguments that could alter codegen in unexpected ways.
- the Kotlin → max-JVM-target compatibility table was missing the Kotlin 2.1 (JVM 23) and Kotlin 2.2 (JVM 24) entries. Consumers on Kotlin 2.1+ requesting a JVM target of 23 or 24 were silently capped at JVM 22; they would see no error but the compiled bytecode targeted a lower JVM version than requested.
- `FluxoPublicationConfig.projectUrl` was never used as the POM URL fallback. `MavenPom.url` is a non-null `Property<String>` — calling `.get()` on an unset property returns `""`, which the `?: config.projectUrl` Elvis arm never reached. Changed to `.orNull` so the three-level fallback (`publicationUrl → existing POM url → projectUrl`) works as documented.
- shrinker API verification (`setupVerification = true`) would crash with `NoSuchMethodError` on Gradle 8.14+ and Gradle 9.x: the plugin used an internal `DefaultTestFailureDetails` constructor that Gradle 8.14 removed. Switched to the public `TestFailure.fromTestAssertionFailure` / `TestFailure.fromTestFrameworkFailure` factories (stable since Gradle 7.4).
- the Detekt `languageVersion` clamp parsed Kotlin language versions as `Float`, which silently misranks any future two-digit minor: `"1.10".toFloat() == 1.1f` (would rank below `1.9`), `"2.10".toFloat() == 2.1f` (would not trigger the clamp at all). Replaced with `kotlin.KotlinVersion`-based comparison. Latent today (no Kotlin 1.10/2.10 yet) but a correctness landmine for future minors.
- under AGP 9 + non-KMP `com.android.library`, applying the plugin caused `compileDebugKotlin` to fail at runtime with `NoSuchMethodError: BuildPerformanceMetrics.add$default(...)`. The plugin's published runtime classpath leaked `kotlin-compiler-embeddable` and 25 transitives (incl. detekt-core) onto the consumer's buildscript classpath; under AGP 9's built-in Kotlin (KGP 2.2.10 bundled) Gradle's `<latest>` resolution upgraded `kotlin-compiler-embeddable` to our pinned 2.2.21 while KGP itself stayed at 2.2.10, producing the inlined-helper signature mismatch. Both are now `compileOnly` — `KotlinToolingVersion` is provided transitively by KGP at the consumer-applied version, `BaselineProvider` by detekt-gradle-plugin, and the only direct shrinker call (`Lazy.getValueOrNull`) was inlined. KGP's own warning ("please remove kotlin-compiler-embeddable from the build classpath alongside KGP") no longer fires for our plugin.
- Compose setup no longer adds the `OptimizeNonSkippingGroups` compiler feature flag explicitly. Kotlin 2.2 enables it by default, so the wrapper kept behaviour while producing an avoidable Compose compiler warning in consumer builds.
- `fkcSetupIdeaPlugin`: `sinceBuild` parameter was a no-op since the IJ Platform v1 → v2 migration (the v2 configuration block was commented out). It now correctly wires to `IntelliJPlatformExtension.pluginConfiguration.ideaVersion.sinceBuild`, setting the `since-build` attribute in the patched `plugin.xml`.

### Updated
- bump build Kotlin **2.0.21 → 2.2.21** (matches the Kotlin embedded in Gradle 9.3.1's daemon). KSP follows: **2.0.21-1.0.28 → 2.2.21-2.0.5**. Consumers gain access to the Kotlin 2.2 compiler features through the wrap layer (the plugin's own DSL is still backwards-compatible to the new 2.1 floor).
- bump Gradle wrapper **8.11 → [9.3.1](https://docs.gradle.org/9.3.1/release-notes.html)**. Strict CC is now Gradle's preferred mode; the plugin's own CC compatibility is preserved.
- bump **AGP 8.7.2 → [9.1.1](https://developer.android.com/studio/releases/gradle-plugin#9-1-0)**. AGP 9 hard-requires Gradle 9.x. Internal: `CommonExtension`'s six type parameters collapsed to a single non-parameterised type; the plugin's `the<AndroidComponentsExtension<…>>()` lookups and the `AndroidCommonExtension` typealias have been adapted accordingly.
- bump **Compose Multiplatform 1.7.1 → [1.10.3](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.3)** — common `@Preview` annotation, Navigation 3, stable Compose Hot Reload. The wrapper no longer sets Compose compiler options that Kotlin 2.2 already enables by default.
- bump **Spotless 6.25 → [8.4.0](https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md)** — major-line bump. The plugin previously re-used Spotless internal classes (`FileSignature`, `NoLambda`); both were replaced by local equivalents in the 0.13.4 prep work, so the bump is a clean catalog change here.
- bump **Detekt 1.23.7 → [1.23.8](https://github.com/detekt/detekt/releases/tag/v1.23.8)**. Stays on the 1.23.x line because Detekt 2.0 stable still lacks AGP-9 compat (issue #8908).
- bump **IntelliJ Platform Gradle plugin 2.1.0 → [2.15.0](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases)**. JDK 21 is required when targeting IntelliJ 2026.* — set `javaLangTarget = "21"` on consumer modules that target the latest IntelliJ.
- bump **kotlinx-binary-compatibility-validator 0.16.3 → [0.18.1](https://github.com/Kotlin/binary-compatibility-validator/releases)**. Standalone tool retained — Kotlin 2.x's built-in `kotlin.abiValidation { }` migration is deferred to a future release because routing the customisable `apiDumpDirectory` value through to ShrinkerKeepRulesFromApiTask + SetupArtifactsProcessing as a CC-safe task input is its own non-trivial refactor.
- bump **Vanniktech [0.30.0 → 0.36.0](https://github.com/vanniktech/gradle-maven-publish-plugin/releases/tag/0.36.0)**. 0.34.0 removed `SonatypeHost` and OSSRH support entirely (see `sonatypeHost` removal in **Removed**); 0.36.0 ships Kotlin 2.2 binary metadata, requiring the build-Kotlin bump above. The previously-deprecated `KotlinMultiplatform(JavadocJar, Boolean, …)` constructor is replaced by the explicit `SourcesJar.Sources()` form.
- bump bundled Dokka 2.0.0-Beta → [2.2.0](https://github.com/Kotlin/dokka/releases/tag/v2.2.0) (stable line) and migrate the wrapped publication path to DGPv2 task names (`dokkaGeneratePublicationHtml` / `dokkaGeneratePublicationJavadoc`). Dokka 2.1+ deprecated and 2.2 removes the v1 task graph (`dokkaHtml` / `dokkaJavadoc`); without the migration, consumers' `javadocJar` would fail at task graph construction.
- bump `com.gradle.plugin-publish` 1.3.0 → _1.3.1_ — release notes: "addresses and eliminates all deprecation warnings from Gradle versions up to 8.12.1". Removes 1.3.0's call to `org.gradle.util.VersionNumber.parse` (REMOVED in Gradle 9.0).
- bump bundled shrinker stack: **ProGuard 7.6.0 → [7.9.1](https://github.com/Guardsquare/proguard/releases/tag/v7.9.1)** + **proguard-core 9.1.6 → 9.3.2**. Adds Kotlin 2.3 metadata + Java 26 bytecode support — required by the Kotlin / AGP / Compose bumps in this release. Pair with the new `maxStackSize` default and the `!class/merging/horizontal` optimisation toggle (documented in `pg/rules.pro`) when shrinking Compose Multiplatform 1.10 outputs, where horizontal class merging has a known cycle-in-hierarchy regression.
- bump pinned-bundle and internal dependencies to current stable: `org.bouncycastle:bcprov-jdk18on` _1.79 → 1.84_ (security), `com.squareup.okio:okio` _3.9.1 → 3.17.0_, `org.json:json` _20240303 → 20251224_ (security), `com.google.guava:guava` _33.3.1-jre → 33.6.0-jre_, `org.apache.commons:commons-compress` _1.27.1 → 1.28.0_, `org.jetbrains:annotations` _26.0.1 → 26.1.0_, `org.ow2.asm:asm` _9.7.1 → 9.9.1_. Pinned-bundle members propagate to consumers that opt in via the `pinned` bundle alias.


## [0.13.2] - 2024-11-26

### Fixed
- fix publication config by handling SonatypeHost in the FluxoPublicationConfig dynamically.
- don't try to setup browser target for Wasm WASI.

### Added
- update README with configuration steps and examples.

### Updated
- bump dependency-analysis to _2.5.0_.
- bump mrmans0n's Detekt Compose Rules to [_0.4.19_](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.19).
- bump BuildConfig plugin from to _5.5.1_.


## [0.13.1] - 2024-11-25

### Added
- add JVM compatibility and Kotlin options flags to disable a corresponding autoconfiguration.

### Fixed
-  pin `kotlin-compiler-embeddable` dependency in support for Kotlin 2.1 ([more details](https://kotlinlang.slack.com/archives/C0KLZSCHF/p1729256644747559?thread_ts=1729151089.194689&cid=C0KLZSCHF))


## [0.13.0] - 2024-11-18

### Added
- allow not setting up `fluxo-kmp-conf` containers and use the default KMP hierarchy instead.

### Fixed
- return support for Kotlin's `-Xjdk-release=18+` in JDK 23+.
- fix bundled shrinker loading.

### Changed
- use `vanniktech/gradle-maven-publish-plugin` for publication.
- migrate to the new Develocity plugin for the build scans.
- prefer bundled R8 if it’s newer by default.
- change default values and logic for some configuration options for safety and convenience in the new projects (experimental and/or complicated options should be opt-in)
  - enableApiValidation = `false`
  - setupVerification = `false`
  - enableGenericAndroidLint = `false`
  - enableGradleDoctorProp = `false`
  - latestSettingsForTests = `false`
  - BinaryCompatibilityValidatorConfig.tsApiChecks = `false`

### Updated
- bump Kotlin to _2.0.21_!
- bump KSP to [_1.0.28_](https://github.com/google/ksp/releases/tag/2.0.21-1.0.28).
- bump JetBrains Compose to [_1.7.1_](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.7.1).
- bump Android Gradle Plugin to _8.7.2_ (compile-only dependency).
- bump a lot of other dependencies to the latest versions.


## [0.12.1] - 2024-10-10

### Changed
- ⚠ removed context-receivers support (dropped in Kotlin 2.1).

### Updated
- bump Kotlin to _2.0.20_!
- bump Jetbrains Compose to _1.7.0-rc01_ (compile-only dependency).
- bump Android Gradle Plugin to _8.7.0_ (compile-only dependency).
- bump binary-compatibility-validator to _0.16.3_.
- bump a lot of other dependencies to the latest versions.


## [0.12.0] - 2024-06-29

### Fixed
- fix the broken test-main dependencies in the intermediate KMP source sets.

### Added
- set `unitTest` source set tree for Android to the usual `test` tree.

### Changed
- add more granular control over targets in the `allDefaultTargets` helper.
- allow `bytestring` named dependencies in the DependencyGuard plugin despite the `test` substring.

### Updated
- bump task-tree to [_4.0.0_](https://github.com/dorongold/gradle-task-tree/releases/tag/4.0.0).
- bump Android Gradle Plugin to _8.6.0-alpha08_ (compile-only dependency).


## [0.11.0] - 2024-06-10

### Changed
- rename `jsApiChecks` property to `tsApiChecks`. **BREAKING CHANGE!**

### Updated
- bump [binary-compatibility-validator-js](https://github.com/fluxo-kt/fluxo-bcv-js) from _0.3.0_ to [_1.0.0_](https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v1.0.0).


## [0.10.2] - 2024-06-08

### Updated
- bump jetbrains-compose to [_1.6.11_](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.6.11).
- bump KSP to [_1.0.22_](https://github.com/google/ksp/releases/tag/2.0.0-1.0.22).
- bump Android Gradle Plugin to _8.6.0-alpha05_ (compile-only dependency).
- bump KtLint from _1.2.1_ to [_1.3.0_](https://github.com/pinterest/ktlint/releases/tag/1.3.0).
- bump mrmans0n's Detekt Compose Rules to [_0.4.4_](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.4).
- bump kotlin-compile-testing (kctfork) to [_0.5.0_](https://github.com/ZacSweers/kotlin-compile-testing/releases/tag/0.5.0) (test-only dependency).


## [0.10.1] - 2024-06-02

### Added
- allow disabling Android Lint when no Android plugin is used.
- detect when the project is a child of a composite build and has no startup tasks.
- configure Compose with the new Kotlin compiler plugin.
- use the Gradle Doctor plugin.
- support and use more Detekt rulesets.

### Changed
- revise the hierarchy of the source sets using both the new `KotlinHierarchyTemplate` and the old way. **Can be a BREAKING CHANGE!**

### Fixed
- don't apply Android Lint when an old Gradle is used (min supported Gradle is 8.7).

### Updated
- bump Android Gradle Plugin to _8.6.0-alpha04_ (compile-only dependency).
- bump Gradle from _8.8-rc-1_ to _8.8_.


## [0.10.0] - 2024-05-26

### Updated
- **bump Kotlin to _2.0.0_.**
- bump Android Gradle Plugin from _8.4.1_ to _8.6.0-alpha03_ (compile-only dependency).
- update the list of pinned build-time dependencies.

### Fixed
- fix the dependency pinning logic for buildscript dependencies.


## [0.9.1] - 2024-05-25

_**0.9.0** was skipped due to the release publication issues!_

### Removed
- delete deprecated Kotlin/Native targets, removed in Kotlin 2.0.

### Added
- auto downgrade Kotlin API version when it's greater than the language version (and warn about it).
- set up `validatePlugins` task for Gradle plugins. **BREAKING CHANGE!**
- enable AndroidLint checks for all projects by default.
- configure `updateLintBaseline` task for re-creating the baseline files.

### Changed
- prepare for Kotlin 2.0.
- rename `Unix` common source set to `Nix`. **BREAKING CHANGE!**
- parse fallback toml catalog for enabled plugins.

### Updated
- bump Kotlin from _1.9.23_ to _1.9.24_.
- bump jetbrains-compose to _1.6.10_.
- bump Gradle from _8.6_ to _8.8-rc-1_.
- bump Android Gradle Plugin from _8.4.0_ to _8.4.1_ (compile-only dependency).
- bump Android Lint to _8.6.0-alpha03_ (compile-only dependency).
- bump com.mikepenz.aboutlibraries to _11.1.4_.
- bump dependency-analysis to _1.32.0_.
- bump Guava to _33.2.0-jre_ (build-only dependency).
- bump [proguard-core](https://github.com/Guardsquare/proguard-core) from _9.1.3_ to _9.1.4_.


## [0.8.1] - 2024-05-05

### Added
- allow using JRE 21 as target, already supported in tooling.

### Changed
- tune Kotlin compilation configuration a bit.

### Fixed
- auto-disable `-Xjdk-release` for the broken configurations (_JRE 18..21_).
- fix Detekt BaselineProvider loading and restore `detektBaselineMerge` usage.

### Updated
- bump Android Gradle Plugin from _8.3.2_ to _8.4.0_ (compile-only dependency) in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/48.
- bump [io.nlopez.compose.rules:detekt](https://github.com/mrmans0n/compose-rules) from _0.3.15_ to _0.3.20_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/53.
- bump [binary-compatibility-validator-js](https://github.com/fluxo-kt/fluxo-bcv-js) from _0.2.0_ to _0.3.0_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/52.
- bump [proguard-core](https://github.com/Guardsquare/proguard-core) from _9.1.2_ to _9.1.3_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/49.


## [0.8.0] - 2024-04-22

__Public API is CHANGED!__ <br>
_You need to replace `setup*` calls to `fkcSetup*` ones like this: <br>
`setupMultiplatform` => `fkcSetupMultiplatform`._

### Added
- add Compose Desktop setup support and a test project for it.
- bundle toml version catalog with the plugin as a resolving fallback.
- add logging on auto changed yarn dependenciew for Kotlin/JS.
- enable `androidResources.generateLocaleConfig` in android apps by default.

### Changed
- rewise public APIs for ease of use.
- disable Dokka by default.
- unify `setupKotlin` API.

### Fixed
- connect Gmazzo's `BuildConfigTask` to `prepareKotlinBuildScriptModel`.
- do not use `-Xjdk-release` when compiled against the current JDK version.

### Updated
- bump dependency-analysis to _1.31.0_.
- bump task-tree to [_3.0.0_](https://github.com/dorongold/gradle-task-tree/releases/tag/3.0.0).
- bump KSP to [_1.0.20_](https://github.com/google/ksp/releases/tag/1.9.23-1.0.20).
- bump proguard-core to [_9.1.2_](https://github.com/Guardsquare/proguard-core/releases/tag/v9.1.2).
- bump io.nlopez.compose.rules:detekt to [_0.3.15_](https://github.com/mrmans0n/compose-rules/compare/v0.3.11...v0.3.15).
- bump Guava to [_33.1.0-jre_](https://github.com/google/guava/releases/tag/v33.1.0) (build only dependency).
- bump Okio to [_3.9.0_](https://github.com/square/okio/blob/master/CHANGELOG.md#version-390) (build only dependency).
- bump Android Gradle Plugin to _8.3.2_ (compile-only dependency).
- bump com.mikepenz.aboutlibraries to _11.1.3_.
- bump jetbrains-compose to _1.6.2_.


## [0.7.0] - 2024-03-31

### Changed
- update compatibility methods `NamedDomainObjectSet<T>.named*` for Gradle 8.6+ and older.
- output relative paths for the merged report files in the log.
- replace shrinking setup with full-powered processing chains setup.
- update Kotlin compiler settings for _Kotlin 2.0_ and `-Xjdk-release`.

### Added
- self-apply the plugin to itself immediately with included build.
- support double-shrinking with both R8 and ProGuard.
- invalidate jar task when the artifact version changes (e.g. for git HEAD-based snapshots).
- use the local repository publication as one of the project checks.
- verify shrunken artifacts for all public declarations.

### Fixed
- prevent double escaping of cli arguments.
- fail build when shrinker fails to save size for release artifact.
- prevent double calculation of scmTag with GIT commands execution.

### Updated
- bump Android Gradle Plugin from _8.2.2_ to _8.3.1_ (compile-only dependency).
- bump Kotlin from _1.9.22_ to _1.9.23_.
- bump [KSP](https://github.com/google/ksp) from _1.0.17_ to _1.0.19_.
- bump [KtLint](https://github.com/pinterest/ktlint) from _1.1.1_ to _1.2.1_.
- bump `org.json:json` from _20231013_ to _20240205_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/37
- bump `com.mikepenz.aboutlibraries.plugin` from _10.10.0_ to _11.1.0_.
- bump Dokka from _1.9.10_ to _1.9.20_.
- bump R8 to _8.3.37_.
- bump Detekt to _1.23.6_.
- bump `gradle-intellij-plugin` to _1.17.3_.
- bump kctfork to _0.4.1_ (test dependency).


## [0.7.0-alpha2] - 2024-02-22

### Added
- log memory info on Fluxo context start.
- log R8 compatibility mode (full vs. compat).

### Fixed
- don't run in-memory shrinking if there is not enough memory available.
- don't mark the project as in IDE sync mode when no tasks where called and no composite build detected.
- remove invalid checks for composite mode.
- improve and document composite builds detection.
- properly quote and escape CLI arguments for external tool runner.
- fix R8 external run for Ubuntu and macOS (non-Windows systems).

### Changed
- bump gradle-intellij-plugin to 1.17.2.
- bump dependency-guard to 0.5.0.
- bump dependency-analysis to 1.30.0.
- bump R8 to 8.2.47.
- use R8 as a default shrinker (safer and more stable).
- make default publication configuration lazy (use Gradle Provider in `setupGradleProjectPublication`).


## [0.7.0-alpha1] - 2024-02-02

### Added
- allow switching on/off the R8 full mode, also called "non-compat mode." Disabled by default.
- add `FLUXO_VERBOSE` flag to enable verbose output without enabling the `MAX_DEBUG` mode.
- report a version of the bundled/classpath ProGuard version.
- report `includedBuilds` number during the composite build.
- add some documentation and to-do notes.
- create infrastructure for automated R8 and ProGuard shrinkers testing in [0ee74ca](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/0ee74cad8bb6d84a610cefdbd40dbb6213f9ad68).
- add tests for R8 and ProGuard in [7181a82...226a05b](https://github.com/fluxo-kt/fluxo-kmp-conf/compare/7181a82...226a05b).
- shrink plugin artifact with R8 (saved 35.227%, 293.3 KB).
- control keep rule modifiers for all auto-kept classes (in auto-generated keep rules) in [8c8630c7](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/8c8630c7).
- support R8 or ProgGuard available in the classpath (bundled) and support loading in the classpath as a more stable alternative to external run in [07af4372](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/07af4372).

### Fixed
- Fix `TestReportResult` Gradle compatibility in [1923b815](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/1923b815).

### Changed
- `DISABLE_R8` now disables all shrinking altogether.
- improve logging output in [4357abd7](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/4357abd7ebb5192b2252758aeb9d52181904a500).
- improve error reporting for `ExternalToolRunner` in [34ffc208](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/34ffc208).
- bump kotlinx-metadata-jvm to 0.6.2 for ProGuard (used only for ProGuard in a separate classloader or process).
- bump gradle.enterprise to 3.16.2.
- bump jetbrains-compose to 1.5.12.
- bump android-gradle-plugin to 8.2.2.
- bump binary-compatibility-validator to 0.14.0.
- bump detekt to 1.23.5.
- bump spotless to 6.25.0.
- bump proguard to 7.4.2.
- bump ben-manes.versions to 0.51.0.
- bump gradle.taskinfo to 2.2.0.
- bump compose detekt rules to 0.3.11.


## [0.6.0] - 2024-01-05

_Important release that adds advanced shrinking functionality!_

### Added
- **support for shrinking artifacts with ProGuard and/or R8 (ProGuard is used as a default optimal choice)!**
- **auto-generate R8/ProGuard keep rules from `BinaryCompatibilityValidator` API reports!**
- **support for replacing the original artifact with a shrunken one!**
- highlight publication setup in logs.
- verify that publication artifact version is set.
- both WasmWasi and WasmJS can be used together since Kotlin 2.0.
- register `depsAll` task as a rememberable alias for `allDeps`.
- save and show the reason, why the project is in IDE sync mode.
- add Gradle file and I/O utils.
- add util methods for detached dependencies in Gradle.
- add `ExternalToolRunner` and `AbstractExternalFluxoTask` for external tooling.
- add `JvmFiles` and `JvmFilesProvider` classes for easier universal JVM targets manupulations.
- add compatibility method `TaskCollection<T>.named {}` for Gradle 8.6+ and older.
- add minor improvements for `BinaryCompatibilityValidator` configuration safety.

### Fixed
- move `kotlinConfig` computed property to the project-level configuration extension from the root-level context.
- remove MemoizedProvider incompatibility with Gradle 8.6, prevent crashes on future usage, but log the errors.
- correct apiDump/apiCheck tasks dependency and finalize API reports generation with keep rules generation.
- aligh `iosSimulatorArm64` parameter name with all others (_action_ => _configure_).
- correct default JS/WASM targets setup for Kotlin 2.0.

### Changed
- remove tests & checks from the `release` CI workflow.
- remove explicit gradle plugin configuration, which isn't needed anymore.
- simplify logging, remove custom log markers completely.

### Updated
- bump Kotlin from _1.9.21_ to _1.9.22_.
- pin OkHttp (_4.12.0_), Guava (_33.0.0-jre_), and Json (_20231013_) versions due to the Security Advisories.
- bump github/codeql-action from 2 to 3 by @dependabot in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/23
- bump BuildConfig plugin from _5.1.0_ to _5.3.2_.
- bump Android Gradle Plugin from _8.2.0_ to _8.4.0-alpha02_ (compile-only dependency).


## [0.5.0] - 2023-12-24

### Fixed
- correct publication configuration.
- workaround Gradle 8+ problems with publication.
- correct the Gradle Versions Plugin setup.

### Updated
- pin Okio version to 3.7.0 due to the Security Advisory [CVE-2023-3635](https://github.com/advisories/GHSA-w33c-445m-f8w7).


## [0.4.0] - 2023-12-20

### Fixed
- correct search for non-available extensions, handle more edge-cases overall.
- correct setup for the Binary Compatibility Validator.
- configure the Gradle plugin eagerly to avoid issues with composite builds.
- fix release workflow permissions.

### Changed
- log all configured dependencies.
- cleanup code, fix some Detekt warnings.
- use the plugin to configure and build itself.

### Updated
- build-config gradle plugin 5.1.0
- KtLint 1.1.0


## [0.3.0] - 2023-12-15

_Stabilization release._

### Changed
- setup artifacts publication.
- setup BinaryCompatibilityValidator.
- stabilize Detekt configuration.

### Removed
- remove deprecated API surface parts.

### Updated
- KSP 1.0.16
- Android Gradle Plugin 8.2.0
- Spotless 6.23.3
- Gradle Enterprise 3.16.1
- Dependency Analysis 1.28.0


## [0.2.0] - 2023-12-10

🌱 _Initial pre-release in the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf)._


## Notes

[0.14.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.14.0
[0.13.2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.2
[0.13.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.1
[0.13.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.0
[0.12.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.12.1
[0.12.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.12.0
[0.11.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.11.0
[0.10.2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.2
[0.10.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.1
[0.10.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.0
[0.9.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.9.1
[0.8.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.8.1
[0.8.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.8.0
[0.7.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0
[0.7.0-alpha2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0-alpha2
[0.7.0-alpha1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0-alpha1
[0.6.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.6.0
[0.5.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.5.0
[0.4.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.4.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.2.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
