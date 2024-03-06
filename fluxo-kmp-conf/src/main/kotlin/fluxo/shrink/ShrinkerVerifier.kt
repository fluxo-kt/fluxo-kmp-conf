package fluxo.shrink

import fluxo.conf.impl.e
import fluxo.conf.impl.w
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

internal class ShrinkerVerifier(
    private val mainJarFile: File,
    private val classLoader: URLClassLoader,
    private val signatures: Map<String, ClassSignature>,
    override val logger: Logger,
    override val continueOnFailure: Boolean,
) : ApiVerifier {

    override var verified = true

    private val mainJar by lazy { JarFile(mainJarFile) }

    @Suppress("NestedBlockDepth", "ThrowsCount")
    fun verify() {
        var lastEx: Throwable? = null
        for ((className, signature) in signatures) {
            var ex: Throwable? = null
            try {
                verifyApiWithAsm(mainJar, signature)
            } catch (e: GradleException) {
                throw e
            } catch (e: Throwable) {
                ex = e
            }

            try {
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
                    when (ex) {
                        null -> ex = e
                        else -> ex.addSuppressed(e)
                    }
                }
            }

            if (ex != null) {
                verified = false
                handleError(ex, className)
                lastEx = ex
            }
        }

        if (!verified) {
            throw GradleException("Verification failed", lastEx)
        }
    }

    private fun handleError(e: Throwable, className: String, warn: Boolean = false) {
        val note = if (e is LinkageError) {
            " \n (Note: This error seems be caused by a class loading issue," +
                " maybe a dependency misconfiguration)"
        } else {
            ""
        }
        val message = "Problem verifying class '$className' with reflection:$note \n$e"
        when {
            warn -> logger.w(message)
            continueOnFailure -> logger.e(message, e)
            else -> throw GradleException(message, e)
        }
    }
}
