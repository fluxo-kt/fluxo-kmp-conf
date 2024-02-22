# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)

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
- bump gradle-intellij-plugin to 1.17.1.
- bump dependency-guard to 0.5.0.
- use R8 as a default shrinker (safer and more stable).


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

[0.7.0-alpha1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0-alpha1
[0.6.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.6.0
[0.5.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.5.0
[0.4.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.4.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.2.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
