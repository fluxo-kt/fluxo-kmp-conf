# Roadmap, ideas, and notes

### Plan

- Adds experimental support to jvm for configuring multi-release Jars to include `module-info.java`
  [1](https://github.com/05nelsonm/gradle-kmp-configuration-plugin/pull/45/files).


### Research roadmap

<details>
  <summary>Show</summary>

* Gradle 8.8 support
  * https://docs.gradle.org/8.8/release-notes.html#new-gradle-lifecycle-callbacks
* `org.gradle.unsafe.isolated-projects=true` for Kotlin 2.0.20 (JVM/Android/KAPT only)
  * https://kotlinlang.slack.com/archives/C0KLZSCHF/p1718741426896649
  * https://docs.gradle.org/nightly/userguide/isolated_projects.html
  * https://docs.gradle.org/8.8/release-notes.html#isolated-project-views
* `-Xconsistent-data-class-copy-visibility` for Kotlin 2.0.20
  * https://kotlinlang.org/docs/whatsnew-eap.html#language-data-class-copy-function-to-have-the-same-visibility-as-constructor
* Streamline kotlin configuration and JDK setup
  * https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.17.2
  * https://github.com/slackhq/slack-gradle-plugin/pull/883/files
* Project appearance in the IDE.
  * https://www.jetbrains.com/help/idea/open-close-and-move-projects.html#change-project-icon
  * https://www.jetbrains.com/help/idea/customize-actions-menus-and-toolbars.html#use-colors-in-toolbar
* Create a unified root task `updateBaseline` that depends automatically on all baseline tasks for all connected tools.
* Create a special tasks to save all extra detektPlugins in a single folder for easier usage from the IDE.
  * Configure Detekt in the IDE right away to use this folder for plugins via `.idea/detekt.xml`.
  * Also, set the project config file path in the same XML config.
  * Research if it's possible to ask IDE to install the Detekt plugin if not installed.
* Kotlin Power-assert compiler plugin
  * https://kotlinlang.org/docs/whatsnew20.html#experimental-kotlin-power-assert-compiler-plugin
* https://github.com/jacobras/Human-Readable/commit/041c5dedbd2aa1079616ceb445c93ab3c7547630
* https://github.com/WasmEdge/WasmEdge?tab=readme-ov-file#license
* https://github.com/eygraber/gradle-conventions/blob/master/conventions-detekt/src/main/kotlin/com/eygraber/conventions/detekt/register_detekt_task.kt
* https://github.com/takahirom/roborazzi/issues/302#issuecomment-2115873251
* https://github.com/JakeWharton/kmp-missing-targets
* https://github.com/eygraber/gradle-conventions
  * https://github.com/eygraber/gradle-conventions/pull/367/files#diff-1534017ba077dda735977a1ef95679ee14e382237106d48a4ba8df35d82c1a89
* Kover plugin integration and support
* Only export the framework under an Xcode build as part of the pre-build step script ("XCODE_VERSION_MAJOR")
  * https://github.com/badoo/Reaktive/pull/777/files#diff-06dc42069b6b1b646cdb9afdcdfea2a3175691cc744e94302ac4a9fec1555b7aR12
* Compose Screenshot Testing
  * https://developer.android.com/studio/preview/compose-screenshot-testing
* Code quality rules:
  * `first()` on Flow (not `StateFlow` or at least `SharedFlow`) isdangerous and easily can lead to infinite suspension.
  * Same for `Deffered.await()`, `select{}` with no timeout option, etc.
* https://developers.google.com/android/guides/setup
  * Show open-source licenses `com.google.android.gms:oss-licenses-plugin`
  * Strict version checking `com.google.android.gms:strict-version-matcher-plugin`
* Support `-Xconsistent-data-class-copy-visibility` for Kotlin 2.0.20
  * https://youtrack.jetbrains.com/issue/KT-11914/Confusing-data-class-copy-with-private-constructor
* Support auto detection if `-Xjdk-release` can be used. Fail only for release builds. Warn otherwise.
  * _If there's no `ct.sym` file in JDK but `-Xjdk-release` is used, the compiler will stop with an error. The only workaround in that case is to remove `-Xjdk-release`._
  * https://youtrack.jetbrains.com/issue/KT-29974#focus=Comments-27-9458958.0-0
* Kotlin MPP publication: add an option to support non-Gradle consumers
  * https://youtrack.jetbrains.com/issue/KT-57573
  * https://github.com/arrow-kt/arrow-gradle-config/blob/cba09cc/arrow-gradle-config-publish/src/main/kotlin/internal/PublishMppRootModuleInPlatform.kt#L13
* Security setup
  * Setup for securing API keys in `BuildConfigs` (generated code in general) and in the usual code.
* GIT hooks
  * Spotless
  * Detekt
  * KtLint
* GraalVM native build + UPX compression
* IDEA Ext plugin (declare IDEA settings via gradle)
  * https://github.com/JetBrains/gradle-idea-ext-plugin/wiki
* Louvain algorithm to analyze class dependencies and rank deduced modules based on their conductance score. Lobzik is a Gradle plugin for helping to modularise large Android codebases.
  * https://github.com/Mishkun/lobzik
  * https://dev.to/mishkun/carving-the-path-to-modularity-a-lobzik-tool-case-study-on-the-protonmail-android-app-mid
* https://github.com/topjohnwu/libsu/blob/01570d643af91b0e271de018465a219eed8db322/service/build.gradle.kts#L21
* Disambiguate existing javadoc and sources tasks
* Check BuildConfig tasks (not called on IDE sync?!)
* CI improvements
  * https://github.com/adevinta/spark-android/tree/main/.github
* Repo/build/CI examples.
  * https://github.com/gciatto/kt-math
* Build Variants setup
  * https://docs.gradle.org/current/userguide/variant_attributes.html
  * https://github.com/JetBrains/kotlin/blob/f6d2151/repo/gradle-build-conventions/buildsrc-compat/src/main/kotlin/GradleCommon.kt
  * https://github.com/liutikas/gradle-best-practices
* Compose improvements
  * compose-report-to-html
    * https://github.com/PatilShreyas/compose-report-to-html/releases/tag/v1.3.0
  * Decomposer
    * https://github.com/takahirom/decomposer/
* [Gradle Plugin TestKit](https://github.com/autonomousapps/dependency-analysis-gradle-plugin/tree/main/testkit) ([Docs](https://docs.gradle.org/current/userguide/test_kit.html))
* https://github.com/square/radiography
* https://github.com/JetBrains-Research/reflekt
* https://github.com/mikepenz/AboutLibraries
* Publication tasks configuration cache problems.
  * https://github.com/gradle/gradle/issues/26091#issuecomment-1798137734
* https://github.com/BenWoodworth/Parameterize
* Detekt
  * https://github.com/yandexmobile/detekt-rules-ui-tests
  * https://detekt.dev/marketplace
    * https://detekt.dev/marketplace/#unpublished
  * Custom detekt/lint rules
    * Warn on nutable (var) fields in data classes or beans.
    * Warn on data classes overall (public api, android app, etc.)
    * По гайдлайнам если есть хоть 1 именованный параметр нужно все именовать
    * Бед практис передавать мьютбл (collection, etc.) в параметрах
    * Add lint check to warn about calls to mutableStateOf with a State object
      * https://issuetracker.google.com/issues/169445918
    * Forbid on `java.util.Locale` in Android or commonJvm code.
      * It's not linked to Android `Context` or `Configuration` and can lead to issues with localization.
      * Public declarations in the private companion object are still compiled as public in bytecode with additional deprecation annotation.
        * It's better to use private in this case to simplify bytecode and a shrinker life.
      * Replace set with list (for public) or array (for private) things if no `contains` method used?
      * Replace `setOf` with `hashSetOf` (casted as read-only `Set`) when order is actually not important.
      * Prefer method reference to usual lambda with on action when acceptable.
      * Replace .not() with ! operator.
        * https://github.com/colematthew4/detekt-operator
      * **Architecture**
        * UseCase.
          * Should be Main-safe.
          * Should not use blocking operations.
          * No platform-specific code or UI layer.  E.g. should not have any dependencies on Android.
          * Should have a single responsibility. Only one method.
          * Should not have any state/fields.
  * Create detekt rules for Gradle plugins best practices
    * e.g., not to use `org.gradle.api.tasks.TaskCollection.matching`, `findByName`, etc. when `named` or `withType`
      is enough (don't early create tasks).
      * https://github.com/gmazzo/gradle-buildconfig-plugin/commit/a21a8b9
    * e.g., a task must not use any Project objects at execution time.
    * Also, warn on any `org.gradle.internal` usage because its is internal API.
    * https://docs.gradle.org/8.5/userguide/configuration_cache.html#config_cache:requirements
    * https://marcelkliemannel.com/articles/2022/common-gradle-plugin-mistakes-and-good-practices/
    * https://github.com/liutikas/gradle-best-practices
* Linting
  * https://github.com/jeremymailen/kotlinter-gradle
  * https://dev.to/aseemwangoo/supercharge-your-kotlin-project-2mcb
  * https://habr.com/ru/companies/ru_mts/articles/797053/
  * https://github.com/SonarSource/sonar-kotlin
  * Konsist
    * https://github.com/LemonAppDev/konsist
    * https://proandroiddev.com/protect-kotlin-project-architecture-using-konsist-3bfbe1ad0eea
  * KDoc formatting
    * https://github.com/tnorbye/kdoc-formatter
  * KtFmt
    * https://github.com/androidx/androidx/commit/dcaa116fbfda77e64a319e1668056ce3b032469f
* https://github.com/ashtanko/kotlin-app-template/tree/main
  * Github Action + git-hook + Issues Template
  * https://github.com/danger/kotlin
* __Infrastructure plugins__
  * https://github.com/slackhq/slack-gradle-plugin/
    * https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.13.0
    * https://github.com/slackhq/slack-gradle-plugin/releases/tag/0.14.0
  * https://github.com/avito-tech/avito-android
    * https://github.com/avito-tech/avito-android/blob/a1949b4/subprojects/assemble/proguard-guard/src/main/kotlin/com/avito/android/proguard_guard/shadowr8/ShadowR8TaskCreator.kt
    * GIT hooks: https://github.com/avito-tech/avito-android/tree/develop/.git_hooks
  * palantir gradle baseline
    * https://github.com/palantir/gradle-baseline
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
        * Record information about the build and stores it in the .jar,
          via [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin).
        * Easy specification of people involved in a project
          via [gradle-contacts-plugin](https://github.com/nebula-plugins/gradle-contacts-plugin).
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
        * Noninvasively collect information about the environment, and make information available to other plugins in a statically typed
          way.
        * When possible lazily calculate info.
        * https://github.com/nebula-plugins/gradle-contacts-plugin
          * Structure to define the owners of a project, then contributing this back to other plugins.
      * Linter tool for identifying and reporting on patterns of misuse or deprecations in Gradle scripts (groovy-only).
        * https://github.com/nebula-plugins/gradle-lint-plugin
        * https://docs.gradle.org/current/userguide/authoring_maintainable_build_scripts.html
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
    * iurysza
      * A Gradle Plugin for visualizing your project's structure, powered by mermaidjs.
        * https://github.com/iurysza/module-graph
      * A project setup to bootstrap kotlin library development.
        * https://github.com/iurysza/kotlin-scaffold
* https://github.com/BenWoodworth/Parameterize
* https://github.com/kotlin-hands-on/kotlin-swift-interopedia
* https://github.com/drewhamilton/Poko/
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
* https://github.com/Anamorphosee/stacktrace-decoroutinator
* https://gitlab.com/opensavvy/ci-templates
  * https://gitlab.com/opensavvy/playgrounds/gradle/-/blob/main/.gitlab-ci.yml?ref_type=heads
* https://github.com/gmazzo/gradle-codeowners-plugin
* https://github.com/gmazzo/gradle-docker-compose-plugin
* https://github.com/gmazzo/gradle-report-publications-plugin
* !! https://github.com/gmazzo/gradle-android-manifest-lock-plugin
* Shadowing + minification
  * https://github.com/GradleUp/gr8 (Gr8 = Gradle + R8)
  * https://github.com/nbadal/ktlint-intellij-plugin/pull/501/files
  * Task used by the UI and Android tests to check minification results and keep track of binary size.
    * https://github.com/lowasser/kotlinx.coroutines/blob/fcaa6df/buildSrc/src/main/kotlin/RunR8.kt
  * **A Gradle plugin that infers Proguard/R8 keep rules for androidTest sources.**
    * https://slackhq.github.io/keeper/
      * https://github.com/slackhq/Keeper
    * Example of successful integration
      * https://github.com/ankidroid/Anki-Android/pull/16635
        * https://issuetracker.google.com/issues/126429384#comment19
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
  * MythicDrops repo organization. MegaLinter.io checks. [kodiakhq](https://github.com/apps/kodiakhq) bot.
    * https://github.com/MythicDrops/mythicdrops-gradle-plugin/pull/108
    * https://github.com/MythicDrops/mythicdrops-gradle-plugin?tab=readme-ov-file#maven-publish-plugin
      * Configures published Maven POMs to include `compileOnly` dependencies as `provided`.
      * Configures the project to sign published artifacts with GPG if `PGP_KEY` and `PGP_PWD` environment variables are available.
    * https://github.com/MythicDrops/mythicdrops-gradle-plugin?tab=readme-ov-file#base-project-plugin
      * Applies the [nebula.project](https://github.com/nebula-plugins/nebula-project-plugin) Gradle plugin.
      * Applies the [com.adarshr.test-logger](https://github.com/radarsh/gradle-test-logger-plugin) Gradle plugin (with Mocha theme).
      * Configures all test tasks to use JUnit Jupiter.
      * Applies the [org.shipkit.shipkit-auto-version](https://github.com/shipkit/shipkit-auto-version) Gradle plugin.
  * Karol Wrótniak tools
    * https://github.com/koral--/gradle-pitest-plugin
    * https://github.com/koral-- (note profile readme)
  * Screenshot testing
    https://github.com/pedrovgs/Shot
* Stores publishing
  * https://github.com/chippmann/androidpublisher/
    * https://github.com/chippmann/androidpublisher/releases/tag/0.3.3
* CI security scanning of Android app using AppSweep (API KEY REQUIRED)
  * https://github.com/guardsquare/appsweep-gradle
  * https://appsweep.guardsquare.com/
  * https://plugins.gradle.org/plugin/com.guardsquare.appsweep
* Java 9 modularity support (JPMS)
  * https://github.com/Kotlin/kotlinx.coroutines/blob/d12eb45/buildSrc/src/main/kotlin/Java9Modularity.kt
  * https://github.com/KotlinCrypto/secure-random/pull/13
  * https://github.com/KotlinCrypto/core/pull/58
  * https://github.com/KotlinCrypto/core/pull/56
  * https://github.com/JetBrains/java-annotations/pull/103
  * https://github.com/oshai/kotlin-logging/issues/365
  * https://github.com/05nelsonm/gradle-kmp-configuration-plugin/pull/50/files
  * https://github.com/05nelsonm/gradle-kmp-configuration-plugin/pull/45/commits/cc603b37d2e5bd09f9b391edcf0ff3ccdc26dec2
* Common utils
  * https://github.com/aminography/CommonUtils/tree/1bfbe2d/library/src/main/java/com/aminography/commonutils
* States and Events
  * Circuit: https://slackhq.github.io/circuit/states-and-events/
* Builds organization (multiple flavors, build types, build targets)
  * https://github.com/ankidroid/Anki-Android/releases/tag/v2.17beta2
* Validate & diff resulting artifacts
  * https://github.com/JakeWharton/diffuse
* Gradle task to report native libs from dependencies (dependency + names of the native binaries)
* Gradle plugin for generating Android / KMP string resources from Google Spreadsheets.
  * https://github.com/futuredapp/sheet-happens
* 🐘 A template to let you started with custom Gradle Plugins + Kotlin in a few seconds
  * https://github.com/cortinico/kotlin-gradle-plugin-template
* Jsmints is a suite of libraries and gradle plugins for working with Kotlin JS, with a focus on testing and version updating.
  * https://github.com/robertfmurdock/jsmints
* A Palantir set of Gradle plugins that configure default code quality tools for developers.
  * https://github.com/palantir/gradle-baseline
* Gradle plugin for detecting use of legacy APIs which modern Java versions supersede.
  * https://github.com/andygoossens/gradle-modernizer-plugin
* Check ABI compatibility at build time
  * https://github.com/open-toast/expediter
* Kotlin/JS Fast Configuration
  * https://github.com/turansky/kfc-plugins
  * https://github.com/turansky/seskar (Kotlin/JS sugar)
  * Publish Kotlin/JS to NPM and JSR (https://jsr.io/).
* KtLint-cli setup
  * https://github.com/pinterest/ktlint/blob/cb17bbf/ktlint-cli/build.gradle.kts#L44
* Trace the recomposition of a Composable with its cause without boilerplate code
  * https://github.com/jisungbin/ComposeInvestigator
* Gradle Plugin that allows you to decompile bytecode compiled with Jetpack Compose Compiler Plugin into Java and check it
  * https://github.com/takahirom/decomposer/
* `calf-file-picker` with JS and Wasm support. And other compat widgets.
  * https://calf-library.netlify.app/
  * https://github.com/MohamedRejeb/Calf/releases/tag/v0.4.0
* A Kotlin Symbol Processor to list sealed object instances safely in generated code.
  * https://github.com/SimonMarquis/SealedObjectInstances
* Disable klib signature clash checks for JS compilations (Compose)
  * https://github.com/cashapp/molecule/releases/tag/1.4.2
* multiplatform libs to work w/ maven without requiring users to explicitly depend on the -jvm artifact.
  * https://kotlinlang.slack.com/archives/C8C4JTXR7/p1706909911878839
  * Kotlinx Serialization achieves this by editing the POM for the unflavoured module
    * https://github.com/Kotlin/kotlinx.serialization/blob/1116f5f/gradle/publish-mpp-root-module-in-platform.gradle#L6-L45
  * Arrow does the same
    * https://github.com/arrow-kt/arrow-gradle-config/blob/0.12.0-rc.20/arrow-gradle-config-publish/src/main/kotlin/internal/PublishMppRootModuleInPlatform.kt
</details>
