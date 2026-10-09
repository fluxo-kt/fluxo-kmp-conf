# Fluxo-KMP-Conf

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

A Gradle plugin that configures Kotlin, KMP and Android modules from one root plugin and one `fkcSetup*()` call per module, with strict defaults you can switch off one by one.

- Lazy: configures only what a module uses.
- Sets up hierarchical KMP source sets (diagram below).
- `KMP_TARGETS=JVM,JS` (or `KMP_TARGETS_ALL=true`) picks the targets to build, e.g. per CI job (codes: [`KmpTargetCode`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/container/impl/KmpTargetCode.kt)); a module with all its targets filtered out still configures.
- Ready for Android, JS, KMP, JVM, Gradle plugin, or IDEA plugin modules.
- Allows configuring verification tasks (Detekt, Lint, ABI validation — Kotlin's own engine on Kotlin 2.2+, else BCV — with TypeScript API checks for JS).
  - One merged SARIF report per tool for the whole build.
  - Baseline tasks (`./gradlew detektBaselineMerge updateLintBaseline apiDump`).
- A test summary in the console at build end, plus one merged XML test report.
- ProGuard and/or R8 shrinking of JVM artifacts.

Initially made for the [Fluxo][fluxo] state management framework and other libraries, then published for general use.

Targeted for Gradle 9.0+, JDK 17+, and Kotlin 2.1+.
AGP 8.4+ and 9 are covered (older AGP can't run on Gradle 9). Exact checked rows are in [`compat/matrix.tsv`](compat/matrix.tsv).
Built with:<br>
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Ffluxo-kt%2Ffluxo-kmp-conf%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin&label=Kotlin&color=7F52FF&logo=kotlin&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle](https://img.shields.io/badge/Gradle-wrapper-f68244?logo=gradle&labelColor=2B2B2B)](gradle/wrapper/gradle-wrapper.properties)
[![Android Gradle Plugin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Ffluxo-kt%2Ffluxo-kmp-conf%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions%5B'android-gradle-plugin'%5D&label=Android%20Gradle%20Plugin&color=0E3B1A&logo=android&labelColor=2B2B2B)](https://mvnrepository.com/artifact/com.android.tools.build/gradle?repo=google)

### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in `settings.gradle.kts`, after `pluginManagement {}`.
plugins {
  id("io.github.fluxo-kt.fluxo-kmp-conf.settings") version "0.18.0" // <-- add here
}
```

```kotlin
// in the root `build.gradle.kts`.
plugins {
  kotlin("multiplatform") version "2.4.20"
  id("io.github.fluxo-kt.fluxo-kmp-conf") version "0.18.0" // <-- and here
}
```

Apply the plugin in the root project. Configure modules separately with the setup
functions below. Keep a Kotlin plugin before it in `plugins {}`; `apply false` is fine.

The settings line puts the plugins fluxo applies for you (KSP, plugin-publish, dependency
analysis) on the build classpath of each module without submodules, so they work with the
configuration cache and need no `plugins {}` lines of their own; a version you declare yourself
wins. A module with submodules (the root, or a parent like `:benchmarks`) gets none, since a
copy there would break every submodule that declares the plugin with a version; if it uses KSP
itself, the build tells you the exact line to add. Without the settings line, the root line
alone does the same, except for a single-module build's root project.

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the root `build.gradle.kts`.
plugins {
  kotlin("multiplatform") version "2.4.20"
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
    when (requested.id.toString()) { // ← specify a version or commit
      "io.github.fluxo-kt.fluxo-kmp-conf" ->
        useModule("com.github.fluxo-kt.fluxo-kmp-conf:fluxo-kmp-conf:02a9004934")
      "io.github.fluxo-kt.fluxo-kmp-conf.settings" ->
        useModule("com.github.fluxo-kt.fluxo-kmp-conf:fluxo-kmp-conf-settings:02a9004934")
    }
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

Defaults are strict (extra compiler checks, the JDK API limited to the JVM target, Android
`NewApi` failing `check`), and each strict setting has its own off-switch, so you can start
without extra configuration and relax only what blocks you.

Your own settings beat these defaults:
- What you set in `kotlin { compilerOptions }`, for the module or a target, wins wherever you write it, and a flag you pass there replaces fluxo's flag of the same name.
- A few defaults differ per compilation (warnings as errors off for tests, JS and shared metadata; a separate test language version): set those on the compile task.
- The JVM target is set with fluxo's `jvmTarget`, which keeps Kotlin and Java on the same target.
- In `android {}`, the namespace, SDK levels and test runner you set win wherever you write them; other Android settings fluxo writes, set after `fkcSetup*()`.
- Every compiler default fluxo adds can be switched off per module or with `DISABLE_KOTLIN_DEFAULTS` (see [`FluxoConfigurationExtensionKotlinOptions`](fluxo-kmp-conf/src/main/kotlin/fluxo/conf/dsl/FluxoConfigurationExtensionKotlinOptions.kt)).

A few examples of configuration:
- [Compose desktop application](checks/compose-desktop/build.gradle.kts)
- [Gradle plugin](checks/gradle-plugin/build.gradle.kts)
- [Kotlin Multiplatform library](checks/kmp/build.gradle.kts)

#### Recognised version-catalog aliases

fluxo uses these aliases if your `gradle/libs.versions.toml` defines them:

| Alias | Where to define | What it does |
|---|---|---|
| `androidx.compose.ui.tooling` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `square.leakcanary` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `square.plumber` | `[libraries]` | Excluded from `dependency-analysis` reports (debug-only dep). |
| `pinned` | `[bundles]` | Minimum versions (e.g. security fixes) for every module's dependencies. Constraints only raise versions. The root build classpath loads before any plugin runs, so there fluxo can't apply them; when a pinned module runs older there, the build ends with the `buildscript { dependencies { constraints { … } } }` lines to add. |


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

Target groups (`allDefaultTargets()`, `ios()`, `watchos()`, …) add only the targets your Kotlin version fully supports, and never `iosX64`. To keep a target your Kotlin deprecates but still builds, call it explicitly (e.g. `macosX64()` on Kotlin 2.4).

JS browser tests: by default they run in Karma with a local Chrome and are skipped with a warning where none is found. On Kotlin 2.4.20+, `kotlin { js { browser { test { chromium() } } } }` (opt in with `@OptIn(ExperimentalJsTestDsl::class)`) runs them with Playwright in a browser Gradle downloads, so they run where Chrome isn't installed; Wasm targets stay on Karma.

Multi-release jar: code in `src/jvm<N>Main` (N ≥ 9, e.g. `src/jvm11Main/kotlin`) is compiled for JVM N against `jvmMain` (its `internal` declarations included) and packed into `META-INF/versions/N` of the JVM jar, which Java N+ loads in place of the base classes. A `module-info.java` in `src/jvm9Main/java` makes the jar a Java module on Java 9+ while it still runs on Java 8.

`TEST_JDK=<N>` (env var or Gradle property) runs every JVM test task, Android unit tests included, on JDK N (a Gradle toolchain), while the build itself stays on its own JDK. Run one CI leg per JDK you support to test the code each JDK actually loads: multi-release variants, or paths chosen by a runtime version check.


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
