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
import fluxo.conf.impl.registerCompat
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

    applyAgpSdkProperty(conf.androidCompileSdk, { compileSdk = it }, { compileSdkPreview = it })

    val ctx = conf.ctx
    val pseudoLocales = ctx.isMaxDebug && !ctx.isRelease && !ctx.isCI
    // Explicit list of the locales to keep in the final app.
    // Strips extra locales from libraries like Google Play Services and Firebase.
    // https://developer.android.com/studio/build/shrink-code#unused-alt-resources
    // https://gist.github.com/amake/0ac7724681ac1c178c6f95a5b09f03ce
    // Note: en_XA and ar_XB are pseudo-locales for debugging.
    val languages = conf.androidResourceConfigurations.toMutableSet()
        .apply { if (pseudoLocales) addAll(arrayOf("en_XA", "ar_XB")) }

    // The Action-form `defaultConfig { … }` block lives on the concrete subtypes
    // (`LibraryExtension` / `ApplicationExtension`), not on `CommonExtension` itself —
    // only `getDefaultConfig()` is exposed on the parent. Use `defaultConfig.apply { }`
    // for cross-subtype compatibility; the lambda receiver is the modern `DefaultConfig`
    // (extends `BaseFlavor`) which carries minSdk/testInstrumentationRunner/etc.
    defaultConfig.apply {
        applyAgpSdkProperty(conf.androidMinSdk, { minSdk = it }) {
            // minSdkPreview is deprecated in AGP 9 and will be removed in AGP 10.0.
            // The enclosing noSuchMethodSafe in applyAgpSdkProperty already handles
            // the runtime NoSuchMethodError when AGP 10.0 removes this setter.
            @Suppress("DEPRECATION")
            minSdkPreview = it
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    if (this is ApplicationExtension) {
        // Locale filtering is app-only — libraries do not produce APKs so filtering
        // their resources here would be incorrect. AGP 8.3+ uses
        // androidResources.localeFilters; older AGP falls back to the deprecated
        // defaultConfig.resourceConfigurations via a noSuchMethodSafe catch.
        // https://developer.android.com/studio/build/shrink-code#unused-alt-resources
        // https://stackoverflow.com/a/49117551/1816338
        if (languages.isNotEmpty()) {
            var localeFiltersApplied = false
            noSuchMethodSafe {
                androidResources.localeFilters.addAll(languages)
                localeFiltersApplied = true
            }
            if (!localeFiltersApplied) {
                // AGP < 8.3 fallback: resourceConfigurations is deprecated in AGP 9.x.
                // Remove this branch once the consumer floor exceeds AGP 8.2.
                @Suppress("DEPRECATION")
                noSuchMethodSafe { defaultConfig.resourceConfigurations.addAll(languages) }
            }
        }
        applyApplicationTargetSdk(conf)
    }

    if (conf.kotlinConfig.setupRoom) {
        applyRoomKspAndAndroidTestAssets(project)
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
        resValues = false
        shaders = false
        viewBinding = false

        compose = conf.enableCompose
        buildConfig = conf.enableBuildConfig
    }
    // renderScript defaults to false. Set explicitly for clarity; wrap in noSuchMethodSafe
    // because it will be removed in AGP 10.0. @Suppress for the compile-time deprecation.
    @Suppress("DEPRECATION")
    noSuchMethodSafe { buildFeatures.renderScript = false }

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

/**
 * `targetSdk` was removed from `LibraryBaseFlavor` in AGP 9 (target API affects apps, not
 * libraries). The `is ApplicationExtension` smart-cast at the call site fixes the
 * compile-time half — the property exists on `ApplicationExtension.defaultConfig` in
 * AGP 9.x. Forward-compat for runtime drift across consumer-applied AGP versions is
 * delegated to [applyAgpSdkProperty]'s narrow `NoSuchMethodError` catch.
 */
private fun ApplicationExtension.applyApplicationTargetSdk(
    conf: FluxoConfigurationExtensionImpl,
) = applyAgpSdkProperty(
    conf.androidTargetSdk,
    { defaultConfig.targetSdk = it },
    { defaultConfig.targetSdkPreview = it },
)

/**
 * Applies a legacy AGP "Int-or-Preview" SDK property pair. Non-`Int` values fall through
 * to the preview setter via `toString()` — the legacy path treats `fluxoConfiguration { }`
 * as the single source of truth, so unsupported types are rare and best squashed into the
 * preview slot rather than fail-fast (the AGP-9 KMP path opts for fail-fast logging
 * because that path is bidirectional). The forward-compat NSME catch is shared with the
 * KMP path via [noSuchMethodSafe].
 */
private inline fun applyAgpSdkProperty(
    value: Any,
    asInt: (Int) -> Unit,
    asPreview: (String) -> Unit,
) = noSuchMethodSafe {
    when (value) {
        is Int -> asInt(value)
        else -> asPreview(value.toString())
    }
}

/**
 * Wires Room KSP arguments (plugin-agnostic) and best-effort `androidTest` source-set
 * asset attachment via the legacy `TestedExtension.sourceSets` accessor (AGP-8 path).
 * AGP 9 dropped that accessor from the modern DSL; we log a clear pointer for manual
 * wiring instead. Extracted to keep `setupAndroidCommon`'s nesting depth in check.
 */
private fun CommonExtension.applyRoomKspAndAndroidTestAssets(project: Project) {
    val roomSchemasDir = "${project.projectDir}/schemas"
    project.ksp {
        arg("room.generateKotlin", "true")
        arg("room.incremental", "true")
        arg("room.schemaLocation", roomSchemasDir)
    }
    @Suppress("DEPRECATION")
    (this as? com.android.build.gradle.TestedExtension)?.sourceSets
        ?.findByName("androidTest")?.assets?.srcDir(roomSchemasDir)
        ?: project.logger.l(
            "Room schemas at '$roomSchemasDir'; on AGP 9 attach to `androidTest` " +
                "source set's assets/resources manually — the legacy `TestedExtension." +
                "sourceSets` accessor is unavailable on the modern Android DSL.",
        )
}

private fun Project.configureMonkeyLauncherTasks() {
    the(AndroidComponentsExtension::class).onVariants { variant ->
        if (variant !is ApplicationVariant) {
            return@onVariants
        }
        val appIdProvider = variant.applicationId
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val installTask = tasks.named("install$variantName")
        tasks.registerCompat<Exec>("connected${variant.name}MonkeyTest") {
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
