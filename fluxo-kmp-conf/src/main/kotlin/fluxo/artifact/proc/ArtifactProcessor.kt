package fluxo.artifact.proc

internal sealed interface ArtifactProcessor

internal sealed interface Shrinker : ArtifactProcessor

internal enum class JvmShrinker : Shrinker {
    /**
     * R8 shrinker from Google.
     *
     * Better optimized for Android, most stable,
     * but more fretful and can be tricky to configure sometimes.
     *
     * Always processes Kotlin metadata!
     */
    R8,

    /**
     * ProGuard shrinker from GuardSquare.
     *
     * Seems to provide better results for JVM library artifacts.
     * Supports more precise optimizations configuration.
     * But less stable and less optimized for Kotlin and Android.
     */
    ProGuard,
}
