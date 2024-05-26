package fluxo.conf.dsl.impl

import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.FluxoConfigurationExtensionKotlin
import fluxo.vc.v
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal interface FluxoConfigurationExtensionKotlinImpl :
    FluxoConfigurationExtensionKotlin,
    FluxoConfigurationExtensionImplBase {

    @get:Input
    val onConfigurationProp: Property<(KotlinProjectExtension.() -> Unit)?>
    override var onConfiguration: (KotlinProjectExtension.() -> Unit)?
        get() = onConfigurationProp.orNull ?: parent?.onConfiguration
        set(value) = onConfigurationProp.set(value)


    @get:Input
    val kotlinLangVersionProp: Property<String?>
    override var kotlinLangVersion: String?
        get() {
            return kotlinLangVersionProp.orNull ?: parent?.kotlinLangVersion ?: ctx.libs
                .v(
                    "kotlinLangVersion", "kotlinLang", "kotlinLanguage",
                    allowFallback = false,
                )
        }
        set(value) = kotlinLangVersionProp.set(value)

    @get:Input
    val kotlinApiVersionProp: Property<String?>
    override var kotlinApiVersion: String?
        get() {
            return kotlinApiVersionProp.orNull ?: parent?.kotlinApiVersion ?: ctx.libs
                .v(
                    "kotlinApiVersion", "kotlinApi",
                    allowFallback = false,
                ) ?: kotlinLangVersion
        }
        set(value) = kotlinApiVersionProp.set(value)

    @get:Input
    val kotlinTestsLangVersionProp: Property<String?>
    override var kotlinTestsLangVersion: String?
        get() {
            return kotlinTestsLangVersionProp.orNull
                ?: parent?.kotlinTestsLangVersion
                ?: ctx.libs.v(
                    "testsKotlinLangVersion", "testsKotlinLang",
                    allowFallback = false,
                )
        }
        set(value) = kotlinTestsLangVersionProp.set(value)

    @get:Input
    val kotlinCoreLibrariesProp: Property<String?>
    override var kotlinCoreLibraries: String?
        get() {
            return kotlinCoreLibrariesProp.orNull ?: parent?.kotlinCoreLibraries ?: ctx.libs.v(
                "kotlinCoreLibraries", "kotlinCoreLibrariesVersion",
                "kotlinStdlib", "kotlin", "kotlinVersion",
                allowFallback = false,
            )
        }
        set(value) = kotlinCoreLibrariesProp.set(value)


    @get:Input
    val javaLangTargetProp: Property<String?>
    override var javaLangTarget: String?
        get() {
            return javaLangTargetProp.orNull ?: parent?.javaLangTarget ?: ctx.libs.v(
                "jvmTarget", "javaLangTarget", "javaLangSource", "javaToolchain",
                "sourceCompatibility", "targetCompatibility",
                allowFallback = false,
            )
        }
        set(value) = javaLangTargetProp.set(value)

    @get:Input
    val javaTestsLangTargetProp: Property<String?>
    override var javaTestsLangTarget: String?
        get() {
            return javaTestsLangTargetProp.orNull ?: parent?.javaTestsLangTarget ?: ctx.libs
                .v(
                    "jvmTestsTarget", "javaTestsLangTarget",
                    allowFallback = false,
                )
        }
        set(value) = javaTestsLangTargetProp.set(value)

    @get:Input
    val useJdkReleaseProp: Property<Boolean?>
    override var useJdkRelease: Boolean
        get() = useJdkReleaseProp.orNull ?: parent?.useJdkRelease ?: true
        set(value) = useJdkReleaseProp.set(value)

    @get:Input
    val setupJvmToolchainProp: Property<Boolean?>
    override var setupJvmToolchain: Boolean
        get() = setupJvmToolchainProp.orNull ?: parent?.setupJvmToolchain ?: false
        set(value) = setupJvmToolchainProp.set(value)


    @get:Input
    val setupKotlinProp: Property<Boolean>
    override var setupKotlin: Boolean
        get() = setupKotlinProp.orNull ?: parent?.setupKotlin ?: true
        set(value) = setupKotlinProp.set(value)


    @get:Input
    val setupKspProp: Property<Boolean?>
    override var setupKsp: Boolean?
        get() = setupKspProp.orNull ?: parent?.setupKsp
        set(value) = setupKspProp.set(value)


    @get:Input
    val setupKaptProp: Property<Boolean?>
    override var setupKapt: Boolean?
        get() = setupKaptProp.orNull ?: parent?.setupKapt
        set(value) = setupKaptProp.set(value)


    @get:Input
    val optInProp: ListProperty<String>
    override var optIns: List<String>
        get() = optInProp.orNull.orEmpty() + parent?.optIns.orEmpty()
        set(value) = optInProp.set(value)

    @get:Input
    val optInInternalProp: Property<Boolean?>
    override var optInInternal: Boolean?
        get() = optInInternalProp.orNull ?: parent?.optInInternal
        set(value) = optInInternalProp.set(value)


    @get:Input
    val explicitApiProp: Property<ExplicitApiMode?>
    override var explicitApi: ExplicitApiMode?
        get() = explicitApiProp.orNull ?: parent?.explicitApi
        set(value) = explicitApiProp.set(value)


    @get:Input
    val warningsAsErrorsProp: Property<Boolean?>
    override var allWarningsAsErrors: Boolean?
        get() = warningsAsErrorsProp.orNull ?: parent?.allWarningsAsErrors
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
    val latestSettingsForTestProp: Property<Boolean?>
    override var latestSettingsForTests: Boolean?
        get() = latestSettingsForTestProp.orNull ?: parent?.latestSettingsForTests
        set(value) = latestSettingsForTestProp.set(value)

    @get:Input
    val experimentalLatestCompilationProp: Property<Boolean?>
    override var experimentalLatestCompilation: Boolean?
        get() = experimentalLatestCompilationProp.orNull ?: parent?.experimentalLatestCompilation
        set(value) = experimentalLatestCompilationProp.set(value)

    @get:Input
    val removeAssertionsInReleaseProp: Property<Boolean?>
    override var removeAssertionsInRelease: Boolean?
        get() = removeAssertionsInReleaseProp.orNull ?: parent?.removeAssertionsInRelease
        set(value) = removeAssertionsInReleaseProp.set(value)


    @get:Input
    val applicationFlagProp: Property<Boolean?>
    override var isApplication: Boolean
        get() = applicationFlagProp.orNull ?: false
        set(value) = applicationFlagProp.set(value)


    @get:Input
    val setupDependenciesProp: Property<Boolean>
    override var setupDependencies: Boolean
        get() = setupDependenciesProp.orNull ?: parent?.setupDependencies ?: false
        set(value) = setupDependenciesProp.set(value)

    @get:Input
    val addStdlibDependencyProp: Property<Boolean>
    override var addStdlibDependency: Boolean
        get() = addStdlibDependencyProp.orNull ?: parent?.addStdlibDependency ?: false
        set(value) = addStdlibDependencyProp.set(value)

    @get:Input
    val setupCoroutinesProp: Property<Boolean?>
    override var setupCoroutines: Boolean?
        get() = setupCoroutinesProp.orNull ?: parent?.setupCoroutines
        set(value) = setupCoroutinesProp.set(value)

    @get:Input
    val setupKotlinXSerializationProp: Property<Boolean>
    override var setupKotlinXSerialization: Boolean
        get() = setupKotlinXSerializationProp.orNull ?: parent?.setupKotlinXSerialization ?: false
        set(value) = setupKotlinXSerializationProp.set(value)

    @get:Input
    val setupKnownBomsProp: Property<Boolean>
    override var setupKnownBoms: Boolean
        get() = setupKnownBomsProp.orNull ?: parent?.setupKnownBoms ?: false
        set(value) = setupKnownBomsProp.set(value)


    @get:Input
    val useExperimentalFastJarFsProp: Property<Boolean?>
    override var useExperimentalFastJarFs: Boolean?
        get() = useExperimentalFastJarFsProp.orNull ?: parent?.useExperimentalFastJarFs
        set(value) = useExperimentalFastJarFsProp.set(value)


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
    val enableBuildConfigProp: Property<Boolean>
    override var enableBuildConfig: Boolean
        get() = enableBuildConfigProp.orNull ?: parent?.enableBuildConfig ?: false
        set(value) = enableBuildConfigProp.set(value)


    @get:Input
    val enableApiValidationProp: Property<Boolean?>
    override var enableApiValidation: Boolean
        get() = enableApiValidationProp.orNull ?: parent?.enableApiValidation ?: true
        set(value) = enableApiValidationProp.set(value)

    @get:Input
    val apiValidationProp: Property<BinaryCompatibilityValidatorConfig?>
    val apiValidationGetter: BinaryCompatibilityValidatorConfig?
        get() {
            return apiValidationProp.orNull
                ?: (parent as FluxoConfigurationExtensionKotlinImpl?)?.apiValidationGetter
        }
    override var apiValidation: BinaryCompatibilityValidatorConfig
        get() {
            return apiValidationGetter
                ?: BinaryCompatibilityValidatorConfig().also {
                    apiValidation = it
                }
        }
        set(value) {
            if (enableApiValidationProp.orNull == false) {
                throw GradleException(
                    "BinaryCompatibilityValidator is explicitly disabled, " +
                        "but you are trying to configure it. " +
                        "Please, enable it first!",
                )
            }
            enableApiValidation = true
            apiValidationProp.set(value)
        }


    @get:Input
    val setupVerificationProp: Property<Boolean?>
    override var setupVerification: Boolean
        get() = setupVerificationProp.orNull ?: parent?.setupVerification ?: true
        set(value) = setupVerificationProp.set(value)

    @get:Input
    val enableDetektCompilerPluginProp: Property<Boolean?>
    override var enableDetektCompilerPlugin: Boolean?
        get() = enableDetektCompilerPluginProp.orNull ?: parent?.enableDetektCompilerPlugin
        set(value) = enableDetektCompilerPluginProp.set(value)

    @get:Input
    val enableDetektAutoCorrectProp: Property<Boolean?>
    override var enableDetektAutoCorrect: Boolean?
        get() = enableDetektAutoCorrectProp.orNull ?: parent?.enableDetektAutoCorrect
        set(value) = enableDetektAutoCorrectProp.set(value)

    @get:Input
    val enableGenericAndroidLintProp: Property<Boolean?>
    override var enableGenericAndroidLint: Boolean
        get() = enableGenericAndroidLintProp.orNull ?: parent?.enableGenericAndroidLint ?: true
        set(value) = enableGenericAndroidLintProp.set(value)

    @get:Input
    val enableSpotlessProp: Property<Boolean?>
    override var enableSpotless: Boolean
        get() = enableSpotlessProp.orNull ?: parent?.enableSpotless ?: false
        set(value) = enableSpotlessProp.set(value)


    @get:Input
    val useDokkaProp: Property<Boolean?>
    override var useDokka: Boolean?
        get() = useDokkaProp.orNull ?: parent?.useDokka
        set(value) = useDokkaProp.set(value)
}
