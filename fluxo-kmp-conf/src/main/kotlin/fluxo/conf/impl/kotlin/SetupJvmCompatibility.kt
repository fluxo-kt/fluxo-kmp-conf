package fluxo.conf.impl.kotlin

import com.android.build.gradle.TestedExtension
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.d
import fluxo.conf.impl.l
import kotlin.math.max
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

@Suppress("ReturnCount")
internal fun KotlinProjectExtension.setupJvmCompatibility(project: Project, kc: KotlinConfig) {
    if (this is KotlinSingleTargetExtension<*> &&
        target.run { this is KotlinJvmTarget && !withJavaEnabled }
    ) {
        project.logger.d("KotlinSingleTarget with no Java enabled, skip Java compatibility setup")
        return
    }

    val jvmTarget = kc.jvmTarget
    if (jvmTarget.isNullOrEmpty()) {
        val jreTarget = kc.jvmTargetInt.asJvmTargetVersion()
        project.logger.l(
            "Java compatibility is not explicitly set, current JRE ($jreTarget) will be used!",
        )
        return
    }

    val jvmToolchain = kc.jvmToolchain
    if (jvmToolchain) {
        // Kotlin set up toolchain for java automatically
        jvmToolchain(kc.jvmTargetInt)

        // Gradle Java toolchain support is available from AGP 7.4.0.
        //  https://issuetracker.google.com/issues/194113162
        // Nevertheless,
        // because of the issue (https://issuetracker.google.com/issues/260059413),
        //  AGP didn't set targetCompatibility to be equal to the toolchain's JDK until
        //  the version 8.1.0-alpha09.
        // If you use versions less than 8.1.0-alpha09, configure targetCompatibility
        // manually via compileOptions.
        // https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
    } else {
        // Java
        project.configureExtensionIfAvailable({ JavaPluginExtension::class }) {
            jvmTarget.asJavaVersion().let { v ->
                sourceCompatibility = v
                targetCompatibility = v
            }
        }
        // Global Java tasks configuration isn't applied (project.tasks.withType<JavaCompile>)
        // as more fine-grained configuration is preferred.
    }

    // Android
    project.configureExtensionIfAvailable<TestedExtension>(ANDROID_EXT_NAME) {
        compileOptions {
            jvmTarget.asJavaVersion().let { v ->
                sourceCompatibility = v
                targetCompatibility = v
            }
        }
        /** `android.kotlinOptions` */
        (this as? ExtensionAware)?.configureExtensionIfAvailable<KotlinJvmOptions>("kotlinOptions") {
            setupJvmCompatibility(jvmTarget)
        }
    }
}

internal fun KotlinJvmOptions.setupJvmCompatibility(jvmTarget: String) {
    this.jvmTarget = jvmTarget
}

internal fun KCompilation.setupJvmCompatibility(jvmTarget: String) {
    compileJavaTaskProvider?.configure {
        sourceCompatibility = jvmTarget
        targetCompatibility = jvmTarget
    }
}


/** @see org.jetbrains.kotlin.gradle.plugin.findJavaTaskForKotlinCompilation */
internal val KotlinCompilation<*>.compileJavaTaskProvider: TaskProvider<out JavaCompile>?
    get() = when (this) {
        is KotlinJvmAndroidCompilation -> compileJavaTaskProvider
        is KotlinWithJavaCompilation<*, *> -> compileJavaTaskProvider
        // nullable for Kotlin-only JVM target in KMP-module
        is KotlinJvmCompilation -> compileJavaTaskProvider
        else -> null
    }


internal const val JRE_1_8 = 8

internal const val JRE_1_9 = 9

// https://www.oracle.com/java/technologies/downloads/
private const val LTS_JDK_VERSION = 17

/**
 * StringConcatFactory from JRE 9+ isn't supported on Android without desugaring.
 *
 * AGP uses -XDstringConcat=inline javac flag
 *  to avoid generating class files with StringConcatFactory.
 * Similar Kotlin compiler options are required.
 *
 * See: [b/250197571](https://issuetracker.google.com/issues/250197571),
 * [b/285090974](https://issuetracker.google.com/issues/285090974),
 * [kotlinx.serialization#2145](https://github.com/Kotlin/kotlinx.serialization/issues/2145)
 */
internal const val ANDROID_SAFE_JVM_TARGET = JRE_1_8

internal val JRE_VERSION_STRING: String = run {
    try {
        // For 9+
        Runtime.version().toString()
    } catch (_: Throwable) {
        System.getProperty("java.version")
    }
}

internal val JRE_VERSION: Int = run {
    try {
        // For 9+
        Runtime.version().version().first()
    } catch (_: Throwable) {
        System.getProperty("java.version").asJvmMajorVersion()
    }
}


private fun lastKnownJdkVersion(setupToolchain: Boolean): Int =
    if (setupToolchain) max(JRE_VERSION, LTS_JDK_VERSION) else JRE_VERSION

internal fun lastSupportedJvmMajorVersion(setupToolchain: Boolean): Int =
    lastKnownJdkVersion(setupToolchain).toKotlinSupportedJvmMajorVersion()

internal fun lastSupportedJvmTargetVersion(setupToolchain: Boolean): String =
    lastSupportedJvmMajorVersion(setupToolchain).asJvmTargetVersion()


internal fun String.toJvmMajorVersion(setupToolchain: Boolean): Int {
    return when {
        isBlank() -> 0

        equals("last", ignoreCase = true) ||
            equals("latest", ignoreCase = true) ||
            equals("max", ignoreCase = true) ||
            equals("+")
        -> lastKnownJdkVersion(setupToolchain)

        equals("current", ignoreCase = true) || isEmpty()
        -> JRE_VERSION

        else -> asJvmMajorVersion()
    }.toKotlinSupportedJvmMajorVersion()
}


internal fun Int.asJvmTargetVersion(): String = if (this >= JRE_1_9) toString() else "1.$this"

/** `7` for `1.7`, `8` for `1.8.0_211`, `9` for `9.0.1`. */
internal fun String.asJvmMajorVersion(): Int =
    removePrefix("1.").takeWhile { it.isDigit() }.toInt()

internal fun String.asJavaVersion(): JavaVersion = JavaVersion.toVersion(this)
