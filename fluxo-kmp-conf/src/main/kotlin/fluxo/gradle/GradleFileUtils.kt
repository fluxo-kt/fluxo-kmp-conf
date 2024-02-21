@file:Suppress("TooManyFunctions")

package fluxo.gradle

import fluxo.conf.impl.isWindowsOs
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal fun Provider<String>.toDir(project: Project): Provider<Directory> =
    project.layout.dir(map { File(it) })

internal fun Provider<File>.fileToDir(project: Project): Provider<Directory> =
    project.layout.dir(this)

internal fun Provider<Directory>.file(relativePath: String): Provider<RegularFile> =
    map { it.file(relativePath) }

internal fun Provider<Directory>.dir(relativePath: String): Provider<Directory> =
    map { it.dir(relativePath) }

internal val <T : FileSystemLocation> Provider<T>.ioFile: File
    get() = get().asFile

internal val <T : FileSystemLocation> Provider<T>.ioFileOrNull: File?
    get() = orNull?.asFile

internal fun FileSystemOperations.delete(vararg files: Any) {
    delete { delete(*files) }
}

internal fun FileSystemOperations.mkdirs(vararg dirs: File) {
    for (dir in dirs) {
        dir.mkdirs()
    }
}

internal fun FileSystemOperations.mkdirs(vararg dirs: Provider<out FileSystemLocation>) {
    mkdirs(*dirs.ioFiles())
}

internal fun FileSystemOperations.clearDirs(vararg dirs: File) {
    delete(*dirs)
    mkdirs(*dirs)
}

internal fun FileSystemOperations.clearDirs(vararg dirs: Provider<out FileSystemLocation>) {
    clearDirs(*dirs.ioFiles())
}

private fun Array<out Provider<out FileSystemLocation>>.ioFiles(): Array<File> =
    let { providers -> Array(size) { i -> providers[i].ioFile } }


internal fun File.mangledName(): String = buildString {
    append(nameWithoutExtension)
    append("-")
    append(contentHash())
    val ext = extension
    if (ext.isNotBlank()) {
        append(".$ext")
    }
}

internal fun File.contentHash(): String {
    val md5 = MessageDigest.getInstance("MD5")
    if (isDirectory) {
        walk().filter { it.isFile }.sortedBy { it.relativeTo(this).path }
            .forEach { md5.digestContent(it) }
    } else {
        md5.digestContent(this)
    }
    val digest = md5.digest()
    return buildString(digest.size * 2) {
        for (byte in digest) {
            @Suppress("MagicNumber")
            append(Integer.toHexString(0xFF and byte.toInt()))
        }
    }
}

private fun MessageDigest.digestContent(file: File) {
    file.inputStream().buffered().use { fis ->
        DigestInputStream(fis, this).use { ds ->
            @Suppress("ControlFlowWithEmptyBody", "EmptyWhileBlock")
            while (ds.read() != -1) {
            }
        }
    }
}


internal fun File.normalizedPath(base: File? = null): String {
    val path = base?.let { relativeToOrNull(it)?.path } ?: absolutePath
    return when {
        isWindowsOs -> path.replace("\\", "\\\\")
        else -> path
    }
}
