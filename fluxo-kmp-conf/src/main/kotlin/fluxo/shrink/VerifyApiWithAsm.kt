package fluxo.shrink

import java.lang.System.currentTimeMillis
import java.util.jar.JarFile
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.tasks.testing.TestResult
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

internal fun ApiVerifier.verifyApiWithAsm(
    jar: JarFile,
    signature: ClassSignature,
) {
    val className = signature.name
    val path = className.replace('.', '/') + CLASS_EXTENSION
    val entry = jar.getJarEntry(path)
    requireNotNull(entry) {
        "$A Class not found in the JAR: $className"
    }
    val classReader = jar.getInputStream(entry).use { ClassReader(it) }
    val classVisitor = VerifierClassVisitor(signature, apiVerifier = this)
    classReader.accept(classVisitor, ASM_PARSING_OPTIONS)

    for ((name, expected) in classVisitor.expectedMembers) {
        if (expected.list.isEmpty()) {
            continue
        }
        for (member in expected.list) {
            val methodNameForTest = member.methodNameForTest(expected)
            val testDescr = methodTestDescriptor(methodNameForTest)

            require(false) {
                val type = when (member) {
                    is MethodSignature -> "method"
                    is FieldSignature -> "field"
                }
                "$A Expected $type was not found '$className#$name': ${member.signature}"
            }

            val resultType = TestResult.ResultType.FAILURE
            proc.completed(testDescr.id, TestCompleteEvent(currentTimeMillis(), resultType))
        }
    }
}

private const val A = "ASM,"

private const val CLASS_EXTENSION = ".class"

private const val ASM_PARSING_OPTIONS =
    ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

private val OBJECT_CLASS = Object::class.java.name.replace('.', '/')


private class VerifierClassVisitor(
    private val signature: ClassSignature,
    apiVerifier: ApiVerifier,
) : ClassVisitor(Opcodes.ASM9), ApiVerifier by apiVerifier {

    val expectedMembers = signature.expectedMembersMap()

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        super.visit(version, access, name, signature, superName, interfaces)

        // Verify bytecode version.
        require(version >= Opcodes.V1_6) {
            "$A Unsupported bytecode version '$version': $name"
        }

        val expectedType = this.signature.type
        val expectedSuper = when (expectedType) {
            ClassType.Enum -> Enum::class.java.name.replace('.', '/')
            ClassType.Annotation, ClassType.Interface -> OBJECT_CLASS
            else -> null
        }
        if (expectedSuper != null) {
            requireEquals(expected = expectedSuper, actual = superName) {
                "$A $expectedType expected to have super '$expectedSuper'" +
                    ", but it has '$superName': $name"
            }
        }

        interfaces?.sort()
        val parents = when (superName) {
            null, OBJECT_CLASS -> interfaces?.asList()
            else -> when {
                interfaces.isNullOrEmpty() -> listOf(superName)
                else -> MutableList(interfaces.size + 1) {
                    if (it == 0) superName else interfaces[it - 1]
                }
            }
        }.orEmpty()

        val expectedParents = this.signature.parents
        requireEquals(expected = expectedParents, actual = parents) {
            "$A $expectedType expected to have parents $expectedParents" +
                ", but it has $parents: $name"
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ) = verifyClassMember(name, descriptor, access, type = "Method")

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ) = verifyClassMember(name, descriptor, access, type = "Field")

    private fun verifyClassMember(
        name: String,
        descriptor: String,
        access: Int,
        type: String,
    ): Nothing? {
        val expected = expectedMembers[name]
        val members = expected?.list
        if (members.isNullOrEmpty()) {
            return null
        }
        val iterator = members.iterator()
        while (iterator.hasNext()) {
            val member = iterator.next()
            if (member.descriptor == descriptor) {
                val methodNameForTest = member.methodNameForTest(expected, name, descriptor)
                val testDescr = methodTestDescriptor(methodNameForTest)
                try {
                    // Existance check passed.
                    iterator.remove()

                    // Verify modifiers.
                    verifyModifiers(
                        modifiers = member.modifiers,
                        access = access,
                        description = name + descriptor,
                        type = type,
                    )
                } finally {
                    proc.completed(testDescr.id, TestCompleteEvent(currentTimeMillis()))
                }
            }
        }
        return null
    }

    private fun verifyModifiers(
        modifiers: List<String>,
        access: Int,
        description: String,
        type: String,
    ) {
        for (modifier in modifiers) with(description) {
            when (modifier) {
                "public" -> verifyModifier(access, type, modifier, Opcodes.ACC_PUBLIC)
                "protected" -> verifyModifier(access, type, modifier, Opcodes.ACC_PROTECTED)
                "private" -> verifyModifier(access, type, modifier, Opcodes.ACC_PRIVATE)
                "static" -> verifyModifier(access, type, modifier, Opcodes.ACC_STATIC)
                "final" -> verifyModifier(access, type, modifier, Opcodes.ACC_FINAL)
                "synchronized" -> verifyModifier(access, type, modifier, Opcodes.ACC_SYNCHRONIZED)
                "bridge" -> verifyModifier(access, type, modifier, Opcodes.ACC_BRIDGE)
                "native" -> verifyModifier(access, type, modifier, Opcodes.ACC_NATIVE)
                "abstract" -> verifyModifier(access, type, modifier, Opcodes.ACC_ABSTRACT)
                "strictfp" -> verifyModifier(access, type, modifier, Opcodes.ACC_STRICT)
                // Somehow, `ACC_SYNTHETIC` (for "synthetic") is invisible in ASM.
                // Maybe removed by the shrinker?
            }
        }
    }

    /**
     *
     * @receiver descriptor
     */
    private fun String.verifyModifier(access: Int, type: String, modifier: String, expected: Int) {
        require(access and expected != 0) {
            "$A $type was expected to be $modifier but it's not: ${signature.name}#$this"
        }
    }
}
