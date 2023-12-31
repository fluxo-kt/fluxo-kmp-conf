
# Keep and adapt Kotlin metadata, allow reflection to work
#-keep,allowoptimization class kotlin.Metadata

-keep,allowoptimization public class * {
    public protected *;
}

-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# #####################################
# Help to deal with reflection access #
# #####################################

# see fluxo.conf.feat.SetupAndroidLintKt.reportLintVersion
-dontnote com.android.build.gradle.internal.lint.LintTool
-keep class com.android.build.gradle.internal.lint.LintTool {
    * getVersion();
}
