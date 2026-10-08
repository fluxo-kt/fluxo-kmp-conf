package fluxo.conf.impl.kotlin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Makes KGP's Playwright browser tests (`browser { test { chromium() } }`, KGP 2.4.20+) run.
 *
 * Their page, copied from the `kotlin-web-helpers` npm package, loads Mocha from an unpinned
 * unpkg URL. Mocha 12 (2026-08-31) made its reporters ES classes, which the page's runner calls
 * without `new`: the page throws before any test starts, and the test task times out after 30 s
 * with no result. The page works with Mocha 11, so the URL is pinned to it. The rewrite matches
 * the exact unpinned URL, so once KGP or kotlin-web-helpers pins or drops it, this does nothing.
 */
internal fun Project.pinBrowserTestMocha() {
    tasks.named { it == BROWSER_TEST_BUNDLE_TASK }.configureEach { doLast(PinMochaAction) }
}

private object PinMochaAction : Action<Task> {
    override fun execute(task: Task) {
        for (dir in task.outputs.files) {
            val page = dir.resolve("test.html").takeIf { it.isFile } ?: continue
            val html = page.readText()
            if (UNPINNED_MOCHA in html) page.writeText(html.replace(UNPINNED_MOCHA, PINNED_MOCHA))
        }
    }
}

/** KGP's `WebpackBundleKotlinJsTests`, which writes the test page; the type is internal. */
private const val BROWSER_TEST_BUNDLE_TASK = "prepareWebpackBundleForKotlinJsTests"

private const val UNPINNED_MOCHA = "https://unpkg.com/mocha/"

private const val PINNED_MOCHA = "https://unpkg.com/mocha@11/"
