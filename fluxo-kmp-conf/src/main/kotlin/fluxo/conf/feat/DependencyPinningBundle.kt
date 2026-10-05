package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.VersionCatalogConstants.VC_PINNED_BUNDLE_ALIAS
import fluxo.conf.impl.kotlin.KOTLIN_2_1
import fluxo.conf.impl.kotlin.KOTLIN_PLUGIN_VERSION_STRING
import fluxo.conf.impl.logDependency
import fluxo.log.d
import fluxo.log.l
import fluxo.log.v
import fluxo.vc.b
import java.math.BigInteger
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.logging.Logger

/** Pair is (version, reason) */
private typealias PinnedDeps = HashMap<ModuleIdentifier, Pair<String, String>>


internal fun FluxoKmpConfContext.prepareDependencyPinningBundle() {
    val libs = libs.gradle ?: return
    val p = rootProject

    val pinnedDeps: PinnedDeps = HashMap()

    if (kotlinPluginVersion >= KOTLIN_2_1) {
        // KGP doesn't depend on the `kotlin-compiler-embeddable` dependency
        // starting from Kotlin 2.1.0
        // Other plugins can bring incompatible versions of the compiler.
        // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1729256644747559?thread_ts=1729151089.194689&cid=C0KLZSCHF
        val compilerEmbeddable = object : ModuleIdentifier {
            override fun getGroup() = "org.jetbrains.kotlin"
            override fun getName() = "kotlin-compiler-embeddable"
        }
        val version = KOTLIN_PLUGIN_VERSION_STRING
        pinnedDeps[compilerEmbeddable] = Pair(version, "Pinned to Kotlin plugin version")
    }

    val bundleAliases = libs.bundleAliases
    if (bundleAliases.isNotEmpty()) {
        for (alias in bundleAliases) {
            collectPinnedDependencies(alias, p.logger, pinnedDeps)
        }
    }
    if (pinnedDeps.isEmpty()) {
        p.logger.d("No dependencies pinned by version catalog bundles")
        return
    }

    // Gradle resolves and loads the root build-script classpath before any plugin applies, so a
    // pin registered there now changes no loaded class; it only made later re-resolutions
    // (`buildEnvironment`, dependency-guard's classpath report) show versions that never ran.
    // The root's mismatches are reported with the fix instead. Subprojects resolve their
    // build-script classpath after this point, so their pins work.
    reportUnpinnableRootClasspath(pinnedDeps, root = p)
    pinDependencies(pinnedDeps, project = p, buildscript = false)
    p.subprojects {
        pinDependencies(pinnedDeps, project = this, buildscript = true)
    }
}

private fun pinDependencies(
    pinnedDeps: PinnedDeps,
    project: Project,
    buildscript: Boolean,
) {
    if (buildscript) {
        project.buildscript.configurations.configureEach {
            pinDependencies(pinnedDeps, project, conf = this)
        }
    }
    project.configurations.configureEach {
        pinDependencies(pinnedDeps, project, conf = this)
    }
}

/**
 * One build-end warning listing every pinned module the root build classpath holds at an OLDER
 * version, each as the `buildscript` constraint line that applies before resolution. Pins are
 * mostly security minimums, so an older jar is the risk; a constraint only raises a version (the
 * highest wins), so it fixes exactly that case, while a newer jar already in use needs no action.
 * Reads the classpath Gradle already resolved for the root build script, so it costs no resolution.
 */
private fun FluxoKmpConfContext.reportUnpinnableRootClasspath(
    pinnedDeps: PinnedDeps,
    root: Project,
) {
    val classpath = root.buildscript.configurations.findByName(CLASSPATH) ?: return
    classpath.incoming.resolutionResult.allComponents.asSequence()
        .mapNotNull { it.moduleVersion }
        .mapNotNull { m ->
            val pinned = pinnedDeps[m.module]?.first
            if (pinned != null && isOlder(m.version, pinned)) m to pinned else null
        }
        .forEach { (module, pinned) ->
            val line = "classpath(\"${module.module}:$pinned\") // in use: ${module.version}"
            buildEndReport.warnAggregated(UNPINNABLE_ROOT_CLASSPATH, line) { lines ->
                "Pinned versions can't change the root build classpath: Gradle loads it before " +
                    "any plugin runs, so these modules run at older versions. Add to the root " +
                    "build.gradle.kts:\nbuildscript { dependencies { constraints {\n    " +
                    lines.joinToString("\n    ") + "\n} } }"
            }
        }
}

private fun pinDependencies(
    pinnedDeps: PinnedDeps,
    project: Project,
    conf: Configuration,
) {
    val path = project.path + "::${conf.name}"
    conf.resolutionStrategy.eachDependency d@{
        val module = requested.module
        val (version, reason) = pinnedDeps[module] ?: return@d
        if (DEBUG_PINS) {
            project.logger.v("Pinning ${requested.module} to $version in $path")
        }
        useVersion(version)
        because(reason)
    }
}


private fun FluxoKmpConfContext.collectPinnedDependencies(
    alias: String,
    logger: Logger,
    pinnedDeps: PinnedDeps,
) {
    // Filter "pinned" and "pinned.*" bundles
    alias.startsWith(ALIAS, ignoreCase = true) && alias.run {
        val l = length
        l == ALIAS.length || l > ALIAS.length && this[ALIAS.length] == '.'
    } || return

    val bundle = libs.b(alias)?.get()
    if (bundle.isNullOrEmpty()) {
        return
    }
    logger.l("Pinning ${bundle.size} dependencies from version catalog bundle '$alias'")

    val reason = "$PIN_REASON from bundle '$alias'"
    for (dep in bundle) {
        logger.logDependency("pinned", dep)
        pinnedDeps[dep.module] = Pair(dep.versionConstraint.toString(), reason)
    }
}

/** Compares the numeric parts in order ("1.80.2" < "1.86", "33.4.0-jre" < "33.7.2-jre"). */
private fun isOlder(version: String, than: String): Boolean {
    val a = version.split(NON_DIGITS).filter { it.isNotEmpty() }.map { it.toBigInteger() }
    val b = than.split(NON_DIGITS).filter { it.isNotEmpty() }.map { it.toBigInteger() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = (a.getOrNull(i) ?: BigInteger.ZERO).compareTo(b.getOrNull(i) ?: BigInteger.ZERO)
        if (c != 0) return c < 0
    }
    return false
}

private val NON_DIGITS = Regex("[^0-9]+")

private const val DEBUG_PINS = false

private const val CLASSPATH = "classpath"

private const val UNPINNABLE_ROOT_CLASSPATH = "unpinnable-root-classpath"

private const val ALIAS = VC_PINNED_BUNDLE_ALIAS

private const val PIN_REASON = "Pinned due to security recommendations or other considerations"
