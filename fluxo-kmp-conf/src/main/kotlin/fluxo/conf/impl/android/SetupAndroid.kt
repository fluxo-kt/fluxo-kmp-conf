@file:Suppress("UnstableApiUsage", "CyclomaticComplexMethod", "LongMethod")

package fluxo.conf.impl.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.get
import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.kotlin.ksp
import fluxo.conf.impl.the
import fluxo.log.e
import fluxo.log.l
import fluxo.vc.onLibrary
import fluxo.vc.onVersion
import org.gradle.api.Project

/**
 * @see com.android.build.api.dsl.TestedExtension
 * @see com.android.build.gradle.TestedExtension
 */
internal fun TestedExtension.setupAndroidCommon(conf: FluxoConfigurationExtensionImpl) {
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

    // Note: avoiding setting of generics for CommonExtension is intentional
    //  as number can vary between AGP versions.
    project.the(CommonExtension::class).apply {
        conf.androidCompileSdk.let {
            if (it is Int) compileSdk = it else compileSdkPreview = it.toString()
        }
    }

    val ctx = conf.ctx
    val pseudoLocales = ctx.isMaxDebug && !ctx.isRelease && !ctx.isCI
    defaultConfig {
        conf.androidMinSdk.let { if (it is Int) minSdk = it else minSdkPreview = it.toString() }

        try {
            conf.androidTargetSdk.let {
                if (it is Int) targetSdk = it else targetSdkPreview = it.toString()
            }
        } catch (_: Throwable) {
            // Will be removed from LibraryBaseFlavor DSL in AGP v9.0
        }

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

        if (conf.kotlinConfig.setupRoom) {
            // Exported Room DB schemas
            // Enable incremental compilation for Room
            val roomSchemasDir = "${project.projectDir}/schemas"
            project.ksp {
                arg("room.generateKotlin", "true")
                arg("room.incremental", "true")
                arg("room.schemaLocation", roomSchemasDir)
            }
            // Add exported schema location as test app assets.
            sourceSets["androidTest"].assets.srcDir(roomSchemasDir)
        }
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

    buildTypes {
        maybeCreate(DEBUG).apply {
            matchingFallbacks.addAll(listOf("test", DEBUG, "qa"))

            // UI localization testing.
            // Generate resources for pseudo-locales: en-XA and ar-XB
            // https://developer.android.com/guide/topics/resources/pseudolocales
            if (pseudoLocales) {
                isPseudoLocalesEnabled = true
            }
        }

        maybeCreate(RELEASE).apply {
            matchingFallbacks.addAll(listOf(RELEASE, "prod", "production"))
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
            with(project) {
                dependencies.addAndLog("coreLibraryDesugaring", it)
            }
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

    // Set compose compiler version
    ctx.libs.onVersion(ALIAS_ANDROIDX_COMPOSE_COMPILER) {
        composeOptions.kotlinCompilerExtensionVersion = it
    }

    if (ctx.testsDisabled) {
        val tasks = project.tasks
        val disableTask = getDisableTaskAction()
        tasks.withType(AndroidLintTask::class.java, disableTask)
        tasks.withType(AndroidLintTextOutputTask::class.java, disableTask)
    }

    with(project) {
        project.dependencies.setupAndroidDependencies(
            ctx.libs,
            isApplication = isApplication,
            kc = conf.kotlinConfig,
        )
    }

    project.setupFinalizeAndroidDsl(ctx)
}

private fun Project.configureMonkeyLauncherTasks() {
    the(AndroidComponentsExtension::class).onVariants { variant ->
        if (variant !is ApplicationVariant) {
            return@onVariants
        }
        val appId = variant.applicationId.get()
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val installTask = tasks.named("install$variantName")
        tasks.register("connected${variant.name}MonkeyTest") {
            dependsOn(installTask)
            doLast {
                exec {
                    commandLine =
                        "adb shell monkey -p $appId -c android.intent.category.LAUNCHER 1"
                            .split(" ")
                }
            }
        }
    }
}
