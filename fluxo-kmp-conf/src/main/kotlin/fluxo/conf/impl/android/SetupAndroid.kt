@file:Suppress("UnstableApiUsage", "CyclomaticComplexMethod", "LongMethod")

package fluxo.conf.impl.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.get
import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.kotlin.ksp
import fluxo.conf.impl.register
import fluxo.conf.impl.the
import fluxo.log.e
import fluxo.log.l
import fluxo.vc.onLibrary
import fluxo.vc.onVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Exec

/**
 * Common Android setup for both legacy AGP-8 `com.android.library` /
 * `com.android.application` and AGP-9 (where the runtime extension instance no longer
 * implements `com.android.build.gradle.TestedExtension` — only the modern hierarchy
 * survives). Operates on the modern [CommonExtension] receiver, with `is LibraryExtension`
 * / `is ApplicationExtension` smart-casts where the API needs invariant containers
 * (`buildTypes`) or app-only properties (`applicationId`, `versionCode`, `targetSdk`,
 * `bundle`, `signingConfigs`, `packaging`).
 *
 * @see com.android.build.api.dsl.LibraryExtension
 * @see com.android.build.api.dsl.ApplicationExtension
 */
internal fun CommonExtension.setupAndroidCommon(conf: FluxoConfigurationExtensionImpl) {
    val project = conf.project
    conf.androidNamespace.let { ns ->
        if (ns.isEmpty()) {
            project.logger.e("Required Android namespace IS EMPTY!")
        } else {
            namespace = ns
            project.logger.l("Android namespace '$ns'")
        }
    }
    conf.androidBuildToolsVersion?.let { buildToolsVersion = it }

    conf.androidCompileSdk.let {
        if (it is Int) compileSdk = it else compileSdkPreview = it.toString()
    }

    val ctx = conf.ctx
    val pseudoLocales = ctx.isMaxDebug && !ctx.isRelease && !ctx.isCI
    // The Action-form `defaultConfig { … }` block lives on the concrete subtypes
    // (`LibraryExtension` / `ApplicationExtension`), not on `CommonExtension` itself —
    // only `getDefaultConfig()` is exposed on the parent. Use `defaultConfig.apply { }`
    // for cross-subtype compatibility; the lambda receiver is the modern `DefaultConfig`
    // (extends `BaseFlavor`) which carries minSdk/testInstrumentationRunner/etc.
    defaultConfig.apply {
        conf.androidMinSdk.let { if (it is Int) minSdk = it else minSdkPreview = it.toString() }

        // Explicit list of the locales to keep in the final app.
        // Doing this strips out extra locales from libraries like
        // Google Play Services and Firebase, which add an unnecessary bloat.
        // https://developer.android.com/studio/build/shrink-code#unused-alt-resources
        // https://gist.github.com/amake/0ac7724681ac1c178c6f95a5b09f03ce
        // https://stackoverflow.com/questions/42937870/what-does-b-stand-for-and-what-is-the-syntax-behind-bsrlatn
        // https://stackoverflow.com/a/49117551/1816338
        // Note: en_XA and ar_XB are pseudo-locales for debugging.
        val languages = conf.androidResourceConfigurations.toMutableSet()
            .apply { if (pseudoLocales) addAll(arrayOf("en_XA", "ar_XB")) }
        resourceConfigurations.addAll(languages)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // `targetSdk` was removed from `LibraryBaseFlavor` in AGP 9 (target API affects apps,
    // not libraries — libraries inherit the consumer's effective target). Apply only on
    // `ApplicationExtension`, where it's still the source of truth.
    if (this is ApplicationExtension) {
        defaultConfig.apply {
            try {
                conf.androidTargetSdk.let {
                    if (it is Int) targetSdk = it else targetSdkPreview = it.toString()
                }
            } catch (_: Throwable) {
                // Defensive: AGP may further restrict this property in future patches.
            }
        }
    }

    if (conf.kotlinConfig.setupRoom) {
        // The KSP-arg part is plugin-agnostic (`room.generateKotlin` / `incremental` /
        // `schemaLocation`); applies to both AGP 8 and AGP 9.
        val roomSchemasDir = "${project.projectDir}/schemas"
        project.ksp {
            arg("room.generateKotlin", "true")
            arg("room.incremental", "true")
            arg("room.schemaLocation", roomSchemasDir)
        }
        // The legacy `sourceSets["androidTest"].assets.srcDir(...)` lookup uses AGP's
        // `TestedExtension.sourceSets` collection, which the AGP-9 modern DSL does NOT
        // expose. Wire that yourself if you run instrumented Room tests on AGP 9 —
        // attach `$roomSchemasDir` to your `androidTest` source set's resources/assets
        // manually. The legacy AGP-8 path keeps the auto-wiring for backwards compat.
        @Suppress("DEPRECATION")
        (this as? com.android.build.gradle.TestedExtension)?.sourceSets
            ?.findByName("androidTest")?.assets?.srcDir(roomSchemasDir)
            ?: project.logger.l(
                "Room schemas at '$roomSchemasDir'; on AGP 9 attach to `androidTest` " +
                    "source set's assets/resources manually — the legacy `TestedExtension." +
                    "sourceSets` accessor is unavailable on the modern Android DSL.",
            )
    }

    var isApplication = false
    if (this is ApplicationExtension) {
        isApplication = true

        defaultConfig {
            applicationId = conf.androidApplicationId
            versionCode = conf.androidVersionCode
            conf.version.takeIf { it.isNotBlank() }?.let {
                versionName = it.trim()
            }
        }

        // FIXME: Automatic per-app language support
        // https://developer.android.com/build/releases/past-releases/agp-8-1-0-release-notes#automatic-per-app-languages

        androidResources {
            generateLocaleConfig = true
        }

        setupSigningIn(project = project)

        bundle {
            abi.enableSplit = true
            density.enableSplit = true
            language.enableSplit = false

            // TODO: Support bundletool codeTransparency here.
            //  Optional code signing and verification approach.
            //  Uses a code transparency signing key, which is solely held by the app developer.
            //  Independent of the signing scheme used for app bundles and APKs.
            //  https://www.perplexity.ai/search/whats-the-code-JJHazzhaSHKTyX4J7EKOsg
        }

        dependenciesInfo {
            // Dependency metadata in the signature block
            // No need to give Google Play more info about the app.
            includeInApk = false
            includeInBundle = false
        }

        setupPackagingOptions(
            project = project,
            isCI = ctx.isCI,
            isRelease = ctx.isRelease,
            removeKotlinMetadata = conf.removeKotlinMetadata,
        )
    }

    testOptions.unitTests {
        // Required for Robolectric
        isIncludeAndroidResources = true
        isReturnDefaultValues = true

        // JUnit4 should be used to discover and execute the tests
        // It's the most compatible for now.
        all { it.useJUnit() }
    }

    // `CommonExtension.buildTypes` exposes a wildcard-bounded
    // `NamedDomainObjectContainer<? extends BuildType>` (covariant), so `maybeCreate` /
    // `add` won't compile against it. The Action-form `buildTypes(Function1<...>)` on the
    // concrete subtypes is invariant inside its lambda — `LibraryExtension` exposes
    // `NamedDomainObjectContainer<LibraryBuildType>`, `ApplicationExtension` exposes
    // `NamedDomainObjectContainer<ApplicationBuildType>`. Dispatch by the runtime subtype.
    when (this) {
        is LibraryExtension -> buildTypes {
            maybeCreate(DEBUG).apply {
                matchingFallbacks.addAll(listOf("test", DEBUG, "qa"))
            }
            maybeCreate(RELEASE).apply {
                matchingFallbacks.addAll(listOf(RELEASE, "prod", "production"))
            }
        }
        is ApplicationExtension -> buildTypes {
            maybeCreate(DEBUG).apply {
                matchingFallbacks.addAll(listOf("test", DEBUG, "qa"))

                // UI localization testing.
                // Generate resources for pseudo-locales: en-XA and ar-XB.
                // https://developer.android.com/guide/topics/resources/pseudolocales
                // `isPseudoLocalesEnabled` is on `ApplicationBuildType`, not
                // `LibraryBuildType` — it's the app that bundles resources.
                if (pseudoLocales) {
                    isPseudoLocalesEnabled = true
                }
            }
            maybeCreate(RELEASE).apply {
                matchingFallbacks.addAll(listOf(RELEASE, "prod", "production"))
            }
        }
    }

    /*
     * Enable support for new language APIs.
     * https://developer.android.com/studio/write/java8-support
     * https://developer.android.com/studio/write/java8-support-table
     * https://developer.android.com/studio/write/java11-default-support-table
     * https://jakewharton.com/androids-java-8-support/
     * https://jakewharton.com/androids-java-9-10-11-and-12-support/
     */
    if (ctx.isDesugaringEnabled) {
        // TODO: Test for libraries bytecode.
        //  Is it safe when desugaring is enabled with JVM target 9+?
        compileOptions.isCoreLibraryDesugaringEnabled = true

        // Custom desugaring dependency.
        ctx.libs.onLibrary(ALIAS_DESUGAR_LIBS) {
            project.addAndLog(project.dependencies,"coreLibraryDesugaring", it)
        }
    }

    buildFeatures.apply {
        // Disable all features by default
        aidl = false
        prefab = false
        renderScript = false
        resValues = false
        shaders = false
        viewBinding = false

        compose = conf.enableCompose
        buildConfig = conf.enableBuildConfig
    }

    if (ctx.testsDisabled) {
        val tasks = project.tasks
        val disableTask = getDisableTaskAction()
        tasks.withType(AndroidLintTask::class.java, disableTask)
        tasks.withType(AndroidLintTextOutputTask::class.java, disableTask)
    }

    project.setupAndroidDependencies(
        project.dependencies,
        ctx.libs,
        isApplication = isApplication,
        kc = conf.kotlinConfig,
    )

    project.setupFinalizeAndroidDsl(ctx)
}

private fun Project.configureMonkeyLauncherTasks() {
    the(AndroidComponentsExtension::class).onVariants { variant ->
        if (variant !is ApplicationVariant) {
            return@onVariants
        }
        val appIdProvider = variant.applicationId
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val installTask = tasks.named("install$variantName")
        tasks.register<Exec>("connected${variant.name}MonkeyTest") {
            dependsOn(installTask)
            // Resolved at execution time via Provider — CC-safe.
            argumentProviders.add {
                listOf(
                    "shell", "monkey",
                    "-p", appIdProvider.get(),
                    "-c", "android.intent.category.LAUNCHER",
                    "1",
                )
            }
            executable = "adb"
        }
    }
}
