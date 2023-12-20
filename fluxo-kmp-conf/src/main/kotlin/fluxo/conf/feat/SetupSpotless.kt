package fluxo.conf.feat

import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import com.diffplug.spotless.kotlin.DiktatStep
import com.diffplug.spotless.kotlin.KtLintStep
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.l
import fluxo.conf.impl.v
import org.gradle.api.Project

// TODO: Git pre-commit hook
//  https://medium.com/@mmessell/apply-spotless-formatting-with-git-pre-commit-hook-1c484ea68c34
//  https://detekt.dev/docs/gettingstarted/git-pre-commit-hook

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Project.setupSpotless(
    context: FluxoKmpConfContext,
    enableDiktat: Boolean = false,
) {
    logger.l("setup Spotless")

    // SpotlessPlugin is always availabe in the classpath as it's a dependency.
    pluginManager.apply(SpotlessPlugin::class.java)

    @Suppress("SpreadOperator", "MagicNumber")
    configureExtension<SpotlessExtension>("spotless") {
        // https://github.com/search?l=Kotlin&q=spotless+language%3AKotlin&type=Code
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/c60d0a2/conventions/src/main/kotlin/otel.spotless-conventions.gradle.kts

        // optional: limit format enforcement to the files changed by this feature branch only
        // ratchetFrom = "origin/dev"

        // TODO: read base settings from .editorconfig ?

        if (isRootProject) {
            predeclareDeps()
        }

        fun FormatExtension.defaultFormatSettings(numSpacesPerTab: Int = 4) {
            targetExclude(*SPOTLESS_EXCLUDE_PATHS)
            trimTrailingWhitespace()
            indentWithSpaces(numSpacesPerTab)
            endWithNewline()
        }

        val editorConfigPath = rootProject.file(".editorconfig")
        kotlin {
            target("**/*.kt", "**/*.kts")

            // TODO: Use ktlint directly?
            // https://github.com/search?q=setEditorConfigPath+path%3A*.kt&type=code
            try {
                ktlint(context.libs.v("ktlint") ?: KtLintStep.defaultVersion())
            } catch (e: Throwable) {
                logger.warn("ktlint version error: $e", e)
                ktlint()
            }.setEditorConfigPath(editorConfigPath)

            if (enableDiktat) {
                try {
                    val v = context.libs.v("diktat") ?: DiktatStep.defaultVersionDiktat()
                    diktat(v)
                } catch (e: Throwable) {
                    logger.warn("diktat version error: $e", e)
                    diktat()
                }
            }

            targetExclude(*SPOTLESS_EXCLUDE_PATHS)

            // TODO: Licenses
            // licenseHeader("/* (C)$YEAR */")
        }
        kotlinGradle {
            try {
                ktlint(context.libs.v("ktlint") ?: KtLintStep.defaultVersion())
            } catch (e: Throwable) {
                logger.warn("ktlint version error: $e", e)
                ktlint()
            }.setEditorConfigPath(editorConfigPath)

            if (enableDiktat) {
                try {
                    val v = context.libs.v("diktat") ?: DiktatStep.defaultVersionDiktat()
                    diktat(v)
                } catch (e: Throwable) {
                    logger.warn("diktat version error: $e", e)
                    diktat()
                }
            }
        }

        // TODO: Only if java plugin is enabled?
        java {
            target("**/*.java")
            googleJavaFormat().aosp()
            defaultFormatSettings()
        }

        json {
            target("**/*.json")
            targetExclude(*SPOTLESS_EXCLUDE_PATHS)
            gson().indentWithSpaces(2).sortByKeys()
        }
        format("misc") {
            target(
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
                ktlint()
                if (enableDiktat) {
                    diktat()
                }
            }
            kotlinGradle {
                ktlint()
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

private val SPOTLESS_EXCLUDE_PATHS = arrayOf(
    "**/.gradle-cache/",
    "**/.gradle/",
    "**/.idea/",
    "**/.run/",
    "**/_/",
    "**/build/",
    "**/generated/",
    "**/node_modules/",
    "**/resources/",
)
