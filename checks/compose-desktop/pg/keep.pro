
# See `fluxo.shrink.ShrinkerKeepRulesBySeedsTest` for tests on what's supported by R8 and ProGuard.

# The weakest rule that will keep annotations and attributes is
# https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md#r8-full-mode
# -keep[classmembers],allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class-specification

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-dontwarn androidx.annotation.**

# JetBrains Runtime (JBR) bridge classes shipped with Compose Desktop call
# `MethodHandle.invokeExact(...)` with explicit signatures. ProGuard cannot
# resolve those against the polymorphic-signature method on
# `java.lang.invoke.MethodHandle` because the actual runtime descriptor is
# synthesised per call-site by the JVM. The references are intrinsically
# unresolvable from a static-analysis perspective; suppress the warning.
-dontwarn com.jetbrains.JBR
-dontwarn com.jetbrains.JBR$*
