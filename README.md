# Fluxo-KMP-Conf

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

[![Kotlin](http://img.shields.io/badge/Kotlin-1.9.21-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle](http://img.shields.io/badge/Gradle-8.5-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)
[![Android Gradle Plugin](http://img.shields.io/badge/Android--Gradle--Plugin-8.1-0E3B1A?logo=android&labelColor=2B2B2B)](https://mvnrepository.com/artifact/com.android.tools.build/gradle?repo=google)

Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects.

- Completely lazy on-demand project configuration framework with many nice-to-have things out-of-the-box.
- Automatically configures hierarchical source sets, proveds convenience DSL for them.
- You can control, which targets are enabled by passing properties at build time. With no errors in modules with all targets disabled!
- Ready for Android, JS, KMP, KMM, JVM or IDEA plugin modules.
- Allows configuring verification tasks (Detekt, Lint, BinaryCompatibilityValidator with JS support!).
  - Provides merged Sarif reports for the whole project.
  - Provides baseline configuration tasks.
- Adds the convenience console tests report at the end of the build along with merged XML report for the whole project.
- Enables passing of build targets via command line to control what gets configured (great for CI).

Initially made for the [Fluxo][fluxo] state management framework and other libraries, then published for general use.

### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.21"
  id("io.github.fluxo-kt.fluxo-kmp-conf") version "0.2.0" // <-- add here
}
```

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.21"
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
      useModule("com.github.fluxo-kt.fluxo-kmp-conf:fluxo-kmp-conf:3002cb3137") // <-- specify version or commit
  }
}
```

</details>

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
            |-- js
            '-- commonWasm (unstable, may be not available)
                |-- wasmJs
                '-- wasmWasi
         '-- native
               |-- androidNative (tier 3)
               |     |-- androidNativeArm32
               |     |-- androidNativeArm64
               |     |-- androidNativeX64
               |     '-- androidNativeX86
               |-- unix
               |     |-- apple
               |     |     |-- ios
               |     |     |     |-- iosArm32 (deprecated)
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
               |     |           |-- watchosX86 (deprecated)
               |     |           '-- watchosSimulatorArm64
               |     '-- linux
               |           |-- linuxArm32Hfp (deprecated)
               |           |-- linuxArm64
               |           |-- linuxMips32 (deprecated)
               |           |-- linuxMipsel32
               |           '-- linuxX64 (deprecated)
               |-- mingw
               |     |-- mingwX64
               |     '-- mingwX86 (deprecated)
               '-- wasmNative
                     '-- wasm32 (deprecated)
```


### Heavily inspired by

* [Gradle-Setup-Plugin](https://github.com/arkivanov/gradle-setup-plugin) by @arkivanov
* [Gradle-Kmp-Configuration-Plugin](https://github.com/05nelsonm/gradle-kmp-configuration-plugin)
  and [kotlin-components](https://github.com/05nelsonm/kotlin-components/tree/6286792/includeBuild/kmp/src/main/kotlin/io/matthewnelson/kotlin/components/kmp)
  by @05nelsonm
* [Slack-Gradle-Plugin](https://github.com/slackhq/slack-gradle-plugin) ([docs](https://slackhq.github.io/slack-gradle-plugin/))
* [Gradle-Spotless-Plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle) from @diffplug
* [AndroidX Baseline Profile Gradle Plugin](https://github.com/androidx/androidx/blob/7222fd3/benchmark/baseline-profile-gradle-plugin/src/main/kotlin/androidx/baselineprofile/gradle/utils/AgpPlugin.kt)


### Research roadmap
<details>
  <summary>Show</summary>

* [Gradle Plugin TestKit](https://github.com/autonomousapps/dependency-analysis-gradle-plugin/tree/main/testkit) ([Docs](https://docs.gradle.org/current/userguide/test_kit.html))
* https://github.com/square/radiography
* https://github.com/JetBrains-Research/reflekt
* https://github.com/mikepenz/AboutLibraries
* https://github.com/gradle/gradle/issues/26091#issuecomment-1798137734
* https://github.com/BenWoodworth/Parameterize
* https://github.com/hbmartin/hbmartin-detekt-rules
* https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.13.0
* https://github.com/BenWoodworth/Parameterize
* https://github.com/kotlin-hands-on/kotlin-swift-interopedia
* https://slackhq.github.io/keeper/
  * https://github.com/slackhq/Keeper
* https://github.com/drewhamilton/poko/
  * https://github.com/saket/telephoto/releases/tag/0.7.1
</details>


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
