// Companion getters from Kotlin 2.2; on 2.1 these names are `const val`s, inlined at compile
// time, so the getters don't exist there. Callers read them only on Kotlin 2.2+.
@file:VersionGated

package fluxo.conf.impl.kotlin

import fluxo.annotation.VersionGated
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

internal fun yarnExtensionName() = YarnRootExtension.YARN

internal fun yarnSpecExtensionName() = YarnRootEnvSpec.YARN

internal fun nodeJsExtensionName() = NodeJsRootExtension.EXTENSION_NAME
