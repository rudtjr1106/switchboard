package io.github.rudtjr1106.switchboard.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoticeChecksTest {

    @Test
    fun `missing dates and times are reported`() {
        val source = "9월 20일 새벽 2시부터 4시까지 서버 점검"
        assertEquals(listOf("4"), NoticeChecks.missingNumbers(source, "9월 20일 새벽 2시부터 점검해요"))
        assertTrue(NoticeChecks.missingNumbers(source, "9월 20일 2시~4시에 점검해요").isEmpty())
        // 2026 안의 20 은 20 으로 치지 않는다
        assertEquals(listOf("20"), NoticeChecks.missingNumbers("20일", "2026년"))
    }

    @Test
    fun `stiff tone, placeholders and length are problems`() {
        val problems = NoticeChecks.problems(NoticeDraft("점검 안내", "점검이 진행됩니다. [시간] 이후 이용 바랍니다."), "점검", 40, 10)
        assertTrue(problems.any { it.contains("~해요") }, problems.toString())
        assertTrue(problems.any { it.contains("자리표시자") }, problems.toString())
        assertTrue(problems.any { it.contains("본문이 10자") }, problems.toString())
        assertTrue(NoticeChecks.problems(NoticeDraft("서비스 점검 중이에요", "잠시 후 다시 이용해주세요."), "점검", 40, 200).isEmpty())
    }
}

class KoreanToneTest {

    @Test
    fun `formal endings become haeyo with correct conjugation`() {
        val cases = mapOf(
            "서버 점검 중입니다" to "서버 점검 중이에요",
            "문제입니다." to "문제예요.",
            "점검이 진행됩니다." to "점검이 진행돼요.",
            "지연될 수 있습니다." to "지연될 수 있어요.",
            "메일이 많습니다." to "메일이 많아요.",
            "점검을 마쳤습니다." to "점검을 마쳤어요.",
            "잠시 기다립니다." to "잠시 기다려요.",
            "다시 이용하시기 바랍니다." to "다시 이용해 주세요.",
            "양해 부탁드립니다." to "양해 부탁드려요.",
            "불편을 드려 죄송합니다." to "불편을 드려 죄송해요.",
            "다시 시도하십시오." to "다시 시도하세요.",
            "안내드립니다." to "안내드려요.",
            "이미 반영된 내용이 아닙니다." to "이미 반영된 내용이 아니에요.",
            "기다려주시면 감사하겠습니다." to "기다려주세요.",
            "다시 안내해 드리겠습니다." to "다시 안내해 드릴게요.",
            "최선을 다하겠습니다." to "최선을 다할게요.",
        )
        cases.forEach { (formal, polite) -> assertEquals(polite, KoreanTone.toHaeyo(formal), formal) }
        // 이미 해요체면 그대로
        assertEquals("잠시 후 다시 이용해주세요.", KoreanTone.toHaeyo("잠시 후 다시 이용해주세요."))
    }

    @Test
    fun `greetings are removed from the body`() {
        assertEquals(
            "지금 가입 신청이 많아 승인이 늦어질 수 있어요.",
            KoreanTone.stripGreetings("안녕하세요! 지금 가입 신청이 많아 승인이 늦어질 수 있어요. 고마워요!"),
        )
        assertEquals("점검해요.", KoreanTone.stripGreetings("안녕하세요, 여러분! 점검해요."))
        kotlin.test.assertTrue(KoreanTone.isGreeting("안녕하세요, 여러분!"))
        kotlin.test.assertFalse(KoreanTone.isGreeting("서비스 점검 중이에요"))
    }
}
