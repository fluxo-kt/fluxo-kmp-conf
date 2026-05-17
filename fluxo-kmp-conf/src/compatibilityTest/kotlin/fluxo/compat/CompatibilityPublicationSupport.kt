package fluxo.compat

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal fun localMavenRepoPath(): String {
    val path = checkNotNull(System.getProperty("fluxo.local.maven.repo")) {
        "fluxo.local.maven.repo system property is missing"
    }
    val repo = File(path)
    check(repo.isDirectory) {
        "Local Maven repository is missing: ${repo.absolutePath}"
    }
    assertPublishedArtifacts(repo.toPath())
    return repo.invariantSeparatorsPath
}

internal fun assertPublishedArtifacts(repo: Path) {
    val pluginId = pluginId()
    val version = pluginVersion()
    val markerPom = repo
        .resolve(pluginId.replace('.', File.separatorChar))
        .resolve("$pluginId.gradle.plugin")
        .resolve(version)
        .resolve("$pluginId.gradle.plugin-$version.pom")
    val runtimeJar = repo
        .resolve("io/github/fluxo-kt/fluxo-kmp-conf")
        .resolve(version)
        .resolve("fluxo-kmp-conf-$version.jar")
    val runtimePom = runtimeJar.resolveSibling("fluxo-kmp-conf-$version.pom")
    val runtimeModule = runtimeJar.resolveSibling("fluxo-kmp-conf-$version.module")
    check(Files.isRegularFile(markerPom)) {
        "Published plugin marker POM is missing: $markerPom"
    }
    check(Files.isRegularFile(runtimeJar)) {
        "Published plugin runtime jar is missing: $runtimeJar"
    }
    check(Files.isRegularFile(runtimePom)) {
        "Published plugin runtime POM is missing: $runtimePom"
    }
    check(Files.isRegularFile(runtimeModule)) {
        "Published plugin Gradle module metadata is missing: $runtimeModule"
    }
    assertNoForbiddenRuntimeLeaks(markerPom, runtimePom, runtimeModule)
}

internal fun assertNoForbiddenRuntimeLeaks(vararg metadataFiles: Path) {
    metadataFiles.forEach { file ->
        val metadata = Files.readString(file)
        FORBIDDEN_RUNTIME_LEAKS.forEach { forbidden ->
            check(forbidden !in metadata) {
                "Published metadata leaks forbidden runtime dependency '$forbidden': $file"
            }
        }
    }
}

internal fun assertNoForbiddenResolvedClasspathLeaks(projectDir: Path) {
    val classpath = projectDir.resolve("dependencies/classpath.txt")
    check(Files.isRegularFile(classpath)) {
        "Resolved classpath dependencyGuard output is missing: $classpath"
    }
    val dependencies = Files.readString(classpath)
    FORBIDDEN_RUNTIME_LEAKS.forEach { forbidden ->
        check(forbidden !in dependencies) {
            "Resolved marker consumer classpath leaks forbidden dependency '$forbidden': " +
                classpath
        }
    }
}

internal fun pluginId(): String =
    checkNotNull(System.getProperty("fluxo.plugin.id")) {
        "fluxo.plugin.id system property is missing"
    }

internal fun pluginVersion(): String =
    checkNotNull(System.getProperty("fluxo.plugin.version")) {
        "fluxo.plugin.version system property is missing"
    }
