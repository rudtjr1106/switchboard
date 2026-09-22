package io.github.rudtjr1106.switchboard.config

/**
 * 안내를 띄울 수 있는 화면 하나
 *
 * @property id schema.json 의 `screen` enum 값. 앱의 경로 이름(MainDestination)과 같다
 * @property label 편집기에 보여줄 이름. schema.json 의 `x-switchboard.screens` 에서 읽고 없으면 [id] 그대로다
 * @property group 사이드바·피커에서 묶어 보여줄 구분 (예: "시작·인증", "홈")
 */
data class ScreenInfo(
    val id: String,
    val label: String = ScreenCatalog.defaultLabel(id),
    val group: String? = null,
) {
    val isAll: Boolean get() = id == ScreenCatalog.ALL

    /** 라벨·그룹이 기본값 그대로인지. 기본값뿐이면 schema.json 에 x-switchboard 를 쓰지 않는다 */
    val hasMetadata: Boolean get() = label != ScreenCatalog.defaultLabel(id) || group != null
}

data class ScreenGroup(val name: String?, val screens: List<ScreenInfo>)

/** schema.json 이 허용하는 화면 목록. 순서는 스키마의 enum 순서를 그대로 따른다 */
class ScreenCatalog(screens: List<ScreenInfo>) {

    val screens: List<ScreenInfo> = screens.distinctBy { it.id }
    private val byId: Map<String, ScreenInfo> = this.screens.associateBy { it.id }

    val ids: List<String> get() = screens.map { it.id }

    val groups: List<ScreenGroup> by lazy {
        val ordered = LinkedHashMap<String?, MutableList<ScreenInfo>>()
        for (screen in screens) ordered.getOrPut(screen.group) { mutableListOf() }.add(screen)
        ordered.map { (name, members) -> ScreenGroup(name, members) }
    }

    val hasMetadata: Boolean get() = screens.any { it.hasMetadata }

    operator fun contains(id: String): Boolean = id in byId

    fun find(id: String): ScreenInfo? = byId[id]

    fun label(id: String): String = byId[id]?.label ?: id

    /** 라벨·그룹만 바꾼다. 목록에 없는 id 는 무시한다 */
    fun withMetadata(updates: Collection<ScreenInfo>): ScreenCatalog {
        val byUpdateId = updates.associateBy { it.id }
        return ScreenCatalog(screens.map { screen -> byUpdateId[screen.id]?.let { screen.copy(label = it.label, group = it.group) } ?: screen })
    }

    companion object {
        /** 모든 화면을 뜻하는 값. 점검 안내처럼 앱 전체에 띄울 때 쓴다 */
        const val ALL = "ALL"
        const val ALL_LABEL = "모든 화면"
        const val ALL_GROUP = "전체"

        val EMPTY = ScreenCatalog(emptyList())

        fun defaultLabel(id: String): String = if (id == ALL) ALL_LABEL else id

        /** 새 저장소의 기본 화면 목록. 나중에 프로젝트 스캔으로 채운다 */
        fun starter(): ScreenCatalog = ScreenCatalog(listOf(ScreenInfo(ALL, ALL_LABEL, ALL_GROUP)))
    }
}
