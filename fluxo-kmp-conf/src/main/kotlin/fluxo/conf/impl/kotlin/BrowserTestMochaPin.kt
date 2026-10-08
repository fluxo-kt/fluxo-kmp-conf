package fluxo.conf.impl.kotlin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.targets.js.NpmVersions

/**
 * Makes KGP's Playwright browser tests (`browser { test { chromium() } }`, KGP 2.4.20+) run.
 *
 * Their page, copied from the `kotlin-web-helpers` npm package, loads Mocha from an unpinned
 * unpkg URL. Mocha 12 (2026-08-31) made its reporters ES classes, which the page's runner calls
 * without `new`: the page throws before any test starts, and the test task times out after 30 s
 * with no result. The URL is pinned to [kotlinMocha], the Mocha the page ships with, as Node tests
 * use. The rewrite matches the exact unpinned URL, so once KGP or kotlin-web-helpers pins or
 * drops it, this does nothing.
 */
internal fun Project.pinBrowserTestMocha() {
    tasks.named { it == BROWSER_TEST_BUNDLE_TASK }.configureEach {
        doLast(PinMochaAction("https://unpkg.com/mocha@$kotlinMocha/"))
    }
}

/**
 * The Mocha version the consumer's Kotlin ships and its JS test reporter (`kotlin-web-helpers`)
 * is built for. Read from KGP, never tabulated, so a newer Kotlin moves it.
 */
internal val kotlinMocha: String get() = NpmVersions().mocha.version

private class PinMochaAction(private val pinned: String) : Action<Task> {
    override fun execute(task: Task) {
        for (dir in task.outputs.files) {
            val page = dir.resolve("test.html").takeIf { it.isFile } ?: continue
            val html = page.readText()
            if (UNPINNED_MOCHA in html) page.writeText(html.replace(UNPINNED_MOCHA, pinned))
        }
    }
}

/** KGP's `WebpackBundleKotlinJsTests`, which writes the test page; the type is internal. */
private const val BROWSER_TEST_BUNDLE_TASK = "prepareWebpackBundleForKotlinJsTests"

private const val UNPINNED_MOCHA = "https://unpkg.com/mocha/"
