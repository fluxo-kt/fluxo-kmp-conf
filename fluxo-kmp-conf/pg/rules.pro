
###
# ProGuard/R8 rules
###

# For more details, see
#   https://developer.android.com/studio/build/shrink-code
#   https://r8-docs.preemptive.com/
#   https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md
#   https://www.guardsquare.com/en/products/proguard/manual/usage
#   https://www.zacsweers.dev/android-proguard-rules/
#   http://omgitsmgp.com/2013/09/09/a-conservative-guide-to-proguard-for-android/
#   https://github.com/googleads/googleads-mobile-android-examples/blob/master/admob/InterstitialExample/app/proguard-rules.pro
#   http://www.alexeyshmalko.com/2014/proguard-real-world-example/
#   https://github.com/krschultz/android-proguard-snippets
#   https://github.com/jkasten2/OneSignal-ProGuard-Example/blob/72de6d6/app/proguard-rules.pro
#   https://github.com/informationextraction/core-android/blob/b05e3ec/RCSAndroid/dexguard-assumptions.pro
#   https://github.com/sarikayamehmet/AndroidSecureBlog/tree/f5c0898/DexguardTest/antlibs/DexGuard6.0.24/android
#   https://github.com/sarikayamehmet/AndroidSecureBlog/tree/f5c0898/DexguardTest/antlibs/DexGuard6.0.24/lib

# Rules for Gradle plugins
-dontnote javax.annotation.**
-dontnote javax.inject.**
-dontnote javax.xml.**
-dontnote org.jetbrains.annotations.**
-dontnote org.jetbrains.kotlin.**
-dontnote org.jetbrains.org.objectweb.asm.**
-dontnote org.w3c.dom.**
-dontnote org.xml.sax.**

-dontnote kotlin.**
-dontwarn kotlin.**
-dontnote java.lang.AbstractStringBuilder
-dontwarn java.lang.AbstractStringBuilder
-dontwarn com.diffplug.gradle.spotless.JvmLang

#-ignorewarnings

-verbose

-optimizationpasses 5
-mergeinterfacesaggressively
-overloadaggressively
-allowaccessmodification
-repackageclasses

# Dangerous, can increase size of the artifact!
# https://www.guardsquare.com/manual/configuration/optimizations#aggressive-optimization
-optimizeaggressively

# Horizontal class merging increases size of the artifact.
-optimizations !class/merging/horizontal

#-whyareyoukeeping class **

-skipnonpubliclibraryclasses

-adaptresourcefilenames    **.properties,**.gif,**.jpg,**.png,**.webp,**.svg,**.ttf,**.otf,**.txt,**.xml
-adaptresourcefilecontents **.properties,**.MF

# For library projects.
# See https://www.guardsquare.com/manual/configuration/examples#library
-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions,*Annotation*,
                InnerClasses,PermittedSubclasses,EnclosingMethod,
                Deprecated,SourceFile,LineNumberTable

-include keep.pro
-include assumptions.pro
-include nosideeffects.pro
