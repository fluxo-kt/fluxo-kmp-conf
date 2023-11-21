@file:Suppress("LongParameterList")

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project

public fun Project.setupAndroidLibrary(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    namespace: String? = null,
    enableBuildConfig: Boolean? = null,
    setupRoom: Boolean? = null,
    setupKsp: Boolean? = null,
    setupCompose: Boolean? = null,
    body: (LibraryExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (namespace != null) this.androidNamespace = namespace
    if (setupRoom != null) this.setupRoom = setupRoom
    if (setupKsp != null) this.setupKsp = setupKsp
    if (setupCompose != null) this.enableCompose = setupCompose
    if (enableBuildConfig != null) this.enableBuildConfig = enableBuildConfig
    config?.invoke(this)

    // FIXME: Implement cleaner 2-levels lazy API: asAndroidLib { android { ... } }
    asAndroid(app = false) {
        if (body != null) {
            androidLibrary {
                onAndroidExtension(body)
            }
        }
    }
}

public fun Project.setupAndroidApp(
    applicationId: String? = null,
    versionCode: Int = 0,
    versionName: String? = null,
    enableBuildConfig: Boolean? = null,
    setupRoom: Boolean? = null,
    setupKsp: Boolean? = null,
    setupCompose: Boolean? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    body: (BaseAppModuleExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (applicationId != null) {
        this.androidNamespace = applicationId
        this.androidApplicationId = applicationId
    }
    if (versionCode != 0) this.androidVersionCode = versionCode
    if (versionName != null) this.version = versionName
    if (setupRoom != null) this.setupRoom = setupRoom
    if (setupKsp != null) this.setupKsp = setupKsp
    if (setupCompose != null) this.enableCompose = setupCompose
    if (enableBuildConfig != null) this.enableBuildConfig = enableBuildConfig
    config?.invoke(this)

    // FIXME: Implement cleaner 2-levels lazy API: asAndroidApp { android { ... } }
    asAndroid(app = true) {
        if (body != null) {
            androidApp {
                onAndroidExtension(body)
            }
        }
    }
}
