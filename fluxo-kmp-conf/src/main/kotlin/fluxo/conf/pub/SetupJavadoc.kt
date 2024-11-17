package fluxo.conf.pub

import com.vanniktech.maven.publish.JavadocJar
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.has
import fluxo.conf.impl.kotlin.JRE_11
import fluxo.conf.impl.kotlin.asJavaVersion
import fluxo.conf.impl.kotlin.asJvmTargetVersion
import fluxo.conf.impl.namedOrNull
import fluxo.conf.impl.register
import fluxo.conf.impl.withType
import fluxo.log.l
import fluxo.log.w
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.jetbrains.dokka.gradle.DokkaTask


/**
 *
 * @see com.vanniktech.maven.publish.MavenPublishBaseExtension.defaultJavaDocOption
 */
@Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
internal fun vanniktechJavaDocOption(
    p: Project,
    config: FluxoPublicationConfig,
    conf: FluxoConfigurationExtensionImpl,
    javadocJar: Boolean = true,
    plainJavadocSupported: Boolean = true,
    dokkaSupported: Boolean = conf.useDokka,
    applyDokka: Boolean = false,
): JavadocJar {
    if (!javadocJar) {
        return JavadocJar.None()
    }

    // Dokka artifacts are pretty big, so use them only for release builds, not for snapshots.
    if (dokkaSupported && !config.isSnapshot) {
        var canApplyDokka = applyDokka
        while (true) {
            if (p.plugins.hasPlugin("org.jetbrains.dokka-javadoc")) {
                return JavadocJar.Dokka("dokkaGeneratePublicationJavadoc")
            } else if (p.plugins.hasPlugin("org.jetbrains.dokka")) {
                // only dokka v2 has an extension
                return if (p.extensions.findByName("dokka") != null) {
                    JavadocJar.Dokka("dokkaGeneratePublicationHtml")
                } else {
                    val dokkaTask = p.provider {
                        val tasks = p.tasks.withType(DokkaTask::class.java)
                        tasks.singleOrNull()?.name ?: "dokkaHtml"
                    }
                    JavadocJar.Dokka(dokkaTask)
                }
            }
            if (!canApplyDokka || !conf.ctx.loadAndApplyDokkaIfNotApplied(p)) {
                break
            }
            canApplyDokka = false
        }
    }

    return if (plainJavadocSupported) {
        p.tasks.withType<Javadoc> {
            val options = options as StandardJavadocDocletOptions

            /** @see com.vanniktech.maven.publish.javaVersion */
            val kc = conf.kotlinConfig
            val javaInt = kc.jvmTargetInt
            val jvmTarget = kc.jvmTarget ?: javaInt.asJvmTargetVersion()
            val javaVersion = jvmTarget.asJavaVersion()

            if (javaVersion.isJava9Compatible) {
                options.addBooleanOption("html5", true)
            }
            if (javaVersion.isJava8Compatible) {
                options.addStringOption("Xdoclint:none", "-quiet")
            }

            options.links(
                when {
                    javaInt < JRE_11 -> "https://docs.oracle.com/javase/$javaInt/docs/api/"
                    else -> "https://docs.oracle.com/en/java/javase/$javaInt/docs/api/"
                },
            )
        }
        JavadocJar.Javadoc()
    } else {
        JavadocJar.Empty()
    }
}

private fun FluxoKmpConfContext.loadAndApplyDokkaIfNotApplied(project: Project): Boolean {
    try {
        val result = loadAndApplyPluginIfNotApplied(
            id = BuildConstants.DOKKA_PLUGIN_ID,
            version = BuildConstants.DOKKA_PLUGIN_VERSION,
            catalogPluginId = BuildConstants.DOKKA_PLUGIN_ALIAS,
            project = project,
        )
        if (result.applied) {
            project.logger.l("Applied Dokka publication")
        }
        return result.applied
    } catch (e: Throwable) {
        project.logger.w("Dokka setup error: $e", e)
        return false
    }
}


internal fun FluxoKmpConfContext.setupJavadocTask(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
    type: ConfigurationType? = null,
): NamedDomainObjectProvider<out Task> {
    // Publish docs with each artifact.
    // Dokka artifacts are pretty big, so use them only for release builds, not for snapshots.
    if (useDokka && !config.isSnapshot && loadAndApplyDokkaIfNotApplied(p)) {
        return p.getOrCreateDokkaTask(type)
    }
    p.logger.l("regular Javadoc publication fallback set up instead of Dokka")
    return p.getOrCreateJavadocTask()
}

private fun Project.getOrCreateDokkaTask(
    type: ConfigurationType?,
): NamedDomainObjectProvider<out Task> {
    val tasks = tasks
    val taskName = if (tasks.has(JAVADOC_TASK_NAME)) "dokkaHtmlJar" else JAVADOC_TASK_NAME
    return tasks.namedOrNull(JAVADOC_TASK_NAME)
        ?: tasks.register<Jar>(taskName) {
            configureJavadocTask()
            description = "Assembles Kotlin docs with Dokka into a Javadoc jar"
            // Collect Dokka output
            val dokkaTaskName = when (type) {
                ConfigurationType.KOTLIN_MULTIPLATFORM -> "dokkaHtml"
                // Use a Javadoc-like format for all non-KMP projects.
                else -> "dokkaJavadoc"
            }
            from(project.tasks.named(dokkaTaskName))
        }
}

private fun Project.getOrCreateJavadocTask(): NamedDomainObjectProvider<out Task> {
    return tasks.namedOrNull(JAVADOC_TASK_NAME)
        ?: tasks.register<Jar>(JAVADOC_TASK_NAME, Jar::configureJavadocTask)
}

private fun Jar.configureJavadocTask() {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles a Javadoc jar."
    archiveClassifier.set("javadoc")
}

private const val JAVADOC_TASK_NAME = "javadocJar"
