import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension

public fun Project.setupJsApp() {
    extensions.configure<KotlinJsProjectExtension> {
        js(IR) {
            browser()
            binaries.executable()
        }

        disableCompilationsOfNeeded(project)
    }
}
