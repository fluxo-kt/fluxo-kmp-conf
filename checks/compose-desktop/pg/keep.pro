
# See `fluxo.shrink.ShrinkerKeepRulesBySeedsTest` for tests on what's supported by R8 and ProGuard.

# The weakest rule that will keep annotations and attributes is
# https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md#r8-full-mode
# -keep[classmembers],allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class-specification

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-dontwarn androidx.annotation.**
