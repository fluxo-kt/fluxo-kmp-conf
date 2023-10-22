package fluxo.conf.dsl.container.target

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.CommonJvm.Companion.ANDROID
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

public interface AndroidTarget<out T> :
    KotlinTargetContainer<KotlinAndroidTarget>
    where T : CommonExtension<*, *, *, *, *>, T : TestedExtension {

    @Deprecated(
        message = "Use `onAndroidExtension` instead.",
        replaceWith = ReplaceWith("onAndroidExtension(action)"),
    )
    public fun android(action: T.() -> Unit): Unit = onAndroidExtension(action)

    public fun onAndroidExtension(action: T.() -> Unit)


    // FIXME: Implement API for source sets.
    public fun sourceSetTestInstrumented(action: KotlinSourceSet.() -> Unit)


    public interface Configure {

        /**
         *
         * @see com.android.build.gradle.internal.dsl.BaseAppModuleExtension
         * @see com.android.build.api.dsl.ApplicationExtension
         */
        public fun androidApp(
            targetName: String = ANDROID,
            configure: AndroidTarget<BaseAppModuleExtension>.() -> Unit = EMPTY_FUN,
        )

        /**
         *
         * @see com.android.build.gradle.LibraryExtension
         * @see com.android.build.api.dsl.LibraryExtension
         */
        public fun androidLibrary(
            targetName: String = ANDROID,
            configure: AndroidTarget<LibraryExtension>.() -> Unit = EMPTY_FUN,
        )


        /** Alias for [androidLibrary] */
        @Deprecated(
            message = "Use `androidLibrary` instead.",
            replaceWith = ReplaceWith("androidLibrary(action)"),
        )
        public fun android(configure: AndroidTarget<LibraryExtension>.() -> Unit) {
            // TODO: Detect applied plugin (lib/app) and use appropriate container?
            androidLibrary(configure = configure)
        }
    }
}
