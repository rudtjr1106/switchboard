package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenInfo

data class NoticeDraft(val title: String, val body: String)

/** 안내 문구를 다듬거나 상황 설명에서 초안을 만든다 */
interface NoticeCopywriter {
    suspend fun polish(title: String, body: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft
    suspend fun draft(situation: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft
}

/** 스캔한 화면 이름(EmailSignUp 등)에 한국어 라벨과 구분을 붙인다 */
interface ScreenLabeler {
    suspend fun label(screenIds: List<String>, appDescription: String? = null): List<ScreenInfo>
}

/** 연동 코드 템플릿을 프로젝트 관례(패키지·DI·HTTP 라이브러리)에 맞게 고쳐 쓴다 */
interface CodeAdapter {
    suspend fun adapt(fileName: String, template: String, projectNotes: String): String
}
