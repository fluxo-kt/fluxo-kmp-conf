plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.gradle.plugin.publish) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    id("io.github.fluxo-kt.fluxo-kmp-conf")
}

group = "io.github.fluxo-kt"
version = libs.versions.version.get()

setupGradlePlugin {
    allWarningsAsErrors = true

    // Two separate processing chains.
    // First provides the output jar for the module.
    // The combination of R8 & ProGuard in that order can provide the best results.
    // 1. R8 → ProGuard (1.2 KB → 610 B).
    shrink {
        fullMode = true
        shrinkWithProGuard()
    }
    // 2. ProGuard → R8 (1.2 KB → 793 B).
    shrinkWithProGuard { shrinkWithR8 { fullMode = true } }
    // 3. R8 → R8 (1.2 KB → 980 B).
    shrinkWithR8 { shrinkWithR8 { fullMode = true } }
}
