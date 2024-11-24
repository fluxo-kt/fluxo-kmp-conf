@file:Suppress("TooManyFunctions")

package fluxo.conf.pub

import MAIN_SOURCE_SET_NAME
import com.android.build.gradle.LibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadPluginStaticallyError
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.markAsMustRunAfterBuildConfigTasks
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.create
import fluxo.conf.impl.get
import fluxo.conf.impl.getByName
import fluxo.conf.impl.hasExtension
import fluxo.conf.impl.kotlin.mppExt
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.namedOrNull
import fluxo.conf.impl.register
import fluxo.conf.impl.the
import fluxo.conf.impl.uppercaseFirstChar
import fluxo.conf.impl.withType
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.e
import fluxo.log.l
import fluxo.log.w
import fluxo.test.formatSummary
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.tasks.Jar as JarJvm
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
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

// FIXME: Disambiguate existing javadoc and sources tasks

// TODO: Set RC/alpha/beta releases status to Ivy's: milestone/integration when it will be possible
//  https://github.com/gradle/gradle/issues/12600
//  https://github.com/gradle/gradle/issues/20016

// TODO: Protect publication tasks from invalid GIT state (dirty, untracked files, etc.)
//  https://github.com/tbroyer/gradle-errorprone-plugin/blob/5d83185/build.gradle.kts#L24

// TODO: Decorate the build logs with maven coordinates of published artifacts
//  https://github.com/gmazzo/gradle-report-publications-plugin

// https://github.com/GradleUp/shadow/blob/8aad9ad/build-logic/src/main/kotlin/shadow.convention.publish.gradle.kts

private val CALL_TASK_PREFIXES = arrayOf(
    "publish", "upload", "deploy", "release", "ship",
    "distribute", "check", "verify", "install",
)

internal fun setupPublication(
    conf: FluxoConfigurationExtensionImpl,
) {
    // TODO: Avoid publication setup when possible
    val config = conf.publicationConfig
    if (config == null || conf.enablePublication != true) {
        return
    }
    val ctx = conf.ctx
    val isCalled = ctx.startTaskNames.any { name ->
        CALL_TASK_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }
    ctx.onProjectInSyncRun(forceIf = isCalled) {
        setupGradleProjectPublication(conf.project, config, conf)
    }
}

private fun FluxoKmpConfContext.setupGradleProjectPublication(
    p: Project,
    config: FluxoPublicationConfig,
    conf: FluxoConfigurationExtensionImpl,
) {
    val useDokka = conf.useDokka

    // Use Vanniktech's maven-publish plugin for publication configuration and management.
    // It's much better than default publishing configuration of most plugins.
    // E.g., official Gradle Plugin publishing is just a broken mess.
    //  https://github.com/adamko-dev/dokkatoo/issues/61#issuecomment-1701156702
    //  https://github.com/adamko-dev/dokkatoo/blob/b1ca20c/buildSrc/src/main/kotlin/buildsrc/conventions/maven-publishing.gradle.kts
    val useVanniktech = conf.useVanniktechPublish != false
    if (useVanniktech) {
        if (p.pluginManager.hasPlugin(VANNIKTECH_MAVEN_PUBLISH_PLUGIN_ID) ||
            p.pluginManager.hasPlugin(VANNIKTECH_MAVEN_PUBLISH_BASE_PLUGIN_ID)
        ) {
            p.configureExtension<MavenPublishBaseExtension>("mavenPublishing") {
                setupVanniktechPublication(p, config, conf)
            }
        } else {
            p.loadPluginStaticallyError(VANNIKTECH_MAVEN_PUBLISH_PLUGIN_ID)
        }
    } else {
        when (val mode = conf.mode) {
            ConfigurationType.KOTLIN_MULTIPLATFORM ->
                setupPublicationMultiplatform(p, config, useDokka)

            ConfigurationType.ANDROID_LIB ->
                setupPublicationAndroidLibrary(p, config, useDokka)

            ConfigurationType.GRADLE_PLUGIN ->
                setupPublicationGradlePlugin(p, config, useDokka)

            ConfigurationType.KOTLIN_JVM ->
                setupPublicationKotlinJvm(p, config, useDokka)

            else -> {
                p.logger.e("Unsupported project type for publication: $mode")

                // Java-only project support is experimental
                if (!SHOW_DEBUG_LOGS || !p.hasExtension { JavaPluginExtension::class }) {
                    return
                }
                setupPublicationJava(p, config)
            }
        }
    }

    // Reproducible builds setup, produce deterministic output.
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    // https://github.com/JetBrains/kotlin/commit/68fdeaf
    if (conf.reproducibleArtifacts != false) {
        p.logger.l("reproducible artifacts set up")
        p.tasks.withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            @Suppress("DEPRECATION")
            try {
                // Deprecated in Gradle 8.8
                dirMode = "0755".toInt(radix = 8)
                fileMode = "0644".toInt(radix = 8)
            } catch (e: Throwable) {
                logger.e("dirMode/fileMode reproducibleArtifacts setup error: $e", e)
            }
        }
    }
}

private fun FluxoKmpConfContext.setupPublicationMultiplatform(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    // TODO: Distinguish between common and platform-specific publications
    if (!isTargetEnabled(KmpTargetCode.COMMON)) {
        return
    }

    // FIXME: provide sources for KMP publications
    val publishing = p.applyMavenPublishPlugin(config)
    setupPublicationExtension(
        p = p,
        publishing = publishing,
        config = config,
        useDokka = useDokka,
        type = ConfigurationType.GRADLE_PLUGIN,
    )
    setupPublicationRepositoryAndSigning(p, config, publishing)

    p.mppExt.apply {
        if (targets.any { it.platformType == KotlinPlatformType.androidJvm }) {
            try {
                androidTarget().publishLibraryVariants(RELEASE, DEBUG)
            } catch (e: Throwable) {
                p.logger.e("android.publishLibraryVariants error: $e", e)
            }
        }
    }
}

private fun FluxoKmpConfContext.setupPublicationAndroidLibrary(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.ANDROID)) {
        return
    }

    val publishing = p.applyMavenPublishPlugin(config)

    val androidExtension = p.the<LibraryExtension>()

    val sourcePaths = androidExtension.sourceSets[MAIN_SOURCE_SET_NAME].java.srcDirs
    val sourceJarTask = p.registerSourceJarTask(sourcePaths)
    val javadocTask = setupJavadocTask(p, config, useDokka = useDokka)

    fun PublicationContainer.createMavenPublication(name: String, artifactIdSuffix: String) {
        create<MavenPublication>(name) {
            from(p.components[name])
            artifact(sourceJarTask)
            artifact(javadocTask)

            val project = p.project
            groupId = config.group
            version = config.version
            artifactId = "${project.name}$artifactIdSuffix"

            setupPublicationPom(project, config)
        }
    }

    p.afterEvaluate {
        publishing.publications {
            this.createMavenPublication(name = DEBUG, artifactIdSuffix = "-$DEBUG")
            this.createMavenPublication(name = RELEASE, artifactIdSuffix = "")
        }
    }

    setupPublicationRepositoryAndSigning(p, config, publishing)
}

private fun FluxoKmpConfContext.setupPublicationGradlePlugin(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = p.applyMavenPublishPlugin(config)

    val gradlePluginExtension = p.gradlePluginExt.apply {
        config.projectUrl?.let { website.set(it) }
        config.publicationUrl?.let { vcsUrl.set(it) }
    }

    val sourcePaths = gradlePluginExtension.pluginSourceSet.java.srcDirs +
        p.kotlinExtension.sourceSets[MAIN_SOURCE_SET_NAME].kotlin.srcDirs
    val sourceJarTask = p.registerSourceJarTask(sourcePaths)

    // TODO: Should wrap `setupPublicationExtension` in afterEvaluate?
    setupPublicationExtension(
        p = p,
        publishing = publishing,
        config = config,
        useDokka = useDokka,
        sourceJarTask = sourceJarTask,
        type = ConfigurationType.GRADLE_PLUGIN,
    )

    setupPublicationRepositoryAndSigning(p, config, publishing, mavenRemoteRepo = false)
}

private fun FluxoKmpConfContext.setupPublicationKotlinJvm(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = p.applyMavenPublishPlugin(config)

    val sourcePaths = p.kotlinExtension.sourceSets[MAIN_SOURCE_SET_NAME].kotlin.srcDirs
    val sourceJarTask = p.registerSourceJarTask(sourcePaths)

    setupPublicationExtension(p, publishing, config, useDokka = useDokka, sourceJarTask)
    setupPublicationRepositoryAndSigning(p, config, publishing)
}

private fun FluxoKmpConfContext.setupPublicationJava(p: Project, config: FluxoPublicationConfig) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = p.applyMavenPublishPlugin(config)

    val javaPluginExtension = p.the<JavaPluginExtension>()

    val sourcePaths = javaPluginExtension.sourceSets[MAIN_SOURCE_SET_NAME].java.srcDirs
    val sourceJarTask = p.registerSourceJarTask(sourcePaths)

    setupPublicationExtension(p, publishing, config, useDokka = false, sourceJarTask)
    setupPublicationRepositoryAndSigning(p, config, publishing)
}


private fun MavenPublication.setupPublicationPom(
    project: Project,
    config: FluxoPublicationConfig,
) = pom { setupPublicationPom(project, config) }

/** @see com.vanniktech.maven.publish.MavenPublishBaseExtension.pomFromGradleProperties */
@Suppress("CyclomaticComplexMethod")
internal fun MavenPom.setupPublicationPom(
    project: Project,
    config: FluxoPublicationConfig,
) {
    project.logger.l("setup maven POM for '$name': '${config.projectName}'")

    name.set(config.projectName ?: name.get())
    description.set(config.projectDescription ?: description.get())
    url.set(config.publicationUrl ?: url.get() ?: config.projectUrl)

    config.inceptionYear?.let { inceptionYear.set(it) }

    if (!config.licenseName.isNullOrEmpty()) {
        // TODO: Add support for multiple licenses
        licenses {
            license {
                name.set(config.licenseName)
                config.licenseUrl?.let {
                    url.set(it)
                    if (it.endsWith(".txt")) {
                        distribution.set(it)
                    }
                }
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


private val signingKeyNotificationLogged = AtomicBoolean()

internal fun FluxoKmpConfContext.setupPublicationRepositoryAndSigning(
    p: Project,
    config: FluxoPublicationConfig,
    publishing: PublishingExtension,
    mavenRemoteRepo: Boolean = true,
) {
    val isSigningEnabled = getIfSigningEnabled(config, p)
    if (isSigningEnabled) {
        p.pluginManager.apply(SIGNING_EXT_NAME)
        p.configureExtension<SigningExtension>(name = SIGNING_EXT_NAME) {
            p.logger.l("setup publications signing configuration")
            useInMemoryPgpKeys(config.signingKeyId, config.signingKey, config.signingPassword)
            sign(publishing.publications)

            // Signature is a hard requirement if publishing a non-snapshot
            if (!config.isSnapshot) {
                this.isRequired = true
            }
        }
    }

    setupPublishingRepositories(p, config, publishing, mavenRemoteRepo)

    // Gradle 8+ workaround for https://github.com/gradle/gradle/issues/26091
    // and https://youtrack.jetbrains.com/issue/KT-46466
    // Also see
    // - https://github.com/voize-gmbh/reakt-native-toolkit/commit/baf0392
    // - https://github.com/gradle-nexus/publish-plugin/issues/208
    val tasks = p.tasks
    // TODO: Take jar tasks from project outputs instead of tasks?
    val jarTasks = arrayOf(
        tasks.withType<JarJvm>(),
        tasks.namedCompat { it.endsWith("Jar") },
    )
    val signingTasks = when {
        !isSigningEnabled -> null
        else -> tasks.withType<Sign> {
            // logger.v("setup signing task '$name' ('$path')")
            jarTasks.forEach { mustRunAfter(it) }
        }
    }
    // AbstractPublishToMaven is a parent for PublishToMavenRepository
    tasks.withType<AbstractPublishToMaven> {
        // logger.v("setup publication task '$name' ('$path')")
        signingTasks?.let { dependsOn(it) } // `shouldRunAfter` is not enough
        jarTasks.forEach { mustRunAfter(it) }
    }

    // Invalidate the jar task when the artifact version changes
    // (e.g., for the GIT HEAD-based snapshots)
    val version = config.version
    jarTasks.forEach {
        it.configureEach {
            inputs.property("fluxoVersion", version)
        }
    }
}

internal fun FluxoKmpConfContext.setupPublishingRepositories(
    p: Project,
    config: FluxoPublicationConfig,
    publishing: PublishingExtension,
    mavenRemoteRepo: Boolean,
) {
    val testLocalRepo = !testsDisabled
    if (!testLocalRepo && !mavenRemoteRepo) {
        return
    }

    publishing.repositories {
        if (testLocalRepo) {
            p.logger.l("'$LOCAL_REPO_NAME' maven repository added for tests and local development")
            maven {
                name = LOCAL_REPO_NAME
                url = p.uri(p.rootProject.file(LOCAL_REPO_PATH))
            }
        }
        if (mavenRemoteRepo) {
            maven {
                val url = config.repositoryUrl
                setUrl(url)
                p.logger.l("setup maven repository: $url")

                credentials {
                    username = config.repositoryUserName
                    password = config.repositoryPassword
                }
            }
        }
    }

    // Use the local repository publication as a check.
    // `publishAllPublicationsToLocalDevRepository`.
    if (testLocalRepo) {
        p.tasks.named(CHECK_TASK_NAME) {
            val repoName = LOCAL_REPO_NAME.uppercaseFirstChar()
            dependsOn("publishAllPublicationsTo${repoName}Repository")
        }
    }
}

internal fun getIfSigningEnabled(config: FluxoPublicationConfig, p: Project): Boolean {
    val notify = signingKeyNotificationLogged.compareAndSet(false, true)
    val isSigningEnabled = config.isSigningEnabled
    if (notify) {
        when {
            isSigningEnabled -> p.logger.l("SIGNING_KEY SET, applying signing configuration")

            // TODO: Warn only when assemble and/or publishing is really called.
            else -> p.logger.w("SIGNING_KEY IS NOT SET! Publications are unsigned")
        }
    }
    return isSigningEnabled
}


private fun FluxoKmpConfContext.setupPublicationExtension(
    p: Project,
    publishing: PublishingExtension,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
    sourceJarTask: Any? = null,
    type: ConfigurationType? = null,
) {
    val javadocTask = setupJavadocTask(p, config, useDokka = useDokka, type)
    publishing.publications.withType<MavenPublication> {
        val pName = name

        config.projectName?.let { projectName ->
            val aid = artifactId
            if (aid.isNullOrBlank() || !aid.startsWith(projectName, ignoreCase = true)) {
                p.logger.l("publication '$pName': artifactId replace ($aid -> $projectName)")
                artifactId = projectName
            }
        }

        // Skip manual artifacts control for Gradle plugins
        val skipArtifacts = type === ConfigurationType.GRADLE_PLUGIN

        val coords = "$groupId:$artifactId:$version"
        val artifacts = if (skipArtifacts) "" else "; artifacts added"
        p.logger.l("setup maven publication '$pName': '$coords'$artifacts")

        if (!skipArtifacts) {
            sourceJarTask?.let { artifact(it) }
            artifact(javadocTask)
        }

        setupPublicationPom(p.project, config)
    }
}


// TODO: Make configurable (+ sanitize/verify)
private const val LOCAL_REPO_PATH = "_/local-repo"
private const val LOCAL_REPO_NAME = "localDev"

/** Also a plugin name! */
private const val SIGNING_EXT_NAME = "signing"

internal const val PUBLISHING_EXT_NAME = "publishing"

private const val GRADLE_PLUGIN_EXT_NAME = "gradlePlugin"

private fun Project.applyMavenPublishPlugin(config: FluxoPublicationConfig): PublishingExtension {
    setProjectPublicationProps(config)
    pluginManager.apply("maven-publish")
    return extensions.getByName<PublishingExtension>(PUBLISHING_EXT_NAME)
}

internal fun Project.setProjectPublicationProps(config: FluxoPublicationConfig) {
    val v = config.version
    if (v.contains("unspecified", ignoreCase = true) || v.isBlank()) {
        throw GradleException("Publication artifact version is not set!")
    }

    if (config.group.isBlank()) {
        throw GradleException("Publication artifact group is not set!")
    }

    group = config.group
    version = v
    config.projectDescription?.let { description = it }

    logger.lifecycle(formatSummary("Publication setup: v$v"))
}

internal val Project.gradlePluginExt: GradlePluginDevelopmentExtension
    get() = extensions.getByName<GradlePluginDevelopmentExtension>(GRADLE_PLUGIN_EXT_NAME)


private fun Project.registerSourceJarTask(sourcePaths: Any): NamedDomainObjectProvider<out Task> {
    val taskName = "sourcesJar"
    val existing = tasks.namedOrNull(taskName)
    val provider = existing ?: tasks.register<Jar>(taskName) {
        from(sourcePaths)
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Assembles a project sources jar."
        archiveClassifier.set("sources")
    }

    // Gradle 8+ workaround
    markAsMustRunAfterBuildConfigTasks(provider)
    return provider
}
