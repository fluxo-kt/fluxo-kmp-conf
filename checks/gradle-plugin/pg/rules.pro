
###
# ProGuard/R8 rules
###

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

-verbose

-optimizationpasses 5
-mergeinterfacesaggressively
-overloadaggressively
-allowaccessmodification
-repackageclasses

-skipnonpubliclibraryclasses

# Dangerous, can increase size of the artifact!
# https://www.guardsquare.com/manual/configuration/optimizations#aggressive-optimization
#-optimizeaggressively

# Horizontal class merging increases size of the artifact.
-optimizations !class/merging/horizontal

-adaptresourcefilenames    **.properties,**.gif,**.jpg,**.png,**.webp,**.svg,**.ttf,**.otf,**.txt,**.xml
-adaptresourcefilecontents **.properties,**.MF

# For library projects.
# See https://www.guardsquare.com/manual/configuration/examples#library
-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions,*Annotation*,
                InnerClasses,PermittedSubclasses,EnclosingMethod,
                Deprecated,SourceFile,LineNumberTable

-keep,allowoptimization public class * {
    public protected *;
}

-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
