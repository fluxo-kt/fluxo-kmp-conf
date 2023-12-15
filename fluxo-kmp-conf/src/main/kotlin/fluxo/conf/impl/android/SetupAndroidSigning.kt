package fluxo.conf.impl.android

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import fluxo.conf.impl.get
import fluxo.conf.impl.l
import java.io.File
import java.util.Properties
import org.gradle.api.Project

// TODO: Support for ENV variables instead of file for more straightforward CI/CD

/** Configure signing dynamically with `signing.properties` file */
internal fun ApplicationExtension.setupSigningIn(project: Project) {
    signingConfigs {
        val debug = get(DEBUG)
        debug.enableMaxSigning()

        var releaseConfigured = false
        var signPropsFile = project.rootProject.file("signing.properties")
        if (!signPropsFile.canRead()) {
            signPropsFile = project.file("signing.properties")
        }

        if (signPropsFile.canRead()) {
            val properties = Properties()
            signPropsFile.inputStream().use { properties.load(it) }

            val alias = properties["keyAlias"]?.toString()
            if (!alias.isNullOrEmpty()) {
                releaseConfigured = alias == RELEASE
                @Suppress("UnnecessaryApply")
                maybeCreate(alias).apply {
                    configureFrom(properties, project, alias, release = false)
                }
            }
            if (!releaseConfigured) {
                val releaseKeyAlias = properties["releaseKeyAlias"]?.toString()
                if (!releaseKeyAlias.isNullOrEmpty()) {
                    releaseConfigured = true
                    create(RELEASE) {
                        configureFrom(properties, project, releaseKeyAlias, release = true)
                    }
                }
            }
        }

        // prefill release configuration (at least with debug signing)
        if (!releaseConfigured) {
            create(RELEASE) {
                project.logger.l(":signing: '$name' configuration copied from 'debug'")
                storeFile = debug.storeFile
                keyAlias = debug.keyAlias
                storePassword = debug.storePassword
                keyPassword = debug.keyPassword
                enableMaxSigning()
            }
        }
    }
}

private fun ApkSigningConfig.configureFrom(
    properties: Properties,
    project: Project,
    alias: String,
    release: Boolean,
) {
    project.logger.l(":signing: '$name' configuration loaded from properties")
    keyAlias = alias

    val storeFileKey = if (release) "releaseKeystorePath" else "keystorePath"
    storeFile = project.getKeystoreFile(properties[storeFileKey])

    val passwordKey = if (release) "releaseKeystorePassword" else "keystorePassword"
    val password = properties[passwordKey]?.toString()
    storePassword = password

    val keyPasswordKey = if (release) "releaseKeyPassword" else "keyPassword"
    keyPassword = properties[keyPasswordKey]?.toString()
        .orEmpty().ifEmpty { password }

    enableMaxSigning()
}

private fun Project.getKeystoreFile(rawPath: Any?): File {
    val keystorePath = rawPath.toString()
    var file = rootProject.file(keystorePath).absoluteFile
    if (!file.canRead() || !file.exists()) {
        file = project.file(keystorePath).absoluteFile
    }
    return file
}

private fun ApkSigningConfig.enableMaxSigning() {
    enableV1Signing = true
    enableV2Signing = true
    try {
        enableV3Signing = true
        enableV4Signing = true
    } catch (_: Throwable) {
    }
}
