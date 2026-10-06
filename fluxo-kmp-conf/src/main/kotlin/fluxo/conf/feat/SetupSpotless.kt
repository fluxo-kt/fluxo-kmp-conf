package fluxo.conf.feat

import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import com.diffplug.spotless.kotlin.DiktatStep
import com.diffplug.spotless.kotlin.KtLintStep
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.isRootProject
import fluxo.log.FluxoProblem
import fluxo.log.l
import fluxo.log.reportProblem
import fluxo.vc.v
import org.gradle.api.Project

// TODO: Git pre-commit hook
//  https://medium.com/@mmessell/apply-spotless-formatting-with-git-pre-commit-hook-1c484ea68c34
//  https://detekt.dev/docs/gettingstarted/git-pre-commit-hook

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Project.setupSpotless(
    ctx: FluxoKmpConfContext,
    enableDiktat: Boolean = false,
) {
    logger.l("setup Spotless")

    // The bundled Spotless is published as preferred, so an older one the consumer declares wins.
    // The formats below need Spotless 7.0+ API; before it, task creation failed with a
    // NoSuchMethodError, so Spotless is skipped there with one warning.
    val hasSpotless7Api = runCatching {
        FormatExtension::class.java.getMethod("leadingTabsToSpaces", Int::class.javaPrimitiveType)
    }.isSuccess
    if (!hasSpotless7Api) {
        if (ctx.firstInBuild("spotless-too-old")) {
            reportProblem(
                FluxoProblem.TOOL_TOO_OLD,
                "Spotless on the build classpath is older than 7.0, which fluxo's formatting " +
                    "setup needs, so Spotless is not set up.",
                fix = "Declare Spotless 7.0 or newer, or remove your Spotless version to use " +
                    "the bundled one.",
            )
        }
        return
    }

    // SpotlessPlugin is always available in the classpath as it's a dependency.
    pluginManager.apply(SpotlessPlugin::class.java)

    // Predeclared steps must match the format steps exactly, or the configuration cache fails
    // ("Add a step with [ktlint-cli:<version>] into the `spotlessPredeclare` block").
    val ktlintVersion = ctx.libs.v("ktlint") ?: KtLintStep.defaultVersion()

    @Suppress("SpreadOperator", "MagicNumber")
    configureExtension<SpotlessExtension>("spotless") {
        // https://github.com/search?l=Kotlin&q=spotless+language%3AKotlin&type=Code
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/c60d0a2/conventions/src/main/kotlin/otel.spotless-conventions.gradle.kts

        // optional: limit format enforcement to the files changed by this feature branch only
        // ratchetFrom = "origin/dev"

        // TODO: read base settings from .editorconfig ?

        if (isRootProject) {
            // Deprecated since Spotless 8.10 in favour of declaring formats directly in
            // `spotlessPredeclare`, but older Spotless creates that extension only after this
            // call, and the consumer may apply any Spotless version.
            @Suppress("DEPRECATION")
            predeclareDeps()
        }

        // Excludes go into the target tree, so Gradle skips an excluded directory unread.
        // `targetExclude(String)` would build a second tree listing every file under `.gradle/`,
        // `build/` and `node_modules/` just to subtract it; Spotless 7 hashes that tree for the
        // configuration cache, which fails on Windows on Gradle's locked `checksums.lock`.
        // A FileCollection target also loses Spotless's own `.git`/build-dir excludes for `**/`
        // string targets, so SPOTLESS_EXCLUDED_DIRS repeats them.
        fun FormatExtension.targetTree(vararg includes: String) = target(
            fileTree(projectDir) {
                include(*includes)
                exclude(*SPOTLESS_EXCLUDED_DIRS)
            },
        )

        fun FormatExtension.defaultFormatSettings(numSpacesPerTab: Int = 4) {
            trimTrailingWhitespace()
            leadingTabsToSpaces(numSpacesPerTab)
            endWithNewline()
        }

        // Spotless rejects a path to a missing file, which failed `check` in every build without
        // a root `.editorconfig`; null keeps ktlint's own defaults.
        val editorConfigPath = rootProject.file(".editorconfig").takeIf { it.isFile }
        kotlin {
            targetTree("**/*.kt", "**/*.kts")

            // TODO: Use ktlint directly?
            // https://github.com/search?q=setEditorConfigPath+path%3A*.kt&type=code
            try {
                ktlint(ktlintVersion)
            } catch (e: Throwable) {
                logger.warn("ktlint version error: $e", e)
                ktlint()
            }.setEditorConfigPath(editorConfigPath)

            if (enableDiktat) {
                try {
                    val v = ctx.libs.v("diktat") ?: DiktatStep.defaultVersionDiktat()
                    diktat(v)
                } catch (e: Throwable) {
                    logger.warn("diktat version error: $e", e)
                    diktat()
                }
            }

            // TODO: Licenses
            // licenseHeader("/* (C)$YEAR */")
        }
        kotlinGradle {
            try {
                ktlint(ktlintVersion)
            } catch (e: Throwable) {
                logger.warn("ktlint version error: $e", e)
                ktlint()
            }.setEditorConfigPath(editorConfigPath)

            if (enableDiktat) {
                try {
                    val v = ctx.libs.v("diktat") ?: DiktatStep.defaultVersionDiktat()
                    diktat(v)
                } catch (e: Throwable) {
                    logger.warn("diktat version error: $e", e)
                    diktat()
                }
            }
        }

        // TODO: Only if java plugin is enabled?
        java {
            targetTree("**/*.java")
            googleJavaFormat().aosp()
            defaultFormatSettings()
        }

        json {
            targetTree("**/*.json")
            gson().indentWithSpaces(2).sortByKeys()
        }
        format("misc") {
            targetTree(
                "**/*.css",
                "**/*.dockerfile",
                "**/*.gradle",
                "**/*.htm",
                "**/*.html",
                "**/*.md",
                "**/*.pro",
                "**/*.sh",
                "**/*.xml",
                "**/*.yml",
                "**/gradle.properties",
                "*.md",
                "*.yml",
                ".dockerignore",
                ".editorconfig",
                ".gitattributes",
                ".gitconfig",
                ".gitignore",
            )
            defaultFormatSettings(numSpacesPerTab = 2)
        }

        // TODO: Freshmark
        // https://github.com/diffplug/spotless/tree/main/plugin-gradle#freshmark
        // https://github.com/diffplug/freshmark
    }

    // `spotlessPredeclare` is only declared after spotless.predeclareDeps call
    if (isRootProject) {
        configureExtension<SpotlessExtension>("spotlessPredeclare") {
            kotlin {
                if (runCatching { ktlint(ktlintVersion) }.isFailure) ktlint()
                if (enableDiktat) {
                    diktat()
                }
            }
            kotlinGradle {
                if (runCatching { ktlint(ktlintVersion) }.isFailure) ktlint()
                if (enableDiktat) {
                    diktat()
                }
            }
            java {
                googleJavaFormat()
            }
            json {
                gson()
            }
            yaml {
                jackson()
            }
            format("markdown") {
                prettier()
            }
        }
    }
}

// No trailing slash: the pattern then matches the directory itself, so the walk prunes it
// instead of listing its content.
private val SPOTLESS_EXCLUDED_DIRS = arrayOf(
    "**/.git",
    "**/.gradle",
    "**/.gradle-cache",
    "**/.idea",
    "**/.run",
    "**/_",
    "**/build",
    "**/generated",
    "**/node_modules",
    "**/resources",
)
