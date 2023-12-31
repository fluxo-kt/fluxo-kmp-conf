package fluxo.external

import fluxo.util.alsoOutputTo
import fluxo.gradle.ioFile
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult

internal class ExternalToolRunner(
    private val verbose: Property<Boolean>,
    private val logsDir: Provider<Directory>,
    private val execOperations: ExecOperations,
    private val alwaysPrintErrorOutput: Boolean = true,
) {
    internal enum class LogToConsole {
        Always, Never, OnlyWhenVerbose
    }

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    operator fun invoke(
        tool: File,
        args: Collection<String>,
        environment: Map<String, Any> = emptyMap(),
        workingDir: File? = null,
        checkExitCodeIsNormal: Boolean = true,
        logToConsole: LogToConsole = LogToConsole.OnlyWhenVerbose,
        stdinStr: String? = null,
        processStdout: ((String) -> Unit)? = null,
    ): ExecResult {
        val logsDir = logsDir.ioFile
        logsDir.mkdirs()

        val toolName = tool.nameWithoutExtension
        val timeStamp = currentTimeStamp()
        val outFile = logsDir.resolve("$toolName-$timeStamp-out.txt")
        val errFile = logsDir.resolve("$toolName-$timeStamp-err.txt")

        val result = outFile.outputStream().buffered().use { outFileStream ->


            errFile.outputStream().buffered().use { errFileStream ->
                execOperations.exec {
                    executable = tool.absolutePath
                    args(*args.toTypedArray())
                    workingDir?.let { wd -> workingDir(wd) }
                    environment(environment)
                    // check exit value later
                    isIgnoreExitValue = true

                    if (stdinStr != null) {
                        standardInput = ByteArrayInputStream(stdinStr.toByteArray())
                    }

                    val doLogToConsole = when (logToConsole) {
                        LogToConsole.Always -> true
                        LogToConsole.Never -> false
                        LogToConsole.OnlyWhenVerbose -> verbose.get()
                    }
                    if (doLogToConsole) {
                        standardOutput = standardOutput.alsoOutputTo(outFileStream)
                        errorOutput = errorOutput.alsoOutputTo(errFileStream)
                    } else {
                        standardOutput = outFileStream
                        errorOutput = when {
                            !alwaysPrintErrorOutput -> errFileStream
                            else -> errorOutput.alsoOutputTo(errFileStream)
                        }
                    }
                }
            }
        }

        val exitCodeIsNormal = result.exitValue == 0
        if (checkExitCodeIsNormal && !exitCodeIsNormal) {
            val errMsg = buildString {
                appendLine("External tool execution failed:")
                val cmd = (listOf(tool.absolutePath) + args).joinToString(", ")
                appendLine("* Command: [$cmd]")
                appendLine("* Working dir: [${workingDir?.absolutePath.orEmpty()}]")
                appendLine("* Exit code: ${result.exitValue}")
                appendLine("* Standard output log: ${outFile.absolutePath}")
                appendLine("* Error log: ${errFile.absolutePath}")
            }
            throw GradleException(errMsg)
        }

        if (processStdout != null) {
            processStdout(outFile.readText())
        }

        if (exitCodeIsNormal) {
            outFile.delete()
            errFile.delete()
        }

        return result
    }

    private fun currentTimeStamp() = LocalDateTime.now().format(TIME_STAMP_PATTERN)

    private companion object {
        private val TIME_STAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
    }
}
