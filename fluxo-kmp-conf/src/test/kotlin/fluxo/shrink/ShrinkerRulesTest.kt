package fluxo.shrink

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.DisplayName

/**
 * Test R8 and ProGuard keep rules based on `-printseeds` output.
 *
 * Theese are pretty slow integration tests,
 * but they can ensure correctness of the keep rules.
 */
@Suppress("ShrinkerUnresolvedReference")
internal class ShrinkerRulesTest : ShrinkerTestBase() {
    // https://www.guardsquare.com/manual/configuration/usage#classspecification
    // https://r8-docs.preemptive.com/
    // NOTE: R8 compat always keeps no-argument constructor for kept classes!
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!


    // region simple cases

    @Test
    @OptIn(ExperimentalCompilerApi::class)
    fun compileKotlinClass() {
        // Load compiled classes and inspect generated code through reflection
        val result = compileCode()
        val kClazz = result.classLoader.loadClass("KClass")
        assertNotNull(kClazz.getDeclaredMethod("foo"))

        // Check compiled files
        val outputDir = result.outputDirectory
        assertTrue(outputDir.isDirectory && !outputDir.list().isNullOrEmpty())
        val files = result.compiledClassAndResourceFiles
        assertTrue(files.isNotEmpty())
        assertTrue(files.all { it.isFile && it.exists() })
    }


    // differs: R8 full, R8 compat
    // NOTE: R8 saves the non-argument constructor for kept class with no body in keep rule
    //  even in full-mode, ProGuard doesn't.
    @Test
    @DisplayName("USE WITH CAUTION! -keep class KClass")
    fun keep_noBody() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass",
        expected = "KClass",
        expectedR8 = "KClass\nKClass: KClass()",
    )

    // Keep everything in class.
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
    @Test
    @DisplayName("-keep class KClass { *; }")
    fun keepWithEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *; }",
        expected = KCLASS_ALL_SEEDS,
    )

    // Keep everything in class with explicit groups.
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
    @Test
    @DisplayName("-keep class KClass { <init>(...); <methods>; <fields>; }")
    fun keepWithAllByGroup() = assertSeeds(
        code = KCLASS_CODE,
        rules = """
            -keep class KClass {
                <init>(...);
                <methods>;
                <fields>;
            }
        """,
        expected = KCLASS_ALL_SEEDS,
    )

    // Keep every method in class.
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
    @Test
    @DisplayName("-keep class KClass { <methods>; }")
    fun keepWithMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <methods>; }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: long[] baz(int[])
            KClass: void <clinit>()
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
    )

    // Keep every no-arg constructor in class.
    @Test
    @DisplayName("-keep class KClass { <init>(); }")
    fun keepWithNoArgConstructor() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(); }",
        expected = """
            KClass
            KClass: KClass()
        """,
    )

    // Keep every constructor in class.
    @Test
    @DisplayName("-keep class KClass { <init>(...); }")
    fun keepWithConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
        """,
    )

    // Keep every field in class.
    @Test
    @DisplayName("-keep class KClass { <fields>; }")
    fun keepWithFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <fields>; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: int CONST
            KClass: int i
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
            KClass: long l
        """,
    )

    @Test
    @DisplayName("-keep class KClass { void foo(); }")
    fun keepByFullMethodSignature() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { void foo(); }",
        expected = """
            KClass
            KClass: void foo()
        """,
    )

    // endregion


    // region <init>

    // WARN: both ProGuard and R8 don't properly match arg here!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { <init>(*); }")
    fun keepWithConstructor_argTypeStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(*); }",
        expected = """
            KClass
        """,
    )

    // WARN: both ProGuard don't properly match arg here!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { <init>(*[]); }")
    fun keepWithConstructor_argType1StarArray() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(*[]); }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: KClass(boolean[])
        """,
    )

    // WARN: both ProGuard and R8 don't properly match arg here!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { <init>(**); }")
    fun keepWithConstructor_argType2Stars() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(**); }",
        expected = """
            KClass
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(**[]); }")
    fun keepWithConstructor_argType2StarsArray() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(**[]); }",
        expected = """
            KClass
            KClass: KClass(java.lang.String[])
        """,
        expectedR8 = """
            KClass
            KClass: KClass(boolean[])
            KClass: KClass(java.lang.String[])
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(***[]); }")
    fun keepWithConstructor_argType3Stars() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(***); }",
        expected = """
            KClass
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(%); }")
    fun keepWithConstructor_argTypePerc() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(%); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(int); }")
    fun keepWithConstructor_argTypeInt() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(int); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
    )

    // endregion


    // WARN: ProGuard doesn't understand `*();` syntax for methods, only for the constructor!
    // WARN: ProGuard fails with syntax error if class is specified by name here!
    @Test
    @DisplayName("DO NOT USE! -keep class * { *(); }")
    fun keepApprox_nameStarNoArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass: KClass()
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass${D}Companion: void staticCoMethod()
            KClass: KClass()
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: void foo()
            KClass: void staticCoMethod()
        """,
    )

    // WARN: ProGuard doesn't understand `*(...);` syntax for methods, only for constructors!
    // WARN: ProGuard fails with syntax error if class is specified by name here!
    @Test
    @DisplayName("DO NOT USE! -keep class * { *(...); }")
    fun keepApprox_nameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String[])
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: long[] baz(int[])
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
    )

    // differs: R8 compat, R8 full
    // WARN: ProGuard doesn't understand `* *(...);` syntax for methods!
    // https://r8-docs.preemptive.com/#wildcards-and-special-characters
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { * *(...); }")
    fun keepApprox_typeStarNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { * *(...); }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: java.lang.String getS()
        """,
    )

    // Match non-void non-constructor methods with no arguments.
    // NOTE: Seems lika an unpredictable result.
    @Test
    @DisplayName("USE WITH CAUTION! -keep class KClass { ** *(); }")
    fun keepApprox_type2StarsNameStarNoArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { ** *(); }",
        expected = """
            KClass
            KClass: java.lang.String getS()
        """,
    )

    // WARN: Both shrinkers don't match "any arguments" with `(...)` in that case!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { ** *(...); }")
    fun keepApprox_type2StarsNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { ** *(...); }",
        expected = """
            KClass
            KClass: java.lang.String getS()
        """,
    )

    // differs: R8
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!
    @Test
    @DisplayName("-keep class KClass { *** *(...); }")
    fun keepApprox_type3StarsNameStarAnyArgs() {
        val expected = """
                KClass
                KClass: KClass()
                KClass: KClass(boolean[])
                KClass: KClass(int)
                KClass: KClass(int,long)
                KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
                KClass: KClass(java.lang.String)
                KClass: KClass(java.lang.String[])
                KClass: double bar(byte,float)
                KClass: int bar(java.lang.String,byte,int[])
                KClass: java.lang.Short[] bazShort(java.lang.String[])
                KClass: java.lang.String access${D}getFIELD${D}cp()
                KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
                KClass: java.lang.String getS()
                KClass: java.lang.String getSTATIC_FIELD()
                KClass: long[] baz(int[])
                KClass: void <clinit>()
                KClass: void foo()
                KClass: void setS(java.lang.String)
                KClass: void staticCoMethod()
            """.trimIndent()
        assertSeeds(
            code = KCLASS_CODE,
            rules = "-keep class KClass { *** *(...); }",
            expected = expected,
        )
    }

    // differs: ProGuard
    // WARN: With `% *(...)` ProGuard matches constructors and void methods,
    //  while R8 does work as expected.
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { % *(...); }")
    fun keepApprox_primitiveTypeNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { % *(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
            KClass: void <clinit>()
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
        expectedR8 = """
            KClass
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
        """,
    )

    @Test
    @DisplayName("-keep class KClass { *** *(%); }")
    fun keepApprox_type3StarsNameStarPrimitiveArg() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *** *(%); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
    )

    // WARN: ProGuard matches constructors, R8 doesn't!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { % *(%); }")
    fun keepApprox_primitiveTypeNameStarPrimitiveArg() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { % *(%); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
        expectedR8 = """
            KClass
        """,
    )

    // WARN: ProGuard doesn't understand `* *;` syntax for fields, R8 does!
    // https://r8-docs.preemptive.com/#wildcards-and-special-characters
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { * *; }")
    fun keepApprox_typeStarNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { * *; }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: java.lang.String s
        """,
    )

    @Test
    @DisplayName("-keep class KClass { ** *; }")
    fun keepApprox_type2StarsNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { ** *; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
        """,
    )

    @Test
    @DisplayName("-keep class KClass { *** *; }")
    fun keepApprox_type3StarsNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *** *; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: int CONST
            KClass: int i
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
            KClass: long l
        """,
    )
}
