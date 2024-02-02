package fluxo.shrink

import fluxo.conf.deps.tryGetClassForName
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import java.net.URLClassLoader
import java.util.Properties
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

internal class ShrinkerReflectiveCaller(
    private val shrinker: Shrinker,
    private val logger: Logger,
    private val toolJars: FileCollection,
    private val forceUnbundledShrinker: Boolean,
    private val getShrinkerArgs: () -> List<String>,
) {
    private var v: String? = null

    fun execute(): Boolean {
        val forceUnbundled = forceUnbundledShrinker
        logger.v("Trying to run $shrinker in-memory (forceUnbundled=$forceUnbundled)...")
        try {
            val (className, args) = args()
            val clazz = when (forceUnbundled) {
                true -> tryLoadUnbundled(className) ?: tryLoadBundled(className)
                else -> tryLoadBundled(className) ?: tryLoadUnbundled(className)
            }
            if (clazz != null) {
                /** @see fluxo.shrink.ShrinkerTestBase.shrink */
                when (shrinker) {
                    Shrinker.R8 -> callR8(clazz, args)
                    Shrinker.ProGuard -> callProGuard(clazz, args)
                }
                return true
            } else {
                logger.w("$shrinker could not be loaded in-memory (class=$className)!")
            }
        } catch (e: Throwable) {
            logger.e("Failed to run $shrinker $v in-memory! $e", e)
        }
        return false
    }

    private fun tryLoadBundled(clazz: String): Class<*>? {
        return tryGetClassForName(clazz)?.also {
            v = v(it)
            logger.l("Using $shrinker $v from classpath (bundled)")
        }
    }

    private fun tryLoadUnbundled(clazz: String): Class<*>? {
        val jarUrls = toolJars.map { it.toURI().toURL() }.toTypedArray()
        val classLoader = URLClassLoader(jarUrls, javaClass.classLoader)
        return tryGetClassForName(clazz, classLoader)?.also {
            v = v(it)
            logger.l("Using $shrinker $v (loaded with URLClassLoader)")
        }
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
                Shrinker.ProGuard -> {
                    /** @see proguard.ProGuard.getVersion */
                    clazz.getDeclaredMethod("getVersion").invoke(null)
                }

                Shrinker.R8 -> {
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