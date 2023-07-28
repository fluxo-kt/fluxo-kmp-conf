package fluxo.conf.dsl.impl

import com.android.build.api.variant.VariantBuilder
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEFAULT_ANDROID_COMPILE_SDK
import fluxo.conf.data.BuildConstants.DEFAULT_ANDROID_MIN_SDK
import fluxo.conf.data.BuildConstants.DEFAULT_ANDROID_TARGET_SDK
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoConfigurationExtensionAndroid
import fluxo.conf.impl.v
import fluxo.conf.impl.vInt
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

internal interface FluxoConfigurationExtensionAndroidImpl : FluxoConfigurationExtensionAndroid {

    val project: Project
    val context: FluxoKmpConfContext
    val parent: FluxoConfigurationExtension?


    @get:Input
    val androidNamespacePrefixProp: Property<String?>
    override var androidNamespacePrefix: String?
        get() = androidNamespacePrefixProp.orNull ?: parent?.androidNamespacePrefix
        set(value) = androidNamespacePrefixProp.set(value)

    @get:Input
    val androidNamespaceProp: Property<String?>
    override var androidNamespace: String
        get() = (androidNamespaceProp.orNull
            ?: let {
                val prefix = androidNamespacePrefix
                val ns = if (prefix.isNullOrEmpty()) group else project.name
                if (prefix.isNullOrEmpty()) ns else "$prefix.$ns"
            })
            .replace(':', '.')
            .replace('-', '_')
        set(value) = androidNamespaceProp.set(value)

    @get:Input
    val androidApplicationIdProp: Property<String?>
    override var androidApplicationId: String
        get() = androidApplicationIdProp.orNull ?: parent?.androidApplicationId ?: androidNamespace
        set(value) = androidApplicationIdProp.set(value)


    @get:Input
    val androidVersionCodeProp: Property<Int?>
    override var androidVersionCode: Int
        get() = androidVersionCodeProp.orNull
            ?: parent?.androidVersionCode
            ?: context.libs.vInt("versionCode", "androidVersionCode")
            ?: 0
        set(value) = androidVersionCodeProp.set(value)


    @get:Input
    val androidMinSdkProp: Property<Any?>
    override var androidMinSdk: Any
        get() = androidMinSdkProp.orNull
            ?: parent?.androidMinSdk
            ?: av("Min", DEFAULT_ANDROID_MIN_SDK)
        set(value) {
            require(value is Int || value is String) {
                "androidMinSdk must be an Int or String"
            }
            androidMinSdkProp.set(value)
        }

    @get:Input
    val androidTargetSdkProp: Property<Any?>
    override var androidTargetSdk: Any
        get() = androidTargetSdkProp.orNull
            ?: parent?.androidTargetSdk
            ?: av("Target", DEFAULT_ANDROID_TARGET_SDK)
        set(value) {
            require(value is Int || value is String) {
                "androidTargetSdk must be an Int or String"
            }
            androidTargetSdkProp.set(value)
        }

    @get:Input
    val androidCompileSdkProp: Property<Any?>
    override var androidCompileSdk: Any
        get() = androidCompileSdkProp.orNull
            ?: parent?.androidCompileSdk
            ?: av("Compile", DEFAULT_ANDROID_COMPILE_SDK)
        set(value) {
            require(value is Int || value is String) {
                "androidCompileSdk must be an Int or String"
            }
            androidCompileSdkProp.set(value)
        }

    private fun av(type: String, default: Int): Any {
        val lType = type[0].lowercaseChar() + type.substring(1)
        val preview = context.libs
            .v("androidPreviewSdk", "android${type}SdkPreview", "${lType}SdkPreview")
        return when {
            !preview.isNullOrEmpty() && preview != "0" -> preview
            else -> context.libs.vInt("android${type}Sdk", "${lType}Sdk") ?: default
        }
    }


    @get:Input
    val androidBuildToolsVersionProp: Property<String?>
    override var androidBuildToolsVersion: String?
        get() = androidBuildToolsVersionProp.orNull
            ?: parent?.androidBuildToolsVersion
            ?: run {
                context.libs.v("androidBuildTools", "buildToolsVersion", "androidBuildToolsVersion")
            }
        set(value) = androidBuildToolsVersionProp.set(value)


    @get:Input
    val androidResourceConfsProp: SetProperty<String>
    override var androidResourceConfigurations: Set<String>
        get() = androidResourceConfsProp.orNull ?: parent?.androidResourceConfigurations.orEmpty()
        set(value) = androidResourceConfsProp.set(value)


    @get:Input
    val enableBuildConfigProp: Property<Boolean>
    override var enableBuildConfig: Boolean
        get() = enableBuildConfigProp.orNull ?: parent?.enableBuildConfig ?: false
        set(value) = enableBuildConfigProp.set(value)


    @get:Input
    val enableComposeProp: Property<Boolean?>
    override var enableCompose: Boolean?
        get() = enableComposeProp.orNull ?: parent?.enableCompose
        set(value) = enableComposeProp.set(value)

    @get:Input
    val suppressKotlinComposeCompatibilityCheckProp: Property<Boolean?>
    override var suppressKotlinComposeCompatibilityCheck: Boolean?
        get() = suppressKotlinComposeCompatibilityCheckProp.orNull
            ?: parent?.suppressKotlinComposeCompatibilityCheck
        set(value) = suppressKotlinComposeCompatibilityCheckProp.set(value)


    @get:Input
    val filterVariantsProp: Property<((VariantBuilder) -> Boolean)?>
    override var filterVariants: ((VariantBuilder) -> Boolean)?
        get() = filterVariantsProp.orNull ?: parent?.filterVariants
        set(value) = filterVariantsProp.set(value)


    @get:Input
    val noVerifyBuildTypesProp: ListProperty<String>
    override var noVerificationBuildTypes: List<String>
        get() = noVerifyBuildTypesProp.orNull.orEmpty() + parent?.noVerificationBuildTypes.orEmpty()
        set(value) = noVerifyBuildTypesProp.set(value)

    @get:Input
    val noVerifyFlavorsProp: ListProperty<String>
    override var noVerificationFlavors: List<String>
        get() = noVerifyFlavorsProp.orNull.orEmpty() + parent?.noVerificationFlavors.orEmpty()
        set(value) = noVerifyFlavorsProp.set(value)


    @get:Input
    val setupRoomProp: Property<Boolean?>
    override var setupRoom: Boolean?
        get() = setupRoomProp.orNull ?: parent?.setupRoom
        set(value) = setupRoomProp.set(value)


    @get:Input
    val removeKotlinMetadataProp: Property<Boolean>
    override var removeKotlinMetadata: Boolean
        get() = removeKotlinMetadataProp.orNull ?: parent?.removeKotlinMetadata ?: false
        set(value) = removeKotlinMetadataProp.set(value)
}
