package fluxo.conf.pub

import com.vanniktech.maven.publish.AndroidMultiVariantLibrary
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.GradlePublishPlugin
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.VersionCatalog
import envOrPropValue
import fluxo.conf.data.BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_ID
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.getByName
import fluxo.conf.impl.kotlin.KOTLIN_JVM_PLUGIN_ID
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.conf.impl.withType
import fluxo.log.l
import fluxo.log.w
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// https://vanniktech.github.io/gradle-maven-publish-plugin/central/
// https://vanniktech.github.io/gradle-maven-publish-plugin/other/
// https://vanniktech.github.io/gradle-maven-publish-plugin/what/


/**
 *
 * @see com.vanniktech.maven.publish.MavenPublishPlugin
 * @see com.vanniktech.maven.publish.MavenPublishBasePlugin
 * @see org.gradle.api.publish.maven.plugins.MavenPublishPlugin
 * @see MavenPublishBaseExtension.configureBasedOnAppliedPlugins
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
internal fun MavenPublishBaseExtension.setupVanniktechPublication(
    p: Project,
    config: FluxoPublicationConfig,
    conf: FluxoConfigurationExtensionImpl,
) {
    // Vaniktech already can set everything up based on applied plugins,
    // but let's be sure, plus add some additional configuration here.

    /** @see MavenPublishBaseExtension.configureBasedOnAppliedPlugins */
    val plugins = p.plugins
    val mode = conf.mode
    when {
        mode == ConfigurationType.KOTLIN_MULTIPLATFORM ||
            plugins.hasPlugin(KOTLIN_MPP_PLUGIN_ID) -> {
            // TODO: Distinguish between common and platform-specific publications
            if (!conf.ctx.isTargetEnabled(KmpTargetCode.COMMON)) {
                return
            }
            setupVanniktechKmpPublication(conf, p, config)
        }

        mode == ConfigurationType.ANDROID_LIB || plugins.hasPlugin(ANDROID_LIB_PLUGIN_ID) -> {
            if (!conf.ctx.isTargetEnabled(KmpTargetCode.ANDROID)) {
                return
            }
            setupVanniktechAndroidLibPublication(p)
        }

        else -> {
            if (!conf.ctx.isTargetEnabled(KmpTargetCode.JVM)) {
                return
            }
            when {
                mode == ConfigurationType.GRADLE_PLUGIN ||
                    plugins.hasPlugin(GRADLE_PLUGIN_PUBLISH_PLUGIN_ID) ->
                    configure(GradlePublishPlugin())

                mode == ConfigurationType.KOTLIN_JVM ||
                    plugins.hasPlugin(KOTLIN_JVM_PLUGIN_ID) ->
                    setupVanniktechKotlinJvmPublication(p, config, conf)

                plugins.hasPlugin("java-gradle-plugin") ->
                    configure(GradlePlugin(vanniktechJavaDocOption(p, config, conf)))

                plugins.hasPlugin("java-library") || plugins.hasPlugin("java") ->
                    configure(JavaLibrary(vanniktechJavaDocOption(p, config, conf)))

                plugins.hasPlugin("java-platform") -> configure(JavaPlatform())
                plugins.hasPlugin("version-catalog") -> configure(VersionCatalog())

                else -> p.logger.w("No compatible plugin found in project ${p.path} for publishing")
            }
        }
    }

    coordinates(config.group, config.projectName, config.version)
    pom { setupPublicationPom(p, config) }

    // Set up a local test repo,
    // Vanniktech's plugin handles remote repos.
    val publishing = p.extensions.getByName<PublishingExtension>(PUBLISHING_EXT_NAME)
    conf.ctx.setupPublishingRepositories(p, config, publishing, mavenRemoteRepo = false)

    if (getIfSigningEnabled(config, p)) {
        p.logger.l("setup publications signing configuration")

        // Set Vanniktech's props first as a compatibility measure.
        val extra = p.extensions.extraProperties
        config.signingKey?.let { extra["signingInMemoryKey"] = it }
        config.signingKeyId?.let { extra["signingInMemoryKeyId"] = it }
        config.signingPassword?.let { extra["signingInMemoryKeyPassword"] = it }

        signAllPublications()
    }

    when (mode) {
        ConfigurationType.GRADLE_PLUGIN -> {
            p.gradlePluginExt.apply {
                (config.projectUrl ?: config.publicationUrl)
                    ?.let { website.set(it) }
                config.publicationUrl?.let { vcsUrl.set(it) }
            }
        }

        ConfigurationType.IDEA_PLUGIN -> {}

        else -> publishToMavenCentral(
            host = config.sonatypeHost?.let { it as SonatypeHost } ?: SonatypeHost.DEFAULT,
            automaticRelease = true,
        )
    }

    // Log publications
    // TODO: https://github.com/gmazzo/gradle-report-publications-plugin
    publishing.publications.withType<MavenPublication> {
        p.logger.l("maven publication '$name': '$groupId:$artifactId:$version'")
    }
}


private const val ANDROID_VARIANT_TO_PUBLISH_KEY = "ANDROID_VARIANT_TO_PUBLISH"

private fun MavenPublishBaseExtension.setupVanniktechKmpPublication(
    conf: FluxoConfigurationExtensionImpl,
    p: Project,
    config: FluxoPublicationConfig,
) {
    val variant = p.envOrPropValue(ANDROID_VARIANT_TO_PUBLISH_KEY)
    val androidVariantsToPublish = when {
        variant.isNullOrEmpty() -> listOf(RELEASE, DEBUG)
        else -> listOf(variant)
    }
    val javadocJar = vanniktechJavaDocOption(
        p = p,
        config = config,
        conf = conf,
        plainJavadocSupported = false,
        applyDokka = true,
    )
    configure(
        KotlinMultiplatform(
            javadocJar = javadocJar,
            sourcesJar = true,
            androidVariantsToPublish = androidVariantsToPublish,
        ),
    )
}

private fun MavenPublishBaseExtension.setupVanniktechAndroidLibPublication(p: Project) {
    val variant = p.envOrPropValue(ANDROID_VARIANT_TO_PUBLISH_KEY)
    val platform = when {
        variant.isNullOrEmpty() -> AndroidMultiVariantLibrary()
        else -> AndroidSingleVariantLibrary(variant)
    }
    configure(platform)
}

private fun MavenPublishBaseExtension.setupVanniktechKotlinJvmPublication(
    p: Project,
    config: FluxoPublicationConfig,
    conf: FluxoConfigurationExtensionImpl,
) {
    val javadocJar = vanniktechJavaDocOption(
        p = p,
        config = config,
        conf = conf,
        applyDokka = true,
    )
    configure(KotlinJvm(javadocJar))
}
