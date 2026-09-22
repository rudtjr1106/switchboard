package io.github.rudtjr1106.switchboard.config

import java.time.LocalDate

enum class ChangeKind { ADDED, REMOVED, ENABLED, DISABLED, MODIFIED }

data class Change(val kind: ChangeKind, val text: String, val noticeId: NoticeId? = null)

/** 불러온 설정과 고친 설정의 차이를 사람이 읽을 문장으로 만든다. 커밋 메시지·PR 본문에 쓴다 */
object ConfigDiff {

    fun between(original: AppConfig, draft: AppConfig): List<Change> {
        val changes = mutableListOf<Change>()
        if (original.minimumVersion != draft.minimumVersion) {
            val before = original.minimumVersion.orEmpty().ifEmpty { "없음" }
            val after = draft.minimumVersion.orEmpty().ifEmpty { "없음" }
            changes += Change(ChangeKind.MODIFIED, "최소 버전 $before → $after")
        }
        val originalById = original.notices.associateBy { it.id }
        val draftIds = draft.notices.map { it.id }.toSet()
        for (notice in original.notices) {
            if (notice.id !in draftIds) changes += Change(ChangeKind.REMOVED, "안내 삭제 · ${notice.displayTitle}", notice.id)
        }
        for (notice in draft.notices) {
            val before = originalById[notice.id]
            if (before == null) {
                changes += Change(ChangeKind.ADDED, "안내 추가 · ${notice.displayTitle}", notice.id)
                continue
            }
            var compared = before
            if (before.enabled != notice.enabled) {
                changes += if (notice.enabled) {
                    Change(ChangeKind.ENABLED, "안내 켜짐 · ${notice.displayTitle}", notice.id)
                } else {
                    Change(ChangeKind.DISABLED, "안내 꺼짐 · ${notice.displayTitle}", notice.id)
                }
                compared = before.copy(enabled = notice.enabled)
            }
            if (!compared.contentEquals(notice)) {
                changes += Change(ChangeKind.MODIFIED, "안내 수정 · ${notice.displayTitle}", notice.id)
            }
        }
        return changes
    }

    /** 이번 변경으로 모든 화면을 막는 차단 안내가 새로 켜지는지 */
    fun isDangerous(original: AppConfig, draft: AppConfig, today: LocalDate): Boolean {
        val originalById = original.notices.associateBy { it.id }
        return draft.notices.any { notice ->
            notice.blocksEveryScreen(today) && originalById[notice.id]?.blocksEveryScreen(today) != true
        }
    }

    fun commitTitle(changes: List<Change>): String {
        val summary = changes.firstOrNull()?.text ?: "변경"
        val others = if (changes.size > 1) " 외 ${changes.size - 1}건" else ""
        return "원격 설정: $summary$others"
    }
}
