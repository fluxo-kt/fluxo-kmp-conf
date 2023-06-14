package fluxo.conf.data

internal object VersionCatalogConstants {
    // also: asset-pack, dynamic-feature
    val VC_ANDROID_ALIASES = arrayOf(
        "android-lib",
        "android-app",
        "android-base",
        "android",
    )
    val VC_ANDROID_VERSION_ALIASES = arrayOf("android-gradle-plugin", "agp")


    const val VC_ANDROIDX_COMPOSE_TOOLING_ALIAS = "androidx.compose.ui.tooling"
    const val VC_SQUARE_LEAK_CANARY_ALIAS = "square.leakcanary"
    const val VC_SQUARE_PLUMBER_ALIAS = "square.plumber"

    const val VC_PINNED_BUNDLE_ALIAS = "pinned"
}
