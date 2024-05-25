import java.util.Calendar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.jetbrains.compose)
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

// See JB template and docs:
// https://github.com/JetBrains/compose-multiplatform-desktop-template
// https://github.com/JetBrains/compose-multiplatform/blob/e1aff75/tutorials/Native_distributions_and_local_execution/README.md

fkcSetupKotlinApp {
    replaceOutgoingJar = true
    shrink { fullMode = true }
    shrinkWithProGuard()
}

dependencies {
    // `currentOs` should be used in launcher-sourceSet  and in testMain only.
    // For a library, use compose.desktop.common.
    // With compose.desktop.common you will also lose @Preview functionality.
    implementation(compose.desktop.currentOs)

    testImplementation(compose.desktop.uiTestJUnit4)
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
