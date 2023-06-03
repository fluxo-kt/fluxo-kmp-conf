# Fluxo-KMP-Conf

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

[![Kotlin](http://img.shields.io/badge/Kotlin-1.8.20-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle](http://img.shields.io/badge/Gradle-8.1.1-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)
[![Android Gradle Plugin](http://img.shields.io/badge/Android--Gradle--Plugin-8.0.2-0E3B1A?logo=android&labelColor=2B2B2B)](https://mvnrepository.com/artifact/com.android.tools.build/gradle?repo=google)

Convenience Gradle plugin for reliable configuration of Kotlin projects.

- Automatically configures hierarchical source sets, proveds convenience DSL for them.
- Ready for Android, JS, KMP, KMM, JVM or IDEA plugin modules.
- Allows configuring verification tasks (Detekt, Lint, BinaryCompatibilityValidator with JS support!).
  - Provides merged Sarif reports for the whole project.
  - Provides baseline configuration tasks.
- Adds convenience console tests report in the end of the build along with merged xml report for the whole project.
- Enables passing of build targets via command line to control what gets configured (great for CI).

Initially made for the [Fluxo][fluxo] state management framework and other libraries, then published for general use.


### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.8.21"
  id("io.github.fluxo-kt.fluxo-kmp-conf") version "0.1.0" // <-- add here
}
```

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.8.21"
  id("io.github.fluxo-kt.fluxo-kmp-conf") // <-- add here, no version needed for jitpack usage
}
```
```kotlin
// in the `settings.gradle.kts` of the project
pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://jitpack.io") // <-- add jitpack repo
  }
  resolutionStrategy.eachPlugin {
    if (requested.id.toString() == "io.github.fluxo-kt.fluxo-kmp-conf")
      useModule("com.github.fluxo-kt.fluxo-kmp-conf:fluxo-kmp-conf:d29a9564b4") // <-- specify version or commit
  }
}
```
</details>


### Heavily inspired by

* [Gradle-Setup-Plugin](https://github.com/arkivanov/gradle-setup-plugin) by @arkivanov
* [Gradle-Kmp-Configuration-Plugin](https://github.com/05nelsonm/gradle-kmp-configuration-plugin) by @05nelsonm


### Versioning

Uses [SemVer](http://semver.org/) for versioning.


### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.

[plugin]: https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf
[badge-plugin]: https://img.shields.io/gradle-plugin-portal/v/io.github.fluxo-kt.fluxo-kmp-conf?label=Gradle%20Plugin&logo=gradle

[jitpack]: https://www.jitpack.io/#fluxo-kt/fluxo-kmp-conf
[badge-jitpack]: https://www.jitpack.io/v/fluxo-kt/fluxo-kmp-conf.svg

[fluxo]: https://github.com/fluxo-kt/fluxo
