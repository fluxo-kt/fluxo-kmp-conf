package fluxo.conf.impl.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import fluxo.conf.impl.configureExtension
import org.gradle.api.Project

internal fun ApplicationExtension.setupPackagingOptions(
    project: Project,
    isCI: Boolean,
    isRelease: Boolean,
    removeKotlinMetadata: Boolean,
) = packaging {
    resources.pickFirsts += listOf(
        // byte-buddy-agent vs kotlinx-coroutines-debug conflict
        "**/attach_hotspot_windows.dll",
    )
    // remove all unneeded files from the apk/bundle
    resources.excludes += listOfNotNull(
        "**-metadata.json",
        "**-metadata.properties",
        "**.readme",
        "**/**-metadata.properties",
        "**/**version.txt",
        "**/*-ktx.version",
        "**/CertPathReviewerMessages_de.properties",
        "**/app-metadata.properties",
        "**/org/apache/commons/**",
        "**/version.properties",
        "*.txt",
        "META-INF/**.pro",
        "META-INF/**.properties",
        "META-INF/CHANGES**",
        "META-INF/DEPENDENCIES**",
        "META-INF/LICENSE**",
        "META-INF/MANIFEST**",
        "META-INF/NOTICE**",
        "META-INF/README**",
        "META-INF/licenses/**",
        "META-INF/native-image/**",
        "META-INF/{AL2.0,LGPL2.1,beans.xml}",
        "jni/**",
        "jsr305_annotations/**",
        "okhttp3/**",
        "res/**/keep.xml",

        // See https://github.com/Kotlin/kotlinx.coroutines#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
        "**/DebugProbesKt.bin",

        // See https://github.com/Kotlin/kotlinx.coroutines/issues/3668
        "**/previous-compilation-data.bin",

        // /com/google/api/client/googleapis/google-api-client.properties
        // required for GoogleUtils!
        "*-*.properties",
        "*.properties",
        "firebase-**.properties",
        "play-services-**.properties",
        "protolite-**.properties",
        "transport-**.properties",
        "vision-**.properties",
        "{barcode-scanning-common,build-data,common,image}.properties",

        // Warn: required for kotlin metadata & serialization
        if (removeKotlinMetadata) "**/*.kotlin_*" else null,

        // Required for Compose Layout Inspector (see b/223435818)
        if (isCI || isRelease) "META-INF/**.version" else null,
    )

    // Release-only packaging options with `androidComponents`
    // https://issuetracker.google.com/issues/155215248#comment5
    project.configureExtension("androidComponents", AndroidComponentsExtension::class) {
        onVariants(selector().withBuildType(RELEASE)) {
            it.packaging.resources.excludes.add("META-INF/**.version")
        }
    }
}
