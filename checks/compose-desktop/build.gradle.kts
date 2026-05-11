import java.util.Calendar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.gradle.doctor) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

// https://github.com/JetBrains/compose-multiplatform/blob/e1aff75/tutorials/Native_distributions_and_local_execution/README.md#specifying-package-version
val appVersion = libs.versions.version.get()
    .substringBefore("-")
    .let {
        val (a, b) = it.split(".", limit = 3)
        if (a == "0") "1.0.$b" else it
    }

version = appVersion
group = "io.github.fluxo-kt"

val mainClassName = "MainKt"
tasks.named<Jar>("jar") {
    manifest.attributes["Main-Class"] = mainClassName
}

fun composeDesktopCurrentOs(): String {
    val os = System.getProperty("os.name")
    val arch = when (val value = System.getProperty("os.arch")) {
        "x86_64", "amd64" -> "x64"
        "aarch64" -> "arm64"
        else -> error("Unsupported OS arch: $value")
    }
    val platform = when {
        os.equals("Mac OS X", ignoreCase = true) -> "macos"
        os.startsWith("Win", ignoreCase = true) -> "windows"
        os.startsWith("Linux", ignoreCase = true) -> "linux"
        else -> error("Unknown OS name: $os")
    }
    return "org.jetbrains.compose.desktop:desktop-jvm-$platform-$arch:${libs.versions.jetbrains.compose.get()}"
}

// See JB template and docs:
// https://github.com/JetBrains/compose-multiplatform-desktop-template
// https://github.com/JetBrains/compose-multiplatform/blob/e1aff75/tutorials/Native_distributions_and_local_execution/README.md

fkcSetupKotlinApp {
    replaceOutgoingJar = true
    shrink { fullMode = true }
    shrinkWithProGuard()

    experimentalLatestCompilation = true
    latestSettingsForTests = true
    setupVerification = true
    enableApiValidation = true
    enableGenericAndroidLint = true
    enableGradleDoctor = true
}

dependencies {
    implementation(composeDesktopCurrentOs())
    implementation(libs.compose.components.resources)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.compose.ui.test.junit4)
}

compose.desktop.application {
    mainClass = mainClassName

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageVersion = appVersion
        packageName = "MyApp"
        description = "MyDescription"
        vendor = "MyCompany"

        val year = Calendar.getInstance().get(Calendar.YEAR)
        copyright = "© $year $packageName. All rights reserved."

        windows {
            shortcut = true
            menuGroup = packageName
            upgradeUuid = "3d4241d1-0400-401f-bd1f-000fdc8ae989"
        }
    }
}
