package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.util.UUID

/** 편집 중에만 쓰는 안내 식별자. 파일에는 저장되지 않는다 */
@JvmInline
value class NoticeId(val value: String) {
    companion object {
        fun random(): NoticeId = NoticeId(UUID.randomUUID().toString())
    }
}

/** 앱이 아는 안내 모양. 스키마에 다른 값이 있으면 [fromId] 가 null 을 돌려주고 편집기는 원시 값으로 다룬다 */
enum class NoticeTemplate(val id: String, val label: String, val summary: String) {
    INFO("INFO", "안내", "제목·본문과 확인 버튼이 있어요. 닫으면 앱을 다시 켜기 전까지 뜨지 않아요."),
    BLOCKING("BLOCKING", "차단", "닫을 수 없는 전체 화면이에요. '앱 종료' 버튼만 있어요."),
    ;

    companion object {
        fun fromId(id: String): NoticeTemplate? = entries.firstOrNull { it.id == id }
    }
}

enum class NoticeStatus { LIVE, OFF, EXPIRED }

data class Notice(
    val id: NoticeId = NoticeId.random(),
    val screen: String = ScreenCatalog.ALL,
    val enabled: Boolean = false,
    val template: String = NoticeTemplate.INFO.id,
    val title: String = "",
    val body: String = "",
    /** `YYYY-MM-DD`. 이 날짜 당일까지 뜬다. null 이면 기한이 없다 */
    val until: String? = null,
    /** 스키마에 없는 필드. 편집기가 모르는 값도 지우지 않고 그대로 다시 쓴다 */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    val displayTitle: String get() = title.ifBlank { "제목 없음" }
    val templateKind: NoticeTemplate? get() = NoticeTemplate.fromId(template)
    val isBlocking: Boolean get() = template == NoticeTemplate.BLOCKING.id

    fun untilDate(): LocalDate? = until?.let(ConfigDates::parse)

    fun status(today: LocalDate): NoticeStatus {
        if (!enabled) return NoticeStatus.OFF
        val lastDay = untilDate() ?: return NoticeStatus.LIVE
        return if (today.isAfter(lastDay)) NoticeStatus.EXPIRED else NoticeStatus.LIVE
    }

    /** 모든 화면을 막는 차단 안내(킬스위치)가 켜져 있는지 */
    fun blocksEveryScreen(today: LocalDate): Boolean =
        screen == ScreenCatalog.ALL && isBlocking && status(today) == NoticeStatus.LIVE

    /** [id] 를 뺀 내용이 같은지. data class 의 == 는 id 까지 비교한다 */
    fun contentEquals(other: Notice): Boolean =
        screen == other.screen && enabled == other.enabled && template == other.template &&
            title == other.title && body == other.body && until == other.until && extras == other.extras
}

data class AppConfig(
    val version: Int = 1,
    /** 강제 업데이트 기준 버전. 스키마가 지원하지 않으면 null, 지원하지만 비어 있으면 "" */
    val minimumVersion: String? = null,
    val notices: List<Notice> = emptyList(),
    /**
     * 앱이 읽어가는 자유 값 (`values`). 스키마의 [ValueSpec] 이 규칙을 정한다
     *
     * 편집기가 다루지 못하는 모양(목록·객체)이 들어 있어도 그대로 보존하려고 [JsonElement] 로 담는다.
     */
    val values: Map<String, JsonElement> = emptyMap(),
    /** `$schema` 값. 편집기 자동완성용이라 그대로 보존한다 */
    val schemaRef: String? = DEFAULT_SCHEMA_REF,
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    fun notice(id: NoticeId): Notice? = notices.firstOrNull { it.id == id }

    /** 값 하나. 파일에 없으면 스키마의 기본값 */
    fun value(spec: ValueSpec): JsonPrimitive = values[spec.key] as? JsonPrimitive ?: spec.default

    fun setValue(key: String, value: JsonPrimitive): AppConfig =
        copy(values = LinkedHashMap(values).apply { put(key, value) })

    fun removeValue(key: String): AppConfig =
        copy(values = LinkedHashMap(values).apply { remove(key) })

    fun indexOf(id: NoticeId): Int = notices.indexOfFirst { it.id == id }

    fun update(id: NoticeId, transform: (Notice) -> Notice): AppConfig =
        copy(notices = notices.map { if (it.id == id) transform(it) else it })

    fun add(notice: Notice = Notice()): AppConfig = copy(notices = notices + notice)

    fun remove(id: NoticeId): AppConfig = copy(notices = notices.filterNot { it.id == id })

    /** 바로 아래에 복제하고 새 id 를 돌려준다 */
    fun duplicate(id: NoticeId): Pair<AppConfig, NoticeId>? {
        val index = indexOf(id).takeIf { it >= 0 } ?: return null
        val copy = notices[index].copy(id = NoticeId.random())
        val updated = notices.toMutableList().apply { add(index + 1, copy) }
        return copy(notices = updated) to copy.id
    }

    fun contentEquals(other: AppConfig): Boolean =
        version == other.version && minimumVersion == other.minimumVersion && schemaRef == other.schemaRef &&
            extras == other.extras && values == other.values && notices.size == other.notices.size &&
            notices.zip(other.notices).all { (a, b) -> a.contentEquals(b) }

    companion object {
        const val DEFAULT_SCHEMA_REF = "./schema.json"

        fun empty(schema: ConfigSchema): AppConfig = AppConfig(
            version = schema.version,
            minimumVersion = if (schema.supportsMinimumVersion) "" else null,
        )
    }
}
