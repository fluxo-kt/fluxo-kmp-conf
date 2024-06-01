package fluxo.conf.feat

import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_LINT_PLUGIN_ID
import org.gradle.api.Project
import org.gradle.util.GradleVersion

/**
 * Setup Android Lint for non-Android projects.
 */
internal fun Project.setupAndroidLintPluginWithNoAndroid(conf: FluxoConfigurationExtensionImpl) {
    if (GradleVersion.current() < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
        return
    }

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
     *  https://github.com/JetBrains/compose-multiplatform-core/blob/3b8ba3c/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt
     *  https://github.com/JetBrains/compose-multiplatform-core/blob/c366505/buildSrc/private/src/main/kotlin/androidx/build/AndroidXImplPlugin.kt#L591
     *  https://github.com/androidx/androidx/blob/8cc7a40/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt#L49
     *  https://github.com/slackhq/slack-gradle-plugin/blob/a9f12a9/slack-plugin/src/main/kotlin/slack/gradle/lint/LintTasks.kt#L105
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

// Min supported Gradle is 8.7 for the newest Android Lint plugin.
private const val MINIMUM_GRADLE_VERSION = "8.7"
