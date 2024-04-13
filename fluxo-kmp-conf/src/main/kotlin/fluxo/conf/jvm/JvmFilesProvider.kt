package fluxo.conf.jvm

import MAIN_SOURCE_SET_NAME
import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal sealed class JvmFilesProvider {
    abstract fun jvmCompileFiles(project: Project): JvmFiles

    abstract fun jvmRuntimeFiles(project: Project): JvmFiles

    abstract class GradleJvmFilesProvider : JvmFilesProvider() {
        protected abstract val jarTaskName: String
        protected abstract val compileFiles: FileCollection
        protected abstract val runtimeFiles: FileCollection

        override fun jvmCompileFiles(project: Project): JvmFiles =
            jvmFiles(project, compileFiles)

        override fun jvmRuntimeFiles(project: Project): JvmFiles =
            jvmFiles(project, runtimeFiles)

        private fun jvmFiles(project: Project, files: FileCollection): JvmFiles {
            val jarTask = project.tasks.named(jarTaskName, Jar::class.java)
            val mainJar = jarTask.flatMap { it.archiveFile }
            val jarFiles = project.objects.fileCollection()
            jarFiles.from(files.filter(JarFilterSpec))
            return JvmFiles(mainJar, jarFiles, arrayOf(jarTask))
        }
    }

    private object JarFilterSpec : Spec<File> {
        override fun isSatisfiedBy(file: File): Boolean =
            file.path.endsWith(".jar")
    }


    class FromGradleSourceSet(private val sourceSet: SourceSet) :
        GradleJvmFilesProvider() {
        override val jarTaskName: String
            get() = sourceSet.jarTaskName

        override val compileFiles: FileCollection
            get() = sourceSet.compileClasspath

        override val runtimeFiles: FileCollection
            get() = sourceSet.runtimeClasspath
    }

    class FromKotlinMppTarget(private val target: KotlinJvmTarget) :
        GradleJvmFilesProvider() {

        override val jarTaskName: String
            get() = target.artifactsTaskName

        override val compileFiles: FileCollection
            get() = mainCompilation.compileDependencyFiles

        override val runtimeFiles: FileCollection
            get() = mainCompilation.runtimeDependencyFiles

        private val mainCompilation: KotlinJvmCompilation
            get() = target.compilations.getByName(MAIN_SOURCE_SET_NAME)
    }
}
