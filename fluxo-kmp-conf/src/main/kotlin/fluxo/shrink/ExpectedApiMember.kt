package fluxo.shrink

import java.util.Collections.emptyMap

internal typealias ExpectedMembersMap = Map<String, ExpectedApiMember<ClassMemberSignature>>

internal class ExpectedApiMember<S : ClassMemberSignature>(
    val list: MutableList<S>,
    val isUnique: Boolean,
    var default: ClassMemberSignature? = null,
)

internal fun ClassSignature.expectedMembersMap(): ExpectedMembersMap {
    val expectedMap = hashMapOf<String, MutableList<ClassMemberSignature>>()
    memberSignatures.values.forEach {
        expectedMap.getOrPut(it.name) { mutableListOf() }.add(it)
    }
    return expectedMap.takeUnless { it.isEmpty() }
        ?.mapValues { (_, list) ->
            ExpectedApiMember(
                list = list,
                isUnique = list.size == 1,
                default = list.firstOrNull { it is MethodSignature && it.parameterTypes.isEmpty() },
            )
        }
        ?: emptyMap()
}

internal fun ClassMemberSignature.methodNameForTest(
    expected: ExpectedApiMember<*>?,
    name: String = this.name,
    descriptor: String = this.descriptor,
): String {
    if (expected != null) {
        if (expected.default == null) {
            expected.default = this
        }
        if (expected.isUnique || expected.default === this) {
            return name
        }
    }
    return when (this) {
        is MethodSignature -> name + descriptor
        is FieldSignature -> "$name: $descriptor"
    }
}

internal val ClassMemberSignature.descriptor: String
    get() = when (this) {
        is MethodSignature -> parameterTypes.joinToString(
            separator = "",
            prefix = "(",
            postfix = ")" + descriptorTypeFromName(returnType),
            transform = ::descriptorTypeFromName,
        )

        is FieldSignature -> descriptorTypeFromName(returnType)
    }
