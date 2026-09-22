package io.github.rudtjr1106.switchboard.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmCodeAdapterTest {

    private val template = "package com.example.template\n\nclass RemoteConfigClient(private val http: Any)\n"

    @Test
    fun `strips fences and prose and sends template with notes`() = runTest {
        val fake = FakeLanguageModel("여기 결과예요:\n```kotlin\npackage com.acme.app\n\nclass RemoteConfigClient(private val http: OkHttpClient)\n```\n도움이 됐길 바라요.")
        val code = LlmCodeAdapter(FakeLlmEngine(fake))
            .adapt("RemoteConfigClient.kt", template, "패키지 com.acme.app, HTTP 는 OkHttp, DI 는 Hilt")

        assertEquals("package com.acme.app\n\nclass RemoteConfigClient(private val http: OkHttpClient)\n", code)
        val call = fake.calls.single()
        assertTrue(call.user.contains("RemoteConfigClient.kt"))
        assertTrue(call.user.contains(template.trimEnd()))
        assertTrue(call.user.contains("OkHttp"))
        assertTrue(call.system.contains("Kotlin"))
        assertEquals(2048, call.options.maxTokens)
        assertEquals(0.1f, call.options.temperature)
    }

    @Test
    fun `drops commentary before package without fences`() = runTest {
        val fake = FakeLanguageModel("물론이죠! 아래처럼 고쳤어요.\npackage a.b\n\nclass X\n\n")
        assertEquals("package a.b\n\nclass X\n", LlmCodeAdapter(FakeLlmEngine(fake)).adapt("X.kt", template, ""))
    }

    @Test
    fun `blank answer throws`() = runTest {
        assertFailsWith<AiUnavailableException> {
            LlmCodeAdapter(FakeLlmEngine(FakeLanguageModel("```\n\n```"))).adapt("X.kt", template, "")
        }
    }
}
