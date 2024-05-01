
-include rules.pro

# ProGuard-only configuration.
# Dangerous, can increase size of the artifact!
# https://www.guardsquare.com/manual/configuration/optimizations#aggressive-optimization
-optimizeaggressively

# ProGuard-only configuration.
-skipnonpubliclibraryclasses

# The `-keepkotlinmetadata` option is deprecated and will be removed in a future ProGuard release.
# Please use `-keep class kotlin.Metadata` instead.
#-keepkotlinmetadata
