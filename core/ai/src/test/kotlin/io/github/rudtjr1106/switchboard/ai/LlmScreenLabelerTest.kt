package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.ScreenInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmScreenLabelerTest {

    @Test
    fun `keeps ALL first, falls back for missing ids and drops unknown or duplicate ones`() = runTest {
        val fake = FakeLanguageModel(
            """[{"id":"Login","label":"로그인","group":"시작·인증"},{"id":"Home","label":"홈","group":"홈"},
               {"id":"Ghost","label":"유령","group":"?"},{"id":"Login","label":"두 번째","group":"x"}]""",
        )
        val result = LlmScreenLabeler(FakeLlmEngine(fake)).label(listOf("Login", "ALL", "Mycard", "Home", "Login"))

        assertEquals(
            listOf(
                ScreenInfo("ALL", "모든 화면", "전체"),
                ScreenInfo("Login", "로그인", "시작·인증"),
                ScreenInfo("Mycard"),
                ScreenInfo("Home", "홈", "홈"),
            ),
            result,
        )
        val call = fake.calls.single()
        assertTrue(call.user.contains("화면 id: Login, Mycard, Home"), call.user)
        assertTrue(call.user.contains("이메일 회원가입"), "few-shot 예시")
        assertTrue(call.options.jsonSchema!!.contains(""""enum":["Login","Mycard","Home"]"""), call.options.jsonSchema)
        assertTrue(call.options.jsonSchema.contains(""""minItems":3"""))
    }

    @Test
    fun `splits into batches of twenty and passes groups along`() = runTest {
        val ids = (1..45).map { "Screen$it" }
        val fake = FakeLanguageModel(
            """[{"id":"Screen1","label":"첫 화면","group":"홈"}]""",
            """[{"id":"Screen21","label":"스물한 번째","group":"공지"}]""",
            """[{"id":"Screen45","label":"마지막","group":"홈"}]""",
        )
        val result = LlmScreenLabeler(FakeLlmEngine(fake)).label(ids, appDescription = "스터디 모임 앱")

        assertEquals(3, fake.calls.size)
        assertEquals(listOf("ALL") + ids, result.map { it.id })
        assertEquals(ScreenInfo("Screen21", "스물한 번째", "공지"), result[21])
        assertTrue(fake.calls[0].user.contains("스터디 모임 앱"))
        assertTrue(fake.calls[0].options.jsonSchema!!.contains(""""maxItems":20"""))
        assertTrue(fake.calls[2].options.jsonSchema!!.contains(""""maxItems":5"""))
        assertFalse(fake.calls[0].user.contains("이미 쓴 group"))
        assertTrue(fake.calls[1].user.contains("이미 쓴 group 이름은 그대로 다시 쓴다: 홈"), fake.calls[1].user)
        assertTrue(fake.calls[2].user.contains("홈, 공지"), fake.calls[2].user)
    }

    @Test
    fun `only ALL when there is nothing to label`() = runTest {
        val fake = FakeLanguageModel()
        assertEquals(listOf(ScreenInfo("ALL", "모든 화면", "전체")), LlmScreenLabeler(FakeLlmEngine(fake)).label(listOf("ALL", " ")))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun `unparseable answer falls back to plain ids`() = runTest {
        val result = LlmScreenLabeler(FakeLlmEngine(FakeLanguageModel("죄송해요, 모르겠어요"))).label(listOf("Act"))
        assertEquals(listOf(ScreenInfo("ALL", "모든 화면", "전체"), ScreenInfo("Act")), result)
    }
}
