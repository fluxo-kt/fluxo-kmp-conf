import org.gradle.api.Plugin
import org.gradle.api.Project

public class NewPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // project.extensions.create("newPlugin", NewPluginExtension::class.java)
    }
}
