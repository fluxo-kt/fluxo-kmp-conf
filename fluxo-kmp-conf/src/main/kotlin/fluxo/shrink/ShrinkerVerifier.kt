package fluxo.shrink

import fluxo.conf.impl.w
import java.io.File
import java.lang.System.currentTimeMillis
import java.net.URLClassLoader
import java.util.jar.JarFile
import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.kotlin.util.getValueOrNull

@Suppress("LongParameterList")
internal class ShrinkerVerifier(
    private val taskName: String,
    private val mainJarFile: File,
    private val signatures: Map<String, ClassSignature>,
    override val proc: TestResultProcessor,
    override val logger: Logger,
    override val continueOnFailure: Boolean,
    private val classLoader: URLClassLoader,
) : ApiVerifier, AutoCloseable {

    @Volatile
    private var stopped = false

    @Volatile
    override var verified = true

    @Volatile
    override lateinit var currentTestClass: String

    @Volatile
    override var currentTestMethod: String = ""

    @Volatile
    override var currentTestFile: String? = null

    @Volatile
    override lateinit var currentClassTestDescriptor: TestDescriptorInternal

    @Volatile
    override lateinit var currentTestDescriptor: TestDescriptorInternal

    @Volatile
    private var processor: String = ""

    private val mainJar = lazy { JarFile(mainJarFile) }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ThrowsCount")
    fun verify() {
        var lastEx: Throwable? = null

        val suiteDescr = DefaultTestSuiteDescriptor(taskName, ":$taskName, automatic verification")
        proc.started(suiteDescr, TestStartEvent(currentTimeMillis()))

        for ((className, signature) in signatures) {
            if (checkStop()) {
                break
            }

            val classDescr = classTestDescriptor(className, suiteDescr)

            var classEx: Throwable? = null
            try {
                processor = "ASM"
                verifyApiWithAsm(mainJar.value, signature)
            } catch (e: GradleException) {
                throw e
            } catch (e: Throwable) {
                classEx = e
            }

            try {
                processor = "Reflection"
                verifyApiWithReflection(classLoader, signature)
            } catch (e: GradleException) {
                throw e
            } catch (e: Throwable) {
                // Skip the error caused by internal classes.
                // This is a known issue with Gradle plugins.
                @Suppress("InstanceOfCheckForException")
                val showWarning = e is NoClassDefFoundError &&
                    e.message?.contains("/internal/") == true

                if (showWarning) {
                    handleError(e, className, warn = true)
                } else {
                    when (classEx) {
                        null -> classEx = e
                        else -> classEx.addSuppressed(e)
                    }
                }
            }

            if (classEx != null) {
                verified = false
                handleError(classEx, className)
                lastEx = classEx
            }

            val resultType = when (classEx) {
                null -> null
                else -> TestResult.ResultType.FAILURE
            }
            proc.completed(classDescr.id, TestCompleteEvent(currentTimeMillis(), resultType))
        }

        val resultType = when {
            verified && lastEx == null -> null
            else -> TestResult.ResultType.FAILURE
        }
        proc.completed(suiteDescr.id, TestCompleteEvent(currentTimeMillis(), resultType))

        if (!verified) {
            throw GradleException("Verification failed", lastEx)
        }
    }

    private fun classTestDescriptor(
        className: String,
        suiteDescr: TestDescriptorInternal,
    ): VerifierClassDescriptor {
        currentTestClass = className
        currentTestFile = className.replace('.', '/') + ".kt"
        currentTestMethod = ""

        val classDescr = VerifierClassDescriptor(taskName, className, suiteDescr)
        currentClassTestDescriptor = classDescr
        currentTestDescriptor = classDescr
        proc.started(classDescr, TestStartEvent(currentTimeMillis(), suiteDescr.id))
        return classDescr
    }

    override fun methodTestDescriptor(methodName: String): TestDescriptorInternal {
        currentTestMethod = methodName
        val clazz = currentTestClass
        val parent = currentClassTestDescriptor
        val id = "${taskName}_$clazz#$methodName;$processor"
        return VerifierMethodDescriptor(
            id = id,
            clazz = clazz,
            method = methodName,
            processor = processor,
            parent = parent,
        ).also {
            currentTestDescriptor = it
            proc.started(it, TestStartEvent(currentTimeMillis(), parent.id))
        }
    }

    private fun handleError(e: Throwable, className: String, warn: Boolean = false) {
        val note = if (e is LinkageError) {
            " \n (Note: This error seems be caused by a class loading issue," +
                " maybe a dependency misconfiguration)"
        } else {
            ""
        }
        val message = "Problem verifying class '$className':$note \n$e"
        when {
            warn -> logger.w(message)
            continueOnFailure -> require(false, throwable = e) { message }
            else -> throw GradleException(message, e)
        }
    }

    private fun checkStop(): Boolean {
        if (stopped) {
            logger.w("Verification stopped")
            return true
        }
        return false
    }

    fun stopNow() {
        stopped = true
    }

    override fun close() {
        try {
            classLoader.close()
        } finally {
            mainJar.getValueOrNull()?.close()
        }
    }

    private class VerifierClassDescriptor(
        task: String,
        clazz: String,
        private val parent: TestDescriptorInternal,
    ) : DefaultTestClassDescriptor("${task}_$clazz", clazz) {
        override fun getParent() = parent
        override fun getDisplayName() = "$className API verification"
    }

    private class VerifierMethodDescriptor(
        id: String,
        clazz: String,
        method: String,
        private val processor: String,
        private val parent: TestDescriptorInternal,
    ) : DefaultTestMethodDescriptor(id, clazz, method) {
        override fun getParent() = parent
        override fun getDisplayName() = "$name API verification ($processor)"
    }
}
