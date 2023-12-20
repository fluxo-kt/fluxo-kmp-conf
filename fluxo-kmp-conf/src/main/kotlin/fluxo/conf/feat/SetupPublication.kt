@file:Suppress("TooManyFunctions")

package fluxo.conf.feat

import MAIN_SOURCE_SET_NAME
import com.android.build.gradle.LibraryExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.create
import fluxo.conf.impl.e
import fluxo.conf.impl.get
import fluxo.conf.impl.getByName
import fluxo.conf.impl.hasExtension
import fluxo.conf.impl.kotlin.KOTLIN_EXT
import fluxo.conf.impl.kotlin.multiplatformExtension
import fluxo.conf.impl.l
import fluxo.conf.impl.named
import fluxo.conf.impl.the
import fluxo.conf.impl.w
import fluxo.conf.impl.withType
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType


/*
 * Set up publishing.
 *
 * Useful resources:
 *
 * - https://kotlinlang.org/docs/mpp-publish-lib.html
 * - https://central.sonatype.org/publish/publish-guide/
 * - https://central.sonatype.org/publish/publish-gradle/
 * - https://central.sonatype.org/publish/requirements/coordinates/#choose-your-coordinates
 * - https://getstream.io/blog/publishing-libraries-to-mavencentral-2021/
 * - https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
 */

// TODO: Set RC/alpha/beta releases status to Ivy's: milestone/integration when it will be possible
//  https://github.com/gradle/gradle/issues/12600
//  https://github.com/gradle/gradle/issues/20016

// TODO: Protect publication tasks from invalid GIT state (dirty, untracked files, etc.)
//  https://github.com/tbroyer/gradle-errorprone-plugin/blob/5d83185/build.gradle.kts#L24

// TODO: Replace Gradle Plugin publishing with something less broken (official is just a mess)?
//  https://github.com/vanniktech/gradle-maven-publish-plugin
//  https://github.com/adamko-dev/dokkatoo/issues/61#issuecomment-1701156702
//  https://github.com/adamko-dev/dokkatoo/blob/b1ca20c/buildSrc/src/main/kotlin/buildsrc/conventions/maven-publishing.gradle.kts

// TODO: Decorate the build logs with maven coordinates of published artifacts
//  https://github.com/gmazzo/gradle-report-publications-plugin

internal fun setupGradleProjectPublication(
    config: FluxoPublicationConfig,
    configuration: FluxoConfigurationExtensionImpl,
    type: ConfigurationType,
    project: Project = configuration.project,
) = project.run r@{
    val context = configuration.context
    val useDokka = configuration.useDokka ?: true
    when {
        type === ConfigurationType.KOTLIN_MULTIPLATFORM ->
            setupPublicationMultiplatform(config, context, useDokka)

        type === ConfigurationType.ANDROID_LIB ->
            setupPublicationAndroidLibrary(config, context, useDokka)

        type === ConfigurationType.GRADLE_PLUGIN ->
            setupPublicationGradlePlugin(config, context, useDokka)

        type === ConfigurationType.KOTLIN_JVM ->
            setupPublicationKotlinJvm(config, context, useDokka)

        else -> {
            logger.e("Unsupported project type for publication: $type")
            if (SHOW_DEBUG_LOGS && hasExtension { JavaPluginExtension::class }) {
                setupPublicationJava(config, context)
            }
            return@r
        }
    }

    // Reproducible builds setup, produce deterministic output.
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    // https://github.com/JetBrains/kotlin/commit/68fdeaf
    if (configuration.reproducibleArtifacts != false) {
        tasks.withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

internal fun Project.setupPublicationMultiplatform(
    config: FluxoPublicationConfig,
    context: FluxoKmpConfContext,
    useDokka: Boolean,
) {
    if (!context.isTargetEnabled(KmpTargetCode.COMMON)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)
    setupPublicationExtension(publishing, context, config, useDokka = useDokka)
    setupPublicationRepository(config, publishing)

    multiplatformExtension.apply {
        if (targets.any { it.platformType == KotlinPlatformType.androidJvm }) {
            try {
                // Kotlin before 1.9
                @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
                android().publishLibraryVariants(RELEASE, DEBUG)
            } catch (e: Throwable) {
                logger.e("android.publishLibraryVariants error: $e", e)
            }

            // Gradle 8 compatibility
            if (config.isSigningEnabled) {
                val deps = tasks.matching {
                    it.name.startsWith("sign") && it.name.endsWith("Publication")
                }
                tasks.matching {
                    it.name.endsWith("PublicationToMavenLocal") ||
                        it.name.endsWith("PublicationToMavenRepository")
                }.configureEach {
                    dependsOn(deps)
                }
            }
        }
    }
}

internal fun Project.setupPublicationAndroidLibrary(
    config: FluxoPublicationConfig,
    context: FluxoKmpConfContext,
    useDokka: Boolean,
) {
    if (!context.isTargetEnabled(KmpTargetCode.ANDROID)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val androidExtension = the<LibraryExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(androidExtension.sourceSets.getByName(MAIN_SOURCE_SET_NAME).java.srcDirs)
        archiveClassifier.set("source")
    }

    fun PublicationContainer.createMavenPublication(name: String, artifactIdSuffix: String) {
        create<MavenPublication>(name) {
            from(components[name])
            artifact(sourceJarTask)

            groupId = config.group
            version = config.version
            artifactId = "${project.name}$artifactIdSuffix"

            setupPublicationPom(project, context, config, useDokka = useDokka)
        }
    }

    afterEvaluate {
        publishing.publications {
            createMavenPublication(name = DEBUG, artifactIdSuffix = "-$DEBUG")
            createMavenPublication(name = RELEASE, artifactIdSuffix = "")
        }
    }

    setupPublicationRepository(config, publishing)
}

internal fun Project.setupPublicationGradlePlugin(
    config: FluxoPublicationConfig,
    context: FluxoKmpConfContext,
    useDokka: Boolean,
) {
    if (!context.isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val gradlePluginExtension = gradlePluginExt.apply {
        config.projectUrl?.let { website.set(it) }
        config.publicationUrl?.let { vcsUrl.set(it) }
    }

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(gradlePluginExtension.pluginSourceSet.java.srcDirs)
        archiveClassifier.set("sources")
    }

    afterEvaluate {
        setupPublicationExtension(publishing, context, config, sourceJarTask, useDokka = useDokka)
    }

    setupPublicationRepository(config, publishing, mavenRepo = false)
}

internal fun Project.setupPublicationKotlinJvm(
    config: FluxoPublicationConfig,
    context: FluxoKmpConfContext,
    useDokka: Boolean,
) {
    if (!context.isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val kotlinPluginExtension = extensions.getByName<KotlinJvmProjectExtension>(KOTLIN_EXT)

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(kotlinPluginExtension.sourceSets.getByName(MAIN_SOURCE_SET_NAME).kotlin.srcDirs)
        archiveClassifier.set("sources")
    }

    setupPublicationExtension(publishing, context, config, sourceJarTask, useDokka = useDokka)
    setupPublicationRepository(config, publishing)
}

internal fun Project.setupPublicationJava(
    config: FluxoPublicationConfig,
    context: FluxoKmpConfContext,
) {
    if (!context.isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val javaPluginExtension = the<JavaPluginExtension>()

    val sourceJarTask = tasks.create<Jar>("sourceJarTask") {
        from(javaPluginExtension.sourceSets.getByName(MAIN_SOURCE_SET_NAME).java.srcDirs)
        archiveClassifier.set("sources")
    }

    setupPublicationExtension(publishing, context, config, sourceJarTask, useDokka = false)
    setupPublicationRepository(config, publishing)
}

internal fun MavenPublication.setupPublicationPom(
    project: Project,
    context: FluxoKmpConfContext,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    // Publish docs with each artifact.
    // Dokka artifacts are pretty big, so use them only for release builds, not for snapshots.
    var fallback = !useDokka || config.isSnapshot
    if (!fallback) {
        try {
            // Apply Dokka plugin first
            val result = context.loadAndApplyPluginIfNotApplied(
                id = BuildConstants.DOKKA_PLUGIN_ID,
                version = BuildConstants.DOKKA_PLUGIN_VERSION,
                catalogPluginId = BuildConstants.DOKKA_PLUGIN_ALIAS,
                project = project,
            )
            if (result.applied) {
                val dokkaHtmlAsJavadocTaskName = "dokkaHtmlAsJavadoc"
                val dokkaHtmlAsJavadoc = project.tasks.run {
                    findByName(dokkaHtmlAsJavadocTaskName) ?: run {
                        val dokkaHtml = project.tasks.named<DokkaTask>("dokkaHtml")
                        create<Jar>(dokkaHtmlAsJavadocTaskName) {
                            setupJavadocJar()
                            from(dokkaHtml)
                        }
                    }
                }
                artifact(dokkaHtmlAsJavadoc)
            } else {
                fallback = true
            }
        } catch (e: Throwable) {
            fallback = true
            project.logger.w("Fallback to Javadoc. Dokka publication setup error: $e", e)
        }
    }
    if (fallback) {
        artifact(project.javadocJarTask())
    }

    pom {
        name.set(config.projectName)
        description.set(config.projectDescription)
        url.set(config.publicationUrl)

        if (!config.licenseName.isNullOrEmpty()) {
            // TODO: Add support for multiple licenses
            licenses {
                license {
                    name.set(config.licenseName)
                    url.set(config.licenseUrl)
                }
            }
        }

        developers {
            // TODO: Add support for multiple developers
            if (!config.developerId.isNullOrEmpty() ||
                !config.developerName.isNullOrEmpty() ||
                !config.developerEmail.isNullOrEmpty()
            ) {
                developer {
                    config.developerId?.let { id.set(it) }
                    config.developerName?.let { name.set(it) }
                    config.developerEmail?.let { email.set(it) }
                }
            }
        }

        scm {
            url.set(config.projectUrl)
            connection.set(config.scmUrl)
            developerConnection.set(config.scmUrl)
            config.scmTag.takeIf { !it.isNullOrEmpty() }?.let {
                tag.set(it)
            }
        }
    }
}

private val signingKeyNotificationLogged = AtomicBoolean()

internal fun Project.setupPublicationRepository(
    config: FluxoPublicationConfig,
    publishing: PublishingExtension,
    mavenRepo: Boolean = true,
) {
    val notify = signingKeyNotificationLogged.compareAndSet(false, true)
    if (config.isSigningEnabled) {
        if (notify) logger.l("SIGNING_KEY SET, applying signing configuration")
        pluginManager.apply(SIGNING_EXT_NAME)
    } else if (notify) {
        // TODO: Warn only when assemble and/or publishing really called.
        logger.w("SIGNING_KEY IS NOT SET! Publications are unsigned")
    }

    publishing.apply {
        if (config.isSigningEnabled) {
            configureExtension<SigningExtension>(name = SIGNING_EXT_NAME) {
                useInMemoryPgpKeys(config.signingKey, config.signingPassword)
                sign(publications)
            }
        }

        repositories {
            maven {
                name = "localDev"
                url = uri(rootProject.file(LOCAL_REPO_PATH))
            }

            if (mavenRepo) {
                maven {
                    setUrl(config.repositoryUrl)

                    credentials {
                        username = config.repositoryUserName
                        password = config.repositoryPassword
                    }
                }
            }
        }
    }
}

private fun Project.setupPublicationExtension(
    publishing: PublishingExtension,
    context: FluxoKmpConfContext,
    config: FluxoPublicationConfig,
    sourceJarTask: Jar? = null,
    useDokka: Boolean,
) {
    publishing.publications.withType<MavenPublication> {
        sourceJarTask?.let { artifact(it) }
        setupPublicationPom(project, context, config, useDokka = useDokka)
    }
}

// TODO: Make configurable
private const val LOCAL_REPO_PATH = "_/local-repo"

/** Also a plugin name! */
private const val SIGNING_EXT_NAME = "signing"

private const val PUBLISHING_EXT_NAME = "publishing"

private const val GRADLE_PLUGIN_EXT_NAME = "gradlePlugin"

private fun Project.applyMavenPublishPlugin(config: FluxoPublicationConfig): PublishingExtension {
    group = config.group
    version = config.version

    pluginManager.apply("maven-publish")
    return extensions.getByName<PublishingExtension>(PUBLISHING_EXT_NAME)
}

internal val Project.gradlePluginExt: GradlePluginDevelopmentExtension
    get() = extensions.getByName<GradlePluginDevelopmentExtension>(GRADLE_PLUGIN_EXT_NAME)


private fun Project.javadocJarTask(): Task {
    val taskName = "javadocJar"
    return tasks.findByName(taskName) ?: tasks.create<Jar>(taskName, Jar::setupJavadocJar)
}

private fun Jar.setupJavadocJar() {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Assembles a jar archive containing the Javadoc API documentation."
    archiveClassifier.set("javadoc")
}
