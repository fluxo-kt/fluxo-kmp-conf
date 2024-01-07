# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)

### Added
- allow switching on/off the R8 full mode, also called "non-compat mode." Disabled by default.


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

[0.6.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.6.0
[0.5.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.5.0
[0.4.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.4.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.2.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
