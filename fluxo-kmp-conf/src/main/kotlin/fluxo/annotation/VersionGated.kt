package fluxo.annotation

/**
 * Marks code that calls Gradle, Kotlin Gradle plugin or AGP API missing from the oldest version
 * fluxo supports; every caller must reach it only where that API exists (a version check, a
 * presence probe or a `NoSuchMethodError` catch).
 *
 * The oldest-version linkage check (`checkFloorLinkage`) skips marked code, so keep each marked
 * function down to the gated calls: anything else in it goes unchecked. A lambda compiles to a
 * method of its own that carries no annotation, so a gated call inside a lambda needs either a
 * marked function the lambda calls, or the whole file marked (`@file:VersionGated`).
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CLASS,
    AnnotationTarget.FILE,
)
internal annotation class VersionGated
