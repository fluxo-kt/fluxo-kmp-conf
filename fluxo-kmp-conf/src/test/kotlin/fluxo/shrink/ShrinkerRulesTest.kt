package fluxo.shrink

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.DisplayName

/**
 * Test R8 and ProGuard keep rules based on `-printseeds` output.
 *
 * Theese are pretty slow integration tests,
 * but they can ensure correctness of the keep rules.
 */
@Suppress("ShrinkerUnresolvedReference", "LargeClass")
internal class ShrinkerRulesTest : ShrinkerTestBase() {
    // NOTE: R8 compat always keeps no-argument constructor for kept classes!
    // NOTE: R8 doesn't save static class constructor `<clinit>` or doesn't show it in seeds!

    // https://www.guardsquare.com/manual/configuration/usage#classspecification
    // https://r8-docs.preemptive.com/#wildcards-and-special-characters
    /*
     * Wildcards and Special Characters
     *
     * `!` negates the condition described by the subsequent specification.
     *   Can be used with modifiers and with the class, interface, enum, and @interface keywords.
     *
     * `*` a sequence of zero or more characters, but NOT the package separators (.),
     *   when used with other symbols in a pattern.
     *   Matches any reference type when used alone (not supported in all contexts in ProGuard).
     *   (does not match primitive types or void).
     *
     * `**` a sequence of zero or more characters, INCLUDING package separators (.),
     *   when used with other symbols in a pattern.
     *   Matches any reference type when used alone (does not match primitive types or void).
     *
     * `***` a sequence of zero or more characters, including package separators (.),
     *   when used with other symbols in a pattern.
     *   Matches any type (primitive or non-primitive, or void, array or non-array) when used alone.
     *   Only the `***` matches array types of any dimension.
     *
     * `%` matches any primitive type (does not match void) when used alone.
     *   Declared to match "void" for ProGuard.
     *
     * `?` matches any single character in a class name, but not the package separator.
     *
     * `<integer>` integer (starting at 1) referencing the value that matched a wildcard
     *   used earlier in the specification. For -if-predicated -keep* rules,
     *   the index can reference any earlier wildcard match in the specification for either part.
     *   Neither R8 nor ProGuard seem to handle back references in the presence of wildcards
     *   in both the class name and class member names.
     *   R8 does not appear to handle back references within member specifications.
     *
     * `...` matches any number of arguments when used within
     *   parentheses `(` and `)` of a method specification.
     */


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


    // NOTE: R8 saves the non-argument constructor for kept class with no keep rule body
    //  even in full-mode, ProGuard doesn't.
    @Test
    @DisplayName("USE WITH CAUTION! -keep class KClass")
    fun keep_noBody() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass",
        expected = "KClass",
        expectedR8 = "KClass\nKClass: KClass()",
    )

    @Test
    @DisplayName("USE WITH CAUTION! -keep class *")
    fun keepAny_noBody() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class *",
        expected = """
            KClass
            KClass${D}Companion
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass: KClass()
        """,
    )

    // Keep everything in class.
    @Test
    @DisplayName("-keep class KClass { *; }")
    fun keepWithEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *; }",
        expected = KCLASS_SEEDS,
    )

    // Keep everything in any class.
    @Test
    @DisplayName("-keep class * { *; }")
    fun keepAnyWithEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *; }",
        expected = KCLASS_ALL_SEEDS,
    )

    // Keep everything in class with explicit groups.
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
        expected = KCLASS_SEEDS,
    )

    // Keep everything in any class with explicit groups.
    @Test
    @DisplayName("-keep class * { <init>(...); <methods>; <fields>; }")
    fun keepAnyWithAllByGroup() = assertSeeds(
        code = KCLASS_CODE,
        rules = """
            -keep class * {
                <init>(...);
                <methods>;
                <fields>;
            }
        """,
        expected = KCLASS_ALL_SEEDS,
    )

    // Keep every method in class.
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
            KClass: java.lang.String fooString(java.lang.String)
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


    // region <init> / constructors

    // WARN: ProGuard doesn't properly match arg here!
    @Test
    @DisplayName("DO NOT USE FOR ProGuard! -keep class KClass { <init>(*); }")
    fun keepWithConstructor_argTypeStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(*); }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: KClass(java.lang.String)
        """,
    )

    // WARN: ProGuard doesn't properly match arg here!
    @Test
    @DisplayName("DO NOT USE FOR ProGuard! -keep class KClass { <init>(*[]); }")
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

    @Test
    @DisplayName("-keep class KClass { <init>(**); }")
    fun keepWithConstructor_argType2Stars() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { <init>(**); }",
        expected = """
            KClass
            KClass: KClass(java.lang.String)
        """,
    )

    @Test
    @DisplayName("-keep class * { <init>(**); }")
    fun keepAnyWithConstructor_argType2Stars() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { <init>(**); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
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
    @DisplayName("-keep class KClass { <init>(***); }")
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
    fun keepWithConstructor_argTypePrimitive() = assertSeeds(
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

    // WARN: R8 doesn't understand `*();` syntax for constructors, matching them with methods!
    @Test
    @DisplayName("DO NOT USE! -keep class * { *(); }")
    fun keepApproxAny_nameStarNoArgs() = assertSeeds(
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

    // WARN: ProGuard fails with syntax error if class is specified by name here!
    // WARN: R8 doesn't understand `*();` syntax for constructors, mixing them with methods!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { *(); }")
    fun keepApprox_nameStarNoArgs() {
        val rules = "-keep class KClass { *(); }"

        // ProGuard fails with syntax error if class is specified by name here!
        assertFails {
            assertSeeds(rules = rules, expectedProGuard = "")
        }

        assertSeeds(
            code = KCLASS_CODE,
            rules = rules,
            expectedR8 = """
                KClass
                KClass: KClass()
                KClass: java.lang.String access${D}getFIELD${D}cp()
                KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
                KClass: java.lang.String getS()
                KClass: java.lang.String getSTATIC_FIELD()
                KClass: void foo()
                KClass: void staticCoMethod()
            """,
        )
    }

    // WARN: R8 doesn't understand `*();` syntax for constructors, mixing them with methods!
    @Test
    @DisplayName("DO NOT USE! -keep class * { *(...); }")
    fun keepApproxAny_nameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(...); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(java.lang.String)
            KClass: KClass(java.lang.String[])
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass${D}Companion: void staticCoMethod()
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
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: long[] baz(int[])
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
    )

    // WARN: ProGuard fails with syntax error if class is specified by name here!
    // WARN: R8 doesn't understand `*();` syntax for constructors, mixing them with methods!
    @Test
    @DisplayName("DO NOT USE! -keep class KClass { *(...); }")
    fun keepApprox_nameStarAnyArgs() {
        val rules = "-keep class KClass { *(...); }"

        // ProGuard fails with syntax error if class is specified by name here!
        assertFails {
            assertSeeds(rules = rules, expectedProGuard = "")
        }

        assertSeeds(
            code = KCLASS_CODE,
            rules = rules,
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
                KClass: java.lang.String fooString(java.lang.String)
                KClass: java.lang.String getS()
                KClass: java.lang.String getSTATIC_FIELD()
                KClass: long[] baz(int[])
                KClass: void foo()
                KClass: void setS(java.lang.String)
                KClass: void staticCoMethod()
            """,
        )
    }

    @Test
    @DisplayName("USE WITH CAUTION! -keep class * { *(%); }")
    fun keepApproxAny_nameStarPrimitiveArg() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { *(%); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass: KClass(int)
        """,
    )

    @Test
    @DisplayName("DO NOT USE! -keep class KClass { *(%); }")
    fun keepApprox_nameStarPrimitiveArg() {
        val rules = "-keep class KClass { *(%); }"

        // ProGuard fails with syntax error if class is specified by name here!
        assertFails {
            assertSeeds(rules = rules, expectedProGuard = "")
        }

        assertSeeds(
            code = KCLASS_CODE,
            rules = rules,
            expectedR8 = """
                KClass
                KClass: KClass(int)
            """,
        )
    }

    // endregion


    // region methods

    // WARN: ProGuard doesn't understand `* *(...);` syntax for methods!
    @Test
    @DisplayName("DO NOT USE FOR ProGuard! -keep class * { * *(...); }")
    fun keepApproxAny_typeStarNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { * *(...); }",
        expected = """
            KClass
            KClass${D}Companion
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
        """,
    )

    // WARN: ProGuard doesn't understand `* *(...);` syntax for methods!
    @Test
    @DisplayName("DO NOT USE FOR ProGuard! -keep class KClass { * *(...); }")
    fun keepApprox_typeStarNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { * *(...); }",
        expected = """
            KClass
        """,
        expectedR8 = """
            KClass
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
        """,
    )

    @Test
    @DisplayName("-keep class KClass { ** *(); }")
    fun keepApprox_type2StarsNameStarNoArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { ** *(); }",
        expected = """
            KClass
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
        """,
    )

    @Test
    @DisplayName("-keep class KClass { ** *(...); }")
    fun keepApprox_type2StarsNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { ** *(...); }",
        expected = """
            KClass
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
        """,
    )

    @Test
    @DisplayName("-keep class KClass { *** *(...); }")
    fun keepApprox_type3StarsNameStarAnyArgs() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { *** *(...); }",
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
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: long[] baz(int[])
            KClass: void <clinit>()
            KClass: void foo()
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
    )

    // differs: ProGuard
    // WARN: With `% *(...)` ProGuard matches constructors and void methods,
    //  while R8 does match some expected methods.
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

    // endregion


    // region fields

    // WARN: ProGuard doesn't match all fileds with `* *;` syntax as R8 does!
    @Test
    @DisplayName("DO NOT USE FOR ProGuard! -keep class KClass { * *; }")
    fun keepApprox_typeStarNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { * *; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
        """,
        expectedR8 = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
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

    @Test
    @DisplayName("-keep class KClass { % *; }")
    fun keepApprox_primitiveTypeNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class KClass { % *; }",
        expected = """
            KClass
            KClass: int CONST
            KClass: int i
            KClass: long l
        """,
    )

    @Test
    @DisplayName("-keep class * { % *; }")
    fun keepApproxAny_primitiveTypeNameStar() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { % *; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass: int CONST
            KClass: int i
            KClass: long l
        """,
    )

    // endregion


    // region flags
    // https://r8-docs.preemptive.com/#modifiers
    // https://www.guardsquare.com/manual/configuration/usage#classspecification

    // WARN: ProGuard fails with syntax error here!
    @Test
    @DisplayName("DO NOT USE! -keep class * { abstract *; }")
    fun keepApprox_abstractEverything() {
        @Language("txt") // Uninject because of syntax error
        val rules = "-keep class * { abstract *; }"

        // ProGuard fails with syntax error if class is specified by name here!
        assertFails {
            assertSeeds(rules = rules, expectedProGuard = "")
        }

        assertSeeds(
            code = KCLASS_CODE,
            rules = rules,
            expectedR8 = """
                KClass
                KClass${D}Companion
                KClass: long[] baz(int[])
            """,
        )
    }

    @Test
    @DisplayName("-keep class * { abstract <methods>; }")
    fun keepApprox_abstractMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keep class * { abstract <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass: long[] baz(int[])
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { abstract <methods>; }")
    fun keepclasseswithmembersApprox_abstractMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { abstract <methods>; }",
        expected = """
            KClass
            KClass: long[] baz(int[])
        """,
    )

    // WARN: ProGuard doesn't match all final methods as R8 does this way!
    @Test
    @DisplayName("DO NOT USE! -keepclasseswithmembers class * { final *; }")
    fun keepclasseswithmembersApprox_finalEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { final *; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: double bar(byte,float)
            KClass: int CONST
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: void foo()
            KClass: void staticCoMethod()
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void staticCoMethod()
            KClass: KClass${D}Companion Companion
            KClass: double bar(byte,float)
            KClass: int CONST
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: void foo()
            KClass: void staticCoMethod()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { final <methods>; }")
    fun keepclasseswithmembersApprox_finalMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { final <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void staticCoMethod()
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: void foo()
            KClass: void staticCoMethod()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { final <fields>; }")
    fun keepclasseswithmembersApprox_finalFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { final <fields>; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: int CONST
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { native <methods>; }")
    fun keepclasseswithmembersApprox_nativeMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = """
            -keepclasseswithmembers class * { native <methods>; }
            -keep class KClass # to not leave empty result. It fails the shrinker
        """,
        expected = "KClass",
        expectedR8 = """
            KClass
            KClass: KClass()
        """,
    )

    // WARN: ProGuard doesn't match all private things as R8 does this way!
    @Test
    @DisplayName("DO NOT USE! -keepclasseswithmembers class * { private *; }")
    fun keepclasseswithmembersApprox_privateEverything() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { private *; }",
        expected = """
            KClass
            KClass: KClass(java.lang.String)
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
            KClass: long l
        """,
        expectedR8 = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass: KClass(java.lang.String)
            KClass: java.lang.Short[] bazShort(java.lang.String[])
            KClass: java.lang.String FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
            KClass: long l
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { private <methods>; }")
    fun keepclasseswithmembersApprox_privateMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { private <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass: KClass(java.lang.String)
            KClass: java.lang.Short[] bazShort(java.lang.String[])
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { private <fields>; }")
    fun keepclasseswithmembersApprox_privateFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { private <fields>; }",
        expected = """
            KClass
            KClass: java.lang.String FIELD
            KClass: java.lang.String STATIC_FIELD
            KClass: java.lang.String s
            KClass: long l
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { private <init>(...); }")
    fun keepclasseswithmembersApprox_privateConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { private <init>(...); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion()
            KClass: KClass(java.lang.String)
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { protected <methods>; }")
    fun keepclasseswithmembersApprox_protectedMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { protected <methods>; }",
        expected = """
            KClass
            KClass: KClass(java.lang.String[])
            KClass: void foo()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { protected <fields>; }")
    fun keepclasseswithmembersApprox_protectedFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { protected <fields>; }",
        expected = """
            KClass
            KClass: java.lang.String JVM_FIELD
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { protected <init>(...); }")
    fun keepclasseswithmembersApprox_protectedConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { protected <init>(...); }",
        expected = """
            KClass
            KClass: KClass(java.lang.String[])
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { public <methods>; }")
    fun keepclasseswithmembersApprox_publicMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { public <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass${D}Companion: java.lang.String getFIELD()
            KClass${D}Companion: java.lang.String getSTATIC_FIELD()
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass${D}Companion: void staticCoMethod()
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: double bar(byte,float)
            KClass: int bar(java.lang.String,byte,int[])
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String fooString(java.lang.String)
            KClass: java.lang.String getS()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: long[] baz(int[])
            KClass: void setS(java.lang.String)
            KClass: void staticCoMethod()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { public <fields>; }")
    fun keepclasseswithmembersApprox_publicFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { public <fields>; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: int CONST
            KClass: int i
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { public <init>(...); }")
    fun keepclasseswithmembersApprox_publicConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { public <init>(...); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass()
            KClass: KClass(boolean[])
            KClass: KClass(int)
            KClass: KClass(int,long)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { static <methods>; }")
    fun keepclasseswithmembersApprox_staticMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { static <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
            KClass: java.lang.String getSTATIC_FIELD()
            KClass: void <clinit>()
            KClass: void staticCoMethod()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { static <fields>; }")
    fun keepclasseswithmembersApprox_staticFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { static <fields>; }",
        expected = """
            KClass
            KClass: KClass${D}Companion Companion
            KClass: int CONST
            KClass: java.lang.String FIELD
            KClass: java.lang.String JVM_FIELD
            KClass: java.lang.String STATIC_FIELD
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { strictfp <methods>; }")
    fun keepclasseswithmembersApprox_strictfpMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { strictfp <methods>; }",
        expected = """
            KClass
            KClass: double bar(byte,float)
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { synchronized <methods>; }")
    fun keepclasseswithmembersApprox_synchronizedMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { synchronized <methods>; }",
        expected = """
            KClass
            KClass: java.lang.Short[] bazShort(java.lang.String[])
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { transient <fields>; }")
    fun keepclasseswithmembersApprox_transientFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { transient <fields>; }",
        expected = """
            KClass
            KClass: java.lang.String s
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { volatile <fields>; }")
    fun keepclasseswithmembersApprox_volatileFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { volatile <fields>; }",
        expected = """
            KClass
            KClass: long l
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { synthetic <methods>; }")
    fun keepclasseswithmembersApprox_syntheticMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { synthetic <methods>; }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass${D}Companion: void getFIELD${D}annotations()
            KClass${D}Companion: void getSTATIC_FIELD${D}annotations()
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: java.lang.String access${D}getFIELD${D}cp()
            KClass: java.lang.String access${D}getSTATIC_FIELD${D}cp()
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { synthetic <fields>; }")
    fun keepclasseswithmembersApprox_syntheticFields() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { synthetic <fields>; }",
        expected = """
            KClass
            KClass: java.lang.String JVM_FIELD
        """,
    )

    @Test
    @DisplayName("-keepclasseswithmembers class * { synthetic <init>(...); }")
    fun keepclasseswithmembersApprox_syntheticConstructors() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { synthetic <init>(...); }",
        expected = """
            KClass
            KClass${D}Companion
            KClass${D}Companion: KClass${D}Companion(kotlin.jvm.internal.DefaultConstructorMarker)
            KClass: KClass(int,long,int,kotlin.jvm.internal.DefaultConstructorMarker)
        """,
    )

    // R8 seems to not support `varargs` flag.
    @Test
    @DisplayName("DO NOT USE WITH R8! -keepclasseswithmembers class * { varargs <methods>; }")
    fun keepclasseswithmembersApprox_varargsMethods() = assertSeeds(
        code = KCLASS_CODE,
        rules = "-keepclasseswithmembers class * { varargs <methods>; }",
        expected = """
            KClass
            KClass: java.lang.Short[] bazShort(java.lang.String[])
        """,
        expectedR8 = "",
    )

    // TODO: test 'bridge' flag https://stackoverflow.com/a/58515681/1816338

    // endregion
}
