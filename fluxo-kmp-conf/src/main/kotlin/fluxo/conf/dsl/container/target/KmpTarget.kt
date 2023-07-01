package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.container
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

public sealed class KmpTarget<out T : KotlinTarget>(
    context: ContainerContext,
    name: String,
) : Container.ConfigurableTarget(context, name) {

    internal val lazyTargetConf: (@UnsafeVariance T).() -> Unit
        get() = { lazyTarget.all { this() } }

    private val lazyTarget = context.objects.container<T.() -> Unit>()

    public fun target(action: T.() -> Unit) {
        lazyTarget.add(action)
    }


    public sealed class CommonJvm<out T : AbstractKotlinTarget>(
        context: ContainerContext,
        name: String,
    ) : KmpTarget<T>(context, name) {

        internal companion object {
            internal const val COMMON_JVM = "commonJvm"
        }

        public var kotlinJvmTarget: JavaVersion? = null
        public var compileSourceCompatibility: JavaVersion? = null
        public var compileTargetCompatibility: JavaVersion? = null
    }

    public sealed class NonJvm<out T : KotlinTarget>(
        context: ContainerContext,
        name: String,
    ) : KmpTarget<T>(context, name) {

        internal companion object {
            internal const val NON_JVM = "nonJvm"
        }

        public sealed class CommonJs<out T : KotlinJsTargetDsl>(
            context: ContainerContext,
            name: String,
        ) : NonJvm<T>(context, name) {

            internal companion object {
                internal const val COMMON_JS = "commonJs"
            }
        }

        @Suppress("MemberNameEqualsClassName")
        public sealed class Native<out T : KNT>(
            context: ContainerContext,
            name: String,
        ) : NonJvm<T>(context, name) {

            internal companion object {
                internal const val NATIVE = "native"
            }

            public sealed class Android(
                context: ContainerContext,
                name: String,
            ) : Native<KNT>(context, name)

            public sealed class Unix<out T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name) {

                internal companion object {
                    internal const val UNIX = "unix"
                }

                public sealed class Apple<out T : KNT>(
                    context: ContainerContext,
                    name: String,
                ) : Unix<T>(context, name) {

                    internal companion object {
                        internal const val APPLE = "apple"
                    }

                    public sealed class Ios<out T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)

                    public sealed class Macos(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<KotlinNativeTargetWithHostTests>(context, name)

                    public sealed class Tvos<out T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)

                    public sealed class Watchos<out T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)
                }

                public sealed class Linux<out T : KNT>(
                    context: ContainerContext,
                    name: String,
                ) : Unix<T>(context, name)
            }

            public sealed class Mingw<out T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name)

            public sealed class Wasm(
                context: ContainerContext,
                name: String,
            ) : Native<KNT>(context, name)
        }
    }
}
