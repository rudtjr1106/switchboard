package io.github.rudtjr1106.switchboard.config

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 저장소의 validate 워크플로(jsonschema + FormatChecker)와 같은 검사를 로컬에서 미리 돌린다
 *
 * 폼 검사([ConfigValidator])가 스키마와 어긋나도 여기서 한 번 더 걸러지므로 PR 이 검사에서 막히는 일이 없다.
 */
object SchemaValidation {

    fun validate(configJson: String, schemaJson: String): List<String> {
        val registry = SchemaRegistry.withDialect(Dialects.getDraft202012())
        val schema = registry.getSchema(withoutExtensions(schemaJson), InputFormat.JSON)
        val errors = schema.validate(configJson, InputFormat.JSON) { context ->
            // format_checker 가 없으면 "until": "2026/09/17" 같은 값이 통과한다
            context.executionConfig { config -> config.formatAssertionsEnabled(true) }
        }
        return errors.map { it.toString() }
    }

    /**
     * 최상위 `x-` 키(예: 화면 이름표 [ConfigSchema.EXTENSION_KEY])를 뺀다
     *
     * 검사에 쓰는 값이 아니어서 결과는 같지만, 검사기가 모르는 키워드라며 경고를 남긴다.
     * 저장소의 validate 워크플로(python jsonschema)는 모르는 키를 조용히 무시한다.
     */
    private fun withoutExtensions(schemaJson: String): String {
        val root = runCatching { Json.parseToJsonElement(schemaJson) as? JsonObject }.getOrNull() ?: return schemaJson
        if (root.keys.none { it.startsWith("x-") }) return schemaJson
        return JsonObject(root.filterKeys { !it.startsWith("x-") }).toString()
    }
}
