package fluxo.shrink

import fluxo.artifact.dsl.ProcessorCallType
import fluxo.artifact.proc.JvmShrinker
import fluxo.artifact.proc.JvmShrinker.ProGuard
import fluxo.artifact.proc.JvmShrinker.R8
import fluxo.conf.deps.tryGetClassForName
import fluxo.conf.impl.TOTAL_OS_MEMORY
import fluxo.conf.impl.XMX
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import fluxo.util.readableByteSize
import java.net.URLClassLoader
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Abstraction for the [shrinker] in-memory reflective caller.
 */
internal class ShrinkerReflectiveCaller(
    private val shrinker: JvmShrinker,
    private val logger: Logger,
    private val toolJars: FileCollection,
    private val getShrinkerArgs: () -> List<String>,
) {
    private companion object {
        private const val MIN_XMX_GB = 2
        private const val MIN_XMX_FOR_IN_MEMORY: Long = 1024L * 1024 * 1024 * MIN_XMX_GB
    }


    private var v: String? = null

    /**
     * Tries to run the shrinker in-memory.
     *
     * @return `true` if the shrinker was run in-memory, `false` otherwise.
     */
    @Suppress("ReturnCount")
    fun execute(
        callType: ProcessorCallType,
        ignoreMemoryLimit: Boolean = false,
    ): Boolean {
        logger.v("Trying to run $shrinker in-memory ($callType)...")

        val xmx = XMX
        if (!ignoreMemoryLimit && xmx < MIN_XMX_FOR_IN_MEMORY) {
            if (TOTAL_OS_MEMORY > MIN_XMX_GB * 2) {
                logger.e(
                    "Low memory (Xmx=${readableByteSize(xmx)})" +
                        " will likely to cause $shrinker to fail!\n" +
                        "Please set the max heap size to at least -Xmx${MIN_XMX_GB}G" +
                        " to run $shrinker in-memory.",
                )
            }
            return false
        }

        try {
            val (className, args) = args()
            val (clazz, closeable) = when (callType) {
                ProcessorCallType.BUNDLED -> tryLoadBundled(className) to null
                else -> tryLoadUnbundled(className)
            }
            closeable.use {
                if (clazz != null) {
                    /** @see fluxo.shrink.ShrinkerTestBase.shrink */
                    when (shrinker) {
                        R8 -> callR8(clazz, args)
                        ProGuard -> callProGuard(clazz, args)
                    }
                    return true
                } else {
                    logger.w("$shrinker could not be loaded in-memory as $callType (class=$className)!")
                }
            }
        } catch (e: Throwable) {
            throw GradleException("Failed to run $callType $shrinker in-memory! $e", e)
        }
        return false
    }

    private fun tryLoadBundled(clazz: String): Class<*>? {
        return tryGetClassForName(clazz)?.also {
            v = v(it)
            logger.l("Using $shrinker $v from classpath (bundled)")
        }
    }

    private fun tryLoadUnbundled(className: String): Pair<Class<*>?, AutoCloseable?> {
        val jarUrls = toolJars.map { it.toURI().toURL() }.toTypedArray()
        val classLoader = URLClassLoader(jarUrls, javaClass.classLoader)
        val clazz = tryGetClassForName(className, classLoader)?.also {
            v = v(it)
            logger.l("Using $shrinker $v (loaded with URLClassLoader)")
        }
        if (clazz == null) {
            classLoader.close()
            return null to null
        }
        return clazz to classLoader
    }

    private fun callProGuard(clazz: Class<*>, args: Array<String>) {
        /**
         * @see proguard.ProGuard.main
         * @see proguard.Configuration
         * @see proguard.ConfigurationParser
         */
        val packageName = clazz.packageName
        val confClassName = "$packageName.Configuration"
        val parserClassName = "$packageName.ConfigurationParser"
        val confClass = Class.forName(confClassName, true, clazz.classLoader)
        val parserClass = Class.forName(parserClassName, true, clazz.classLoader)

        val configuration = confClass.getDeclaredConstructor().newInstance()

        val parser = parserClass.getDeclaredConstructor(
            // (String[] args, Properties properties)
            Array<String>::class.java,
            Properties::class.java,
        ).newInstance(args, System.getProperties()) as AutoCloseable

        parser.use {
            /** @see proguard.ConfigurationParser.parse */
            parserClass.getDeclaredMethod("parse", confClass)
                .invoke(it, configuration)
        }

        val pg = clazz.getDeclaredConstructor(confClass)
            .newInstance(configuration)

        /** @see proguard.ProGuard.execute */
        clazz.getDeclaredMethod("execute").invoke(pg)
    }

    private fun callR8(clazz: Class<*>, args: Array<String>) {
        /** @see com.android.tools.r8.R8.main */
        val mainMethod = clazz
            .getDeclaredMethod("main", Array<String>::class.java)
        mainMethod.invoke(null, args)
    }

    /** Class name to arguments */
    private fun args(): Pair<String, Array<String>> {
        val args = getShrinkerArgs()
        return args.first() to args.map {
            when {
                !it.startsWith('"') -> it
                // Required for R8
                else -> it.trim('"').replace("\\\\", "\\")
            }
        }.drop(1).toTypedArray()
    }

    /**
     * Returns the version of the shrinker.
     */
    private fun v(clazz: Class<*>): String {
        var ex: Throwable? = null
        return try {
            'v' + when (shrinker) {
                ProGuard -> {
                    /** @see proguard.ProGuard.getVersion */
                    clazz.getDeclaredMethod("getVersion").invoke(null)
                }

                R8 -> {
                    /** @see com.android.tools.r8.R8 */
                    /** @see com.android.tools.r8.Version.LABEL */
                    /** @see com.android.tools.r8.Version.getVersionString */
                    val versionClassName = clazz.packageName + ".Version"
                    val versionClass = Class.forName(versionClassName, true, clazz.classLoader)
                    try {
                        versionClass.getField("LABEL").get(null)
                    } catch (e: Throwable) {
                        ex = e
                        versionClass.getDeclaredMethod("getVersionString").invoke(null).toString()
                            .substringBefore(" (")
                    }
                }
            }.toString()
        } catch (e: Throwable) {
            ex?.let(e::addSuppressed)
            logger.e("Failed to get $shrinker version! $e", e)
            "(unknown version)"
        }
    }
}
