# Fluxo-KMP-Conf

[![Gradle Plugin Portal][badge-plugin]][plugin]
[![JitPack][badge-jitpack]][jitpack]
[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![Common Changelog](https://common-changelog.org/badge.svg)](CHANGELOG.md)

Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects.

- Completely lazy on-demand project configuration framework with many nice-to-have things out-of-the-box.
- Automatically configures hierarchical source sets, proveds convenience DSL for them.
- You can control, which targets are enabled by passing properties at build time. With no errors in modules with all targets disabled!
- Ready for Android, JS, KMP, KMM, JVM or IDEA plugin modules.
- Allows configuring verification tasks (Detekt, Lint, BinaryCompatibilityValidator with JS support!).
  - Provides merged Sarif reports for the whole project.
  - Provides baseline configuration tasks.
- Convenience console tests report at the end of the build along with merged XML report for the whole project.
- Allows using ProGuard and/or R8 as an optimizer for JVM targets.
- Enables passing of build targets via command line to control what gets configured (great for CI).

Initially made for the [Fluxo][fluxo] state management framework and other libraries, then published for general use.

Targeted for Gradle 8+ and Kotlin 1.9+. Built with:<br>
[![Kotlin](http://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoWidth=10&logoColor=7F52FF&labelColor=2B2B2B)](https://github.com/JetBrains/Kotlin)
[![Gradle](http://img.shields.io/badge/Gradle-8.5-f68244?logo=gradle&labelColor=2B2B2B)](https://gradle.org/releases/)
[![Android Gradle Plugin](http://img.shields.io/badge/Android--Gradle--Plugin-8.2-0E3B1A?logo=android&labelColor=2B2B2B)](https://mvnrepository.com/artifact/com.android.tools.build/gradle?repo=google)

### How to use

[![Gradle Plugin Portal][badge-plugin]][plugin]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.22"
  id("io.github.fluxo-kt.fluxo-kmp-conf") version "0.6.0" // <-- add here
}
```

<details>
<summary>How to use snapshots from JitPack repository</summary>

[![JitPack][badge-jitpack]][jitpack]

```kotlin
// in the `build.gradle.kts` of the target module
plugins {
  kotlin("multiplatform") version "1.9.22"
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
* [Avito android infrastructure](https://github.com/avito-tech/avito-android) ([docs](https://avito-tech.github.io/avito-android/))


### Research roadmap
<details>
  <summary>Show</summary>

* [Gradle Plugin TestKit](https://github.com/autonomousapps/dependency-analysis-gradle-plugin/tree/main/testkit) ([Docs](https://docs.gradle.org/current/userguide/test_kit.html))
* https://github.com/square/radiography
* https://github.com/JetBrains-Research/reflekt
* https://github.com/mikepenz/AboutLibraries
* https://github.com/gradle/gradle/issues/26091#issuecomment-1798137734
* https://github.com/BenWoodworth/Parameterize
* Set JDK release for kotlin compilation safety (`-Xjdk-release=`)
* Detekt rules
  * Enable more rules aside from baseline
  * https://github.com/hbmartin/hbmartin-detekt-rules
  * https://github.com/woltapp/arrow-detekt-rules
  * https://detekt.dev/docs/rules/libraries/
  * https://detekt.dev/docs/rules/ruleauthors
  * https://github.com/topics/detekt-rules
  * https://detekt.dev/marketplace
    * https://detekt.dev/marketplace/#unpublished
  * Create detekt rules for Gradle plugins best practices
    * e.g., not to use `org.gradle.api.tasks.TaskCollection.matching`, `findByName`, etc. when `named` or `withType`
     is enough (don't early create tasks).
      * https://github.com/gmazzo/gradle-buildconfig-plugin/commit/a21a8b9
    * e.g., a task must not use any Project objects at execution time.
    * Also, warn on any `org.gradle.internal` usage because its is internal API.
    * https://docs.gradle.org/8.5/userguide/configuration_cache.html#config_cache:requirements
* Linting
  * https://github.com/jeremymailen/kotlinter-gradle
  * https://dev.to/aseemwangoo/supercharge-your-kotlin-project-2mcb
  * Konsist
    * https://github.com/LemonAppDev/konsist
    * https://proandroiddev.com/protect-kotlin-project-architecture-using-konsist-3bfbe1ad0eea
  * KDoc formatting
    * https://github.com/tnorbye/kdoc-formatter
* https://github.com/ashtanko/kotlin-app-template/tree/main
  * Github Action + git-hook + Issues Template
* __Infrastructure plugins__
  * https://github.com/slackhq/slack-gradle-plugin/
    * https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.13.0
    * https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.14.0
  * https://github.com/avito-tech/avito-android
    * https://github.com/avito-tech/avito-android/blob/a1949b4/subprojects/assemble/proguard-guard/src/main/kotlin/com/avito/android/proguard_guard/shadowr8/ShadowR8TaskCreator.kt
    * GIT hooks: https://github.com/avito-tech/avito-android/tree/develop/.git_hooks
  * Gradle Core plugins
    * https://github.com/gradle/gradle/tree/a300b86/platforms/documentation/docs/src/docs/userguide/core-plugins
  * Gradle configuration
    * https://github.com/Kotlin/kotlinx.coroutines/blob/d12eb45/kotlinx-coroutines-core/build.gradle#L238
    * Test Suites
      * https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html
      * https://github.com/unbroken-dome/gradle-testsets-plugin
    * Testing Gradle Builds
      * Gradle TestKit: https://docs.gradle.org/current/userguide/test_kit.html
        * TODO: `testSourceSets(sourceSets.functionalTest)`
    * TWiStErRob's Quality plugin for Gradle that supports Android flavors
      * https://github.com/TWiStErRob/net.twisterrob.gradle
    * Nebula-plugins
      * Healthy defaults for a standard Gradle project.
        * https://github.com/nebula-plugins/nebula-project-plugin
        * Builds Javadoc and Sources jars.
        * Doesn't fail javadoc if there are none found.
        * Record information about the build and stores it in the .jar, via [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin).
        * Easy specification of people involved in a project via [gradle-contacts-plugin](https://github.com/nebula-plugins/gradle-contacts-plugin).
        * Introduces Nebula Dependency Lock Plugin out of the box, supports Gradle's Locking dependency versions mechanism too.
        * Introduces Nebula Facet Plugin. A routine pattern is wanting a new SourceSet with an accompanying Configuration for dependencies.
        * Introduces IntegTest Plugin specifically for Integration Tests.
      * Nebula Dependency Lock Plugin
        * https://github.com/nebula-plugins/gradle-dependency-lock-plugin
        * Allows people using dynamic dependency versions to lock them to specific versions.
        * Support saving and checking hash or signature of dependency in a report.
          * Also note for safety against supply chain attacks:
            https://github.com/dropbox/dependency-guard/issues/103
      * Plugin to gather information about the environment
        * https://github.com/nebula-plugins/gradle-info-plugin
        * Noninvasively collect information about the environment, and make information available to other plugins in a statically typed way.
        * When possible lazily calculate info.
        * https://github.com/nebula-plugins/gradle-contacts-plugin
          * Structure to define the owners of a project, then contributing this back to other plugins.
      * Linter tool for identifying and reporting on patterns of misuse or deprecations in Gradle scripts.
        * https://github.com/nebula-plugins/gradle-lint-plugin
      * Gradle plugin for providing reusable dependency resolution rules.
        * https://github.com/nebula-plugins/gradle-resolution-rules-plugin
      * Gradle capabilities and transforms to ease the migration from Java EE to Jakarta EE.
        * https://github.com/nebula-plugins/gradle-jakartaee-migration-plugin
      * Gradle plugin for constructing linux packages, specifically RPM and DEBs.
        * https://github.com/nebula-plugins/gradle-ospackage-plugin
      * Publishing related plugins
        * https://github.com/nebula-plugins/nebula-publishing-plugin
      * Test harness for Gradle plugins, leveraging [Spock](http://spockframework.org/).
        * https://github.com/nebula-plugins/nebula-test
      * Adds lot of NodeJS-based technologies as part of build without having Node.js installed locally.
        * https://github.com/nebula-plugins/nebula-node-plugin
      * Kotlin library providing extensions to assist with Gradle iterop and backwards compatibility.
        * https://github.com/nebula-plugins/nebula-gradle-interop
      * Gradle plugin introducing a provided dependency configuration and marking a dependency as optional.
        * https://github.com/nebula-plugins/gradle-extra-configurations-plugin
      * Base SCM Plugin for gathering information or performing actions (Archived).
        * https://github.com/nebula-plugins/gradle-scm-plugin
    * SgtSilvio gradle plugins
      * Example: https://github.com/SgtSilvio/gradle-proguard/blob/61e7230/build.gradle.kts
      * Gradle plugin to ease using and producing (multi-arch) OCI (Open Container Initiative, prev. Docker) images.
        without requiring external tools.
        * https://github.com/SgtSilvio/gradle-oci
        * https://github.com/SgtSilvio/oci-registry (OCI registry Java library that allows serving OCI artifacts to pull operations).
        * https://github.com/SgtSilvio/gradle-oci-junit-jupiter.
      * Gradle plugin to ease defining project metadata (urls, license, scm).
        * module name, readable name, url, docUrl, organization, license, developers, issue management, github.
        * https://github.com/SgtSilvio/gradle-metadata
      * Gradle plugin to ease defining Javadoc links.
        * https://github.com/SgtSilvio/gradle-javadoc-links
      * Gradle plugin that configures sensible defaults.
        * https://github.com/SgtSilvio/gradle-defaults
        * UTF 8 for Java compilation and Javadoc
        * Reproducible artifacts
        * Granular test reports per test case (method instead of class)
* https://github.com/BenWoodworth/Parameterize
* https://github.com/kotlin-hands-on/kotlin-swift-interopedia
* https://github.com/drewhamilton/poko/
  * https://github.com/saket/telephoto/releases/tag/0.7.1
* https://telegra.ph/Compose-stabilityConfigurationPath-11-30
  * https://fvilarino.medium.com/exploring-jetpack-compose-compilers-stability-config-f1ccb197d6c0
* https://github.com/yandexmobile/detekt-rules-ui-tests
  * https://habr.com/ru/companies/yandex/articles/779152/
  * https://t.me/c/1198043993/3696
  * https://edmundkirwan.com/general/cdd.html
  * https://edmundkirwan.com/general/c-and-c.html
* __https://github.com/VKCOM/vkompose/__
  * https://mobiusconf.com/talks/0beebbbd16bf4358ab2a1b60cabf57a1
  * https://t.me/compose_broadcast/202
  * https://t.me/int_ax/47
    * https://t.me/int_ax/47?comment=25
    * https://t.me/int_ax/47?comment=29
    * https://t.me/int_ax/47?comment=49
* https://github.com/saveourtool/diktat
* https://github.com/Kotlin/kotlinx-benchmark/
  * https://github.com/CharlieTap/cachemap
  * https://github.com/CharlieTap/cachemap/tree/failing-native-benchmark
  * https://github.com/CharlieTap/cachemap/tree/benchmarking
* https://gitlab.com/opensavvy/ci-templates
  * https://gitlab.com/opensavvy/playgrounds/gradle/-/blob/main/.gitlab-ci.yml?ref_type=heads
* https://github.com/gmazzo/gradle-codeowners-plugin
* https://github.com/gmazzo/gradle-docker-compose-plugin
* https://github.com/gmazzo/gradle-report-publications-plugin
* Shadowing + minification
  * https://github.com/GradleUp/gr8 (Gr8 = Gradle + R8)
  * Task used by the UI and Android tests to check minification results and keep track of binary size.
    * https://github.com/lowasser/kotlinx.coroutines/blob/fcaa6df/buildSrc/src/main/kotlin/RunR8.kt
  * A Gradle plugin that infers Proguard/R8 keep rules for androidTest sources.
    * https://slackhq.github.io/keeper/
      * https://github.com/slackhq/Keeper
  * dProtect obfuscator
    * https://github.com/open-obfuscator/dProtect
  * Optimize app images
    * https://tinypng.com/
* Control licenses
  * https://github.com/JetBrains/intellij-community/blob/8b5ce28/platform/build-scripts/src/org/jetbrains/intellij/build/CommunityLibraryLicenses.kt
  * https://github.com/mikepenz/AboutLibraries
* GitHub CI/CD, workflows and repo organization.
  * Add automatic adding PR comment with Gradle Job Summary
    * https://github.com/gradle/gradle-build-action/pull/1021/files
    * https://github.com/gradle/gradle-build-action/issues/1020
  * https://github.com/actions/dependency-review-action
  * Compare artifacts in the commit (with prev commit) or PR (with upstream)
    * https://github.com/JakeWharton/diffuse
  * https://github.com/square/leakcanary/tree/main/.github
* Stores publishing
  * https://github.com/chippmann/androidpublisher/
    * https://github.com/chippmann/androidpublisher/releases/tag/0.3.3
* CI security scanning of Android app using AppSweep (API KEY REQUIRED)
  * https://github.com/guardsquare/appsweep-gradle
  * https://appsweep.guardsquare.com/
  * https://plugins.gradle.org/plugin/com.guardsquare.appsweep
* Java 9 modularity support
  * https://github.com/Kotlin/kotlinx.coroutines/blob/d12eb45/buildSrc/src/main/kotlin/Java9Modularity.kt
* Common utils
  * https://github.com/aminography/CommonUtils/tree/1bfbe2d/library/src/main/java/com/aminography/commonutils
* States and Events
  * Circuit: https://slackhq.github.io/circuit/states-and-events/
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
