@file:Suppress("TooManyFunctions")

package fluxo.conf.feat

import MAIN_SOURCE_SET_NAME
import com.android.build.gradle.LibraryExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.FluxoConfigurationExtension
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
import fluxo.conf.impl.has
import fluxo.conf.impl.hasExtension
import fluxo.conf.impl.kotlin.multiplatformExtension
import fluxo.conf.impl.l
import fluxo.conf.impl.namedOrNull
import fluxo.conf.impl.register
import fluxo.conf.impl.the
import fluxo.conf.impl.w
import fluxo.conf.impl.withType
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
    configuration: FluxoConfigurationExtensionImpl,
    type: ConfigurationType,
) {
    val config = configuration.publicationConfig
    if (configuration.enablePublication == false || config == null) {
        return
    }
    val context = configuration.context
    val isCalled = context.startTaskNames.any { name ->
        CALL_TASK_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }
    context.onProjectInSyncRun(forceIf = isCalled) {
        configuration.project.setupGradleProjectPublication(config, configuration, type)
    }
}

context(FluxoKmpConfContext)
private fun Project.setupGradleProjectPublication(
    config: FluxoPublicationConfig,
    configuration: FluxoConfigurationExtension,
    type: ConfigurationType,
) {
    val useDokka = configuration.useDokka != false
    when {
        type === ConfigurationType.KOTLIN_MULTIPLATFORM ->
            setupPublicationMultiplatform(config, useDokka)

        type === ConfigurationType.ANDROID_LIB ->
            setupPublicationAndroidLibrary(config, useDokka)

        type === ConfigurationType.GRADLE_PLUGIN ->
            setupPublicationGradlePlugin(config, useDokka)

        type === ConfigurationType.KOTLIN_JVM ->
            setupPublicationKotlinJvm(config, useDokka)

        else -> {
            logger.e("Unsupported project type for publication: $type")

            // Java-only project support is experimental
            if (!SHOW_DEBUG_LOGS || !hasExtension { JavaPluginExtension::class }) {
                return
            }
            setupPublicationJava(config)
        }
    }

    // Reproducible builds setup, produce deterministic output.
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    // https://github.com/JetBrains/kotlin/commit/68fdeaf
    if (configuration.reproducibleArtifacts != false) {
        logger.l("setup reproducibleArtifacts")
        tasks.withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

context(FluxoKmpConfContext)
private fun Project.setupPublicationMultiplatform(
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    // TODO: Distinguish between common and platform-specific publications
    if (!isTargetEnabled(KmpTargetCode.COMMON)) {
        return
    }

    // FIXME: provide sources for KMP publications
    val publishing = applyMavenPublishPlugin(config)
    setupPublicationExtension(
        publishing = publishing,
        config = config,
        useDokka = useDokka,
        type = ConfigurationType.GRADLE_PLUGIN,
    )
    setupPublicationRepositoryAndSigning(config, publishing)

    multiplatformExtension.apply {
        if (targets.any { it.platformType == KotlinPlatformType.androidJvm }) {
            try {
                // Kotlin before 1.9
                @Suppress("DEPRECATION", "KotlinRedundantDiagnosticSuppress")
                android().publishLibraryVariants(RELEASE, DEBUG)
            } catch (e: Throwable) {
                logger.e("android.publishLibraryVariants error: $e", e)
            }
        }
    }
}

context(FluxoKmpConfContext)
private fun Project.setupPublicationAndroidLibrary(
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.ANDROID)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val androidExtension = the<LibraryExtension>()

    val sourcePaths = androidExtension.sourceSets[MAIN_SOURCE_SET_NAME].java.srcDirs
    val sourceJarTask = registerSourceJarTask(sourcePaths)
    val javadocTask = setupJavadocTask(config, useDokka = useDokka)

    fun PublicationContainer.createMavenPublication(name: String, artifactIdSuffix: String) {
        create<MavenPublication>(name) {
            from(components[name])
            artifact(sourceJarTask)
            artifact(javadocTask)

            val project = project
            groupId = config.group
            version = config.version
            artifactId = "${project.name}$artifactIdSuffix"

            setupPublicationPom(project, config)
        }
    }

    afterEvaluate {
        publishing.publications {
            createMavenPublication(name = DEBUG, artifactIdSuffix = "-$DEBUG")
            createMavenPublication(name = RELEASE, artifactIdSuffix = "")
        }
    }

    setupPublicationRepositoryAndSigning(config, publishing)
}

context(FluxoKmpConfContext)
private fun Project.setupPublicationGradlePlugin(
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val gradlePluginExtension = gradlePluginExt.apply {
        config.projectUrl?.let { website.set(it) }
        config.publicationUrl?.let { vcsUrl.set(it) }
    }

    val sourcePaths = gradlePluginExtension.pluginSourceSet.java.srcDirs +
        kotlinExtension.sourceSets[MAIN_SOURCE_SET_NAME].kotlin.srcDirs
    val sourceJarTask = registerSourceJarTask(sourcePaths)

    // TODO: Should wrap `setupPublicationExtension` in afterEvaluate?
    setupPublicationExtension(
        publishing = publishing,
        config = config,
        useDokka = useDokka,
        sourceJarTask = sourceJarTask,
        type = ConfigurationType.GRADLE_PLUGIN,
    )

    setupPublicationRepositoryAndSigning(config, publishing, mavenRepo = false)
}

context(FluxoKmpConfContext)
private fun Project.setupPublicationKotlinJvm(
    config: FluxoPublicationConfig,
    useDokka: Boolean,
) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val sourcePaths = kotlinExtension.sourceSets[MAIN_SOURCE_SET_NAME].kotlin.srcDirs
    val sourceJarTask = registerSourceJarTask(sourcePaths)

    setupPublicationExtension(publishing, config, useDokka = useDokka, sourceJarTask)
    setupPublicationRepositoryAndSigning(config, publishing)
}

context(FluxoKmpConfContext)
private fun Project.setupPublicationJava(config: FluxoPublicationConfig) {
    if (!isTargetEnabled(KmpTargetCode.JVM)) {
        return
    }

    val publishing = applyMavenPublishPlugin(config)

    val javaPluginExtension = the<JavaPluginExtension>()

    val sourcePaths = javaPluginExtension.sourceSets[MAIN_SOURCE_SET_NAME].java.srcDirs
    val sourceJarTask = registerSourceJarTask(sourcePaths)

    setupPublicationExtension(publishing, config, useDokka = false, sourceJarTask)
    setupPublicationRepositoryAndSigning(config, publishing)
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

context(FluxoKmpConfContext)
private fun Project.setupPublicationRepositoryAndSigning(
    config: FluxoPublicationConfig,
    publishing: PublishingExtension,
    mavenRepo: Boolean = true,
) {
    val notify = signingKeyNotificationLogged.compareAndSet(false, true)
    val isSigningEnabled = config.isSigningEnabled
    if (isSigningEnabled) {
        if (notify) logger.l("SIGNING_KEY SET, applying signing configuration")
        pluginManager.apply(SIGNING_EXT_NAME)
    } else if (notify) {
        // TODO: Warn only when assemble and/or publishing is really called.
        logger.w("SIGNING_KEY IS NOT SET! Publications are unsigned")
    }
    if (isSigningEnabled) {
        configureExtension<SigningExtension>(name = SIGNING_EXT_NAME) {
            logger.l("setup publications signing configuration")
            useInMemoryPgpKeys(config.signingKey, config.signingPassword)
            sign(publishing.publications)

            // Signature is a hard requirement if publishing a non-snapshot
            if (!config.isSnapshot) {
                isRequired = true
            }
        }
    }

    publishing.repositories {
        logger.l("setup localDev maven repository")
        maven {
            name = "localDev"
            url = uri(rootProject.file(LOCAL_REPO_PATH))
        }

        if (mavenRepo) {
            maven {
                val url = config.repositoryUrl
                setUrl(url)
                logger.l("setup maven repository: $url")

                credentials {
                    username = config.repositoryUserName
                    password = config.repositoryPassword
                }
            }
        }
    }

    // Gradle 8+ workaround for https://github.com/gradle/gradle/issues/26091
    // and https://youtrack.jetbrains.com/issue/KT-46466
    // Also see
    // - https://github.com/voize-gmbh/reakt-native-toolkit/commit/baf0392
    // - https://github.com/gradle-nexus/publish-plugin/issues/208
    val tasks = tasks
    val jarTasks = arrayOf(
        tasks.withType<JarJvm>(),
        tasks.matching { it.name.endsWith("Jar") },
    )
    val signingTasks = when {
        !isSigningEnabled -> null
        else -> tasks.withType<Sign> {
            // logger.v("setup signing task '$name' ('$path')")
            jarTasks.forEach { shouldRunAfter(it) }
        }
    }
    // AbstractPublishToMaven is a parent for PublishToMavenRepository
    tasks.withType<AbstractPublishToMaven> {
        // logger.v("setup publication task '$name' ('$path')")
        signingTasks?.let { dependsOn(it) } // `shouldRunAfter` is not enough
        jarTasks.forEach { shouldRunAfter(it) }
    }
}


context(FluxoKmpConfContext)
private fun Project.setupPublicationExtension(
    publishing: PublishingExtension,
    config: FluxoPublicationConfig,
    useDokka: Boolean,
    sourceJarTask: Any? = null,
    type: ConfigurationType? = null,
) {
    val javadocTask = setupJavadocTask(config, useDokka = useDokka, type)
    publishing.publications.withType<MavenPublication> {
        val pName = name

        config.projectName?.let { projectName ->
            val aid = artifactId
            if (aid.isNullOrBlank() || !aid.startsWith(projectName, ignoreCase = true)) {
                logger.l("publication '$pName': artifactId replace ($aid -> $projectName)")
                artifactId = projectName
            }
        }

        // Skip manual artifacts control for Gradle plugins
        val skipArtifacts = type === ConfigurationType.GRADLE_PLUGIN

        val coords = "$groupId:$artifactId:$version"
        val artifacts = if (skipArtifacts) "" else "; artifacts added"
        logger.l("setup maven publication '$pName': '$coords'$artifacts")

        if (!skipArtifacts) {
            sourceJarTask?.let { artifact(it) }
            artifact(javadocTask)
        }

        setupPublicationPom(project, config)
    }
}


// TODO: Make configurable
private const val LOCAL_REPO_PATH = "_/local-repo"

/** Also a plugin name! */
private const val SIGNING_EXT_NAME = "signing"

private const val PUBLISHING_EXT_NAME = "publishing"

private const val GRADLE_PLUGIN_EXT_NAME = "gradlePlugin"

private fun Project.applyMavenPublishPlugin(config: FluxoPublicationConfig): PublishingExtension {
    logger.l("setupPublication")

    if (config.group.isBlank()) {
        throw GradleException("Publication artifact group is not set!")
    }

    group = config.group
    version = config.version
    description = config.projectDescription

    pluginManager.apply("maven-publish")
    return extensions.getByName<PublishingExtension>(PUBLISHING_EXT_NAME)
}

internal val Project.gradlePluginExt: GradlePluginDevelopmentExtension
    get() = extensions.getByName<GradlePluginDevelopmentExtension>(GRADLE_PLUGIN_EXT_NAME)


context(FluxoKmpConfContext)
private fun Project.setupJavadocTask(
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
                project = this,
            )
            if (result.applied) {
                logger.l("setup Dokka publication")
                return getOrCreateDokkaTask(type)
            }
        } catch (e: Throwable) {
            logger.w("Fallback to Javadoc due to Dokka setup error: $e", e)
        }
    }
    logger.l("setup Javadoc publication fallback")
    return getOrCreateJavadocTask()
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
                // Use Javadoc-like format for all non-KMP projects
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
