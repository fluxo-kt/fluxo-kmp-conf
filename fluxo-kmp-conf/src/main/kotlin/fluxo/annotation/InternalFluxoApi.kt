package fluxo.annotation

/**
 * Marks **internal** parts in API. Such APIs may be changed in the future without notice.
 */
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "This is an internal API and should not be used from outside",
    level = RequiresOptIn.Level.ERROR,
)
internal annotation class InternalFluxoApi
