import impl.configureExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension

public fun Project.setupJsApp() {
    configureExtension<KotlinJsProjectExtension> {
        js(IR) {
            browser()
            binaries.executable()
        }

        disableCompilationsOfNeeded(project)
    }
}
