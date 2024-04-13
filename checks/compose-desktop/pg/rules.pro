###
# ProGuard/R8 rules
###

-optimizationpasses 8
-repackageclasses

-overloadaggressively
-allowaccessmodification

# Horizontal class merging increases size of the artifact.
#-optimizations !class/merging/horizontal

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
