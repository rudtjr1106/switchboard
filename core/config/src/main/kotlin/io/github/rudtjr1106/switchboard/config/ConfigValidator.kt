package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.json.JsonPrimitive

/**
 * 입력하는 즉시 잡아내는 검사. 규칙은 [ConfigSchema] 에서 읽는다
 *
 * @property noticeId 안내 항목의 문제면 그 id, 앱 전체(최소 버전) 문제면 null
 */
data class ValidationIssue(val noticeId: NoticeId?, val field: String, val message: String)

class ConfigValidator(private val schema: ConfigSchema) {

    fun validate(config: AppConfig): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        minimumVersionIssue(config.minimumVersion)?.let { issues += it }
        config.notices.forEach { issues += noticeIssues(it) }
        issues += valueIssues(config)
        return issues
    }

    /**
     * 자유 값이 스키마에 맞는지
     *
     * 스키마에 없는 키는 지우지 않고 알리기만 한다. 다른 사람이 스키마를 고치는 중일 수 있고, 값 자체는 보존해야 한다.
     */
    fun valueIssues(config: AppConfig): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for ((key, element) in config.values) {
            val spec = schema.value(key)
            if (spec == null) {
                issues += ValidationIssue(null, "values.$key", "'$key' 는 schema.json 에 없는 값이에요")
                continue
            }
            val primitive = element as? JsonPrimitive
            if (primitive == null) {
                issues += ValidationIssue(null, "values.$key", "'$key' 는 편집기가 다루지 못하는 모양이에요")
                continue
            }
            ValueChecks.problem(spec, primitive)?.let {
                issues += ValidationIssue(null, "values.$key", "${spec.displayLabel}: $it")
            }
        }
        return issues
    }

    fun valueIssue(spec: ValueSpec, value: JsonPrimitive): ValidationIssue? =
        ValueChecks.problem(spec, value)?.let { ValidationIssue(null, "values.${spec.key}", it) }

    fun minimumVersionIssue(value: String?): ValidationIssue? {
        if (value == null || schema.minimumVersionPattern.matches(value)) return null
        return ValidationIssue(null, "minimumVersion", "최소 버전은 2.3 이나 2.3.0 처럼 숫자와 점만 써요")
    }

    fun noticeIssues(notice: Notice): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun issue(field: String, message: String) {
            issues += ValidationIssue(notice.id, field, message)
        }
        if (notice.screen !in schema.catalog) issue("screen", "화면 '${notice.screen}' 은 schema.json 에 없어요")
        if (notice.template !in schema.templateIds) issue("template", "모양 '${notice.template}' 은 schema.json 에 없어요")
        when {
            notice.title.isEmpty() -> issue("title", "제목을 입력하세요")
            notice.title.codePointLength() > schema.titleLimit -> issue("title", "제목은 ${schema.titleLimit}자 이내로 써 주세요")
        }
        when {
            notice.body.isEmpty() -> issue("body", "본문을 입력하세요")
            notice.body.codePointLength() > schema.bodyLimit -> issue("body", "본문은 ${schema.bodyLimit}자 이내로 써 주세요")
        }
        untilIssue(notice.until)?.let { issue("until", it) }
        return issues
    }

    fun untilIssue(until: String?): String? {
        if (until == null || ConfigDates.parse(until) != null) return null
        return "종료일 '$until' 이 yyyy-MM-dd 형식의 날짜가 아니에요"
    }

    companion object {
        /** validate 워크플로의 jsonschema maxLength 는 글자가 아닌 코드 포인트 수로 센다 */
        fun String.codePointLength(): Int = codePointCount(0, length)
    }
}
