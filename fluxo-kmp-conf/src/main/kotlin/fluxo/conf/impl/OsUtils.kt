@file:Suppress("MatchingDeclarationName")

package fluxo.conf.impl

import java.io.File
import org.gradle.api.provider.Provider

internal enum class OS {
    Linux,
    Windows,
    MacOS
}

internal val currentOS: OS by lazy {
    val os = System.getProperty("os.name")
    when {
        os.equals("Mac OS X", ignoreCase = true) -> OS.MacOS
        os.startsWith("Win", ignoreCase = true) -> OS.Windows
        os.startsWith("Linux", ignoreCase = true) -> OS.Linux
        else -> error("Unknown OS name: $os")
    }
}

internal fun executableName(nameWithoutExtension: String): String =
    if (currentOS == OS.Windows) "$nameWithoutExtension.exe" else nameWithoutExtension

internal fun javaExecutable(javaHome: String): String =
    File(javaHome).resolve("bin/${executableName("java")}").absolutePath

internal fun jvmToolFile(toolName: String, javaHome: Provider<String>): File =
    jvmToolFile(toolName, File(javaHome.get()))

internal fun jvmToolFile(toolName: String, javaHome: File): File {
    val jtool = javaHome.resolve("bin/${executableName(toolName)}")
    check(jtool.isFile) {
        "Invalid JDK: $jtool is not a file! \n" +
            "Ensure JAVA_HOME or buildSettings.javaHome " +
            "is set to JDK $MIN_JAVA_RUNTIME_VERSION or newer"
    }
    return jtool
}

private const val MIN_JAVA_RUNTIME_VERSION = 17
