package fluxo.conf.impl

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Task

internal fun Named.isTestRelated(): Boolean {
    // return getTaskDetailsFromName(name, allowNonDetekt = true).isTest
    return name.contains("Test", ignoreCase = true)
}


internal fun String.splitCamelCase(limit: Int = 0): List<String> = split(CAMEL_CASE_REGEX, limit)

private val CAMEL_CASE_REGEX = Regex("(?<![A-Z])\\B(?=[A-Z])")


internal fun getDisableTaskAction(value: Any? = null): Action<in Task> = Action {
    disableTask(value)
}

internal fun Task.disableTask(value: Any? = null) {
    if (enabled) {
        enabled = false
        logger.d(
            "task '{}' disabled{}",
            path,
            if (value != null) ", $value" else "",
        )
    }
}
