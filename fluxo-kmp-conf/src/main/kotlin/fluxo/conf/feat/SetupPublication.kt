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
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.create
import fluxo.conf.impl.get
import fluxo.conf.impl.getByName
import fluxo.conf.impl.has
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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
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

// TODO: Replace Gradle Plugin publishing with something less broken (official is just a mess)?
//  https://github.com/vanniktech/gradle-maven-publish-plugin
//  https://github.com/adamko-dev/dokkatoo/issues/61#issuecomment-1701156702
//  https://github.com/adamko-dev/dokkatoo/blob/b1ca20c/buildSrc/src/main/kotlin/buildsrc/conventions/maven-publishing.gradle.kts

// TODO: Decorate the build logs with maven coordinates of published artifacts
//  https://github.com/gmazzo/gradle-report-publications-plugin

private val CALL_TASK_PREFIXES = arrayOf(
    "publish", "upload", "deploy", "release", "ship", "distribute", "check", "verify", "install",
)

internal fun setupGradleProjectPublication(
    conf: FluxoConfigurationExtensionImpl,
) {
    // TODO: Avoid publication setup when possible
    val config = conf.publicationConfig
    if (conf.enablePublication == false || config == null) {
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
    val useDokka = conf.useDokka == false
    when (val type = conf.mode) {
        ConfigurationType.KOTLIN_MULTIPLATFORM ->
            setupPublicationMultiplatform(p, config, useDokka)

        ConfigurationType.ANDROID_LIB ->
            setupPublicationAndroidLibrary(p, config, useDokka)

        ConfigurationType.GRADLE_PLUGIN ->
            setupPublicationGradlePlugin(p, config, useDokka)

        ConfigurationType.KOTLIN_JVM ->
            setupPublicationKotlinJvm(p, config, useDokka)

        else -> {
            p.logger.e("Unsupported project type for publication: $type")

            // Java-only project support is experimental
            if (!SHOW_DEBUG_LOGS || !p.hasExtension { JavaPluginExtension::class }) {
                return
            }
            setupPublicationJava(p, config)
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
                // Kotlin before 1.9
                @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
                android().publishLibraryVariants(RELEASE, DEBUG)
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

    setupPublicationRepositoryAndSigning(p, config, publishing, mavenRepo = false)
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
) {
    project.logger.l("setup maven POM for '$name': '${config.projectName}'")
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

private fun FluxoKmpConfContext.setupPublicationRepositoryAndSigning(
    p: Project,
    config: FluxoPublicationConfig,
    publishing: PublishingExtension,
    mavenRepo: Boolean = true,
) {
    val notify = signingKeyNotificationLogged.compareAndSet(false, true)
    val isSigningEnabled = config.isSigningEnabled
    if (isSigningEnabled) {
        if (notify) p.logger.l("SIGNING_KEY SET, applying signing configuration")
        p.pluginManager.apply(SIGNING_EXT_NAME)
    } else if (notify) {
        // TODO: Warn only when assemble and/or publishing is really called.
        p.logger.w("SIGNING_KEY IS NOT SET! Publications are unsigned")
    }
    if (isSigningEnabled) {
        p.configureExtension<SigningExtension>(name = SIGNING_EXT_NAME) {
            p.logger.l("setup publications signing configuration")
            this.useInMemoryPgpKeys(config.signingKey, config.signingPassword)
            this.sign(publishing.publications)

            // Signature is a hard requirement if publishing a non-snapshot
            if (!config.isSnapshot) {
                this.isRequired = true
            }
        }
    }

    if (!testsDisabled) {
        publishing.repositories {
            p.logger.l("'$LOCAL_REPO_NAME' maven repository added for tests and local development")
            maven {
                name = LOCAL_REPO_NAME
                url = p.uri(p.rootProject.file(LOCAL_REPO_PATH))
            }

            if (mavenRepo) {
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
        p.tasks.named(CHECK_TASK_NAME) {
            val repoName = LOCAL_REPO_NAME.uppercaseFirstChar()
            dependsOn("publishAllPublicationsTo${repoName}Repository")
        }
    }

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


// TODO: Make configurable (sanitize/verify)
private const val LOCAL_REPO_PATH = "_/local-repo"
private const val LOCAL_REPO_NAME = "localDev"

/** Also a plugin name! */
private const val SIGNING_EXT_NAME = "signing"

private const val PUBLISHING_EXT_NAME = "publishing"

private const val GRADLE_PLUGIN_EXT_NAME = "gradlePlugin"

private fun Project.applyMavenPublishPlugin(config: FluxoPublicationConfig): PublishingExtension {
    if (config.group.isBlank()) {
        throw GradleException("Publication artifact group is not set!")
    }

    val v = config.version
    if (v.contains("unspecified", ignoreCase = true)) {
        throw GradleException("Publication artifact version is not set!")
    }

    group = config.group
    version = v
    description = config.projectDescription

    logger.lifecycle(formatSummary("Publication setup: v$v"))

    pluginManager.apply("maven-publish")
    return extensions.getByName<PublishingExtension>(PUBLISHING_EXT_NAME)
}

internal val Project.gradlePluginExt: GradlePluginDevelopmentExtension
    get() = extensions.getByName<GradlePluginDevelopmentExtension>(GRADLE_PLUGIN_EXT_NAME)


private fun FluxoKmpConfContext.setupJavadocTask(
    p: Project,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
    type: ConfigurationType? = null,
): NamedDomainObjectProvider<out Task> {
    // Publish docs with each artifact.
    // Dokka artifacts are pretty big, so use them only for release builds, not for snapshots.
    if (useDokka && !config.isSnapshot) {
        try {
            // Apply Dokka plugin first
            val result = loadAndApplyPluginIfNotApplied(
                id = BuildConstants.DOKKA_PLUGIN_ID,
                version = BuildConstants.DOKKA_PLUGIN_VERSION,
                catalogPluginId = BuildConstants.DOKKA_PLUGIN_ALIAS,
                project = p,
            )
            if (result.applied) {
                p.logger.l("setup Dokka publication")
                return p.getOrCreateDokkaTask(type)
            }
        } catch (e: Throwable) {
            p.logger.w("Fallback to Javadoc due to Dokka setup error: $e", e)
        }
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
