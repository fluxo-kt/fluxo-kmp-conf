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


    // differs: r8 compat, r8 full
    // NOTE: R8 saves the non-argument constructor for kept class with no body in keep rule,
    //  ProGuard doesn't.
    @Test
    @DisplayName("-keep class KClass")
    fun keepByFullClassName() {
        assertSeeds(
            code = KCLASS_CODE,
            rules = "-keep class KClass",
            expected = "KClass",
            expectedR8Full = "KClass\nKClass: KClass()",
        )
    }

    @Test
    @DisplayName("-keep class KClass { *; }")
    fun keepByFullClassWithEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *; }",
        expected = KCLASS_ALL_SEEDS,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(...); <methods>; <fields>; }")
    fun keepByFullClassWithAllByGroup() = assertSeeds(
        code = KCLASS_CODE,
        rules = """
            -keep class KClass {
                <init>(...);
                <methods>;
                <fields>;
            }
        """.trimIndent(),
        expected = KCLASS_ALL_SEEDS,
    )

    @Test
    @DisplayName("-keep class KClass { <methods>; }")
    fun keepByFullClassWithMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <methods>; }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: double bar(byte,float)
            KClass: int[] baz(int[])
            KClass: java.lang.String getS()
            KClass: void foo()
            KClass: void setS(java.lang.String)
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(); }")
    fun keepByFullClassWithNoArgConstructor() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(); }",
        expected = """
            KClass
            KClass: KClass()
        """,
    )

    @Test
    @DisplayName("-keep class KClass { <init>(...); }")
    fun keepByFullClassWithConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class KClass { <fields>; }")
    fun keepByFullClassWithFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <fields>; }",
        expected = """
            KClass
            KClass: int i
            KClass: java.lang.String s
            KClass: long l
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: int i
            KClass: java.lang.String s
            KClass: long l
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class * { void foo(); }")
    fun keepByFullMethodSignature() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { void foo(); }",
        expected = """
            KClass
            KClass: void foo()
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: void foo()
        """,
    )

    // endregion


    // differs: r8 compat, r8 full
    // WARN: ProGuard doesn't understand `*();` syntax for methods, only for constructors!
    @Test
    @DisplayName("-keep class * { *(); }")
    fun keepByApproxMethodSignature_nameStarNoArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(); }",
        expected = """
            KClass
            KClass: KClass()
        """,
        expectedR8Full = """
            KClass
            KClass: KClass()
            KClass: java.lang.String getS()
            KClass: void foo()
        """,
    )

    // differs: r8 compat, r8 full
    // WARN: ProGuard doesn't understand `*(...);` syntax for methods, only for constructors!
    @Test
    @DisplayName("-keep class * { *(...); }")
    fun keepByApproxMethodSignature_nameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
        """,
        expectedR8Full = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: double bar(byte,float)
            KClass: int[] baz(int[])
            KClass: java.lang.String getS()
            KClass: void foo()
            KClass: void setS(java.lang.String)
        """,
    )

    // differs: r8 compat, r8 full
    // WARN: ProGuard doesn't understand `* *(...);` syntax for methods!
    // https://r8-docs.preemptive.com/#wildcards-and-special-characters
    @Test
    @DisplayName("-keep class * { * *(...); }")
    fun keepByApproxMethodSignature_typeStarNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { * *(...); }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: java.lang.String getS()
        """,
        expectedR8Full = """
            KClass
            KClass: java.lang.String getS()
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class * { ** *(...); }")
    fun keepByApproxMethodSignature_type2StarsNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { ** *(...); }",
        expected = """
            KClass
            KClass: java.lang.String getS()
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: java.lang.String getS()
        """,
    )

    @Test
    @DisplayName("-keep class * { *** *(...); }")
    fun keepByApproxMethodSignature_type3StarsNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *** *(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: double bar(byte,float)
            KClass: int[] baz(int[])
            KClass: java.lang.String getS()
            KClass: void foo()
            KClass: void setS(java.lang.String)
        """,
    )

    // differs: ProGuard
    // WARN: With `% *(...)` ProGuard matches constructors and void methods, while R8 doesn't!
    @Test
    @DisplayName("-keep class * { % *(...); }")
    fun keepByApproxMethodSignature_primitiveTypeNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { % *(...); }",
        expected = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: double bar(byte,float)
            KClass: void foo()
            KClass: void setS(java.lang.String)
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: double bar(byte,float)
        """,
        expectedR8Full = """
            KClass
            KClass: double bar(byte,float)
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class * { *** *(%); }")
    fun keepByApproxMethodSignature_type3StarsNameStarPrimitiveArg() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *** *(%); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: KClass(int)
        """,
    )

    // differs: everything!
    // WARN: ProGuard matches constructors, R8 doesn't!
    @Test
    @DisplayName("-keep class * { % *(%); }")
    fun keepByApproxMethodSignature_primitiveTypeNameStarPrimitiveArg() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { % *(%); }",
        expected = """
            KClass
            KClass: KClass(int)
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
        """,
        expectedR8Full = """
            KClass
        """,
    )

    // differs: r8 compat, r8 full
    // WARN: ProGuard doesn't understand `* *;` syntax for fields, R8 does!
    // https://r8-docs.preemptive.com/#wildcards-and-special-characters
    @Test
    @DisplayName("-keep class * { * *; }")
    fun keepByApproxSignature_typeStarNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { * *; }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: java.lang.String s
        """,
        expectedR8Full = """
            KClass
            KClass: java.lang.String s
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class * { ** *; }")
    fun keepByApproxSignature_type2StarsNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { ** *; }",
        expected = """
            KClass
            KClass: java.lang.String s
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: java.lang.String s
        """,
    )

    // differs: r8 compat (no-argument constructor for kept class)
    @Test
    @DisplayName("-keep class * { *** *; }")
    fun keepByApproxSignature_type3StarsNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *** *; }",
        expected = """
            KClass
            KClass: int i
            KClass: java.lang.String s
            KClass: long l
        """,
        expectedR8 = """
            KClass
            KClass: KClass()
            KClass: int i
            KClass: java.lang.String s
            KClass: long l
        """,
    )
}
