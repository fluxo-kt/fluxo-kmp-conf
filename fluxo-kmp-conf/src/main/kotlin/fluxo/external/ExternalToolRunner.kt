package fluxo.external

import fluxo.gradle.ioFile
import fluxo.util.alsoOutputTo
import fluxo.util.readableByteSize
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

@Suppress("LongParameterList")
internal class ExternalToolRunner(
    private val verbose: Property<Boolean>,
    private val logsDir: Provider<Directory>,
    private val execOperations: ExecOperations,
    private val alwaysPrintErrorOutput: Boolean = true,
) {
    internal enum class LogToConsole {
        Always, Never, OnlyWhenVerbose
    }

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

        val toolName = tool.nameWithoutExtension
        val timeStamp = currentTimeStamp()
        val outFile = logsDir.resolve("$toolName-$timeStamp-out.txt")
        val errFile = logsDir.resolve("$toolName-$timeStamp-err.txt")
        logsDir.mkdirs()

        val doLogToConsole = when (logToConsole) {
            LogToConsole.Always -> true
            LogToConsole.Never -> false
            LogToConsole.OnlyWhenVerbose -> verbose.get()
        }

        val result = execute(
            outFile,
            errFile,
            tool,
            args,
            workingDir,
            environment,
            stdinStr,
            doLogToConsole,
        )

        val exitCodeIsNormal = result.exitValue == 0
        if (checkExitCodeIsNormal && !exitCodeIsNormal) {
            throwError(tool, args, workingDir, result, outFile, errFile)
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

    private fun execute(
        outFile: File,
        errFile: File,
        tool: File,
        args: Collection<String>,
        workingDir: File?,
        environment: Map<String, Any>,
        stdinStr: String?,
        doLogToConsole: Boolean,
    ): ExecResult = outFile.outputStream().buffered().use { outFileStream ->
        errFile.outputStream().buffered().use { errFileStream ->
            execOperations.exec {
                executable = tool.absolutePath
                args(args)
                workingDir?.let { wd -> workingDir(wd) }
                environment(environment)
                // check exit value later
                isIgnoreExitValue = true

                if (stdinStr != null) {
                    standardInput = ByteArrayInputStream(stdinStr.toByteArray())
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

    private fun throwError(
        tool: File,
        args: Collection<String>,
        workingDir: File?,
        result: ExecResult,
        outFile: File,
        errFile: File,
    ) {
        val errMsg = buildString {
            appendLine("External tool execution failed:")
            appendLine("* Working dir: [${workingDir?.absolutePath.orEmpty()}]")
            val cmd = args.joinToString(" ", prefix = " ")
            appendLine("* Command: ${tool.absolutePath}$cmd")
            appendLine("* Exit code: ${result.exitValue}")
            readableByteSize(outFile).takeIf { it != "0 B" }?.let { outSize ->
                appendLine("* Standard output log ($outSize): \"${outFile.absolutePath}\"")
            }
            readableByteSize(errFile).takeIf { it != "0 B" }?.let { errSize ->
                appendLine("* Error log ($errSize): \"${errFile.absolutePath}\"")
            }

            // Always pint stderr and stdout if the tool failed.
            // Stderr can be duplicated with 'alwaysPrintErrorOutput',
            // but it's better to output it twice than not to output it at all.
            appendLogFile(errFile, "Standard error output")
            appendLogFile(outFile, "Tool output")
        }
        throw GradleException(errMsg)
    }

    private fun StringBuilder.appendLogFile(file: File, title: String) {
        val out = file.readText().trim()
        if (out.isEmpty()) {
            return
        }
        appendLine("\n* ").append(title).append(": ")
        if (out.length > LOG_LIMIT) {
            append("(truncated)\n ...").appendLine(out.takeLast(LOG_LIMIT).trimStart())
        } else {
            appendLine(out)
        }
    }

    private fun currentTimeStamp() = LocalDateTime.now().format(TIME_STAMP_PATTERN)

    private companion object {
        private val TIME_STAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        private const val LOG_LIMIT = 4096
    }
}
