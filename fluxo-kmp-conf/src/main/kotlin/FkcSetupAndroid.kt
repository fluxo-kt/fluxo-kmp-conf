@file:Suppress("LongParameterList", "DuplicatedCode")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Lazily configures an Android Library module (Gradle [Project]).
 *
 * @receiver The [Project] to configure.
 *
 * @param config The configuration block for the [FluxoConfigurationExtension].
 * @param namespace The Android namespace to use for the project.
 *
 * @param enableBuildConfig Whether to enable the BuildConfig generation.
 * @param setupRoom Whether to set up Room (auto-detected if already applied).
 * @param setupKsp Whether to set up KSP (auto-detected if already applied).
 * @param setupCompose Whether to set up Compose in this module (auto-detected if already applied).
 *
 * @param kotlin The configuration block for the [KotlinAndroidProjectExtension].
 * @param android The configuration block for the Android [LibraryExtension].
 *
 * @see com.android.build.gradle.LibraryExtension
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.androidNamespace
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableBuildConfig
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.setupRoom
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.setupKsp
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableCompose
 */
@JvmName("setupAndroidLibrary")
public fun Project.fkcSetupAndroidLibrary(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    namespace: String? = null,
    enableBuildConfig: Boolean? = null,
    setupRoom: Boolean? = null,
    setupKsp: Boolean? = null,
    setupCompose: Boolean? = null,
    kotlin: (KotlinAndroidProjectExtension.() -> Unit)? = null,
    android: (LibraryExtension.() -> Unit)? = null,
) {
    fluxoConfiguration {
        namespace?.let { this.androidNamespace = it }
        setupRoom?.let { this.setupRoom = it }
        setupKsp?.let { this.setupKsp = it }
        setupCompose?.let { this.enableCompose = it }
        enableBuildConfig?.let { this.enableBuildConfig = it }

        config?.invoke(this)

        // FIXME: Implement cleaner 2-levels lazy API: asAndroidLib { android { ... } }
        asAndroid(app = false) {
            kotlin?.let { this.kotlin(action = it) }

            if (android != null) {
                androidLibrary {
                    onAndroidExtension(android)
                }
            }
        }
    }
}

/**
 * Lazily configures an Android Application module (Gradle [Project]).
 *
 * @receiver The [Project] to configure.
 *
 * @param config Configuration block for the Fluxo Configuration.
 *
 * @param applicationId The application ID to use for the project.
 * @param versionCode The version code to use for the project.
 * @param versionName The version name to use for the project.
 *
 * @param enableBuildConfig Whether to enable the BuildConfig generation.
 * @param setupRoom Whether to set up Room (auto-detected if already applied).
 * @param setupKsp Whether to set up KSP (auto-detected if already applied).
 * @param setupCompose Whether to set up Compose in this module (auto-detected if already applied).
 *
 * @param kotlin Configuration block for the [KotlinAndroidProjectExtension].
 * @param android Configuration block for the Android [BaseAppModuleExtension].
 *
 * @see com.android.build.gradle.AppExtension
 * @see com.android.build.gradle.internal.dsl.BaseAppModuleExtension
 *
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.androidNamespace
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.androidApplicationId
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.androidVersionCode
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.version
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableBuildConfig
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.setupRoom
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.setupKsp
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.enableCompose
 */
@JvmName("setupAndroidApp")
public fun Project.fkcSetupAndroidApp(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    applicationId: String? = null,
    versionCode: Int = 0,
    versionName: String? = null,
    enableBuildConfig: Boolean? = null,
    setupRoom: Boolean? = null,
    setupKsp: Boolean? = null,
    setupCompose: Boolean? = null,
    kotlin: (KotlinAndroidProjectExtension.() -> Unit)? = null,
    android: (BaseAppModuleExtension.() -> Unit)? = null,
) {
    project.fluxoConfiguration {
        isApplication = true

        if (applicationId != null) {
            this.androidNamespace = applicationId
            this.androidApplicationId = applicationId
        }
        if (versionCode != 0) {
            this.androidVersionCode = versionCode
        }

        versionName?.let { this.version = it }
        setupRoom?.let { this.setupRoom = it }
        setupKsp?.let { this.setupKsp = it }
        setupCompose?.let { this.enableCompose = it }
        enableBuildConfig?.let { this.enableBuildConfig = it }

        config?.invoke(this)

        // FIXME: Implement cleaner 2-levels lazy API: asAndroidApp { android { ... } }
        asAndroid(app = true) {
            kotlin?.let { this.kotlin(action = it) }

            if (android != null) {
                androidApp {
                    onAndroidExtension(android)
                }
            }
        }
    }
}
