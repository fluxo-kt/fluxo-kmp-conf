package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * An Android application: fluxo's app-only settings (targetSdk, kept locales) must land on every
 * AGP line, and an app that sets no version code must configure. AGP 8's `ApplicationExtension`
 * declares no `ApplicationDefaultConfig`-typed `getDefaultConfig()` (AGP 9's does), so a call
 * compiled against AGP 9 through the `ApplicationExtension` receiver fails on AGP 8.
 */
internal fun runAndroidAppCase(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-android-app",
        projectDir = tempDir.resolve(row.getValue("id") + "-app"),
        tasks = listOf("help"),
        forbiddenOutput = ANDROID_NOISE,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAndroidAppBuildScript(row))
    }
}

private fun markerAndroidAppBuildScript(row: Map<String, String>): String {
    val isAgp8 = row.getValue("fixture").startsWith("android-lib-agp8")
    val kgp = row.getValue("kgpVersion")
    val kotlinAndroidPlugin = "id(\"org.jetbrains.kotlin.android\") version \"$kgp\"" +
        if (isAgp8) "" else " apply false"
    return """
    plugins {
        id("com.android.application") version "${row.getValue("agpVersion")}" apply false
        $kotlinAndroidPlugin
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupAndroidApp(
        applicationId = "compat.app",
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidResourceConfigurations = setOf("en")
        },
    )

    gradle.taskGraph.whenReady {
        run {
            val android = project.extensions.getByName("android")
                as com.android.build.api.dsl.ApplicationExtension
            val agpMax = com.android.builder.core.ToolsRevisionUtils
                .MAX_RECOMMENDED_COMPILE_SDK_VERSION.apiLevel
            val targetSdk = android.defaultConfig.targetSdk
            check(targetSdk == agpMax) { "targetSdk ${'$'}targetSdk, want ${'$'}agpMax" }
            // By name: `localeFilters` exists from AGP 8.8, older AGP keeps the locales in
            // `defaultConfig.resourceConfigurations` (deprecated on AGP 9).
            fun Any.getter(name: String) = runCatching { javaClass.getMethod(name).invoke(this) }
            val locales = android.androidResources.getter("getLocaleFilters").getOrNull()
                ?: android.defaultConfig.getter("getResourceConfigurations").getOrThrow()
            check(locales == setOf("en")) { "kept locales ${'$'}locales, want [en]" }
        }
    }
    """.trimIndent()
}
