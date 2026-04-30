###
# ProGuard/R8 rules
###

-optimizationpasses 8
-repackageclasses

-overloadaggressively
-allowaccessmodification

# Horizontal class merging is disabled for two reasons:
# 1. Empirically increases artifact size on Compose Desktop bytecode.
# 2. ProGuard 7.9.x + proguard-core 9.3.x with Compose Multiplatform 1.10 input
#    triggers infinite recursion in `ProgramClass.hierarchyAccept` (cycle in the
#    merged super-class chain) → StackOverflowError mid-`Optimizing pass 2/8`.
#    Vertical merging alone is sufficient for our size goals; horizontal merging
#    is opt-in via Compose's own rules if/when consumers want it.
-optimizations !class/merging/horizontal

-adaptclassstrings
-adaptresourcefilenames    **.properties,**.gif,**.jpg,**.png,**.webp,**.svg,**.ttf,**.otf,**.txt,**.xml
-adaptresourcefilecontents **.properties,**.MF

# For problems reporting.
-renamesourcefileattribute P
-keepattributes SourceFile,LineNumberTable

-include keep.pro
-include compose-desktop-rules.pro

# Note: -assumenosideeffects should not be used with constructors!
# It leads to bugs like https://sourceforge.net/p/proguard/bugs/702/
# Using -assumenoexternalsideeffects is fine though and should be used for that purpose.
-include assumptions.pro
-include assumenosideeffects.pro
