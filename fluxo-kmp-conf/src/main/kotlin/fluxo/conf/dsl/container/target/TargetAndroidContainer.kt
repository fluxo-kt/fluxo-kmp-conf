package fluxo.conf.dsl.container.target

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.InternalFluxoApi
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.KOTLIN_1_8
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.container
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

public sealed class TargetAndroidContainer<T : TestedExtension>
private constructor(
    context: ContainerContext,
    name: String,
) : KmpTarget.CommonJvm<KotlinAndroidTarget>(context, name) {

    internal val lazyAndroid = context.objects.container<T.() -> Unit>()

    private val lazySourceSetTestInstrumented =
        context.objects.container<KotlinSourceSet.() -> Unit>()

    public fun android(action: T.() -> Unit) {
        lazyAndroid.add(action)
    }

    public fun sourceSetTestInstrumented(action: KotlinSourceSet.() -> Unit) {
        lazySourceSetTestInstrumented.add(action)
    }

    internal abstract fun setupAndroid(project: Project)

    final override fun KotlinMultiplatformExtension.setup() {
        val target = android(name) {
            kotlinJvmTarget?.toString()?.let { version ->
                compilations.all {
                    kotlinOptions.jvmTarget = version
                }
            }
            lazyTargetConf()
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${COMMON_JVM}Main"))
                lazySourceSetMainConf()
            }

            val layoutVersion = when {
                context.kotlinPluginVersion >= KOTLIN_1_8 -> {
                    target.project.extraProperties
                        .properties["kotlin.mpp.androidSourceSetLayoutVersion"]
                        ?.toString()
                        ?.toIntOrNull() ?: 1
                }

                else -> 1
            }

            val (test, instrumented) = when (layoutVersion) {
                2 -> Pair("androidUnitTest", "androidInstrumentedTest")
                else -> Pair("androidTest", "androidAndroidTest")
            }

            val jvmAndroidTest = getByName("${COMMON_JVM}Test")

            getByName(test) {
                dependsOn(jvmAndroidTest)
                lazySourceSetTestConf()
            }
            getByName(instrumented) {
                dependsOn(jvmAndroidTest)
                lazySourceSetTestInstrumented.all { this() }
            }
        }

        setupAndroid(target.project)
    }


    final override val sortOrder: Byte = 1

    @JvmSynthetic
    @InternalFluxoApi
    final override fun equals(other: Any?): Boolean = other is TargetAndroidContainer<*>

    @JvmSynthetic
    @InternalFluxoApi
    final override fun hashCode(): Int = typeHashCode<TargetAndroidContainer<*>>()


    @FluxoKmpConfDsl
    public sealed interface Configure : ContainerHolderAware {

        public fun androidApp(targetName: String = "android", action: App.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::App, action)
        }

        public fun androidLibrary(
            targetName: String = "android",
            action: Library.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Library, action)
        }

        /** Alias for [androidLibrary] */
        public fun android(targetName: String = "android", action: Library.() -> Unit = EMPTY_FUN) {
            androidLibrary(targetName, action)
        }
    }

    @FluxoKmpConfDsl
    public class App internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidContainer<BaseAppModuleExtension>(context, targetName) {

        override fun setupAndroid(project: Project) {
            project.configureExtension(BaseAppModuleExtension::class) {
                // Set before executing action so that they may be overridden if desired.
                compileOptions {
                    compileSourceCompatibility?.let { compatibility ->
                        sourceCompatibility = compatibility
                    }
                    compileTargetCompatibility?.let { compatibility ->
                        targetCompatibility = compatibility
                    }
                }
                lazyAndroid.all {
                    this()
                }
            }
        }
    }

    @FluxoKmpConfDsl
    public class Library
    internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidContainer<LibraryExtension>(context, targetName) {

        override fun setupAndroid(project: Project) {
            project.configureExtension<LibraryExtension> {
                // Set before executing action so that they may be overridden if desired.
                compileOptions {
                    compileSourceCompatibility?.let { compatibility ->
                        sourceCompatibility = compatibility
                    }
                    compileTargetCompatibility?.let { compatibility ->
                        targetCompatibility = compatibility
                    }
                }
                lazyAndroid.all {
                    this()
                }
            }
        }
    }
}
