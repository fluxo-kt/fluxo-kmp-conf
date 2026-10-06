package fluxo.conf.pub

import com.vanniktech.maven.publish.JavadocJar
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.isConfigurationCacheActive
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.deps.missingFromBuildClasspathMessage
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.has
import fluxo.conf.impl.kotlin.JRE_11
import fluxo.conf.impl.kotlin.asJavaVersion
import fluxo.conf.impl.namedOrNull
import fluxo.conf.impl.registerCompat
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
            // Dokka v2 (DGPv2) only — v1 task model is non-functional in 2.2+.
            // The two distinct plugin ids select output format: javadoc vs HTML.
            if (p.plugins.hasPlugin("org.jetbrains.dokka-javadoc")) {
                return JavadocJar.Dokka("dokkaGeneratePublicationJavadoc")
            } else if (p.plugins.hasPlugin("org.jetbrains.dokka")) {
                return JavadocJar.Dokka("dokkaGeneratePublicationHtml")
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
            val javaVersion = kc.jvmTarget.asJavaVersion()

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

/**
 * Dokka reads the Kotlin plugin's model, so it must load where it can see the Kotlin plugin: from
 * the module's build classpath. Fetched through a Gradle script plugin it could not ("Dokka could
 * not load KotlinBasePlugin") and wrote docs with no Kotlin sources while the build passed. It is
 * not added to every build classpath like KSP: it brings Jackson, coroutines and serialization.
 * Undeclared under the configuration cache, the publication gets plain Javadoc and a warning with
 * the `plugins {}` line (fluxo's own class loader, which sees the Kotlin plugin, can't be stored by
 * the cache); without the cache fluxo loads it there. Publication setup errors are only logged
 * (`onProjectInSyncRun`), so failing here would leave the publication half configured.
 */
private fun FluxoKmpConfContext.loadAndApplyDokkaIfNotApplied(project: Project): Boolean {
    val id = BuildConstants.DOKKA_PLUGIN_ID
    val declared = project.buildscript.classLoader
        .getResource("META-INF/gradle-plugins/$id.properties") != null
    if (!declared && project.isConfigurationCacheActive()) {
        project.logger.w(
            missingFromBuildClasspathMessage(project, id, BuildConstants.DOKKA_PLUGIN_VERSION) +
                " Until then the publication gets plain Javadoc.",
        )
        return false
    }
    val result = loadAndApplyPluginIfNotApplied(
        id = id,
        version = BuildConstants.DOKKA_PLUGIN_VERSION,
        catalogPluginId = BuildConstants.DOKKA_PLUGIN_ALIAS,
        project = project,
    )
    if (result.applied) {
        project.logger.l("Applied Dokka publication")
    }
    return result.applied
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
        ?: tasks.registerCompat<Jar>(taskName) {
            configureJavadocTask()
            description = "Assembles Kotlin docs with Dokka into a Javadoc jar"
            // Dokka v2 (DGPv2) splits formats by plugin id: `org.jetbrains.dokka`
            // generates `dokkaGeneratePublicationHtml`; `org.jetbrains.dokka-javadoc`
            // generates `dokkaGeneratePublicationJavadoc`. The Javadoc-like format does
            // not cover KMP source-set hierarchies, so KMP always keeps HTML. For
            // non-KMP, honour the consumer-applied javadoc plugin when present.
            val dokkaJavadoc = type != ConfigurationType.KOTLIN_MULTIPLATFORM &&
                project.plugins.hasPlugin("org.jetbrains.dokka-javadoc")
            val dokkaTaskName = if (dokkaJavadoc) {
                "dokkaGeneratePublicationJavadoc"
            } else {
                "dokkaGeneratePublicationHtml"
            }
            from(project.tasks.named(dokkaTaskName))
        }
}

private fun Project.getOrCreateJavadocTask(): NamedDomainObjectProvider<out Task> {
    return tasks.namedOrNull(JAVADOC_TASK_NAME)
        ?: tasks.registerCompat<Jar>(JAVADOC_TASK_NAME, Jar::configureJavadocTask)
}

private fun Jar.configureJavadocTask() {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles a Javadoc jar."
    archiveClassifier.set("javadoc")
}

private const val JAVADOC_TASK_NAME = "javadocJar"
