package fluxo.shrink

import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.util.mapToArray
import java.lang.System.currentTimeMillis
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.tasks.testing.TestResult

@Suppress("NestedBlockDepth")
internal fun ApiVerifier.verifyApiWithReflection(
    classLoader: URLClassLoader,
    signature: ClassSignature,
) {
    val className = signature.name
    val clazz = Class.forName(className, true, classLoader)

    // Check the kotlin class reflection.
    val kClass = clazz.kotlin
    kClass.members.forEach { member ->
        member.parameters.forEach {
            it.type
            it.isOptional
        }
        member.returnType
    }

    // TODO: Check parent classes and interfaces.

    // Check the class type.
    verifyClassType(signature.type, clazz, className)

    // Check the class modifiers.
    val isClassPublic = clazz.modifiers and Modifier.PUBLIC != 0
    require(isClassPublic) {
        "Expected public class, but it's not (modifiers=${clazz.modifiers}): $className"
    }

    // Check the methods and fields.
    val declaredMethods = clazz.declaredMethods
        .groupByTo(HashMap()) { it.name }
    val declaredConstructors = clazz.declaredConstructors.asList()
    val expectedMembers = signature.expectedMembersMap()

    for (member in signature.memberSignatures.values) {
        val name = member.name
        var resultType: TestResult.ResultType? = null
        val methodNameForTest = member.methodNameForTest(expectedMembers[name], name)
        val testDescr = methodTestDescriptor(methodNameForTest)
        try {
            val returnType = descriptorTypeFromName(member.returnType, javaName = true)
            resultType = when (member) {
                is FieldSignature -> verifyField(clazz, name, returnType, className, member)
                is MethodSignature -> when (name) {
                    "<init>" -> verifyConstructor(declaredConstructors, className, member)
                    else -> verifyMethod(declaredMethods, name, returnType, className, member)
                }
            }
        } catch (e: Throwable) {
            resultType = TestResult.ResultType.FAILURE
            throw e
        } finally {
            proc.completed(testDescr.id, TestCompleteEvent(currentTimeMillis(), resultType))
        }
    }
}

private fun ApiVerifier.verifyConstructor(
    declared: List<Constructor<*>>,
    className: String,
    member: MethodSignature,
): TestResult.ResultType? {
    val pTypes = member.descriptorParameterTypes
    val constructors = declared.filterMethodsByPTypes(pTypes)
    var checked = requireEquals(expected = 1, actual = constructors?.size) {
        "Constructor was not found: $className#${member.signature}"
    }
    constructors?.let {
        if (!requireParamsAndPublic(it[0], pTypes, className, member)) {
            checked = false
        }
    }
    return if (checked) null else TestResult.ResultType.FAILURE
}

private fun ApiVerifier.verifyMethod(
    declared: Map<String, List<Method>>,
    name: String,
    returnType: String,
    className: String,
    member: MethodSignature,
): TestResult.ResultType? {
    val pTypes = member.descriptorParameterTypes
    val methods = declared[name].filterMethodsByPTypes(pTypes)
    val method = when {
        methods.isNullOrEmpty() -> null
        else -> when (methods.size) {
            1 -> methods.single()
            else -> methods.firstOrNull {
                it.returnType.name == returnType
            }
        }
    }

    // `$default` methods may be not visible in reflection `declaredMethods`.
    if (method == null && name.endsWith("\$default")) {
        return TestResult.ResultType.SKIPPED
    }

    requireNotNull(method) {
        "Method was not found: $className#${member.signature}"
    }
    val actualReturnType = method.returnType.name
    var checked = requireEquals(expected = returnType, actual = actualReturnType) {
        "Method return type mismatch (actual=$actualReturnType): $className#${member.signature}"
    }
    if (!requireParamsAndPublic(method, pTypes, className, member)) {
        checked = false
    }
    return if (checked) null else TestResult.ResultType.FAILURE
}

private fun <M> ApiVerifier.requireParamsAndPublic(
    method: M,
    pTypes: Array<String>,
    className: String,
    member: MethodSignature,
): Boolean where M : Executable, M : Member {
    val actualParams = method.parameterTypes.map { it.name }
    var checked = requireEquals(pTypes.asList(), actualParams) {
        "Parameters mismatch (actual=$actualParams): $className#${member.signature}"
    }
    if (!requirePublic(method) { "$className#${member.signature}" }) {
        checked = false
    }
    return checked
}

private val MethodSignature.descriptorParameterTypes
    get() = parameterTypes.mapToArray {
        descriptorTypeFromName(it, javaName = true)
    }

private fun <M : Executable> List<M>?.filterMethodsByPTypes(pTypes: Array<String>): List<M>? {
    if (!SHOW_DEBUG_LOGS && (isNullOrEmpty() || size == 1)) {
        return this
    }
    return this?.filter f@{ m ->
        if (m.parameterCount != pTypes.size) return@f false
        val t = m.parameterTypes
        for (element in t.indices) if (t[element].name != pTypes[element]) return@f false
        return@f true
    }
}

private fun ApiVerifier.verifyField(
    clazz: Class<*>,
    name: String,
    returnType: String,
    className: String,
    member: ClassMemberSignature,
): TestResult.ResultType? {
    val field = clazz.getDeclaredField(name)
    val actual = field.type.name
    var checked = requireEquals(expected = returnType, actual = actual) {
        "Field type mismatch (actual=$actual): $className#${member.signature}"
    }
    if (!requirePublic(field) { "$className#${member.signature}" }) {
        checked = false
    }
    return if (checked) null else TestResult.ResultType.FAILURE
}

private fun ApiVerifier.verifyClassType(
    classType: ClassType,
    clazz: Class<*>,
    className: String,
) {
    when (classType) {
        ClassType.Interface -> require(clazz.isInterface) {
            "Expected interface, but it's not: $className"
        }

        ClassType.Annotation -> require(clazz.isAnnotation) {
            "Expected annotation, but it's not: $className"
        }

        ClassType.Enum -> require(clazz.isEnum) {
            "Expected enum, but it's not: $className"
        }

        ClassType.Class -> {
            val checks = !clazz.isInterface &&
                !clazz.isAnnotation &&
                !clazz.isEnum &&
                !clazz.isArray &&
                !clazz.isPrimitive
            require(checks) {
                "Expected class, but it's not: $className"
            }
        }
    }
}
