package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenInfo

data class NoticeDraft(val title: String, val body: String)

/** 안내 문구를 다듬거나 상황 설명에서 초안을 만든다 */
interface NoticeCopywriter {
    suspend fun polish(title: String, body: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft
    suspend fun draft(situation: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft
}

/**
 * 화면 하나에 대해 소스에서 읽은 힌트
 *
 * @property comment 목적지 선언 위의 주석 (예: `공지 상세`, `비밀번호 찾기 (이메일 인증 후 …)`)
 * @property section 목적지가 속한 구역 제목 (예: `공지 섹션`, `// region 인증`)
 */
data class ScreenHint(val id: String, val comment: String? = null, val section: String? = null)

/** 스캔한 화면 이름(EmailSignUp 등)에 한국어 라벨과 구분을 붙인다 */
interface ScreenLabeler {
    suspend fun label(screenIds: List<String>, appDescription: String? = null): List<ScreenInfo>

    /** 주석·구역 힌트를 함께 넘긴다. 기본 구현은 힌트를 버리고 [label] 을 부른다 */
    suspend fun labelWithHints(hints: List<ScreenHint>, appDescription: String? = null): List<ScreenInfo> =
        label(hints.map { it.id }, appDescription)
}

/** 연동 코드 템플릿을 프로젝트 관례(패키지·DI·HTTP 라이브러리)에 맞게 고쳐 쓴다 */
interface CodeAdapter {
    suspend fun adapt(fileName: String, template: String, projectNotes: String): String
}
