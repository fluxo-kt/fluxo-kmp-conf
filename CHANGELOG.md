# Changelog [^1]


## Unreleased

[//]: # (Removed, Added, Changed, Fixed, Updated)

### Added
- highlight publication setup in logs.
- verify that publication artifact version is set.

### Changed
- remove tests & checks from the `release` CI workflow.
- remove explicit gradle plugin configuration, which isn't needed anymore.

### Updated
- bump Kotlin from _1.9.21_ to _1.9.22_.


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

[0.5.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.5.0
[0.4.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.4.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.2.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
