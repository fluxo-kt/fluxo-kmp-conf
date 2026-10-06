package fluxo.conf.impl.kotlin

import java.io.File

/*
 * Browser tests of JS and Wasm-JS targets run in Karma, by default in headless Chrome, which a
 * machine may not have. fluxo skips such a test task where Karma would find no Chrome, so `check`
 * still passes there and the tests run wherever Chrome is installed (developer machines, most CI
 * images). Kept free of KGP references so it loads without the Kotlin plugin.
 */

/** Karma launchers resolved through `CHROME_BIN` and the Chrome install locations. */
private val CHROME_LAUNCHERS = setOf("Chrome", "ChromeHeadless", "ChromeHeadlessNoSandbox")

private val LINUX_CHROME_NAMES = listOf("google-chrome", "google-chrome-stable")

private val WINDOWS_INSTALL_ROOTS =
    listOf("LOCALAPPDATA", "PROGRAMFILES", "PROGRAMFILES(X86)", "ProgramW6432")

/**
 * Whether every browser in a Karma test task's settings (`KotlinJsTest.testFrameworkSettings`,
 * `KotlinKarma(KarmaConfig(…, browsers=[ChromeHeadless], …))` on Kotlin 2.1–2.4) needs Chrome.
 * Settings it can't read, or any other browser the consumer chose, give `false`: such a task is
 * never skipped.
 */
internal fun karmaNeedsOnlyChrome(frameworkSettings: String): Boolean {
    if (!frameworkSettings.startsWith("KotlinKarma(")) return false
    val browsers = frameworkSettings.substringAfter("browsers=[", missingDelimiterValue = "")
        .substringBefore(']')
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return browsers.isNotEmpty() && browsers.all { it in CHROME_LAUNCHERS }
}

/**
 * Whether Karma would find Chrome here. Mirrors the lookup of karma-chrome-launcher 3.2.0 (the
 * version KGP pins): `CHROME_BIN`; Linux `google-chrome`/`google-chrome-stable` on `PATH`; macOS
 * the app in `~/Applications` or `/Applications`; Windows `Google\Chrome\Application\chrome.exe`
 * under `LOCALAPPDATA`, `PROGRAMFILES`, `PROGRAMFILES(X86)` or `ProgramW6432`.
 */
internal fun karmaFindsChrome(env: (String) -> String?, osName: String): Boolean {
    env("CHROME_BIN")?.takeIf { it.isNotBlank() }?.let { return File(it).exists() }
    val os = osName.lowercase()
    val candidates = when {
        os.startsWith("mac") -> {
            val app = "Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
            listOfNotNull(env("HOME")?.let { File(it, app) }, File("/$app"))
        }

        os.startsWith("windows") -> WINDOWS_INSTALL_ROOTS.mapNotNull { key ->
            env(key)?.let { File(it, "Google\\Chrome\\Application\\chrome.exe") }
        }

        else -> env("PATH").orEmpty().split(File.pathSeparatorChar)
            .filter { it.isNotEmpty() }
            .flatMap { dir -> LINUX_CHROME_NAMES.map { File(dir, it) } }
    }
    return candidates.any { it.exists() }
}
