@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.deps.guard)
}

dependencyGuard {
    configuration("classpath")
}

if (hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

val buildTask = tasks.register("buildPlugins")
subprojects {
    buildTask.configure { dependsOn(tasks.named("build")) }
}
