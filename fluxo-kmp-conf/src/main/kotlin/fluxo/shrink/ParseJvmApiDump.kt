@file:Suppress("TooManyFunctions")

package fluxo.shrink

import fluxo.conf.impl.ifNotEmpty
import fluxo.log.e
import fluxo.log.i
import fluxo.log.v
import java.io.BufferedReader
import org.gradle.api.Task

internal const val KEEP_RULES_GEN_DBG = false

/**
 *
 * @see kotlinx.validation.KotlinApiBuildTask.generate
 * @see kotlinx.validation.api.ClassBinarySignature.signature
 * @see kotlinx.validation.api.ClassBinarySignature.memberSignatures
 *
 * @TODO: Can be optimized for performance
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
internal fun Task.parseJvmApiDumpTo(
    br: BufferedReader,
    signatures: LinkedHashMap<String, ClassSignature>,
) {
    val lines = br.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)

    var mode = ParserMode.EXPECT_CLASS
    var type: ClassType
    lateinit var signature: ClassSignature
    var className = ""
    f@ for (line in lines) {
        when (mode) {
            ParserMode.EXPECT_CLASS -> {
                val lineNoSuffix = line.removeSuffix(" {")
                val extendsIdx = lineNoSuffix.lastIndexOf(EXTENDS_DELIMITER)
                val parents: List<String>
                val parts = if (extendsIdx == -1) {
                    parents = emptyList()
                    lineNoSuffix
                } else {
                    val startIndex = extendsIdx + EXTENDS_DELIMITER.length
                    parents = lineNoSuffix.substring(startIndex, lineNoSuffix.length)
                        .split(EXTENDS_DELIMITER2)
                    lineNoSuffix.substring(0, extendsIdx)
                }.split(SPACE)

                check(
                    parts.size >= 2 &&
                        parts.elementAtOrNull(parts.lastIndex - 1) == CLASS,
                ) {
                    "Unexpected class declaration parts: $parts\n" +
                        "\t$line\n" +
                        "Names with special characters are not supported!"
                }

                // Drop class name and "class" keyword
                var modifiers = parts.dropLast(2)
                val clazz = parts.last().filterClassName()

                // annotation
                // [public, abstract, interface, annotation]
                val lastElement = modifiers.lastOrNull()
                if (lastElement == ANNOTATION) {
                    type = ClassType.Annotation
                    modifiers = modifiersForAnnotation(modifiers, type, line)
                }

                // interface
                // [public, abstract, interface]
                else if (lastElement == INTERFACE) {
                    type = ClassType.Interface
                    modifiers = modifiersForInterface(modifiers, type, line)
                }

                // enum
                // [public, final, class]
                else if (ENUM_MARKER in line) {
                    type = ClassType.Enum
                    modifiers = modifiersForEnum(modifiers, type, line)
                }

                // class
                // [public, final, class]
                else {
                    type = ClassType.Class
                    modifiers = modifiersForClass(modifiers)
                }

                mode = ParserMode.EXPECT_MEMBER
                signature = signatures.getOrCreate(clazz, modifiers, line, type, parents)
                className = clazz.substringAfterLast('.')

                if (KEEP_RULES_GEN_DBG) {
                    logger.i("\t- ${modifiers.joinToString(SPACE)} $clazz")
                }
            }

            ParserMode.EXPECT_MEMBER -> {
                if (line.length == 1 && line[0] == '}') {
                    mode = ParserMode.EXPECT_CLASS
                    continue@f
                }

                try {
                    val isField = FIELD in line
                    val (partsStr, member) = if (isField) {
                        line.split(FIELD, limit = 2)
                    } else {
                        line.split(FUN, limit = 2)
                    }.also {
                        check(it.size == 2) {
                            "Unexpected member declaration parts: $it\n\t$line"
                        }
                    }

                    val modifiers = partsStr.split(SPACE)
                    val memberSignature: ClassMemberSignature

                    if (isField) {
                        val (name, typeStr) = member
                            .split(' ', limit = 2)
                            .also {
                                check(it.size == 2) {
                                    "Unexpected field declaration parts: $it\n\t$line"
                                }
                            }

                        check(name.isValidName) {
                            "Field name contains forbidden characters: '$name'\n\t" +
                                "Names with special characters are not supported!"
                        }

                        memberSignature = FieldSignature(
                            name,
                            typeStr.parseSingleType(),
                            modifiers,
                        )
                    } else {
                        val (name, argsStr, returnTypeStr) = member
                            .split(" (", ")", limit = 3)
                            .also {
                                @Suppress("MagicNumber")
                                check(it.size == 3) {
                                    "Unexpected method declaration parts: $it\n\t$line"
                                }
                            }

                        check(name.isValidMethodName) {
                            "Method name contains forbidden characters: '$name'\n\t" +
                                "Names with special characters are not supported!"
                        }

                        var n = name
                        if (n == className) {
                            n = INIT
                        }

                        var returnType = returnTypeStr.parseSingleType()
                        if (n == INIT) {
                            check(returnType == VOID) {
                                "Unexpected return type for constructor: '$returnTypeStr'\n\t$line"
                            }
                            returnType = ""
                        }

                        memberSignature = MethodSignature(
                            name,
                            returnType,
                            argsStr.parseTypes(),
                            modifiers,
                        )
                    }

                    if (modifiers.contains(ABSTRACT)) {
                        check(modifiers.contains(PUBLIC)) {
                            "Abstract member must be public: $line"
                        }
                    }

                    val prev = signature.memberSignatures
                        .putIfAbsent(memberSignature, memberSignature)
                    if (prev != null) {
                        val pm = prev.modifiers
                        check(pm == modifiers) {
                            "Unexpected modifiers for member signature: $pm != $modifiers" +
                                "\n\tcurrent: $line" +
                                "\n\tprevious: $prev"
                        }
                    }

                    if (KEEP_RULES_GEN_DBG) {
                        val s = buildString { memberSignature.writeTo(this) }
                        logger.v("\t\t| ${s.trimStart()}")
                    }
                } catch (e: Throwable) {
                    val msg = e.message?.let { "\n\t$it" }.orEmpty()
                    throw IllegalStateException("Failed to parse line: $line$msg", e)
                }
            }
        }
    }
}

internal class ClassSignature(
    val name: String,
    val type: ClassType,
    val modifiers: List<String>,
    val parents: List<String>,
    val memberSignatures: MutableMap<ClassMemberSignature, ClassMemberSignature> = LinkedHashMap(),
) {
    fun writeTo(writer: Appendable, keepModifiers: String = AUTOGEN_KEEP_MODIFIERS) {
        writer.append("-keep$keepModifiers ")
        modifiers.joinTo(buffer = writer, separator = SPACE, postfix = SPACE)
        writer.append(name)

        val memberSignatures = memberSignatures
        if (memberSignatures.isNotEmpty()) {
            writer.append(SPACE).appendLine('{')
            memberSignatures.values.joinTo(writer, separator = "\n") {
                it.writeTo(writer)
                ""
            }
            writer.appendLine().append('}')
        }

        writer.appendLine()
    }
}

internal sealed interface ClassMemberSignature {
    val name: String
    val returnType: String
    val modifiers: List<String>

    fun writeTo(writer: Appendable)
}

internal class FieldSignature(
    override val name: String,
    override val returnType: String,
    override val modifiers: List<String>,
) : ClassMemberSignature {

    override fun writeTo(writer: Appendable) {
        repeat(times = 4) { writer.append(SPACE) }

        modifiers.ifNotEmpty { joinTo(buffer = writer, separator = SPACE, postfix = SPACE) }
        writer.append(returnType).append(SPACE).append(name).append(';')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FieldSignature) return false
        if (name != other.name) return false
        if (returnType != other.returnType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = MemberType.Field.ordinal + 1
        result = 31 * result + name.hashCode()
        result = 31 * result + returnType.hashCode()
        return result
    }
}

internal class MethodSignature(
    override val name: String,
    override val returnType: String,
    val parameterTypes: List<String>,
    /** Not part of the JVM method signature! */
    override val modifiers: List<String>,
) : ClassMemberSignature {

    override fun writeTo(writer: Appendable) {
        repeat(times = 4) { writer.append(SPACE) }

        modifiers.ifNotEmpty { joinTo(buffer = writer, separator = SPACE, postfix = SPACE) }
        if (returnType.isNotEmpty()) {
            writer.append(returnType).append(SPACE)
        }

        writer.append(name)

        writer.append('(')
        parameterTypes.ifNotEmpty { joinTo(writer) }
        writer.append(')').append(';')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodSignature) return false
        if (name != other.name) return false
        if (returnType != other.returnType) return false
        if (parameterTypes != other.parameterTypes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = MemberType.Method.ordinal + 1
        result = 31 * result + name.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + parameterTypes.hashCode()
        return result
    }
}

internal enum class ClassType {
    Class,
    Interface,
    Annotation,
    Enum,
}


private fun MutableMap<String, ClassSignature>.getOrCreate(
    clazz: String,
    modifiers: List<String>,
    line: String,
    type: ClassType,
    parents: List<String>,
): ClassSignature {
    return this[clazz]?.also {
        check(it.modifiers == modifiers) {
            "Unexpected modifiers for $clazz: ${it.modifiers} != $modifiers" +
                "\n\t$line" +
                "\n\t$it"
        }
        check(it.type == type) {
            "Unexpected type for $clazz: ${it.type} != $type" +
                "\n\t$line" +
                "\n\t$it"
        }
        check(it.parents == parents) {
            "Unexpected parents for $clazz: ${it.parents} != $parents" +
                "\n\t$line" +
                "\n\t$it"
        }
    } ?: ClassSignature(clazz, type, modifiers, parents).also {
        this[clazz] = it
    }
}


// region Modifiers

private fun Task.modifiersForAnnotation(
    modifiers: List<String>,
    type: ClassType,
    line: String,
): List<String> {
    val lastIndex = modifiers.lastIndex
    val mInterf = modifiers.elementAtOrNull(lastIndex - 1)
    val mAbstract = modifiers.elementAtOrNull(lastIndex - 2)
    check(mInterf == INTERFACE && mAbstract == ABSTRACT) {
        "Unexpected $type modifiers: $modifiers\n\t$line"
    }
    @Suppress("MagicNumber")
    return filterModifiers(modifiers.dropLast(3)) + INTERFACE
}

private fun Task.modifiersForInterface(
    modifiers: List<String>,
    type: ClassType,
    line: String,
): List<String> {
    val mAbstract = modifiers.elementAtOrNull(modifiers.lastIndex - 1)
    check(mAbstract == ABSTRACT) {
        "Unexpected $type modifiers: $modifiers\n\t$line"
    }
    return filterModifiers(modifiers.dropLast(2)) + INTERFACE
}

private fun Task.modifiersForEnum(
    modifiers: List<String>,
    type: ClassType,
    line: String,
): List<String> {
    check(modifiers.lastOrNull() == FINAL) {
        "Unexpected $type modifiers: $modifiers\n\t$line"
    }
    return filterModifiers(modifiers.dropLast(1)) + ENUM
}

private fun Task.modifiersForClass(modifiers: List<String>) =
    filterModifiers(modifiers) + CLASS

private fun Task.filterModifiers(list: List<String>): List<String> {
    if (list.any { it !in ALLOWED_CLASS_MODIFIERS }) {
        logger.e("Unexpected modifiers: {}", list)
        return list.filter { it in ALLOWED_CLASS_MODIFIERS }
    }
    return list
}

// endregion


private fun String.filterClassName(): String {
    val clazz = replace('/', '.')
    check(clazz.isValidName) {
        "Class name contains forbidden characters: '$clazz'\n\t" +
            "Names with special characters are not supported!"
    }
    return clazz
}


private fun String.parseSingleType(): String {
    val types = parseTypes()
    check(types.size == 1) {
        "Single type expected, but found: $types\n\t$this"
    }
    return types.single()
}

@Suppress("CyclomaticComplexMethod")
private fun String.parseTypes(): List<String> {
    if (isEmpty()) {
        return emptyList()
    }

    // Examples:
    // Lorg/jetbrains/kotlin/gradle/plugin/KotlinDependencyHandler;Ljava/lang/Object;
    // LExample;I[IILjava/lang/Object;
    // ID[Z[C[D[F[I[J[S[Ljava/lang/Integer
    // V

    val c = replace('/', '.')
    val types = mutableListOf<String>()

    var i = 0
    while (i < c.length) {
        var start = i
        var firstChar = c[start]

        val isArray = firstChar == '['
        if (isArray) {
            firstChar = c[++start]
        }


        var type: String
        if (firstChar == 'L') {
            val end = c.indexOf(';', start)
            check(end != -1) {
                "Unexpected end of type: ${c.substring(start)}\n\t$c"
            }
            type = c.substring(start + 1, end)
            check(type.isValidName) {
                "Type name contains forbidden characters: '$type'\n\t" +
                    "Names with special characters are not supported!"
            }

            i = end + 1
        } else {
            type = when (firstChar) {
                'V' -> VOID
                'Z' -> "boolean"
                'B' -> "byte"
                'C' -> "char"
                'S' -> "short"
                'I' -> "int"
                'J' -> "long"
                'F' -> "float"
                'D' -> "double"
                else -> error("Unexpected type: '${c.substring(start)}'\n\tin '$c'")
            }
            i = start + 1
        }

        if (isArray) {
            type += "[]"
        }

        types += type
    }

    return types
}

internal fun descriptorTypeFromName(name: String, javaName: Boolean = false): String {
    var arrayLevels = 0
    var i = name.length - 2
    while (i >= 1 && name[i] == '[' && name[i + 1] == ']') {
        arrayLevels++
        i -= 2
    }
    return when (arrayLevels) {
        0 -> descriptorTypeFromNameConvert(name, javaName, isArray = false)
        else -> "[".repeat(arrayLevels) + descriptorTypeFromNameConvert(
            name = name.substring(0, i + 2),
            javaName = javaName,
            isArray = true,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun descriptorTypeFromNameConvert(
    name: String,
    javaName: Boolean = false,
    isArray: Boolean = false,
): String {
    return when (name) {
        "boolean" -> if (isArray || !javaName) "Z" else name
        "byte" -> if (isArray || !javaName) "B" else name
        "char" -> if (isArray || !javaName) "C" else name
        "short" -> if (isArray || !javaName) "S" else name
        "int" -> if (isArray || !javaName) "I" else name
        "long" -> if (isArray || !javaName) "J" else name
        "float" -> if (isArray || !javaName) "F" else name
        "double" -> if (isArray || !javaName) "D" else name
        "void", "" -> if (isArray || !javaName) "V" else name
        else -> {
            val prefix = if (isArray || !javaName) "L" else ""
            val n = if (javaName) name else name.replace('.', '/')
            val suffix = if (isArray || !javaName) ";" else ""
            "$prefix$n$suffix"
        }
    }
}


private enum class ParserMode {
    EXPECT_CLASS,
    EXPECT_MEMBER,
}

private enum class MemberType {
    Field,
    Method,
}


// https://r8-docs.preemptive.com/#keep-rules
// https://www.guardsquare.com/manual/configuration/usage#keepoptionmodifiers
// 'allowoptimization' can break API sometimes, so don't use it here by default.
internal const val AUTOGEN_KEEP_MODIFIERS = ",includedescriptorclasses"

private const val EXTENDS_DELIMITER = " : "
private const val EXTENDS_DELIMITER2 = ", "

private const val ENUM_MARKER = EXTENDS_DELIMITER + "java/lang/Enum"

private const val ENUM = "enum"

private const val CLASS = "class"

private const val INTERFACE = "interface"

private const val ANNOTATION = "annotation"

private const val ABSTRACT = "abstract"

private const val PUBLIC = "public"

private const val FINAL = "final"

private const val VOID = "void"

private const val SPACE = " "

private const val FIELD = " field "

private const val FUN = " fun "

private const val INIT = "<init>"


// https://www.guardsquare.com/manual/configuration/usage#classspecification
@Suppress("ArgumentListWrapping")
private val FORBIDDEN_CHARS = charArrayOf(
    '?', '*', '<', '(', ')', ' ', '!', '%', '/', '\\', '+',
).let { chars ->
    val array = BooleanArray(chars.max().code + 1)
    chars.forEach { array[it.code] = true }
    array
}

private val ALLOWED_CLASS_MODIFIERS = hashSetOf(
    PUBLIC,
    FINAL,
    ABSTRACT,
)


private val String.isValidMethodName
    get() = equals(INIT) || isValidName

private val String.isValidName
    get() = isNotEmpty() && none {
        FORBIDDEN_CHARS.getOrNull(it.code) == true && it.isWhitespace()
    }
