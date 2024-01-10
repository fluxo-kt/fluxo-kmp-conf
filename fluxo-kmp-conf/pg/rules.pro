
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
-dontnote com.android.tools.r8.**
-dontnote java.lang.AbstractStringBuilder
-dontnote javax.annotation.**
-dontnote javax.inject.**
-dontnote javax.xml.**
-dontnote kotlin.**
-dontnote org.jetbrains.**
-dontnote org.w3c.dom.**
-dontnote org.xml.sax.**
-dontnote proguard.**

-dontwarn com.android.**
-dontwarn com.autonomousapps.**
-dontwarn com.diffplug.gradle.spotless.JvmLang
-dontwarn com.mikepenz.aboutlibraries.**
-dontwarn com.pinterest.ktlint.**
-dontwarn java.lang.AbstractStringBuilder
-dontwarn kotlin.**
-dontwarn org.conscrypt.**
-dontwarn org.jetbrains.**

# Kotlin metadata warnings
-dontwarn com.github.gmazzo.buildconfig.**
-dontwarn com.google.**
-dontwarn com.gradle.**
-dontwarn io.gitlab.arturbosch.detekt.**
-dontwarn kotlinx.**
-dontwarn okhttp3.internal.**
-dontwarn okio.internal.**
-dontwarn org.gradle.**
-dontwarn proguard.**
-dontwarn retrofit2.**

#-ignorewarnings
#-forceprocessing
-addconfigurationdebugging
#-whyareyoukeeping class **

-verbose

-optimizationpasses 7
-repackageclasses

# Not safe for Android
-overloadaggressively
# Suboptimal for library projects
-allowaccessmodification
# Can reduce the performance of the processed code on some JVMs
-mergeinterfacesaggressively

# Horizontal class merging increases size of the artifact.
# https://www.guardsquare.com/manual/configuration/optimizations
#-optimizations !library/*,!class/*,!field/*,!method/*,!code/*
-optimizations !class/merging/horizontal

#-dontoptimize
#-dontshrink
#-dontobfuscate

#-adaptclassstrings
-adaptresourcefilenames    **.properties,**.gif,**.jpg,**.png,**.webp,**.svg,**.ttf,**.otf,**.txt,**.xml
-adaptresourcefilecontents **.properties,**.MF

# For library projects.
# See https://www.guardsquare.com/manual/configuration/examples#library
-keepparameternames
-renamesourcefileattribute SourceFile

# https://stackoverflow.com/a/69523469/1816338
-keepattributes Signature,Exceptions,
                RuntimeVisibleAnnotations,AnnotationDefault,
                InnerClasses,PermittedSubclasses,EnclosingMethod,
                Deprecated,SourceFile,LineNumberTable,
                Synthetic,MethodParameters

-include keep.pro

# Note: -assumenosideeffects should not be used with constructors!
# It leads to bugs like https://sourceforge.net/p/proguard/bugs/702/
# Using -assumenoexternalsideeffects is fine though and should be used for that purpose.
-include assumptions.pro
-include nosideeffects.pro
