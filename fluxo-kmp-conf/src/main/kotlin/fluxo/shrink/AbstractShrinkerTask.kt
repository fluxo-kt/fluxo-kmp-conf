@file:Suppress(
    "KDocUnresolvedReference",
    "LeakingThis",
    "LongParameterList",
    "LongParameterList",
    "NestedBlockDepth",
    "TooManyFunctions",
)

package fluxo.shrink

import fluxo.artifact.dsl.ProcessorCallType
import fluxo.artifact.proc.JvmShrinker
import fluxo.artifact.proc.JvmShrinker.ProGuard
import fluxo.artifact.proc.JvmShrinker.R8
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.e
import fluxo.conf.impl.i
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.jvmToolFile
import fluxo.conf.impl.l
import fluxo.conf.impl.lc
import fluxo.conf.impl.w
import fluxo.external.AbstractExternalFluxoTask
import fluxo.external.ExternalToolRunner
import fluxo.gradle.cliArg
import fluxo.gradle.ioFile
import fluxo.gradle.ioFileOrNull
import fluxo.gradle.listProperty
import fluxo.gradle.mkdirs
import fluxo.gradle.normalizedPath
import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import fluxo.util.readableByteSize
import isRelease
import java.io.File
import java.io.Writer
import java.lang.System.currentTimeMillis
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
 * @see fluxo.shrink.ShrinkerKeepRulesBySeedsTest
 *
 * @see org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
 * @see com.android.build.gradle.internal.tasks.ProguardConfigurableTask
 * @see com.android.build.gradle.internal.tasks.R8Task
 * @see proguard.gradle.ProGuardTask
 */
@CacheableTask
internal abstract class AbstractShrinkerTask : AbstractExternalFluxoTask() {

    @get:Input
    val shrinker: Property<JvmShrinker> = objects.notNullProperty()

    @get:Optional
    @get:Input
    val toolName: Property<String> = objects.notNullProperty(shrinker.map { it.name.lc() })

    @get:Optional
    @get:Input
    val chainForLog: Property<String?> = objects.nullableProperty()

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
    val dontoptimize: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Input
    val dontobfuscate: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Input
    val obfuscateIncrementally: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Input
    val r8FulMode: Property<Boolean?> = objects.nullableProperty()

    private val r8Compat
        get() = r8FulMode.orNull != true

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val defaultRulesFile: RegularFileProperty = objects.fileProperty()

    /**
     * ProGuard/R8 mapping file from the previous obfuscation step.
     * Required for [incremental obfuscation][obfuscateIncrementally].
     *
     * @see obfuscateIncrementally
     */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val applyMapping: RegularFileProperty = objects.fileProperty()

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
    val mainClasses: ListProperty<String> = objects.listProperty()

    @get:Input
    @get:Optional
    val gradlePluginClasses: ListProperty<String> = objects.listProperty()

    @get:Input
    @get:Optional
    val jvmTarget: Property<String?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    val androidMinSdk: Property<Int?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    val callFallbackOrder: ListProperty<ProcessorCallType> =
        objects.listProperty<ProcessorCallType>()
            .convention(ProcessorCallType.DEFAULT_FALLBACK_ORDER)

    @get:Internal
    val maxHeapSize: Property<String?> = objects.nullableProperty()

    @get:Internal
    val vmOptions: Property<Boolean> = objects.notNullProperty(true)

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = objects.directoryProperty()

    /** Primary destination file */
    @get:LocalState
    val destinationFile: Provider<RegularFile> = destinationDir.zip(mainJar) { dir, jar ->
        dir.file(jar.asFile.name)
    }

    private val workDir: DirectoryProperty

    @get:LocalState
    protected val workingTmpDir: Provider<Directory>

    @get:LocalState
    protected val reportsDir: Provider<Directory>

    @get:LocalState
    val mappingFile: Provider<RegularFile>

    private val isRelease: Provider<Boolean>

    private val projectLogPath: String


    init {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Shrink and optimize final JVM artifact"

        val p = project
        val layout = p.layout
        val buildDir = layout.buildDirectory
        val toolName = toolName
        workDir = buildDir
        workingTmpDir = buildDir.zip(toolName) { d, tool ->
            d.dir("tmp/shrink/$tool")
        }
        reportsDir = buildDir.zip(toolName) { d, tool ->
            d.dir("reports/shrink/$tool")
        }
        logsDir.set(
            buildDir.zip(toolName) { d, tool ->
                d.dir("logs/shrink/$tool")
            },
        )
        destinationDir.set(
            buildDir.zip(toolName) { d, tool ->
                d.dir("output/shrink/$tool")
            },
        )

        mappingFile = destinationDir.file("mapping.txt")

        val projectDirectory = layout.projectDirectory
        defaultRulesFile.set(projectDirectory.file("pg/rules.pro"))

        isRelease = p.isRelease()
        projectLogPath = if (p.isRootProject) p.name else p.path
    }

    @TaskAction
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    operator fun invoke() {
        val javaHome = File(javaHome.get())
        val destinationDir = destinationDir.ioFile.absoluteFile
        val workingDir = workingTmpDir.get()
        val reportsDir = reportsDir.get()
        fileOperations.mkdirs(destinationDir, workingDir.asFile, reportsDir.asFile)

        // FIXME: When java toolchain used or JDK target is specified, read the specified JDK.
        // TODO: Can be cached for a JDK.

        val shrinker = shrinker.get()
        val jmods = when (shrinker) {
            ProGuard -> getJmods(javaHome)
            else -> {
                // R8 don't need jmods
                val r8Mode = if (r8Compat) "compat " else "full-"
                logger.l("R8 in ${r8Mode}mode")
                null
            }
        }

        // For a final application, we need to process all jars,
        // but for a library, only the main jar.
        val onlyMainJar = !processApplication.get()
        val inputToOutputJars = LinkedHashMap<File, File?>()
        var initialSize: Long = 0

        // Avoid mangling mainJar
        mainJar.ioFile.let { mainJar ->
            inputToOutputJars[mainJar] = destinationFile.ioFile
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

        val rootConfigFile = workingDir.file("root-config.pro").asFile
        writeRootConfiguration(rootConfigFile, reportsDir, jarsConfFile, shrinker)

        // By default,
        // 1. Try to call as a separate process first.
        // 2. Try to use the bundled ProGuard/R8.
        // 3. Fallback to custom classloader.
        val callFallbackOrder = callFallbackOrder.get()
            .ifEmpty { ProcessorCallType.DEFAULT_FALLBACK_ORDER }
            .toCollection(LinkedHashSet())

        // TODO: Process output and print only main information if not verbose
        var called = false
        var ex: Throwable? = null
        var caller: ShrinkerReflectiveCaller? = null
        val last = callFallbackOrder.last()!!
        val start = currentTimeMillis()
        callType@ for (callType in callFallbackOrder) {
            try {
                when (callType) {
                    ProcessorCallType.EXTERNAL -> {
                        logger.l("Calling $shrinker externally!")
                        callShrikerExternally(javaHome, rootConfigFile, outJars, reportsDir)
                    }

                    else -> {
                        if (caller == null) {
                            caller = ShrinkerReflectiveCaller(
                                shrinker = shrinker,
                                logger = logger,
                                toolJars = toolJars,
                            ) { getShrinkerArgs(rootConfigFile, outJars, reportsDir, javaHome) }
                        }
                        val isLastFallback = last == callType
                        if (!caller.execute(callType, ignoreMemoryLimit = isLastFallback)) {
                            continue@callType
                        }
                    }
                }
                called = true
                break@callType
            } catch (e: Throwable) {
                logger.e("$callType $shrinker shrinker call failed: $e")
                when (ex) {
                    null -> ex = e
                    else -> e.addSuppressed(ex)
                }
            }
        }
        if (!called) {
            val message = "All $shrinker call types failed ($callFallbackOrder) for $path!"
            throw GradleException(message, ex)
        }

        @Suppress("MagicNumber")
        val elapsedSec = (currentTimeMillis() - start) / 1_000f
        reportSavings(initialSize, elapsedSec)
    }

    private fun callShrikerExternally(
        javaHome: File,
        rootConfigFile: File?,
        outJars: MutableList<File>,
        reportsDir: Directory,
    ) {
        val workDir = workDir.ioFileOrNull
        val args = getShrinkerArgs(rootConfigFile, outJars, reportsDir, javaHome, workDir)
        val javaBinary = jvmToolFile(toolName = "java", javaHome = javaHome)
        runExternalTool(
            tool = javaBinary,
            args = args,
            workingDir = workDir,
            logToConsole = ExternalToolRunner.LogToConsole.OnlyWhenVerbose,
        ).assertNormalExitValue()
    }

    private fun reportSavings(initialSize: Long, elapsedSec: Float) {
        val files = arrayOf(destinationFile.ioFile)
        val finalSize = files.sumOf { it.length() }
        val isFailed = initialSize < finalSize
        val message = buildString(capacity = 128) {
            append(shrinker.get()).append(" (")

            val chainForLog = chainForLog.get()
            when {
                chainForLog.isNullOrBlank() -> append(path)
                else -> append(projectLogPath).append(" ").append(chainForLog)
            }.append(") ")

            when {
                !isFailed -> append("shrinker results: ")
                else -> append("shrinker failed to save size: ")
            }

            val initial = readableByteSize(initialSize)
            val final = readableByteSize(finalSize)
            append(initial).append(" -> ").append(final)

            append(" (")
            if (!isFailed) {
                @Suppress("MagicNumber")
                val savedPercent = (1f - (finalSize / initialSize.toFloat())) * 100f
                val savedBytes = readableByteSize(initialSize - finalSize)
                append("saved ").append("%.03f".format(savedPercent))
                append("%, ").append(savedBytes)
            } else {
                val addedBytes = readableByteSize(finalSize - initialSize)
                append("INCREASED ").append(addedBytes)
            }

            append(") in ").append(elapsedSec).append(" s")
        }
        if (isFailed) {
            // Info ("i: ") messages are highlighted in the IDEA console.
            if (SHOW_DEBUG_LOGS) {
                logger.i(message)
            }
            if (isRelease.orNull == true) {
                throw GradleException(message)
            }
            logger.e(message)
        } else {
            logger.l(message)
        }
    }

    private fun getShrinkerArgs(
        rootConfigurationFile: File?,
        outJars: List<File>,
        reportsDir: Directory,
        javaHome: File,
        workDir: File? = null,
        external: Boolean = true,
    ): List<String> {
        return arrayListOf<String>().apply {
            if (external) {
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
            }

            when (checkNotNull(shrinker.get())) {
                ProGuard -> {
                    /**
                     * @see proguard.ProGuard
                     * @see proguard.ProGuard.main
                     */
                    add("proguard.ProGuard")
                    cliArg("-include", rootConfigurationFile, base = workDir)
                }

                R8 -> {
                    // R8 is not command line compatible with ProGuard
                    // https://r8.googlesource.com/r8/#running-r8

                    /**
                     * @see com.android.tools.r8.R8
                     * @see com.android.builder.dexing.runR8
                     */
                    add("com.android.tools.r8.R8")
                    add("--release") // (default) vs --debug
                    add("--classfile") // vs --dex (default)
                    cliArg("--lib", javaHome)
                    cliArg("--output", outJars.single(), base = workDir)

                    // Only for DEX output
                    // androidMinSdk.orNull?.let { cliArg("--min-api", it) }

                    cliArg("--pg-conf", rootConfigurationFile, base = workDir)

                    // Output the collective configuration to <file>.
                    cliArg("--pg-conf-output", getFinalConfigFile(reportsDir, base = workDir))
                    cliArg("--pg-map-output", getMappingFile(base = workDir))

                    // Force disable minification of names.
                    cliArg("--no-minification", dontobfuscate.orNull == true)

                    // Force disable tree shaking of unreachable classes.
                    cliArg("--no-tree-shaking", dontoptimize.orNull == true)

                    // Compile with R8 in Proguard compatibility mode.
                    // Opposite of non-compat mode, also called "full mode".
                    // 'android.enableR8.fullMode' controls it for android builds.
                    // https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#r8-full-mode
                    cliArg("--pg-compat", r8Compat)
                }
            }
        }
    }

    /**
     * @see com.android.build.gradle.internal.tasks.ProguardConfigurableTask
     * @see com.android.build.gradle.internal.tasks.R8Task
     */
    @Suppress("LongMethod")
    private fun writeRootConfiguration(
        file: File,
        reportsDir: Directory,
        jarsConfigurationFile: File,
        shrinker: JvmShrinker,
    ) = file.bufferedWriter().use { writer ->
        if (verbose.get()) {
            writer.ln("-verbose")
        }

        jvmTarget.orNull?.let { jvmTarget ->
            writer.ln("-target $jvmTarget")
        }

        if (dontobfuscate.orNull == true) {
            writer.ln("-dontobfuscate")
        } else if (obfuscateIncrementally.orNull != false) {
            // Incremental obfuscation
            // https://www.guardsquare.com/manual/configuration/usage#applymapping
            // https://r8-docs.preemptive.com/#mapping-files
            val applyMapping = applyMapping.orNull?.asFile
            if (applyMapping != null && applyMapping.exists()) {
                writer.ln("-applymapping '${applyMapping.normalizedPath()}'")
            }
            // Reduce mapping conflicts chances
            writer.ln("-useuniqueclassmembernames")
        }

        if (dontoptimize.orNull == true) {
            writer.ln("-dontoptimize")
        }

        writer.ln("-printseeds '${getSeedFile(reportsDir)}'")
        writer.ln("-printusage '${getUsageFile(reportsDir)}'")
        when (shrinker) {
            ProGuard -> {
                writer.ln("-printmapping '${getMappingFile()}'")
                writer.ln("-printconfiguration '${getFinalConfigFile(reportsDir)}'")
            }

            R8 -> {
                // Passed via arguments for R8
            }
        }

        // TODO: Shared obfuscation dictionaries
        //  https://github.com/Guardsquare/proguard/tree/master/examples/dictionaries
        //  https://github.com/sarikayamehmet/AndroidSecureBlog/blob/f5c0898/DexguardTest/app/dictionary.txt
        //  https://github.com/informationextraction/core-android/blob/095ffae/RCSAndroid/classdictionary.txt
        //  https://github.com/informationextraction/core-android/blob/b05e3ec/RCSAndroid/compact.txt

        // TODO: Debugging
        //  -addconfigurationdebugging
        //  -dump

        // FIXME: Automatic main class detection from jar manifest,
        //  processor configuration,
        //  plugin configuration
        mainClasses.get().ifNotEmpty {
            writer.ln()
            forEach { mainClass ->
                writer.ln(
                    """
                    -keep public class $mainClass {
                        public static void main(java.lang.String[]);
                    }
                    """.trimIndent(),
                )
            }
            writer.ln()
        }
        gradlePluginClasses.get().ifNotEmpty {
            writer.ln()
            forEach { pluginClass ->
                writer.ln(
                    """
                    -keep,includedescriptorclasses public class $pluginClass implements org.gradle.api.Plugin {
                        public <init>();
                        public void apply(java.lang.Object);
                    }
                    """.trimIndent(),
                )
            }
            writer.ln()
        }

        val includeFiles = sequenceOf(
            jarsConfigurationFile,
            defaultRulesFile.ioFile,
        ) + configurationFiles.files.asSequence()

        for (configFile in includeFiles.filterNotNull()) {
            writer.ln("-include '${configFile.normalizedPath()}'")
        }
    }

    private fun getMappingFile(base: File? = null): String =
        mappingFile.ioFile.normalizedPath(base)

    private fun getSeedFile(dir: Directory): String =
        dir.file("seeds.txt").asFile.normalizedPath()

    private fun getUsageFile(dir: Directory): String =
        dir.file("usage.txt").asFile.normalizedPath()

    private fun getFinalConfigFile(
        dir: Directory = this.reportsDir.get(),
        base: File? = null,
    ): String =
        dir.file("final-config.pro").asFile.normalizedPath(base)

    private fun writeJarsConfigurationFile(
        file: File,
        inputToOutputJars: LinkedHashMap<File, File?>,
        libraryJarsFilter: String,
        jmods: Sequence<File>,
        javaHome: String?,
        shrinker: JvmShrinker,
    ) = file.bufferedWriter().use { writer ->
        val outJars = mutableListOf<File>()

        for ((input, output) in inputToOutputJars.entries) {
            val inputPath = input.normalizedPath()
            if (output == null) {
                writer.ln("-libraryjars '$inputPath'$libraryJarsFilter")
            } else {
                writer.ln("-injars '$inputPath'")
                outJars += output

                // R8 does not support -outjars here, passed via argument later.
                if (shrinker == ProGuard) {
                    writer.ln("-outjar '${output.normalizedPath()}'")
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

    private fun Writer.ln() = appendLine()

    private fun Writer.ln(s: String) = appendLine(s)
}

private fun getJmods(javaHome: File): Sequence<File> {
    // TODO: Before Java 9, the runtime classes were packaged in a single jar file.
    //  https://github.com/ArcticLampyrid/gradle-git-version/blob/23ccfc8/build.gradle.kts#L72
    //  https://github.com/Guardsquare/proguard?tab=readme-ov-file#gradle-task

    return javaHome.resolve("jmods").walk().filter {
        it.isFile && it.path.endsWith("jmod", ignoreCase = true)
    }
}

// TODO: Move into a separate file, loaded from resources
// language=Shrinker Config
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
