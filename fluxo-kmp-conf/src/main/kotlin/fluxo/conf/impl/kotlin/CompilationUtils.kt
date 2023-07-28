package fluxo.conf.impl.kotlin

import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.isTestRelated
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal fun KotlinTarget.disableCompilations(testOnly: Boolean = false) {
    compilations.configureEach {
        if (!testOnly || isTestRelated()) {
            disableCompilation()
        }
    }
}

internal fun KotlinCompilation<*>.disableCompilation() {
    val action = getDisableTaskAction(this)
    compileTaskProvider.configure(action)
    compileJavaTaskProvider?.configure(action)
}
