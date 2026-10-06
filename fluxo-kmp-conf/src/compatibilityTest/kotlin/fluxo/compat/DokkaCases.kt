package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Dokka reads the Kotlin plugin's model, so it only works loaded where it can see the Kotlin
 * plugin. Fetched through a Gradle script plugin it printed "Dokka could not load
 * KotlinBasePlugin" and wrote docs without the Kotlin sources while the build passed. Declared on
 * the build classpath, `useDokka` must document the module's code; undeclared, under the
 * configuration cache, the publication falls back to plain Javadoc and names the line to add.
 */
internal fun runDokkaCase(row: Map<String, String>, tempDir: Path) {
    for (declared in listOf(true, false)) {
        val name = "dokka" + if (declared) "-declared" else ""
        val projectDir = tempDir.resolve(row.getValue("id") + "-$name")
        val output = runConsumerCase(
            row,
            tempDir,
            rootProjectName = "compat-$name",
            projectDir = projectDir,
            tasks = listOf(if (declared) "dokkaGeneratePublicationHtml" else "help"),
            forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + "could not load KotlinBasePlugin",
        ) {
            var script = markerKotlinJvmBuildScript(row).replace(
                "enablePublication = false",
                "enablePublication = true\n    useVanniktechPublish = false\n" +
                    "    useDokka = true\n    publicationConfig {}",
            )
            if (declared) {
                // Any Dokka 2 release: what matters is where it is loaded from.
                script = script.replace(
                    "plugins {",
                    "plugins {\n    id(\"org.jetbrains.dokka\") version \"2.2.0\" apply false",
                )
            }
            it.resolve("build.gradle.kts").writeText(script)
            writeKotlinJvmSources(it)
        }.output
        if (!declared) {
            check("'org.jetbrains.dokka' is not on the build classpath" in output) { output }
            check("Setup step skipped" !in output) { output }
            continue
        }
        val pages = projectDir.resolve("build/dokka").toFile().walk()
            .filter { it.extension == "html" }.toList()
        check(pages.any { "normalizeName" in it.readText() }) {
            "Dokka documented no Kotlin code: ${pages.size} pages under $projectDir/build/dokka"
        }
    }
}
