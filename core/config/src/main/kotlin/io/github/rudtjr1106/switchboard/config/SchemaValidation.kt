package io.github.rudtjr1106.switchboard.config

import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects

/**
 * 저장소의 validate 워크플로(jsonschema + FormatChecker)와 같은 검사를 로컬에서 미리 돌린다
 *
 * 폼 검사([ConfigValidator])가 스키마와 어긋나도 여기서 한 번 더 걸러지므로 PR 이 검사에서 막히는 일이 없다.
 */
object SchemaValidation {

    fun validate(configJson: String, schemaJson: String): List<String> {
        val registry = SchemaRegistry.withDialect(Dialects.getDraft202012())
        val schema = registry.getSchema(schemaJson, InputFormat.JSON)
        val errors = schema.validate(configJson, InputFormat.JSON) { context ->
            // format_checker 가 없으면 "until": "2026/09/17" 같은 값이 통과한다
            context.executionConfig { config -> config.formatAssertionsEnabled(true) }
        }
        return errors.map { it.toString() }
    }
}
