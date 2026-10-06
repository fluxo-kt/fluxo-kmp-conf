package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * fluxo bundles Detekt 1, Spotless and gradle-versions on the consumer's build classpath. A
 * version the consumer declares must win, also an older one: published as `requires`, fluxo's
 * version silently replaced the consumer's whenever it was higher. `buildEnvironment` shows a
 * replaced version as `<declared> -> <resolved>`. fluxo's code must then work with those older
 * versions: the tasks that use them are configured (`--dry-run`), which is where it calls them.
 */
internal fun runBundledToolVersionCase(row: Map<String, String>, tempDir: Path) {
    if (row.kgpMinor() < NEWEST_TESTED_KOTLIN) return
    fun case(tasks: List<String>, arguments: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-bundled-tool-versions",
        projectDir = tempDir.resolve(row.getValue("id") + "-bundled-tool-versions"),
        tasks = tasks,
        arguments = arguments,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        assertTasksSucceed = arguments.isEmpty(),
        seedBaseline = false,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row)
                .replaceFirst("plugins {\n", "plugins {\n$DECLARED\n")
                .replace("setupVerification = false", VERIFICATION_ON),
        )
        writeKotlinJvmSources(projectDir)
    }.output
    val output = case(listOf("buildEnvironment"), emptyList())
    val replaced = OLDER.filter { (module, version) -> "$module:$version -> " in output }
    check(replaced.isEmpty()) { "fluxo replaced the declared $replaced:\n$output" }
    case(listOf("check", "dependencyUpdates"), listOf("--dry-run"))
}

/** Older than fluxo's bundled versions, each loadable on Gradle 9. */
private val OLDER = mapOf(
    "io.gitlab.arturbosch.detekt:detekt-gradle-plugin" to "1.23.7",
    "com.diffplug.spotless:spotless-plugin-gradle" to "7.2.1",
    "io.github.ben-manes.versions:io.github.ben-manes.versions.gradle.plugin" to "0.55.0",
)

private val DECLARED = """
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("com.diffplug.spotless") version "7.2.1" apply false
    id("io.github.ben-manes.versions") version "0.55.0" apply false
""".trimIndent()

private const val VERIFICATION_ON = "setupVerification = true\n    enableSpotless = true"
