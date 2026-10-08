import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer
import ru.vyarus.gradle.plugin.animalsniffer.signature.BuildSignatureTask
import java.lang.management.ManagementFactory
import java.util.Properties
import com.sun.management.OperatingSystemMXBean

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.build.config)
    alias(libs.plugins.vanniktech.mvn.publish)
    // Task types only, for `checkFloorLinkage`; applied, it would add checks of its own.
    alias(libs.plugins.animalsniffer) apply false
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()
val pluginPortalDescription = "Gradle convention plugin for Kotlin Multiplatform (KMP): " +
    "target-aware setup for Android, JVM, JS, Native, Compose, lint, Detekt, publishing, and CI."
description = pluginPortalDescription
val pluginId = libs.plugins.fluxo.conf.get().pluginId
val pluginDisplayName = "Fluxo KMP Configuration"
val pluginImplementationClass = "fluxo.conf.FluxoKmpConfPlugin"
val pluginPortalTags = listOf(
    "kotlin",
    "kotlin-multiplatform",
    "android",
    "compose",
    "gradle-configuration",
    "convenience",
)

val resDir: Provider<Directory> = layout.buildDirectory
    .dir("generated/sources/fluxo/resources").map { dir ->
        val dirFile = dir.asFile
        dirFile.mkdirs()
        val targetFile = File(dirFile, "fluxo.versions.toml")
        project.file("../gradle/libs.versions.toml").useLines { lines ->
            targetFile.bufferedWriter().use { writer ->
                lines.map { it.substringBefore('#').trim() }
                    .filter { it.isNotEmpty() }
                    .joinTo(writer, separator = "\n")
            }
        }
        logger.lifecycle("   Generated: ${targetFile.relativeTo(layout.projectDirectory.asFile)}")
        dir
    }

fkcSetupGradlePlugin(
    pluginId = pluginId,
    pluginName = "fluxo-kmp-conf",
    pluginClass = pluginImplementationClass,
    displayName = pluginDisplayName,
    tags = pluginPortalTags,
    kotlin = {
        sourceSets.main {
            resources.srcDir(resDir.get())
            // The settings plugin's tool injector, run here for root-only setups.
            kotlin.srcDir("../fluxo-kmp-conf-settings/src/shared/kotlin")
        }
    },
) {
    githubProject = "fluxo-kt/fluxo-kmp-conf"
    enablePublication = true
    enableGradleDoctor = true
//    enableSpotless = true
    setupVerification = true
    enableApiValidation = true
    enableGenericAndroidLint = true
    latestSettingsForTests = true
    experimentalLatestCompilation = true
    setupCoroutines = false

    // Check shrinking possibilities with `R8(full)` chain,
    // but don't replace the outgoing jar.
    replaceOutgoingJar = false
    shrink {
        fullMode = true
    }

    publicationConfig {
        developerId = "amal"
        developerName = "Art Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    apiValidation {
        nonPublicMarkers.add("fluxo.annotation.InternalFluxoApi")
    }
}

// Plugin Portal compatibility flags (shown on the plugin page and read by tooling).
// Configuration cache: supported; every compat TestKit fixture runs with it and fails on any problem.
// Isolated Projects: explicitly not yet, because the plugin still reads parent-project extensions.
// plugin-publish is provisioned at runtime by `fkcSetupGradlePlugin`, so its `compatibility`
// DSL classes are not on this script's classpath: reach the extension by name. `withId` orders
// this after plugin-publish applies, which is when it attaches the extension to each declaration.
plugins.withId("com.gradle.plugin-publish") {
    extensions.getByType<GradlePluginDevelopmentExtension>().plugins.configureEach {
        val compatibility = (this as ExtensionAware).extensions.getByName("compatibility")
        val features = compatibility.withGroovyBuilder { getProperty("features") }
        features.withGroovyBuilder {
            @Suppress("UNCHECKED_CAST")
            (getProperty("configurationCache") as Property<Boolean>).set(true)
            @Suppress("UNCHECKED_CAST")
            (getProperty("isolatedProjects") as Property<Boolean>).set(false)
        }
    }
}

// The plugin's own main sources compile with warnings as errors on every machine, not only on CI.
// Upstream deprecations (KGP, AGP, Gradle) are the early notice of a removal that would otherwise
// reach consumers as a NoSuchMethodError. An API kept on purpose carries a reasoned
// `@Suppress("DEPRECATION")` at its call site instead.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    compilerOptions.allWarningsAsErrors = true
}

// Exclude Kotlin stdlib from the implementation classpath entirely
configurations.implementation {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
}

dependencies {
    // Mirrored with `self/build.gradle.kts` — the region between the
    // MIRROR-START / MIRROR-END markers must stay byte-identical in both files.
    // Verified by the `verifyBuildScriptMirror` task (runs as part of `check`).
    // MIRROR-START
    // Bundled tool plugins are published as `prefers`, not `requires`: a version the consumer
    // declares wins, older or newer (a `requires` replaced any lower one), so fluxo's code meets
    // the consumer's tool version. With none declared, ours is used.
    fun preferred(module: String, v: String) = implementation(module) { version { prefer(v) } }
    fun preferred(p: Provider<MinimalExternalModuleDependency>) =
        p.get().run { preferred("$module", versionConstraint.requiredVersion) }

    implementation(libs.tomlj)
    // Spotless util classes are used internally
    preferred(libs.plugin.spotless)
    // Both Detekt lines are configured with typed code, so both must be on fluxo's classpath:
    // a plugin downloaded at runtime would be invisible to it.
    preferred(libs.plugin.detekt)
    preferred(libs.plugin.detekt2)
    // `kotlin-compiler-embeddable` is `compileOnly` so it stays off the published plugin's
    // runtime classpath. It conflicts with KGP-bundled compiler internals on the consumer's
    // buildscript classpath (KGP since 2.1.0 no longer drags it in transitively, and the
    // synthetic `BuildPerformanceMetrics.add$default` and similar helpers diverge between
    // Kotlin patch versions). When the consumer is on AGP 9 with built-in Kotlin (KGP 2.2.10
    // bundled) plus our pinned 2.2.21, Gradle's `<latest>` resolution picks 2.2.21 for
    // `kotlin-compiler-embeddable` while KGP itself stays at 2.2.10 → `NoSuchMethodError`
    // from `GradleCompilationResults` on `compileDebugKotlin`. Compile-time access is enough
    // because at runtime KGP brings `kotlin-tooling-core` (where `KotlinToolingVersion` lives)
    // and Detekt brings `kotlin-compiler-embeddable` transitively at the consumer-applied
    // version, so reflective callers see a self-consistent classpath.
    compileOnly(libs.kotlin.compiler.embeddable)
    // ASM for bytecode verification.
    implementation(libs.asm)

    implementation(platform(libs.okhttp.bom))

    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    // Only `AndroidVersion` is used; non-transitive keeps Guava off the compile classpath.
    compileOnly(libs.plugin.android.tools.common) { isTransitive = false }
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.ksp)

    compileOnly(libs.plugins.develocity.toModuleDependency())
    compileOnly(libs.plugins.kotlin.sam.receiver.toModuleDependency())
    compileOnly(libs.plugins.kotlinx.binCompatValidator.toModuleDependency())
    compileOnly(libs.plugins.vanniktech.mvn.publish.toModuleDependency())
    compileOnly(libs.plugins.fluxo.bcv.js.toModuleDependency())
    // MIRROR-END


    testCompileOnly(libs.jetbrains.annotation)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.proguard.plugin)
    testImplementation(libs.proguard.core)
    testImplementation(libs.r8)
    testImplementation(kotlin("test", libs.versions.kotlin.asProvider().get()))
}

tasks.test {
    useJUnitPlatform()
}

val publishPluginToLocalDevTasks = tasks.matching {
    it.name == "publishAllPublicationsToLocalDevRepository"
}
val compatibilityLocalMavenRepo = layout.buildDirectory.dir("compatibility/local-maven")

plugins.withId("maven-publish") {
    extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
        repositories.withType<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
            .matching { it.name == "localDev" }
            .configureEach {
                url = uri(compatibilityLocalMavenRepo)
            }
    }
}

testing {
    suites {
        val compatibilityTest = register<org.gradle.api.plugins.jvm.JvmTestSuite>("compatibilityTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(gradleTestKit())
            }
            targets.configureEach {
                testTask.configure {
                    dependsOn(publishPluginToLocalDevTasks)
                    dependsOn(":fluxo-kmp-conf-settings:publishAllPublicationsToLocalDevRepository")
                    shouldRunAfter(tasks.test)
                    systemProperty("fluxo.repo.root", rootDir.absolutePath)
                    // Read by the tests at run time, so an edit to it alone must rerun the suite.
                    inputs.file(rootProject.file("compat/matrix.tsv"))
                        .withPropertyName("compatRuntimeFiles")
                        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
                    // The newest-upstream versions the weekly run passes (`withNewest`); an input,
                    // so an up-to-date or cached result never stands in for that run.
                    inputs.property(
                        "compatNewest",
                        providers.environmentVariable("FLUXO_COMPAT_NEWEST").orElse(""),
                    )
                    systemProperty(
                        "fluxo.local.maven.repo",
                        compatibilityLocalMavenRepo.get().asFile.absolutePath,
                    )
                    systemProperty("fluxo.plugin.id", pluginId)
                    systemProperty("fluxo.plugin.version", version.toString())
                    // `<k>/<n>`: CI runs the suite as n legs (`selectedRows`). A system property is
                    // a task input, so shards never share an up-to-date or cached result.
                    providers.gradleProperty("compatShard").orNull?.let {
                        systemProperty("fluxo.compat.shard", it)
                    }
                    // `-PcompatCase=<text>`: run only the cases whose name contains it.
                    providers.gradleProperty("compatCase").orNull?.let {
                        systemProperty("fluxo.compat.case", it)
                    }
                    // Every fixture builds in its own row-keyed directory, so classes, methods and
                    // rows all run concurrently. Each running fixture is a Gradle daemon of up to
                    // 3 GB (`compatRunner`'s heap + Metaspace) plus its Kotlin daemon, so the pool
                    // is capped by memory (5 GB each) as well as CPUs: a 16 GB CI runner gets 3,
                    // never swapping. `max-pool-size` makes the cap hard; JUnit's default lets
                    // blocked threads be replaced, beyond `parallelism`.
                    val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
                    val parallelism = minOf(
                        Runtime.getRuntime().availableProcessors(),
                        (os.totalMemorySize / (5L shl 30)).toInt(),
                    ).coerceAtLeast(1)
                    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
                    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
                    systemProperty(
                        "junit.jupiter.execution.parallel.config.fixed.parallelism",
                        parallelism,
                    )
                    systemProperty(
                        "junit.jupiter.execution.parallel.config.fixed.max-pool-size",
                        parallelism,
                    )
                    // Under `.gradle/` so it survives `clean`; see `compatGradleUserHome`.
                    val testKitHome = rootDir.resolve(".gradle/compat-testkit")
                    systemProperty("fluxo.compat.gradle.home", testKitHome.absolutePath)
                    // Gradle's own cleanup bounds the caches and distributions of this home
                    // (versions unused for 30 days go), but deletes daemon logs only under
                    // `daemon/`: TestKit daemons log to `test-kit-daemon/` and leave worker
                    // classpath files in `.tmp/`, which grow with every run. No fixture
                    // daemon outlives the test JVM, so a day's files are enough for debugging.
                    // Only those two kinds: `.tmp/.cache` is the Kotlin compiler's klib
                    // expansion cache, which a partial delete would corrupt.
                    // Each plugin rebuild makes new artifact-transform keys in this home, so with
                    // Gradle's default retention (7 days for created resources) it grew by tens
                    // of GB in days. Fixtures need only what the current plugin build uses.
                    // Downloads (default 30 days) shrink to a week: a matrix bump strands the
                    // previous toolchain's artifacts, and a re-download costs one run.
                    doFirst {
                        testKitHome.resolve("init.d").apply { mkdirs() }
                            .resolve("cache-retention.gradle").writeText(
                                """
                                beforeSettings { settings ->
                                    settings.caches {
                                        createdResources.removeUnusedEntriesAfterDays = 1
                                        downloadedResources.removeUnusedEntriesAfterDays = 7
                                    }
                                }
                                """.trimIndent(),
                            )
                    }
                    doFirst {
                        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                        val logs = testKitHome.resolve("test-kit-daemon").listFiles().orEmpty()
                            .flatMap { it.listFiles().orEmpty().asList() }
                            .filter { it.name.startsWith("daemon-") && it.name.endsWith(".log") }
                        val workerClasspaths = testKitHome.resolve(".tmp").listFiles().orEmpty()
                            .filter { it.name.startsWith("gradle-worker-classpath") }
                        (logs + workerClasspaths)
                            .filter { it.isFile && it.lastModified() < cutoff }
                            .forEach { it.delete() }
                    }
                    // Fixture projects run to hundreds of MB per run. A failed run keeps its own
                    // for debugging; a green run, and the next run's start, delete them. They are
                    // deleted here, not by JUnit: TestKit daemons hold files in them until the test
                    // JVM exits, which fails deletion on Windows. They live outside this module:
                    // its Android Lint analysis read fixture sources under `build/` while the
                    // suite was rewriting them, and failed `check` on a vanished file.
                    val projects = rootDir.resolve(".gradle/compat-projects")
                    systemProperty("fluxo.compat.projects.dir", projects.absolutePath)
                    doFirst { projects.deleteRecursively() }
                    doLast { projects.deleteRecursively() }
                }
            }
        }
        tasks.named("check") { dependsOn(compatibilityTest) }
    }
}

abstract class VerifyCompatibilityStaticTask : DefaultTask() {

    @get:org.gradle.api.tasks.Input
    abstract val rootDirPath: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.InputFile
    abstract val matrixFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val sourcesFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val unsafeAllowlistFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFiles
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.InputFiles
    abstract val workflowFiles: org.gradle.api.file.ConfigurableFileCollection

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val failures = ArrayList<String>()
        verifyMatrix(failures)
        verifyUnsafePatterns(failures)
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(separator = "\n"))
        }
        logger.lifecycle("Compatibility static checks passed.")
    }

    // `invariantSeparatorsPath`, not `.path`: this value is compared for equality against the
    // forward-slash paths stored in `compat/*.tsv` (notably the unsafe-pattern allowlist). `.path`
    // yields OS-native separators, so on Windows every allowlist comparison silently mismatched.
    private fun File.relativePath(): String =
        relativeTo(File(rootDirPath.get())).invariantSeparatorsPath

    private fun File.readTsvRows(failures: MutableList<String>): List<Map<String, String>> {
        val lines = readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split('\t')
        return lines.drop(1).mapIndexed { index, line ->
            val cells = line.split('\t')
            if (cells.size != header.size) {
                failures += "${relativePath()}:${index + 2}: expected ${header.size} columns, got ${cells.size}"
            }
            header.mapIndexed { i, key -> key to cells.getOrElse(i) { "" } }.toMap()
        }
    }

    private fun requireField(
        row: Map<String, String>,
        field: String,
        path: String,
        failures: MutableList<String>,
    ): String {
        val value = row[field]
        if (value.isNullOrBlank()) {
            failures += "$path: row ${row["id"] ?: "<unknown>"} missing $field"
            return ""
        }
        return value
    }

    private fun verifyMatrix(failures: MutableList<String>) {
        val matrixPath = matrixFile.asFile.get().relativePath()
        val sources = sourcesFile.asFile.get().readTsvRows(failures).map { it["id"] }.toSet()
        val matrix = matrixFile.asFile.get().readTsvRows(failures)
        val duplicateIds = matrix.groupingBy { it["id"].orEmpty() }.eachCount()
            .filterValues { it > 1 }.keys
        if (matrix.isEmpty()) failures += "$matrixPath: matrix is empty"
        duplicateIds.forEach { failures += "$matrixPath: duplicate id $it" }

        val statuses = setOf("declaredSupported", "unsupported")
        matrix.forEach { row ->
            val id = row["id"].orEmpty()
            val status = requireField(row, "status", matrixPath, failures)
            if (status !in statuses) failures += "$matrixPath: $id: unknown status $status"
            requireField(row, "sourceRefs", matrixPath, failures).split(',')
                .filter(String::isNotBlank)
                .forEach { ref ->
                    if (ref !in sources) failures += "$matrixPath: $id: unknown source $ref"
                }
        }
    }

    private fun verifyUnsafePatterns(failures: MutableList<String>) {
        val allowPath = unsafeAllowlistFile.asFile.get().relativePath()
        val allowed = unsafeAllowlistFile.asFile.get().readTsvRows(failures)
            .map { "${it["patternId"]}\t${it["file"]}\t${it["contains"]}" }
            .toSet()
        val seen = HashSet<String>()
        val patterns = mapOf(
            "rawSystemGetenv" to "System.getenv(",
            "rawRuntimeExec" to "Runtime.getRuntime().exec",
            "rawProcessBuilder" to "ProcessBuilder(",
            "runtimeGetRuntime" to "Runtime.getRuntime()",
            "taskGraphWhenReady" to "taskGraph.whenReady",
            "resolvedConfiguration" to "resolvedConfiguration",
        )
        sourceFiles.files.forEach { file ->
            val relative = file.relativePath()
            file.readLines().forEachIndexed { index, line ->
                patterns.forEach { (patternId, token) ->
                    if (token !in line) return@forEach
                    val match = allowed.firstOrNull { entry ->
                        val parts = entry.split('\t')
                        parts.size == 3 && parts[0] == patternId &&
                            parts[1] == relative && parts[2] in line
                    }
                    if (match == null) failures += "$relative:${index + 1}: unallowlisted $patternId"
                    else seen += match
                }
            }
        }

        val actionPin = Regex("""uses:\s*[^@\s]+@[0-9a-fA-F]{40}(?:\s|$)""")
        workflowFiles.files.forEach { file ->
            val relative = file.relativePath()
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("uses: ") || trimmed.startsWith("uses: ./")) return@forEachIndexed
                if ("anthropics/claude-code-action" in trimmed) return@forEachIndexed
                if (!actionPin.containsMatchIn(trimmed)) {
                    failures += "$relative:${index + 1}: workflow action is not SHA-pinned"
                }
            }
        }

        (allowed - seen).forEach { failures += "$allowPath: stale allowlist entry $it" }
    }
}

abstract class VerifyPluginPortalMetadataTask : DefaultTask() {

    @get:org.gradle.api.tasks.Input
    abstract val pluginId: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val pluginName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val pluginDisplayName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val pluginDescription: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val implementationClass: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val website: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val vcsUrl: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val tags: org.gradle.api.provider.ListProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualPluginId: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualPluginDisplayName: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualPluginDescription: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualImplementationClass: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualWebsite: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualVcsUrl: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualTags: org.gradle.api.provider.ListProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val failures = ArrayList<String>()
        fun checkValue(label: String, actual: String?, expected: String) {
            if (actual != expected) {
                failures += "$label mismatch: expected `$expected`, got `${actual ?: "<null>"}`"
            }
        }
        checkValue("website", actualWebsite.orNull, website.get())
        checkValue("vcsUrl", actualVcsUrl.orNull, vcsUrl.get())
        checkValue("plugin id", actualPluginId.orNull, pluginId.get())
        checkValue("displayName", actualPluginDisplayName.orNull, pluginDisplayName.get())
        checkValue("description", actualPluginDescription.orNull, pluginDescription.get())
        checkValue("implementationClass", actualImplementationClass.orNull, implementationClass.get())
        val resolvedActualTags = actualTags.get()
        val expectedTags = tags.get()
        if (resolvedActualTags != expectedTags) {
            failures += "tags mismatch: expected `$expectedTags`, got `$resolvedActualTags`"
        }

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(separator = "\n"))
        }
        logger.lifecycle("Plugin Portal metadata checks passed.")
    }
}

buildConfig {
    // MIRROR-START
    className("BuildConstants")
    packageName("fluxo.conf.data")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")

    fun buildConfigField(
        name: String,
        p: Provider<PluginDependency>,
        alias: String? = null,
        alias2: String? = null,
        implementation: Boolean = false,
    ) {
        val aliasName = alias ?: name.lowercase().replace('_', '-')
        buildConfigField("String", "${name}_PLUGIN_ALIAS", "\"$aliasName\"")

        alias2?.let {
            buildConfigField("String", "${name}_PLUGIN_ALIAS2", "\"$it\"")
        }

        val pd = p.get()
        val pluginId = pd.pluginId
        buildConfigField("String", "${name}_PLUGIN_ID", "\"$pluginId\"")

        "${pd.version}".ifBlank { null }?.let { version ->
            buildConfigField("String", "${name}_PLUGIN_VERSION", "\"$version\"")
        }

        p.get().toModuleDependency().let { dependency ->
            when {
                implementation -> dependencies.implementation(dependency.substringBeforeLast(':')) {
                    version { prefer(dependency.substringAfterLast(':')) }
                }
                else -> dependencies.compileOnly(dependency)
            }
        }
    }

    buildConfigField("DOKKA", libs.plugins.dokka)
    buildConfigField("GRADLE_PLUGIN_PUBLISH", libs.plugins.gradle.plugin.publish)
    buildConfigField("DEPS_VERSIONS", libs.plugins.deps.versions, implementation = true)
    buildConfigField("DEPS_ANALYSIS", libs.plugins.deps.analysis)
    buildConfigField("FLUXO_BCV_JS", libs.plugins.fluxo.bcv.js)
    buildConfigField("DEPS_GUARD", libs.plugins.deps.guard, implementation = true)
    buildConfigField("TASK_TREE", libs.plugins.task.tree)
    buildConfigField("TASK_INFO", libs.plugins.task.info)
    buildConfigField("MODULE_DEPENDENCY_GRAPH", libs.plugins.module.dependency.graph)
    buildConfigField("BUILD_CONFIG", libs.plugins.build.config)
    // KSP has no plugin alias in the catalog, only its version.
    buildConfigField("String", "KSP_PLUGIN_VERSION", "\"${libs.versions.ksp.get()}\"")

    fun buildConfigField(
        fieldName: String,
        p: Provider<MinimalExternalModuleDependency>,
        compileOnly: Boolean = true,
    ) {
        p.get().apply {
            buildConfigField("String", fieldName, "\"$group:$name:$version\"")
        }
        if (compileOnly) {
            dependencies.compileOnly(p)
        }
    }
    buildConfigField("PROGUARD_PLUGIN", libs.proguard.plugin)
    buildConfigField("PROGUARD_CORE", libs.proguard.core)
    buildConfigField("KOTLINX_METADATA_JVM", libs.kotlin.metadata.jvm)
    buildConfigField("R8", libs.r8)
    // MIRROR-END
}

// `fluxo-kmp-conf` and `self` share most of their build configuration via mirrored
// `dependencies {}` and `buildConfig {}` blocks (the `self` module uses the plugin's
// own sources via `kotlin.srcDir(...)` so it must declare the same classpath).
// `kotlin.srcDir` can't carry build-script-level config; mirroring is the
// least invasive option, and this task structurally replaces "discipline only"
// with a CI-enforced byte-identity invariant on the marked regions.

val verifyCompatibilityStatic = tasks.register<VerifyCompatibilityStaticTask>("verifyCompatibilityStatic") {
    group = "verification"
    description = "Run cheap compatibility-model and static-drift checks."
    rootDirPath.set(rootDir.absolutePath)
    matrixFile.set(rootProject.file("compat/matrix.tsv"))
    sourcesFile.set(rootProject.file("compat/sources.tsv"))
    unsafeAllowlistFile.set(rootProject.file("compat/unsafe-pattern-allowlist.tsv"))
    sourceFiles.from(rootProject.fileTree("fluxo-kmp-conf/src/main/kotlin") { include("**/*.kt") })
    workflowFiles.from(rootProject.fileTree(".github/workflows") { include("*.yml", "*.yaml") })
    outputs.upToDateWhen { true }
}

val expectedPluginId = pluginId
val expectedPluginWebsite = "https://github.com/fluxo-kt/fluxo-kmp-conf"
val expectedPluginDisplayName = pluginDisplayName
val expectedPluginDescription = pluginPortalDescription
val expectedPluginImplementationClass = pluginImplementationClass
val expectedPluginPortalTags = pluginPortalTags
val pluginDevelopment = extensions.getByType(org.gradle.plugin.devel.GradlePluginDevelopmentExtension::class.java)
val pluginDeclaration = pluginDevelopment.plugins.named("fluxo-kmp-conf")
val verifyPluginPortalMetadata = tasks.register<VerifyPluginPortalMetadataTask>(
    "verifyPluginPortalMetadata",
) {
    group = "verification"
    description = "Verify the effective Gradle Plugin Portal metadata before publication."
    this.pluginId.set(expectedPluginId)
    this.pluginName.set("fluxo-kmp-conf")
    this.pluginDisplayName.set(expectedPluginDisplayName)
    this.pluginDescription.set(expectedPluginDescription)
    this.implementationClass.set(expectedPluginImplementationClass)
    this.website.set(expectedPluginWebsite)
    this.vcsUrl.set(project.provider { "$expectedPluginWebsite/tree/v${project.version}" })
    this.tags.set(expectedPluginPortalTags)
    this.actualWebsite.set(pluginDevelopment.website)
    this.actualVcsUrl.set(pluginDevelopment.vcsUrl)
    this.actualPluginId.set(pluginDeclaration.map { it.id })
    this.actualPluginDisplayName.set(pluginDeclaration.map { it.displayName })
    this.actualPluginDescription.set(pluginDeclaration.map { it.description })
    this.actualImplementationClass.set(pluginDeclaration.map { it.implementationClass })
    this.actualTags.set(pluginDeclaration.flatMap { it.tags })
    // Metadata drift is cheap to check and expensive to discover after publication.
    // Keep this release gate active instead of relying on prior up-to-date state.
    outputs.upToDateWhen { false }
}

val verifyBuildScriptMirror = tasks.register("verifyBuildScriptMirror") {
    group = "verification"
    description =
        "Verify that `// MIRROR-START` / `// MIRROR-END` regions in " +
        "`fluxo-kmp-conf/build.gradle.kts` and `self/build.gradle.kts` are byte-identical."
    val pluginScript = layout.projectDirectory.file("build.gradle.kts").asFile
    val selfScript = layout.projectDirectory.file("../self/build.gradle.kts").asFile
    inputs.files(pluginScript, selfScript)
    // No real output — declare up-to-date when inputs unchanged so the task
    // skips on cached `check` runs. Without this, a verification-only task
    // re-runs every build because Gradle has no outputs to compare against.
    outputs.upToDateWhen { true }
    doLast {
        val markerRegex = Regex(
            """// MIRROR-START\s*\n(.*?)\s*// MIRROR-END""",
            RegexOption.DOT_MATCHES_ALL,
        )
        fun regionsOf(file: File): List<String> {
            val regions = markerRegex.findAll(file.readText())
                .map { it.groupValues[1] }
                .toList()
            check(regions.isNotEmpty()) {
                "No `// MIRROR-START` / `// MIRROR-END` marker pair found in ${file.name}"
            }
            return regions
        }
        val pluginRegions = regionsOf(pluginScript)
        val selfRegions = regionsOf(selfScript)
        check(pluginRegions.size == selfRegions.size) {
            "Mirror region count mismatch: " +
                "fluxo-kmp-conf=${pluginRegions.size}, self=${selfRegions.size}"
        }
        pluginRegions.zip(selfRegions).forEachIndexed { i, (a, b) ->
            check(a == b) {
                buildString {
                    appendLine("Mirror drift detected in region #${i + 1}.")
                    appendLine("Update both blocks in lockstep — they must remain byte-identical.")
                    appendLine()
                    appendLine("--- fluxo-kmp-conf/build.gradle.kts ---")
                    appendLine(a)
                    appendLine("--- self/build.gradle.kts ---")
                    appendLine(b)
                }
            }
        }
    }
}

// region Oldest-version linkage check
// The plugin compiles against the newest Kotlin Gradle plugin, AGP and Gradle but runs on the
// consumer's, down to `compat/linkage-floor.properties`. A call into API those versions lack
// compiles and passes every test on newer versions, then fails only for an old consumer, and only
// on the path that makes the call. This check reads every compiled class instead: references
// into Kotlin Gradle plugin, AGP or Gradle API must exist in the oldest versions, unless the code
// is marked `@VersionGated` (reached only where the API exists).
val linkageFloor = Properties().apply {
    rootProject.file("compat/linkage-floor.properties").reader().use { load(it) }
}
val linkageTool: Configuration = configurations.create("linkageTool") {
    isCanBeConsumed = false
}
val linkageJdkSignature: Configuration = configurations.create("linkageJdkSignature") {
    isCanBeConsumed = false
}
val linkageGradleDistribution: Configuration = configurations.create("linkageGradleDistribution") {
    isCanBeConsumed = false
}
val linkageFloorJars: Configuration = configurations.create("linkageFloorJars") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements.JAR),
        )
        attribute(
            GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
            objects.named(linkageFloor.getProperty("gradle")),
        )
    }
    // The Kotlin standard library is the one Gradle bundles, taken from its distribution below.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}
// Floor versions stay out of the version catalog so dependency updates never move them.
@Suppress("UseTomlInstead")
dependencies {
    linkageTool(libs.animal.sniffer)
    // The JDK 8 API, only so inherited members (`Collection.add`, `Enum.ordinal`) resolve; JDK
    // references themselves are not checked (`ignoreClasses`).
    linkageJdkSignature("org.codehaus.mojo.signature:java18:1.0@signature")
    linkageGradleDistribution("gradle:gradle:${linkageFloor.getProperty("gradle")}@zip")
    val kotlin = linkageFloor.getProperty("kotlin")
    linkageFloorJars("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
    linkageFloorJars("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin")
    linkageFloorJars("org.jetbrains.kotlin:kotlin-sam-with-receiver:$kotlin")
    linkageFloorJars("com.android.tools.build:gradle:${linkageFloor.getProperty("agp")}")
}

val unpackFloorGradleApi = tasks.register<Sync>("unpackFloorGradleApi") {
    description = "Extracts the oldest supported Gradle's API jars for checkFloorLinkage."
    from(zipTree(linkageGradleDistribution.elements.map { it.single().asFile }))
    // Gradle's own jars and its Kotlin standard library; its embedded Kotlin compiler would
    // clash with the Kotlin Gradle plugin's copy of the same classes.
    include(
        "*/lib/gradle-*.jar",
        "*/lib/plugins/gradle-*.jar",
        "*/lib/groovy-4*.jar",
        "*/lib/groovy-json-*.jar",
        "*/lib/kotlin-stdlib-*.jar",
        "*/lib/kotlin-reflect-*.jar",
    )
    eachFile { path = name }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("linkage-gradle-api"))
}

val buildFloorSignature = tasks.register<BuildSignatureTask>("buildFloorSignature") {
    description = "Builds the API signature of the oldest supported Kotlin, AGP and Gradle."
    animalsnifferClasspath = linkageTool
    // Jars that re-ship JDK 8 `javax` packages clash with the JDK signature.
    val jdkDuplicates = Regex(
        "^(jakarta\\.xml\\.bind-api|jakarta\\.activation-api|javax\\.activation|" +
            "javax\\.annotation-api|xml-apis)-",
    )
    files(linkageFloorJars.filter { !jdkDuplicates.containsMatchIn(it.name) })
    files(unpackFloorGradleApi)
    signatures(linkageJdkSignature)
    outputDirectory = layout.buildDirectory.dir("linkage").get().asFile
    outputName = "floor"
}

// Everything else on the compile classpath is trusted as is: the tools fluxo bundles run at the
// version it ships. Gradle's API and Kotlin library come in as files, not modules, so the module
// filter drops them along with what the floor replaces.
val linkageTrustedClasspath = configurations.compileClasspath.flatMap { compile ->
    compile.incoming.artifacts.resolvedArtifacts.zip(
        linkageFloorJars.incoming.artifacts.resolvedArtifacts,
    ) { artifacts, floor ->
        val replaced = floor.mapNotNullTo(HashSet()) {
            (it.id.componentIdentifier as? ModuleComponentIdentifier)?.moduleIdentifier
        }
        artifacts.filter {
            val id = it.id.componentIdentifier as? ModuleComponentIdentifier
            id != null && id.moduleIdentifier !in replaced &&
                !(id.group == "org.jetbrains.kotlin" && id.module.startsWith("kotlin-stdlib"))
        }.map { it.file }
    }
}

val checkFloorLinkage = tasks.register<AnimalSniffer>("checkFloorLinkage") {
    group = "verification"
    description = "Fails on calls into Kotlin Gradle plugin, AGP or Gradle API that the " +
        "oldest supported versions lack, outside code marked @VersionGated."
    animalsnifferClasspath = linkageTool
    animalsnifferSignatures = files(buildFloorSignature.map { it.outputFiles })
    source = sourceSets.main.get().output.classesDirs.asFileTree
    sourcesDirs = files(sourceSets.main.get().kotlin.srcDirs)
    classpath = files(linkageTrustedClasspath)
    annotation = "fluxo.annotation.VersionGated"
    ignoreClasses = listOf("java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*", "org.w3c.*")
        .plus("org.xml.*")
    reports.text.required = true
    reports.text.outputLocation = layout.buildDirectory.file("reports/linkage/floor.txt")
    reports.csv.outputLocation = layout.buildDirectory.file("reports/linkage/floor.csv")
}
// endregion

tasks.named("check") {
    dependsOn(
        checkFloorLinkage,
        verifyBuildScriptMirror,
        verifyCompatibilityStatic,
        verifyPluginPortalMetadata,
    )
}
