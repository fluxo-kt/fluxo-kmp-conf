package fluxo.conf.dsl.container.target

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

public interface TargetAndroid<out T : TestedExtension> :
    KotlinTargetContainer<KotlinAndroidTarget> {

    public fun android(action: T.() -> Unit)

    // FIXME: Implement better API for source sets.
    public fun sourceSetTestInstrumented(action: KotlinSourceSet.() -> Unit)


    public interface Configure {

        // TODO: Is it ok to have android app target in the KMP module?
        public fun androidApp(
            targetName: String = "android",
            action: TargetAndroid<BaseAppModuleExtension>.() -> Unit = EMPTY_FUN,
        )

        public fun androidLibrary(
            targetName: String = "android",
            action: TargetAndroid<LibraryExtension>.() -> Unit = EMPTY_FUN,
        )


        /** Alias for [androidLibrary] */
        public fun android(
            targetName: String = "android",
            action: TargetAndroid<LibraryExtension>.() -> Unit = EMPTY_FUN,
        ) {
            // FIXME: Detect applied plugin (lib/app) and use appropriate container.
            androidLibrary(targetName, action)
        }
    }
}
