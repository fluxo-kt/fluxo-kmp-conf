package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.container
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

public sealed class KmpTarget<T : KotlinTarget>(
    context: ContainerContext,
    name: String,
) : Container.ConfigurableTarget(context, name) {

    internal val lazyTargetConf: T.() -> Unit
        get() = { lazyTarget.all { this() } }

    private val lazyTarget = context.objects.container<T.() -> Unit>()

    public fun target(action: T.() -> Unit) {
        lazyTarget.add(action)
    }


    public sealed class CommonJvm<T : KotlinTarget>(
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

    public sealed class NonJvm<T : KotlinTarget>(
        context: ContainerContext,
        name: String,
    ) : KmpTarget<T>(context, name) {

        internal companion object {
            internal const val NON_JVM = "nonJvm"
        }

        public sealed class CommonJs<T : KotlinJsTargetDsl>(
            context: ContainerContext,
            name: String,
        ) : NonJvm<T>(context, name) {

            public sealed interface Configure : ContainerHolderAware {

                public fun KotlinJsTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
                    browser {
                        testTimeout(seconds)
                    }
                    nodejs {
                        testTimeout(seconds)
                    }
                    if (this is KotlinWasmTargetDsl) {
                        d8 {
                            testTimeout(seconds)
                        }
                    }
                }

                public fun KotlinJsSubTargetDsl.testTimeout(seconds: Int = TEST_TIMEOUT) {
                    require(seconds > 0) { "Timeout seconds must be greater than 0." }
                    testTask {
                        useMocha { timeout = "${seconds}s" }
                    }
                }
            }


            internal companion object {
                internal const val COMMON_JS = "commonJs"

                /**
                 * Default timeout for Kotlin/JS tests is `2s`.
                 *
                 * @see org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha.DEFAULT_TIMEOUT
                 */
                // https://mochajs.org/#-timeout-ms-t-ms
                private const val TEST_TIMEOUT = 10
            }
        }

        @Suppress("MemberNameEqualsClassName")
        public sealed class Native<T : KNT>(
            context: ContainerContext,
            name: String,
        ) : NonJvm<T>(context, name) {

            internal companion object {
                internal const val NATIVE = "native"
            }

            public sealed class Android<T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name)

            public sealed class Unix<T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name) {

                internal companion object {
                    internal const val UNIX = "unix"
                }

                public sealed class Apple<T : KNT>(
                    context: ContainerContext,
                    name: String,
                ) : Unix<T>(context, name) {

                    internal companion object {
                        internal const val APPLE = "apple"
                    }

                    public sealed class Ios<T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)

                    public sealed class Macos<T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)

                    public sealed class Tvos<T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)

                    public sealed class Watchos<T : KNT>(
                        context: ContainerContext,
                        name: String,
                    ) : Apple<T>(context, name)
                }

                public sealed class Linux<T : KNT>(
                    context: ContainerContext,
                    name: String,
                ) : Unix<T>(context, name)
            }

            public sealed class Mingw<T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name)

            public sealed class Wasm<T : KNT>(
                context: ContainerContext,
                name: String,
            ) : Native<T>(context, name)
        }
    }
}
