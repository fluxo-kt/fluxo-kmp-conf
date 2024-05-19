package fluxo.conf.feat

import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_LINT_PLUGIN_ID
import org.gradle.api.Project

/**
 * Setup Android Lint for non-Android projects.
 */
internal fun Project.setupAndroidLintPluginWithNoAndroid(conf: FluxoConfigurationExtensionImpl) {
    // Available as separate artifact, but references the usual
    // `com.android.tools.build:gradle` artifact.
    conf.ctx.loadAndApplyPluginIfNotApplied(
        id = ANDROID_LINT_PLUGIN_ID,
        catalogPluginIds = LINT_CATALOG_PLUGIN_IDS,
        className = ANDROID_LINT_PLUGIN_CLASS_NAME,
        project = this,
    )

    /*
     * References:
     *  https://github.com/ZacSweers/MoshiX/pull/553/files
     */

    /**
     * @see com.android.build.api.dsl.Lint
     * @see com.android.build.gradle.internal.plugins.LintPlugin
     * @see com.android.build.gradle.LintPlugin
     */
    pluginManager.withPlugin(ANDROID_LINT_PLUGIN_ID) {
        setupAndroidLint(conf = conf, testsDisabled = false, notForAndroid = true)
    }
}

/** @see com.android.build.gradle.LintPlugin */
private const val ANDROID_LINT_PLUGIN_CLASS_NAME = "com.android.build.gradle.LintPlugin"

private val LINT_CATALOG_PLUGIN_IDS = arrayOf("android-lint", "lint")
