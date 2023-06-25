package fluxo.conf.dsl.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoConfigurationExtensionKotlin
import fluxo.conf.impl.v
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

internal interface FluxoConfigurationExtensionKotlinImpl : FluxoConfigurationExtensionKotlin {

    val context: FluxoKmpConfContext
    val parent: FluxoConfigurationExtension?


    @get:Input
    val kotlinLangVersionProp: Property<String?>
    override var kotlinLangVersion: String?
        get() {
            return kotlinLangVersionProp.orNull ?: parent?.kotlinLangVersion ?: context.libs
                .v("kotlinLangVersion", "kotlinApiVersion", "kotlinLang", "kotlinApi")
        }
        set(value) = kotlinLangVersionProp.set(value)


    @get:Input
    val kotlinCoreLibrariesProp: Property<String?>
    override var kotlinCoreLibraries: String
        get() {
            return kotlinCoreLibrariesProp.orNull
                ?: parent?.kotlinCoreLibraries
                ?: context.libs.v("kotlinCoreLibraries", "kotlinCoreLibrariesVersion", "kotlin")
                ?: context.kotlinPluginVersion.toString()
        }
        set(value) = kotlinCoreLibrariesProp.set(value)


    @get:Input
    val javaLangTargetProp: Property<String?>
    override var javaLangTarget: String?
        get() {
            return javaLangTargetProp.orNull ?: parent?.javaLangTarget ?: context.libs
                .v("javaLangTarget", "jvmTarget", "sourceCompatibility", "targetCompatibility")
        }
        set(value) = javaLangTargetProp.set(value)

    @get:Input
    val setupJvmToolchainProp: Property<Boolean?>
    override var setupJvmToolchain: Boolean?
        get() = setupJvmToolchainProp.orNull ?: parent?.setupJvmToolchain
        set(value) = setupJvmToolchainProp.set(value)


    @get:Input
    val optInProp: ListProperty<String>
    override var optIns: List<String>
        get() = optInProp.orNull ?: parent?.optIns.orEmpty()
        set(value) = optInProp.set(value)

    @get:Input
    val optInInternalProp: Property<Boolean?>
    override var optInInternal: Boolean?
        get() = optInInternalProp.orNull ?: parent?.optInInternal
        set(value) = optInInternalProp.set(value)


    @get:Input
    val warningsAsErrorsProp: Property<Boolean?>
    override var warningsAsErrors: Boolean?
        get() = warningsAsErrorsProp.orNull ?: parent?.warningsAsErrors
        set(value) = warningsAsErrorsProp.set(value)

    @get:Input
    val javaParametersProp: Property<Boolean?>
    override var javaParameters: Boolean?
        get() = javaParametersProp.orNull ?: parent?.javaParameters
        set(value) = javaParametersProp.set(value)

    @get:Input
    val useIndyLambdasProp: Property<Boolean?>
    override var useIndyLambdas: Boolean?
        get() = useIndyLambdasProp.orNull ?: parent?.useIndyLambdas
        set(value) = useIndyLambdasProp.set(value)

    @get:Input
    val progressiveModeProp: Property<Boolean?>
    override var progressiveMode: Boolean?
        get() = progressiveModeProp.orNull ?: parent?.progressiveMode
        set(value) = progressiveModeProp.set(value)

    @get:Input
    val removeAssertionsInReleaseProp: Property<Boolean?>
    override var removeAssertionsInRelease: Boolean?
        get() = removeAssertionsInReleaseProp.orNull ?: parent?.removeAssertionsInRelease
        set(value) = removeAssertionsInReleaseProp.set(value)


    @get:Input
    val addStdlibDependencyProp: Property<Boolean?>
    override var addStdlibDependency: Boolean?
        get() = addStdlibDependencyProp.orNull ?: parent?.addStdlibDependency
        set(value) = addStdlibDependencyProp.set(value)

    @get:Input
    val setupCoroutinesProp: Property<Boolean?>
    override var setupCoroutines: Boolean?
        get() = setupCoroutinesProp.orNull ?: parent?.setupCoroutines
        set(value) = setupCoroutinesProp.set(value)

    @get:Input
    val setupSerializationKotlinXProp: Property<Boolean?>
    override var setupSerializationKotlinX: Boolean?
        get() = setupSerializationKotlinXProp.orNull ?: parent?.setupSerializationKotlinX
        set(value) = setupSerializationKotlinXProp.set(value)

    @get:Input
    val setupKnownBomsProp: Property<Boolean?>
    override var setupKnownBoms: Boolean?
        get() = setupKnownBomsProp.orNull ?: parent?.setupKnownBoms
        set(value) = setupKnownBomsProp.set(value)


    @get:Input
    val useExperimentalFastJarFsProp: Property<Boolean?>
    override var useExperimentalFastJarFs: Boolean?
        get() = useExperimentalFastJarFsProp.orNull ?: parent?.useExperimentalFastJarFs
        set(value) = useExperimentalFastJarFsProp.set(value)


    @get:Input
    val enableApiValidationProp: Property<Boolean?>
    override var enableApiValidation: Boolean?
        get() = enableApiValidationProp.orNull ?: parent?.enableApiValidation
        set(value) = enableApiValidationProp.set(value)

    @get:Input
    val apiValidationProp: Property<BinaryCompatibilityValidatorConfig?>
    override var apiValidation: BinaryCompatibilityValidatorConfig?
        get() = apiValidationProp.orNull ?: parent?.apiValidation
        set(value) = apiValidationProp.set(value)


    @get:Input
    val setupVerificationProp: Property<Boolean?>
    override var setupVerification: Boolean?
        get() = setupVerificationProp.orNull ?: parent?.setupVerification
        set(value) = setupVerificationProp.set(value)
}
