# Fluxo-KMP-Conf

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects.

- Completely lazy on-demand project configuration framework with many nice-to-have things out-of-the-box.
- Automatically configures hierarchical source sets and provides convenience DSL for them.
- You can control which targets are enabled by passing properties at build time. With no errors in modules with all targets disabled!
- Ready for Android, JS, KMP, KMM, JVM, or IDEA plugin modules.
- Allows configuring verification tasks (Detekt, Lint, BinaryCompatibilityValidator with JS support!).
  - Provides merged Sarif reports for the whole project.
  - Provides baseline configuration tasks.
- Convenience console tests report at the end of the build along with a merged XML report for the whole project.
- Allows using ProGuard and/or R8 as an optimizer for JVM targets.
- Enables passing of build targets via command line to control what gets configured (great for CI).

Initially made for the [Fluxo][fluxo] state management framework and other libraries, then published for general use.

Targeted for Gradle 9.0+, JDK 17+, and Kotlin 2.1+.
AGP 8 and 9 are covered. Exact checked rows are in [`compat/matrix.tsv`](compat/matrix.tsv).
Built with:<br>
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)
[![Android Gradle Plugin](https://img.shields.io/badge/Android--Gradle--Plugin-9.1.1-0E3B1A?logo=android&labelColor=2B2B2B)](https://mvnrepository.com/artifact/com.android.tools.build/gradle?repo=google)

### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the root `build.gradle.kts`.
plugins {
  kotlin("multiplatform") version "2.2.21"
  id("io.github.fluxo-kt.fluxo-kmp-conf") version "0.15.0" // <-- add here
}
```

Apply the plugin in the root project. Configure modules separately with the setup
functions below. Keep a Kotlin plugin before it in `plugins {}`; `apply false` is fine.

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the root `build.gradle.kts`.
plugins {
  kotlin("multiplatform") version "2.2.21"
  id("io.github.fluxo-kt.fluxo-kmp-conf") // ← add here, no version needed for jitpack usage
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
      useModule("com.github.fluxo-kt.fluxo-kmp-conf:fluxo-kmp-conf:5b57cfecad") // ← specify a version or commit
  }
}
```

</details>

### Configuration

Start by calling the matching setup function in each module's `build.gradle.kts`:

- [`fkcSetupAndroidLibrary()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupAndroid.kt) for the Android library setup.
- [`fkcSetupAndroidApp()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupAndroid.kt) for the Android application setup.
- [`fkcSetupGradlePlugin()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupGradlePlugin.kt) for the Gradle plugin setup.
- [`fkcSetupIdeaPlugin()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupIdeaPlugin.kt) for the IntelliJ Platform plugin setup.
- [`fkcSetupKotlin()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupKotlin.kt) for the regular Kotlin JVM setup of any kind.
  - or [`fkcSetupKotlinApp()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupKotlinApp.kt) - same but tailored for JVM applications.
- [`fkcSetupMultiplatform()`](fluxo-kmp-conf/src/main/kotlin/FkcSetupMultiplatform.kt) for the Kotlin Multiplatform setup.

See the corresponding KDocs for more details.

`Fluxo-KMP-Conf` configures the project from applied plugins and enabled targets.
Use these DSLs when defaults are not enough:
- [`FluxoConfigurationExtension`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtension.kt)
- [`FluxoConfigurationExtensionCommon`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionCommon.kt)
- [`FluxoConfigurationExtensionKotlinOptions`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionKotlinOptions.kt)
- [`FluxoConfigurationExtensionKotlin`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionKotlin.kt)
- [`FluxoConfigurationExtensionAndroid`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionAndroid.kt)
- [`FluxoConfigurationExtensionPublication`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionPublication.kt)
- [`FluxoPublicationConfig`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoPublicationConfig.kt)
- [`BinaryCompatibilityValidatorConfig`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/BinaryCompatibilityValidatorConfig.kt)
- [`KmpConfigurationContainerDsl`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/container/KmpConfigurationContainerDsl.kt)

Only the safest, most broadly useful settings are enabled by default,
so you can start without extra configuration.

A few examples of configuration:
- [Compose desktop application](checks/compose-desktop/build.gradle.kts)
- [Gradle plugin](checks/gradle-plugin/build.gradle.kts)
- [Kotlin Multiplatform library](checks/kmp/build.gradle.kts)

#### Recognised version-catalog aliases

The plugin opportunistically wires in the following aliases when defined in your `gradle/libs.versions.toml`. Absence is silent; presence triggers the documented behaviour.

| Alias | Where to define | What it does |
|---|---|---|
| `androidx.compose.ui.tooling` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `square.leakcanary` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `square.plumber` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `pinned` | `[bundles]` | List dependencies whose version the plugin should constrain against accidental upstream downgrade. |


## Hierarchical KMP project structure

- [Kotlin docs: Hierarchical project structure](https://kotlinlang.org/docs/multiplatform-hierarchy.html)
- [Kotlin/Native target support](https://kotlinlang.org/docs/native-target-support.html)
- [Distinguish several targets for one platform](https://kotlinlang.org/docs/multiplatform-set-up-targets.html#distinguish-several-targets-for-one-platform)

`Fluxo-KMP-Conf` automatically configures KMP projects with a hierarchical source-set structure based on the module configuration.

```text
 common
   |-- commonJvm
   |     |-- jvm
   |     '-- android
   '-- nonJvm
         |-- commonJs
         |  |-- js
         |  '-- commonWasm (unstable, may be not available)
         |      |-- wasmJs
         |      '-- wasmWasi (experimental)
         '-- native
               |-- nix (unix-like systems)
               |     |-- apple
               |     |     |-- ios
               |     |     |     |-- iosArm64
               |     |     |     |-- iosX64
               |     |     |     '-- iosSimulatorArm64
               |     |     |-- macos
               |     |     |     |-- macosArm64
               |     |     |     '-- macosX64
               |     |     |-- tvos
               |     |     |     |-- tvosArm64
               |     |     |     |-- tvosX64
               |     |     |     '-- tvosSimulatorArm64
               |     |     '-- watchos
               |     |           |-- watchosArm32
               |     |           |-- watchosArm64
               |     |           |-- watchosDeviceArm64 (tier 3)
               |     |           |-- watchosX64
               |     |           '-- watchosSimulatorArm64
               |     |-- linux
               |     |     |-- linuxArm32Hfp (deprecated)
               |     |     |-- linuxArm64
               |     |     '-- linuxX64
               |     '-- androidNative (tier 3, can has limited set of POSIX APIs)
               |          |-- androidNativeArm32
               |          |-- androidNativeArm64
               |          |-- androidNativeX64
               |          '-- androidNativeX86
               '-- mingw (Windows with limited set of POSIX APIs)
                     '-- mingwX64
```


### Build and development notes

- **REQUIRES ENABLED GIT SYMLINKS** for the project to work correctly **during plugin development**!
  - *not needed for the plugin usage!*
  - Usually it’s already enabled on Linux or macOS.
  - On Windows, see [this doc](https://github.com/git-for-windows/git/wiki/Symbolic-Links) for more info.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for more info on how to contribute.


### Heavily inspired by

* [Gradle-Setup-Plugin](https://github.com/arkivanov/gradle-setup-plugin) by @arkivanov
* [Gradle-Kmp-Configuration-Plugin](https://github.com/05nelsonm/gradle-kmp-configuration-plugin)
  and [kotlin-components](https://github.com/05nelsonm/kotlin-components/tree/6286792/includeBuild/kmp/src/main/kotlin/io/matthewnelson/kotlin/components/kmp)
  by @05nelsonm
* [Slack-Gradle-Plugin](https://github.com/slackhq/slack-gradle-plugin) ([docs](https://slackhq.github.io/slack-gradle-plugin/))
* [Gradle-Spotless-Plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle) from @diffplug
* [AndroidX Baseline Profile Gradle Plugin](https://github.com/androidx/androidx/blob/7222fd3/benchmark/baseline-profile-gradle-plugin/src/main/kotlin/androidx/baselineprofile/gradle/utils/AgpPlugin.kt)
* [Avito android infrastructure](https://github.com/avito-tech/avito-android) ([docs](https://avito-tech.github.io/avito-android/))


### Versioning

Uses [SemVer](http://semver.org/) for versioning.

### License

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

This project is licensed under the Apache License, Version 2.0 — see the
[license](LICENSE) file for details.

[plugin]: https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf

[badge-plugin]: https://img.shields.io/gradle-plugin-portal/v/io.github.fluxo-kt.fluxo-kmp-conf?label=Gradle%20Plugin&logo=gradle

[jitpack]: https://www.jitpack.io/#fluxo-kt/fluxo-kmp-conf

[badge-jitpack]: https://www.jitpack.io/v/fluxo-kt/fluxo-kmp-conf.svg

[fluxo]: https://github.com/fluxo-kt/fluxo
