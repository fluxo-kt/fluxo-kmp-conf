package fluxo.shrink

import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import fluxo.log.l
import fluxo.util.mapToArray
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.gradle.work.NormalizeLineEndings

/**
 * Task that verifies the shrunken artifact.
 * Checks every declaration from the public API for availability.
 *
 * @see org.gradle.api.tasks.testing.AbstractTestTask
 * @see org.gradle.api.tasks.testing.Test
 * @see org.jetbrains.kotlin.gradle.tasks.KotlinTest
 */
@CacheableTask
@Suppress("LeakingThis")
internal abstract class ShrinkerVerificationTestTask : AbstractTestTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:NormalizeLineEndings
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val generatedDefinitions: ConfigurableFileCollection = project.objects.fileCollection()

    @get:Input
    @get:Optional
    val chainId: Property<Int> = project.objects.notNullProperty(defaultValue = 0)

    @get:Input
    @get:Optional
    val chainForLog: Property<String?> = project.objects.nullableProperty()

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jar: RegularFileProperty = project.objects.fileProperty()

    @get:InputFiles
    @get:CompileClasspath
    abstract val inputFiles: ConfigurableFileCollection

    private val continueOnFailure: Boolean

    private val projectDir: File

    init {
        group = VERIFICATION_GROUP
        description = "Verifies the processed (shrunken) artifact."

        val p = project
        val layout = p.layout

        // Task name contains the processing chain ID, so it's unique.
        val outputDir = layout.buildDirectory.dir("reports/$name")
        binaryResultsDirectory.set(outputDir)
        for (report in arrayOf(reports.getJunitXml(), reports.getHtml())) {
            report.required.set(true)
            report.outputLocation.set(outputDir)
        }

        projectDir = layout.projectDirectory.asFile
        continueOnFailure = p.gradle.startParameter.isContinueOnFailure
    }

    override fun createTestExecutionSpec(): TestExecutionSpec {
        val chainId = chainId.get()
        val chainForLog = chainForLog.orNull?.takeIf { it.isNotBlank() } ?: path
        val note = "($chainForLog, chain #$chainId)"
        logger.l("$note Verifying the processed artifact API with ASM and Reflection...")

        // Read generated definitions and parse the Kotlin APIs format.
        val signatures = LinkedHashMap<String, ClassSignature>(@Suppress("MagicNumber") 64)
        generatedDefinitions.forEach { file ->
            file.bufferedReader().use { it.parseJvmApiDumpTo(signatures) }
        }

        return ShrinkerVerificationTestExecutionSpec(
            continueOnFailure = ignoreFailures || continueOnFailure,
            signatures = signatures,
            inputFiles = inputFiles,
            projectDir = projectDir,
            mainJar = jar,
            taskName = name,
        )
    }

    override fun createTestExecuter(): TestExecuter<out TestExecutionSpec> =
        ShrinkerVerificationTest(logger)

    private class ShrinkerVerificationTest(
        private val logger: Logger,
    ) : TestExecuter<ShrinkerVerificationTestExecutionSpec> {

        @Volatile
        private var verifier: WeakReference<ShrinkerVerifier>? = null

        override fun execute(
            spec: ShrinkerVerificationTestExecutionSpec,
            proc: TestResultProcessor,
        ) {
            // Verify the shrinked artifact
            val mainFile = spec.mainJar.get().asFile
            val jarUrls = spec.inputFiles.mapTo(LinkedHashSet()) { it.toURI() }
                .also { it += mainFile.toURI() }
                .mapToArray { it.toURL() }
            logger.l("- {}", mainFile.toRelativeString(spec.projectDir))
            logger.l("- {} JARs in the classpath for verification", jarUrls.size)
            ShrinkerVerifier(
                taskName = spec.taskName,
                mainJarFile = mainFile,
                signatures = spec.signatures,
                logger = logger,
                proc = proc,
                continueOnFailure = spec.continueOnFailure,
                classLoader = URLClassLoader(jarUrls),
            ).use {
                verifier = WeakReference(it)
                it.verify()
            }

            // TODO: Verify the jar itself (artifact signature, reproducibility, etc.)
        }

        override fun stopNow() {
            verifier?.get()?.stopNow()
        }
    }

    private class ShrinkerVerificationTestExecutionSpec(
        val continueOnFailure: Boolean,
        val signatures: Map<String, ClassSignature>,
        val mainJar: Provider<RegularFile>,
        val inputFiles: FileCollection,
        val projectDir: File,
        val taskName: String,
    ) : TestExecutionSpec
}
