plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.build.config)
    alias(libs.plugins.vanniktech.mvn.publish)
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()
description =
    "Convenience Gradle plugin for reliable configuration of Kotlin & KMP projects. Made by Fluxo."
val pluginId = libs.plugins.fluxo.conf.get().pluginId

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
    pluginClass = "fluxo.conf.FluxoKmpConfPlugin",
    displayName = "Fluxo KMP Configuration",
    tags = listOf(
        "kotlin",
        "kotlin-multiplatform",
        "android",
        "gradle-configuration",
        "convenience",
    ),
    kotlin = {
        sourceSets.main {
            resources.srcDir(resDir.get())
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
    implementation(libs.tomlj)
    // Spotless util classes are used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)
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
    // `detekt-core` is `compileOnly`: the only direct compile-time use is the
    // `io.github.detekt.tooling.api.BaselineProvider` import in `MergeDetektBaselinesTask`,
    // plus the reflective fallback resolved via `Class.forName`. Consumer builds that apply
    // the Detekt plugin pull detekt-core transitively via detekt-gradle-plugin, so the
    // consumer-facing runtime surface stays self-consistent without us republishing it.
    compileOnly(libs.detekt.core)
    // ASM for bytecode verification.
    implementation(libs.asm)

    implementation(platform(libs.okhttp.bom))

    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
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

    // Test scope re-declares `detekt-core` so the reflective-fallback unit test
    // (`MergeDetektBaselinesReflectiveTest`) keeps it on testRuntimeClasspath even though
    // the plugin scope above carries it as `compileOnly`.
    testImplementation(libs.detekt.core)

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

val pluginUnderTestMetadataTask = tasks.named("pluginUnderTestMetadata")
val publishPluginToLocalDevTask = tasks.named("publishAllPublicationsToLocalDevRepository")
val compatibilityTestKotlinPluginClasspath = configurations.register(
    "compatibilityTestKotlinPluginClasspath",
) {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add("compatibilityTestKotlinPluginClasspath", libs.plugin.kotlin)
}

testing {
    suites {
        val compatibilityTest by registering(org.gradle.api.plugins.jvm.JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(gradleTestKit())
            }
            targets.configureEach {
                testTask.configure {
                    dependsOn(pluginUnderTestMetadataTask)
                    dependsOn(publishPluginToLocalDevTask)
                    classpath += files(pluginUnderTestMetadataTask)
                    shouldRunAfter(tasks.test)
                    systemProperty("fluxo.repo.root", rootDir.absolutePath)
                    systemProperty(
                        "fluxo.local.maven.repo",
                        rootProject.file("_/local-repo").absolutePath,
                    )
                    systemProperty("fluxo.plugin.id", pluginId)
                    systemProperty("fluxo.plugin.version", version.toString())
                    systemProperty(
                        "compat.profile",
                        providers.gradleProperty("compat.profile").orElse("pr").get(),
                    )
                    jvmArgumentProviders.add(object : org.gradle.process.CommandLineArgumentProvider {
                        @get:org.gradle.api.tasks.Classpath
                        val classpath = files(compatibilityTestKotlinPluginClasspath)

                        override fun asArguments(): Iterable<String> =
                            listOf("-Dfluxo.compat.kotlinPluginClasspath=${classpath.asPath}")
                    })
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
    abstract val docClaimsFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val unsafeAllowlistFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val versionCatalogFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val gradleWrapperFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val readmeFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFiles
    abstract val sourceFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.InputFiles
    abstract val workflowFiles: org.gradle.api.file.ConfigurableFileCollection

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val failures = ArrayList<String>()
        verifyMatrix(failures)
        verifyDocs(failures)
        verifyUnsafePatterns(failures)
        verifyReleaseDocs(failures)
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(separator = "\n"))
        }
        logger.lifecycle("Compatibility static checks passed.")
    }

    private fun File.relativePath(): String = relativeTo(File(rootDirPath.get())).path

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

    private fun catalogVersion(catalog: String, name: String, failures: MutableList<String>): String {
        val prefix = "$name = \""
        val value = catalog.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.substringBefore('"')
        if (value == null) failures += "gradle/libs.versions.toml: missing version $name"
        return value.orEmpty()
    }

    private fun wrapperVersion(wrapper: String, failures: MutableList<String>): String {
        val value = wrapper.lineSequence()
            .firstOrNull { "distributionUrl=" in it }
            ?.substringAfter("gradle-")
            ?.substringBefore("-bin.zip")
        if (value.isNullOrBlank()) {
            failures += "gradle/wrapper/gradle-wrapper.properties: missing Gradle version"
        }
        return value.orEmpty()
    }

    private fun verifyMatrix(failures: MutableList<String>) {
        val matrixPath = matrixFile.asFile.get().relativePath()
        val sources = sourcesFile.asFile.get().readTsvRows(failures).map { it["id"] }.toSet()
        val matrix = matrixFile.asFile.get().readTsvRows(failures)
        val duplicateIds = matrix.groupingBy { it["id"].orEmpty() }.eachCount()
            .filterValues { it > 1 }.keys
        if (matrix.isEmpty()) failures += "$matrixPath: matrix is empty"
        duplicateIds.forEach { failures += "$matrixPath: duplicate id $it" }

        val statuses = setOf("buildPin", "declaredSupported", "forwardTested", "unsupported")
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

        val currentRows = matrix.filter { it["id"] == "current-build" }
        if (currentRows.size != 1) {
            failures += "$matrixPath: expected exactly one current-build row, got ${currentRows.size}"
            return
        }

        val catalog = versionCatalogFile.asFile.get().readText()
        val wrapper = gradleWrapperFile.asFile.get().readText()
        val expected = mapOf(
            "gradleVersion" to wrapperVersion(wrapper, failures),
            "kgpVersion" to catalogVersion(catalog, "kotlin", failures),
            "kotlinLangVersion" to catalogVersion(catalog, "kotlinLangVersion", failures),
            "kotlinApiVersion" to catalogVersion(catalog, "kotlinApiVersion", failures),
            "agpVersion" to catalogVersion(catalog, "android-gradle-plugin", failures),
            "composeVersion" to catalogVersion(catalog, "jetbrains-compose", failures),
            "kspVersion" to catalogVersion(catalog, "ksp", failures),
            "detektVersion" to catalogVersion(catalog, "detekt", failures),
            "vanniktechVersion" to catalogVersion(catalog, "vanniktech-mvn-publish", failures),
            "bcvVersion" to catalogVersion(catalog, "bcv", failures),
            "dokkaVersion" to catalogVersion(catalog, "dokka", failures),
        )
        val current = currentRows.single()
        expected.forEach { (field, value) ->
            if (current[field] != value) {
                failures += "$matrixPath: current-build $field=${current[field]} expected $value"
            }
        }
    }

    private fun verifyDocs(failures: MutableList<String>) {
        val claimsPath = docClaimsFile.asFile.get().relativePath()
        val sources = sourcesFile.asFile.get().readTsvRows(failures).map { it["id"] }.toSet()
        docClaimsFile.asFile.get().readTsvRows(failures).forEach { row ->
            val id = row["id"].orEmpty()
            requireField(row, "sourceRefs", claimsPath, failures).split(',')
                .filter(String::isNotBlank)
                .forEach { ref ->
                    if (ref !in sources) failures += "$claimsPath: $id: unknown source $ref"
                }
            val file = File(rootDirPath.get(), requireField(row, "file", claimsPath, failures))
            val claim = requireField(row, "mustContain", claimsPath, failures)
            if (!file.readText().contains(claim)) {
                failures += "$claimsPath: $id: missing claim in ${file.relativePath()}"
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

    private fun verifyReleaseDocs(failures: MutableList<String>) {
        val catalog = versionCatalogFile.asFile.get().readText()
        val readme = readmeFile.asFile.get().readText()
        val snippets = mapOf(
            "plugin version example" to
                """id("io.github.fluxo-kt.fluxo-kmp-conf") version "${
                    catalogVersion(catalog, "version", failures)
                }"""",
            "quick-start Kotlin version" to
                """kotlin("multiplatform") version "${catalogVersion(catalog, "kotlin", failures)}"""",
        )
        snippets.forEach { (name, snippet) ->
            if (snippet !in readme) failures += "README.md: release snippet drift: $name"
        }
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

        p.toModuleDependency().let { dependency ->
            when {
                implementation -> dependencies.implementation(dependency)
                else -> dependencies.compileOnly(dependency)
            }
        }
    }

    buildConfigField("DOKKA", libs.plugins.dokka)
    buildConfigField("GRADLE_PLUGIN_PUBLISH", libs.plugins.gradle.plugin.publish)
    buildConfigField("COMPLETE_KOTLIN", libs.plugins.complete.kotlin)
    buildConfigField("DEPS_VERSIONS", libs.plugins.deps.versions, implementation = true)
    buildConfigField("DEPS_ANALYSIS", libs.plugins.deps.analysis)
    buildConfigField("DEPS_GUARD", libs.plugins.deps.guard, implementation = true)
    buildConfigField("TASK_TREE", libs.plugins.task.tree)
    buildConfigField("TASK_INFO", libs.plugins.task.info)
    buildConfigField("MODULE_DEPENDENCY_GRAPH", libs.plugins.module.dependency.graph)
    buildConfigField("BUILD_CONFIG", libs.plugins.build.config)

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
    docClaimsFile.set(rootProject.file("compat/doc-claims.tsv"))
    unsafeAllowlistFile.set(rootProject.file("compat/unsafe-pattern-allowlist.tsv"))
    versionCatalogFile.set(rootProject.file("gradle/libs.versions.toml"))
    gradleWrapperFile.set(rootProject.file("gradle/wrapper/gradle-wrapper.properties"))
    readmeFile.set(rootProject.file("README.md"))
    sourceFiles.from(rootProject.fileTree("fluxo-kmp-conf/src/main/kotlin") { include("**/*.kt") })
    workflowFiles.from(rootProject.fileTree(".github/workflows") { include("*.yml", "*.yaml") })
    outputs.upToDateWhen { true }
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

tasks.named("check") {
    dependsOn(
        verifyBuildScriptMirror,
        verifyCompatibilityStatic,
    )
}
