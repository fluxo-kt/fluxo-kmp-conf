# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)

### Added
- allow disabling Android Lint when no Android plugin is used.
- detect when the project is a child of a composite build and has no startup tasks.


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
