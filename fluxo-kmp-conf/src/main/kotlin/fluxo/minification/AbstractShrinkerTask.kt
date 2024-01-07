@file:Suppress("KDocUnresolvedReference")

package fluxo.minification

import fluxo.conf.impl.e
import fluxo.conf.impl.jvmToolFile
import fluxo.conf.impl.lc
import fluxo.conf.impl.w
import fluxo.external.AbstractExternalFluxoTask
import fluxo.external.ExternalToolRunner
import fluxo.gradle.clearDirs
import fluxo.gradle.cliArg
import fluxo.gradle.ioFile
import fluxo.gradle.normalizedPath
import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import fluxo.minification.Shrinker.ProGuard
import fluxo.minification.Shrinker.R8
import fluxo.util.readableByteSize
import java.io.File
import java.io.Writer
import java.lang.System.currentTimeMillis
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 *
 * @see org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
 * @see com.android.build.gradle.internal.tasks.ProguardConfigurableTask
 * @see com.android.build.gradle.internal.tasks.R8Task
 * @see proguard.gradle.ProGuardTask
 */
@CacheableTask
@Suppress("LeakingThis", "TooManyFunctions")
internal abstract class AbstractShrinkerTask : AbstractExternalFluxoTask() {

    @get:Input
    val shrinker: Property<Shrinker> = objects.notNullProperty()

    @get:InputFiles
    @get:CompileClasspath
    val inputFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:InputFile
    @get:CompileClasspath
    val mainJar: RegularFileProperty = objects.fileProperty()

    @get:Optional
    @get:Input
    val processApplication: Property<Boolean> = objects.notNullProperty(false)

    @get:Optional
    @get:Input
    val filterMultireleaseJars: Property<Boolean> = objects.notNullProperty(true)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val configurationFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:Optional
    @get:Input
    val dontobfuscate: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Input
    val dontoptimize: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Input
    val r8FulMode: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val defaultRulesFile: RegularFileProperty = objects.fileProperty()

    /**
     * Internal dependency on all R8/ProGuard rules files.
     * Needed to invalidate the task when any of the rules files is changed.
     */
    @get:Optional
    @get:InputFiles
    @Suppress("unused")
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val rulesFile: Provider<FileCollection> = defaultRulesFile.map { file ->
        objects.fileTree().from(file.asFile.parentFile)
            .filter { it.path.endsWith(".pro") }
    }

    @get:Input
    val toolCoordinates: Property<String> = objects.notNullProperty()

    @get:InputFiles
    @get:Classpath
    val toolJars: ConfigurableFileCollection = objects.fileCollection()

    @get:Input
    val javaHome: Property<String> = objects.notNullProperty(System.getProperty("java.home"))

    @get:Input
    @get:Optional
    val mainClass: Property<String?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    val jvmTarget: Property<String?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    val androidMinSdk: Property<Int?> = objects.nullableProperty()

    @get:Internal
    val maxHeapSize: Property<String?> = objects.nullableProperty()

    @get:Internal
    val vmOptions: Property<Boolean> = objects.notNullProperty(true)

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = objects.directoryProperty()

    /** Primary destination file */
    @get:LocalState
    internal val destinationFile: Provider<RegularFile> = destinationDir.zip(mainJar) { dir, jar ->
        dir.file(jar.asFile.name)
    }

    @get:LocalState
    protected val workingTmpDir: Provider<Directory>

    @get:LocalState
    protected val reportsDir: Provider<Directory>

    init {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Shrink and optimize final JVM artifact"

        val layout = project.layout
        val buildDir = layout.buildDirectory
        val toolLc = shrinker.map { it.name.lc() }
        workingTmpDir = buildDir.zip(toolLc) { d, tool ->
            d.dir("tmp/shrink/$tool")
        }
        reportsDir = buildDir.zip(toolLc) { d, tool ->
            d.dir("reports/shrink/$tool")
        }
        logsDir.set(
            buildDir.zip(toolLc) { d, tool ->
                d.dir("logs/shrink/$tool")
            },
        )
        destinationDir.set(
            buildDir.zip(toolLc) { d, tool ->
                d.dir("output/shrink/$tool")
            },
        )

        val projectDirectory = layout.projectDirectory
        defaultRulesFile.set(projectDirectory.file("pg/rules.pro"))
    }

    @TaskAction
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    operator fun invoke() {
        val javaHome = File(javaHome.get())
        val destinationDir = destinationDir.ioFile.absoluteFile
        val workingDir = workingTmpDir.get()
        val reportsDir = reportsDir.get()
        fileOperations.clearDirs(destinationDir, workingDir.asFile, reportsDir.asFile)

        // FIXME: When java toolchain used or JDK target is specified, read the specified JDK.
        // TODO: Can be cached for a JDK.

        val shrinker = shrinker.get()
        val jmods = when (shrinker) {
            ProGuard -> getJmods(javaHome)
            else -> null
        }

        // For a final application, we need to process all jars,
        // but for a library, only the main jar.
        val onlyMainJar = !processApplication.get()
        val inputToOutputJars = LinkedHashMap<File, File?>()
        var initialSize: Long = 0

        // Avoid mangling mainJar
        mainJar.ioFile.let { mainJar ->
            inputToOutputJars[mainJar] = destinationFile.get().asFile
            initialSize += mainJar.length()
        }

        for (inputFile in inputFiles) {
            val outputFile = destinationDir.resolve(inputFile.name)
            if (!inputFile.name.endsWith(".jar", ignoreCase = true)) {
                if (!onlyMainJar) {
                    inputFile.copyTo(outputFile)
                    initialSize += inputFile.length()
                }
                continue
            }

            val output = when {
                onlyMainJar -> null
                else -> outputFile
            }
            if (inputToOutputJars.putIfAbsent(inputFile, output) != null) {
                logger.w("Duplicate input jar: $inputFile")
            } else if (output != null) {
                initialSize += inputFile.length()
            }
        }

        val libraryJarsFilter = when {
            !filterMultireleaseJars.get() -> ""
            else -> "(!META-INF/versions/**,!**/META-INF/versions/**)"
        }
        val jarsConfFile = workingDir.file("jars-config.pro").asFile
        val outJars = writeJarsConfigurationFile(
            file = jarsConfFile,
            inputToOutputJars = inputToOutputJars,
            libraryJarsFilter = libraryJarsFilter,
            jmods = jmods.orEmpty(),
            javaHome = if (jmods == null) javaHome.path else null,
            shrinker = shrinker,
        )

        val rootConfigurationFile = workingDir.file("root-config.pro").asFile
        writeRootConfiguration(rootConfigurationFile, reportsDir, jarsConfFile)

        val javaBinary = jvmToolFile(toolName = "java", javaHome = javaHome)
        val args = getShrinkerArgs(rootConfigurationFile, outJars, reportsDir, javaHome)

        // TODO: Process output and print only main information if not verbose
        val start = currentTimeMillis()
        runExternalTool(
            tool = javaBinary,
            args = args,
            logToConsole = ExternalToolRunner.LogToConsole.OnlyWhenVerbose,
        ).assertNormalExitValue()

        @Suppress("MagicNumber")
        val elapsedSec = (currentTimeMillis() - start) / 1_000f
        reportSavings(destinationDir, initialSize, elapsedSec)
    }

    private fun reportSavings(destinationDir: File, initialSize: Long, elapsedSec: Float) {
        val files = destinationDir.listFiles()
            ?: return

        val finalSize = files.sumOf { it.length() }
        val initial = readableByteSize(initialSize)
        val final = readableByteSize(finalSize)

        if (initialSize < finalSize) {
            val addedBytes = readableByteSize(finalSize - initialSize)
            logger.e(
                "{} failed to save size: {} -> {} (INCREASED {}) in {} s",
                shrinker.get(),
                initial,
                final,
                addedBytes,
                elapsedSec,
            )
            return
        }

        @Suppress("MagicNumber")
        val savedPercent = (1f - (finalSize / initialSize.toFloat())) * 100f
        val savedBytes = readableByteSize(initialSize - finalSize)
        logger.lifecycle(
            "> {} results: {} -> {} (saved {}%, {}) in {} s",
            shrinker.get(),
            initial,
            final,
            "%.03f".format(savedPercent),
            savedBytes,
            elapsedSec,
        )
    }

    private fun getShrinkerArgs(
        rootConfigurationFile: File?,
        outJars: List<String>,
        reportsDir: Directory,
        javaHome: File,
    ): List<String> {
        return arrayListOf<String>().apply {
            maxHeapSize.orNull?.let {
                add("-Xmx:$it")
            }
            if (vmOptions.get()) {
                add("-XX:+TieredCompilation")
            }
            cliArg(
                "-cp",
                toolJars.joinToString(File.pathSeparator) { it.normalizedPath() },
            )

            when (checkNotNull(shrinker.get())) {
                ProGuard -> {
                    add("proguard.ProGuard")

                    // todo: consider separate flag
                    cliArg("-verbose", verbose)
                    cliArg("-include", rootConfigurationFile)
                }

                R8 -> {
                    // R8 is not command line compatible with ProGuard
                    // https://r8.googlesource.com/r8/#running-r8
                    /** @see com.android.builder.dexing.runR8 */

                    add("com.android.tools.r8.R8")
                    add("--release") // (default) vs --debug
                    add("--classfile") // vs --dex (default)
                    cliArg("--lib", javaHome)
                    cliArg("--output", outJars.single())

                    // Only for DEX output
                    // androidMinSdk.orNull?.let { cliArg("--min-api", it) }

                    cliArg("--pg-conf", rootConfigurationFile)

                    // Output the collective configuration to <file>.
                    cliArg("--pg-conf-output", getFinalConfigFile(reportsDir))
                    cliArg("--pg-map-output", getMappingFile(reportsDir))

                    // Force disable minification of names.
                    if (dontobfuscate.orNull == true) {
                        add("--no-minification")
                    }

                    // Force disable tree shaking of unreachable classes.
                    if (dontoptimize.orNull == true) {
                        add("--no-tree-shaking")
                    }

                    // Compile with R8 in Proguard compatibility mode.
                    // Opposite of non-compat mode, also called "full mode".
                    // 'android.enableR8.fullMode' controls it for android builds.
                    // https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#r8-full-mode
                    if (r8FulMode.orNull != true) {
                        add("--pg-compat")
                    }
                }
            }
        }
    }

    /**
     * @see com.android.build.gradle.internal.tasks.ProguardConfigurableTask
     * @see com.android.build.gradle.internal.tasks.R8Task
     */
    private fun writeRootConfiguration(
        file: File,
        reportsDir: Directory,
        jarsConfigurationFile: File,
    ) = file.bufferedWriter().use { writer ->
        jvmTarget.orNull?.let { jvmTarget ->
            writer.ln("-target $jvmTarget")
        }

        if (dontobfuscate.orNull == true) {
            writer.ln("-dontobfuscate")
        }

        if (dontoptimize.orNull == true) {
            writer.ln("-dontoptimize")
        }

        writer.ln("-printmapping '${getMappingFile(reportsDir)}'")
        writer.ln("-printseeds '${getSeedFile(reportsDir)}'")
        writer.ln("-printusage '${getUsageFile(reportsDir)}'")
        writer.ln("-printconfiguration '${getFinalConfigFile(reportsDir)}'")

        // TODO: Debugging
        //  -addconfigurationdebugging
        //  -dump
        //  -verbose

        mainClass.orNull?.let { mainClass ->
            writer.ln(
                """
                -keep public class $mainClass {
                    public static void main(java.lang.String[]);
                }
                """.trimIndent(),
            )
        }

        val includeFiles = sequenceOf(
            jarsConfigurationFile,
            defaultRulesFile.ioFile,
        ) + configurationFiles.files.asSequence()

        for (configFile in includeFiles.filterNotNull()) {
            writer.ln("-include '${configFile.normalizedPath()}'")
        }
    }

    // FIXME: Move mapping to the output dir?
    private fun getMappingFile(reportsDir: Directory): String =
        reportsDir.file("mapping.txt").asFile.normalizedPath()

    private fun getSeedFile(reportsDir: Directory): String =
        reportsDir.file("seeds.txt").asFile.normalizedPath()

    private fun getUsageFile(reportsDir: Directory): String =
        reportsDir.file("usage.txt").asFile.normalizedPath()

    private fun getFinalConfigFile(reportsDir: Directory): String =
        reportsDir.file("final-config.pro").asFile.normalizedPath()

    @Suppress("LongParameterList", "NestedBlockDepth")
    private fun writeJarsConfigurationFile(
        file: File,
        inputToOutputJars: LinkedHashMap<File, File?>,
        libraryJarsFilter: String,
        jmods: Sequence<File>,
        javaHome: String?,
        shrinker: Shrinker,
    ) = file.bufferedWriter().use { writer ->
        val outJars = mutableListOf<String>()

        for ((input, output) in inputToOutputJars.entries) {
            val inputPath = input.normalizedPath()
            if (output == null) {
                writer.ln("-libraryjars '$inputPath'$libraryJarsFilter")
            } else {
                writer.ln("-injars '$inputPath'")

                val outJar = output.normalizedPath()
                outJars += outJar

                // R8 does not support -outjars
                if (shrinker == ProGuard) {
                    writer.ln("-outjar '$outJar'")
                }
            }
        }

        if (shrinker == ProGuard) {
            for (jmod in jmods) {
                val ln = "-libraryjars '${jmod.normalizedPath()}'(!**.jar;!module-info.class)"
                writer.ln(ln)
            }
        } else if (javaHome != null) {
            // No jmods passed for R8 here.
            writer.ln("# JDK modules")
            writer.ln("#-libraryjars '$javaHome'")
        }

        outJars
    }

    fun writeDefaultRulesFile(file: File) {
        logger.w("Default rules file does not exist, will create a new one: $file")
        file.parentFile.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.ln(DEFAULT_PROGUARD_RULES)
        }
    }

    private fun Writer.ln(s: String) = appendLine(s)
}

private fun getJmods(javaHome: File) =
    javaHome.resolve("jmods").walk().filter {
        it.isFile && it.path.endsWith("jmod", ignoreCase = true)
    }

internal const val SHRINKER_TASK_PREFIX = "shrinkWith"

// TODO: Move into a separate file, loaded from resources
// language=ShrinkerConfig
private val DEFAULT_PROGUARD_RULES =
    """
    ###
    # ProGuard/R8 rules
    ###

    # Rules for Gradle plugins
    -dontnote javax.annotation.**
    -dontnote javax.inject.**
    -dontnote javax.xml.**
    -dontnote org.jetbrains.annotations.**
    -dontnote org.jetbrains.kotlin.**
    -dontnote org.jetbrains.org.objectweb.asm.**
    -dontnote org.w3c.dom.**
    -dontnote org.xml.sax.**

    -dontnote kotlin.**

    #-ignorewarnings

    -verbose

    -optimizationpasses 7
    -repackageclasses

    # Not safe for Android
    -overloadaggressively
    # Suboptimal for library projects
    -allowaccessmodification
    # Can reduce the performance of the processed code on some JVMs
    -mergeinterfacesaggressively

    # Horizontal class merging increases size of the artifact.
    -optimizations !class/merging/horizontal

    #-whyareyoukeeping class **

    -adaptresourcefilenames    **.properties,**.gif,**.jpg,**.png,**.webp,**.svg,**.ttf,**.otf,**.txt,**.xml
    -adaptresourcefilecontents **.properties,**.MF

    # For library projects.
    # See https://www.guardsquare.com/manual/configuration/examples#library
    -keepparameternames
    -renamesourcefileattribute SourceFile
    -keepattributes Signature,Exceptions,*Annotation*,
                    InnerClasses,PermittedSubclasses,EnclosingMethod,
                    Deprecated,SourceFile,LineNumberTable

    -keep,allowoptimization public class * {
        public protected *;
    }

    -keepclassmembers,allowoptimization enum * {
        public static **[] values();
        public static ** valueOf(java.lang.String);
    }
    """.trimIndent()
