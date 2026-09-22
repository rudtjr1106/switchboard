package io.github.rudtjr1106.switchboard.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenDraftsTest {

    @Test
    fun `camel case ids are tokenized including acronyms`() {
        assertEquals(listOf("Admin", "Study", "Group", "Schedule"), ScreenDrafts.tokenize("AdminStudyGroupSchedule"))
        assertEquals(listOf("QR", "Code", "Scan", "2"), ScreenDrafts.tokenize("QRCodeScan2"))
        assertEquals(listOf("Mycard"), ScreenDrafts.tokenize("Mycard"))
    }

    @Test
    fun `tokens are translated with korean word order`() {
        assertEquals("비밀번호 찾기", ScreenDrafts.draft(ScreenHint("FindPassword")).label)
        assertEquals("이메일 회원가입", ScreenDrafts.draft(ScreenHint("EmailSignUp")).label)
        assertEquals("챌린저 상세 (운영진)", ScreenDrafts.draft(ScreenHint("AdminChallengerDetail")).label)
        assertEquals("내 명함", ScreenDrafts.draft(ScreenHint("Mycard")).label)
    }

    @Test
    fun `short korean comments win when they agree with the id`() {
        assertEquals("공지 목록", ScreenDrafts.draft(ScreenHint("Notice", comment = "공지 목록")).label)
        assertEquals("프로필", ScreenDrafts.draft(ScreenHint("MyProfile", comment = "프로필 페이지")).label)
        assertEquals("내 QR 코드", ScreenDrafts.draft(ScreenHint("Qrcode", comment = "내 qr코드 페이지")).label)
        // 주석이 다른 화면 이름(옛 이름)을 말하면 id 번역을 믿는다
        assertEquals("알림", ScreenDrafts.draft(ScreenHint("Notification", comment = "공지 화면")).label)
        assertEquals("내 명함", ScreenDrafts.draft(ScreenHint("Mycard", comment = "신 마이페이지")).label)
    }

    @Test
    fun `sentences, section titles and arrows are not labels`() {
        assertNull(ScreenDrafts.labelFromComment("검색은 목록과 같은 필터 조건으로 조회해야 하므로 현재 탭·필터를 함께 넘긴다"))
        assertNull(ScreenDrafts.labelFromComment("커뮤니티 섹션"))
        assertNull(ScreenDrafts.labelFromComment("(구 마이페이지) -> (신 설정)"))
        assertEquals("개인정보 입력", ScreenDrafts.labelFromComment("개인정보 입력 단계. signUpType(SOCIAL/EMAIL)에 따라 회원가입 API가 분기됨"))
    }

    @Test
    fun `groups come from sections, then auth tokens, then the first word`() {
        assertEquals("홈", ScreenDrafts.draft(ScreenHint("ScheduleAdd", section = "홈 화면 섹션")).group)
        assertEquals("MY", ScreenDrafts.draft(ScreenHint("Qrcode", section = "마이 페이지 섹션")).group)
        assertEquals("인증", ScreenDrafts.draft(ScreenHint("Login", section = "인증")).group.let { if (it == ScreenDrafts.AUTH_GROUP) "인증" else it })
        assertEquals(ScreenDrafts.AUTH_GROUP, ScreenDrafts.draft(ScreenHint("SignUpFailCode")).group)
        assertEquals("공지", ScreenDrafts.draft(ScreenHint("NoticeDetail")).group)
    }
}
