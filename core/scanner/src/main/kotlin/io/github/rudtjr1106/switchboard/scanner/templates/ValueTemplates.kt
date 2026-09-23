package io.github.rudtjr1106.switchboard.scanner.templates

import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.config.ValueType

/**
 * 자유 값(`values`)을 타입이 있는 코틀린 코드로 옮긴다
 *
 * Firebase Remote Config 는 `getBoolean("showEvent")` 처럼 문자열 키로 읽어서 오타를 실행할 때에야 알게 된다.
 * 여기서는 스키마에 적힌 키·타입·기본값을 그대로 클래스로 만들어 컴파일러가 잡게 한다. 기본값이 박혀 있으므로
 * 설정을 못 받아온 첫 실행이나 오프라인에서도 같은 값으로 동작한다.
 */
internal object ValueCode {

    fun kotlinType(spec: ValueSpec): String = when (spec.type) {
        ValueType.BOOLEAN -> "Boolean"
        ValueType.INTEGER -> "Int"
        ValueType.NUMBER -> "Double"
        ValueType.STRING -> "String"
    }

    /** 스키마의 기본값을 코틀린 리터럴로 */
    fun defaultLiteral(spec: ValueSpec): String = when (spec.type) {
        ValueType.BOOLEAN -> (spec.default.content.toBooleanStrictOrNull() ?: false).toString()
        ValueType.INTEGER -> (spec.default.content.toIntOrNull() ?: 0).toString()
        ValueType.NUMBER -> (spec.default.content.toDoubleOrNull() ?: 0.0).toString()
        ValueType.STRING -> quote(spec.default.content)
    }

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** 주석 한 줄 (설명이 있으면 설명, 없으면 이름) */
    fun comment(spec: ValueSpec): String {
        val description = spec.description?.takeIf { it.isNotBlank() }
        val options = if (spec.options.isNotEmpty()) " (${spec.options.joinToString(", ")})" else ""
        return "/** ${description ?: spec.displayLabel}$options */"
    }
}

/** 앱이 읽는 값 묶음. 필드 이름·타입·기본값이 저장소 schema.json 에서 나온다 */
internal object RemoteValuesFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.modelPackage,
        emptyList(),
        """
/**
 * 원격 설정 저장소(${ctx.repoFullName})의 자유 값. 스위치보드가 만든 파일이다
 *
 * 각 값의 기본값은 그 저장소 schema.json 의 `default` 다. 설정을 받지 못하면 이 값으로 동작한다.
 * 값을 더하거나 이름을 바꾸려면 스위치보드에서 고치고 이 파일을 다시 만들면 된다.
 */
data class RemoteValues(
${ctx.values.joinToString("\n") { spec ->
            "    " + ValueCode.comment(spec) + "\n    val ${spec.key}: ${ValueCode.kotlinType(spec)} = ${ValueCode.defaultLiteral(spec)},"
        }}
)
""",
    )
}

internal object GetRemoteValuesUseCaseFile {
    fun render(ctx: TemplateContext): String = ctx.source(
        ctx.useCasePackage,
        listOf("${ctx.modelPackage}.RemoteValues", "${ctx.repositoryPackage}.RemoteConfigRepository") + ctx.injectImports,
        """
/** 원격 설정(${ctx.repoFullName})의 자유 값을 가져온다. 스위치보드가 만든 파일이다 */
class GetRemoteValuesUseCase${ctx.injectConstructor}(
    private val remoteConfigRepository: RemoteConfigRepository,
) {
    // 받아오지 못하면 기본값이 든 RemoteValues 를 돌려준다. 값이 없다고 화면이 멈추면 안 된다
    suspend operator fun invoke(): RemoteValues {
        return remoteConfigRepository.getValues().getOrElse { RemoteValues() }
    }
}
""",
    )
}
