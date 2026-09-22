package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmNoticeCopywriterTest {

    @Test
    fun `polish sends limits and template, strips fences markdown and emoji`() = runTest {
        val fake = FakeLanguageModel(
            "```json\n{\"title\":\"**서비스 점검 중이에요** 🙏\",\"body\":\"더 나은 서비스를 위해 점검하고 있어요.  잠시 후 다시 이용해주세요.\"}\n```",
        )
        val draft = LlmNoticeCopywriter(FakeLlmEngine(fake))
            .polish("서비스 점검중입니다", "점검중입니다.", NoticeTemplate.BLOCKING, titleLimit = 20, bodyLimit = 80)

        assertEquals("서비스 점검 중이에요", draft.title)
        assertEquals("더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요.", draft.body)
        val call = fake.calls.single()
        assertTrue(call.user.contains("20자"), call.user)
        assertTrue(call.user.contains("80자"), call.user)
        assertTrue(call.user.contains("서비스 점검중입니다"))
        assertTrue(call.user.contains("차단 안내"))
        assertTrue(call.system.contains("~해요"))
        assertEquals(LlmNoticeCopywriter.SCHEMA, call.options.jsonSchema)
        assertEquals(0.3f, call.options.temperature)
    }

    @Test
    fun `over-limit answer is retried once with a stricter instruction then truncated`() = runTest {
        val fake = FakeLanguageModel(
            """{"title":"아주아주아주 긴 제목이에요","body":"본문"}""",
            """{"title":"여전히 긴 제목이에요","body":"본문"}""",
        )
        val draft = LlmNoticeCopywriter(FakeLlmEngine(fake))
            .polish("제목", "본문", NoticeTemplate.INFO, titleLimit = 5, bodyLimit = 40)

        assertEquals(2, fake.calls.size)
        val retry = fake.calls[1].user
        assertTrue(retry.contains("5자"), retry)
        assertTrue(retry.contains("아주아주아주 긴 제목이에요"), retry)
        assertEquals("여전히 긴", draft.title)
        assertEquals("본문", draft.body)
    }

    @Test
    fun `a fitting retry is returned as is`() = runTest {
        val fake = FakeLanguageModel(
            """{"title":"제목","body":"이 본문은 너무 길어서 다시 써야 해요"}""",
            """{"title":"제목","body":"짧은 본문"}""",
        )
        val draft = LlmNoticeCopywriter(FakeLlmEngine(fake))
            .draft("점검", NoticeTemplate.INFO, titleLimit = 10, bodyLimit = 10)
        assertEquals(NoticeDraft("제목", "짧은 본문"), draft)
    }

    @Test
    fun `limits count code points not utf-16 units`() = runTest {
        val fake = FakeLanguageModel("""{"title":"𝒜𝒜𝒜","body":"b"}""", """{"title":"𝒜𝒜𝒜","body":"b"}""")
        val draft = LlmNoticeCopywriter(FakeLlmEngine(fake)).draft("x", NoticeTemplate.INFO, titleLimit = 2, bodyLimit = 5)
        assertEquals("𝒜𝒜", draft.title)
    }

    @Test
    fun `draft mentions the situation and the template kind`() = runTest {
        val fake = FakeLanguageModel("""{"title":"9월 20일 새벽에 점검해요","body":"새벽 2시부터 4시까지 앱을 쓸 수 없어요."}""")
        val draft = LlmNoticeCopywriter(FakeLlmEngine(fake))
            .draft("9월 20일 새벽 2시부터 4시까지 서버 점검", NoticeTemplate.INFO, 30, 100)

        assertEquals("9월 20일 새벽에 점검해요", draft.title)
        val user = fake.calls.single().user
        assertTrue(user.contains("9월 20일 새벽 2시부터 4시까지 서버 점검"))
        assertTrue(user.contains("일반 안내"))
    }

    @Test
    fun `empty or unparseable answers throw`() = runTest {
        val copywriter = LlmNoticeCopywriter(FakeLlmEngine(FakeLanguageModel("", """{"title":"","body":" "}""")))
        val first = assertFailsWith<AiUnavailableException> { copywriter.draft("x", NoticeTemplate.INFO, 10, 10) }
        assertEquals(LlmNoticeCopywriter.EMPTY_MESSAGE, first.message)
        assertFailsWith<AiUnavailableException> { copywriter.draft("x", NoticeTemplate.INFO, 10, 10) }
    }

    @Test
    fun `no loaded model throws before asking`() = runTest {
        assertFailsWith<AiUnavailableException> {
            LlmNoticeCopywriter(FakeLlmEngine()).polish("a", "b", NoticeTemplate.INFO, 10, 10)
        }
    }
}
