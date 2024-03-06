package fluxo.shrink

import java.util.Collections.emptyMap
import java.util.jar.JarFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal fun ApiVerifier.verifyApiWithAsm(
    jar: JarFile,
    signature: ClassSignature,
) {
    val className = signature.name
    val path = className.replace('.', '/') + CLASS_EXTENSION
    val entry = jar.getJarEntry(path)
    require(entry != null) {
        "$A Class not found in the JAR: $className"
    }
    entry ?: return
    val classReader = jar.getInputStream(entry).use { ClassReader(it) }
    val classVisitor = VerifierClassVisitor(signature, apiVerifier = this)
    classReader.accept(classVisitor, ASM_PARSING_OPTIONS)

    for ((name, methods) in classVisitor.expectedMethods) {
        require(methods.isEmpty()) {
            val ms = methods.joinToString(", \n") { it.signature }
            "$A Expected method was not found '$className#$name': $ms"
        }
    }

    for ((name, fields) in classVisitor.expectedFields) {
        require(fields.isEmpty()) {
            val fs = fields.joinToString(", \n") { it.signature }
            "$A Expected field was not found '$className#$name': $fs"
        }
    }
}

private val ClassMemberSignature.descriptor: String
    get() = when (this) {
        is MethodSignature -> parameterTypes.joinToString(
            separator = "",
            prefix = "(",
            postfix = ")" + descriptorTypeFromName(returnType),
            transform = ::descriptorTypeFromName,
        )

        is FieldSignature -> descriptorTypeFromName(returnType)
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

    val expectedMethods: Map<String, MutableList<MethodSignature>>
    val expectedFields: Map<String, MutableList<FieldSignature>>

    init {
        val expectedMethods = hashMapOf<String, MutableList<MethodSignature>>()
        val expectedFields = hashMapOf<String, MutableList<FieldSignature>>()

        signature.memberSignatures.values.forEach {
            when (it) {
                is MethodSignature ->
                    expectedMethods.getOrPut(it.name) { mutableListOf() }.add(it)

                is FieldSignature ->
                    expectedFields.getOrPut(it.name) { mutableListOf() }.add(it)
            }
        }
        this.expectedMethods = expectedMethods.takeUnless { it.isEmpty() } ?: emptyMap()
        this.expectedFields = expectedFields.takeUnless { it.isEmpty() } ?: emptyMap()
    }

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

        when (this.signature.type) {
            ClassType.Enum -> {
                val enum = Enum::class.java.name.replace('.', '/')
                require(superName == enum) {
                    "$A Enum class must extend '$enum', was '$superName': $name"
                }
            }
            else -> {
                if (superName == OBJECT_CLASS) {
                    return
                }

                print("")
            }
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        super.visitMethod(access, name, descriptor, signature, exceptions)
        return verifyClassMember(expectedMethods, name, descriptor, access, "Method")
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor? {
        super.visitField(access, name, descriptor, signature, value)
        return verifyClassMember(expectedFields, name, descriptor, access, "Field")
    }


    private fun verifyClassMember(
        map: Map<String, MutableList<out ClassMemberSignature>>,
        name: String,
        descriptor: String,
        access: Int,
        type: String,
    ): Nothing? {
        val members = map[name]
        if (!members.isNullOrEmpty()) {
            val iterator = members.iterator()
            while (iterator.hasNext()) {
                val member = iterator.next()
                if (member.descriptor == descriptor) {
                    iterator.remove()
                    verifyModifiers(
                        modifiers = member.modifiers,
                        access = access,
                        description = name + descriptor,
                        type = type,
                    )
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
