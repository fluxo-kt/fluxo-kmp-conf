package fluxo.shrink

import fluxo.conf.impl.d
import fluxo.conf.impl.l
import fluxo.conf.impl.w
import fluxo.util.mapToArray
import groovy.lang.Closure
import java.io.File
import java.net.URLClassLoader
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.tasks.testing.DefaultTestTaskReports
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.tasks.testing.TestTaskReports
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.work.NormalizeLineEndings

/**
 * Task that verifies the shrunken artifact.
 * Checks every declaration from the public API for availability.
 *
 * @see org.gradle.api.tasks.testing.AbstractTestTask
 */
@Suppress("LeakingThis")
internal abstract class ShrinkerVerificationTestTask :
    ConventionTask(),
    VerificationTask,
    Reporting<TestTaskReports> {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:NormalizeLineEndings
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedDefinitions: ConfigurableFileCollection

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jar: RegularFileProperty

    @get:InputFiles
    @get:CompileClasspath
    abstract val inputFiles: ConfigurableFileCollection

    private val continueOnFailure: Boolean

    private val reports: TestTaskReports

    private val projectDir: File

    init {
        group = VERIFICATION_GROUP
        description = "Verifies the shrunken artifact."

        val p = project
        reports = p.objects.newInstance(DefaultTestTaskReports::class.java, this)
        // FIXME: Make dir to use the chain id!
        val outputDir = p.layout.buildDirectory.dir("reports/shrinkerVerificationTest")
        for (report in arrayOf(reports.getJunitXml(), reports.getHtml())) {
            report.required.set(true)
            report.outputLocation.set(outputDir)
        }

        continueOnFailure = p.gradle.startParameter.isContinueOnFailure
        projectDir = p.layout.projectDirectory.asFile
    }

    @TaskAction
    operator fun invoke() {
        val files = generatedDefinitions.files
        if (files.isEmpty()) {
            logger.w("No API reports found, skipping")
            return
        }

        logger.d("Verifying the shrunken artifact")

        // 1. read files and parse the Kotlin APIs format.
        @Suppress("MagicNumber")
        val signatures = LinkedHashMap<String, ClassSignature>(64)
        files.forEach { file ->
            file.bufferedReader().use { it.parseJvmApiDumpTo(signatures) }
        }

        // 2. verify the shrinked artifact
        val mainFile = jar.get().asFile
        val jarUrls = inputFiles.mapTo(LinkedHashSet()) { it.toURI() }
            .also { it += mainFile.toURI() }
            .mapToArray { it.toURL() }
        logger.l("- {}", mainFile.toRelativeString(projectDir))
        logger.l("- {} JARs in the classpath for verification", jarUrls.size)
        URLClassLoader(jarUrls).use { classLoader ->
            ShrinkerVerifier(
                mainJarFile = mainFile,
                classLoader = classLoader,
                signatures = signatures,
                logger = logger,
                continueOnFailure = continueOnFailure,
            ).verify()
        }

        // TODO: 3. verify the jar itself (signature, reproducibility, etc.)
    }


    @Nested
    override fun getReports(): TestTaskReports = reports

    override fun reports(closure: Closure<*>): TestTaskReports =
        reports(ClosureBackedAction(closure))

    override fun reports(configureAction: Action<in TestTaskReports>): TestTaskReports {
        configureAction.execute(reports)
        return reports
    }
}
