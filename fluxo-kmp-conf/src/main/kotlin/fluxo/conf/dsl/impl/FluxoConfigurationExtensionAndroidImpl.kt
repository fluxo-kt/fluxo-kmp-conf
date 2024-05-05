package fluxo.conf.dsl.impl

import com.android.build.api.variant.VariantBuilder
import fluxo.conf.dsl.FluxoConfigurationExtensionAndroid
import fluxo.conf.dsl.FluxoConfigurationExtensionPublication
import fluxo.conf.impl.android.DEFAULT_ANDROID_COMPILE_SDK
import fluxo.conf.impl.android.DEFAULT_ANDROID_MIN_SDK
import fluxo.conf.impl.android.DEFAULT_ANDROID_TARGET_SDK
import fluxo.conf.impl.uncheckedCast
import fluxo.vc.v
import fluxo.vc.vInt
import java.util.Locale
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

internal interface FluxoConfigurationExtensionAndroidImpl :
    FluxoConfigurationExtensionAndroid,
    FluxoConfigurationExtensionPublication,
    FluxoConfigurationExtensionImplBase {

    @get:Input
    val androidNamespacePrefixProp: Property<String?>
    override var androidNamespacePrefix: String?
        get() = androidNamespacePrefixProp.orNull ?: parent?.androidNamespacePrefix
        set(value) = androidNamespacePrefixProp.set(value)

    @get:Input
    val androidNamespaceProp: Property<String?>
    override var androidNamespace: String
        get() = (
            androidNamespaceProp.orNull.takeIf { !it.isNullOrBlank() }
                ?: run {
                    val ns = group.takeIf { it.isNotBlank() } ?: project.name
                    val prefix = androidNamespacePrefix
                    if (prefix.isNullOrBlank()) ns else "$prefix.$ns"
                }
            )
            .replace("[:_-]".toRegex(), ".")
            .lowercase(Locale.US)
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
            ?: ctx.libs.vInt("versionCode", "androidVersionCode")
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
        val preview = ctx.libs
            .v("androidPreviewSdk", "android${type}SdkPreview", "${lType}SdkPreview")
        return when {
            !preview.isNullOrEmpty() && preview != "0" -> preview
            else -> ctx.libs.vInt("android${type}Sdk", "${lType}Sdk") ?: default
        }
    }


    @get:Input
    val androidBuildToolsVersionProp: Property<String?>
    override var androidBuildToolsVersion: String?
        get() = androidBuildToolsVersionProp.orNull
            ?: parent?.androidBuildToolsVersion
            ?: run {
                ctx.libs.v("androidBuildTools", "buildToolsVersion", "androidBuildToolsVersion")
            }
        set(value) = androidBuildToolsVersionProp.set(value)


    @get:Input
    val androidResourceConfsProp: SetProperty<String>
    override var androidResourceConfigurations: Set<String>
        get() = androidResourceConfsProp.orNull ?: parent?.androidResourceConfigurations.orEmpty()
        set(value) = androidResourceConfsProp.set(value)


    // Mask android VariantBuilder with Any
    // to avoid runtime error when Android plugin isn't present.
    @get:Input
    val filterVariantsProp: Property<((Any) -> Boolean)?>
    val filterVariants: ((Any) -> Boolean)?
        get() = filterVariantsProp.orNull
        // TODO: Avoid casting
            ?: (parent as? FluxoConfigurationExtensionAndroidImpl)?.filterVariants

    override fun filterVariants(predicate: (VariantBuilder) -> Boolean) {
        filterVariantsProp.set(uncheckedCast<(Any) -> Boolean>(predicate))
    }

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
