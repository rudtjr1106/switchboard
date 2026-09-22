package io.github.rudtjr1106.switchboard.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigValidatorTest {

    private val validator = ConfigValidator(Fixtures.iosSchema)

    @Test
    fun `valid notice has no issues`() {
        val notice = Notice(screen = "home", title = "제목", body = "본문", until = "2026-12-31")
        assertTrue(validator.noticeIssues(notice).isEmpty())
    }

    @Test
    fun `limits count code points like jsonschema`() {
        val emoji = "😀".repeat(40)
        assertTrue(validator.noticeIssues(Notice(screen = "home", title = emoji, body = "b")).isEmpty())
        val tooLong = validator.noticeIssues(Notice(screen = "home", title = emoji + "a", body = "b"))
        assertEquals("제목은 40자 이내로 써 주세요", tooLong.single().message)
    }

    @Test
    fun `empty title and body are reported`() {
        val issues = validator.noticeIssues(Notice(screen = "home"))
        assertEquals(listOf("title", "body"), issues.map { it.field })
    }

    @Test
    fun `unknown screen and template are reported`() {
        val issues = validator.noticeIssues(Notice(screen = "Home", template = "TOAST", title = "t", body = "b"))
        assertEquals(listOf("screen", "template"), issues.map { it.field })
    }

    @Test
    fun `until must be a real iso date`() {
        assertNull(validator.untilIssue(null))
        assertNull(validator.untilIssue("2026-02-28"))
        assertTrue(validator.untilIssue("2026/02/28") != null)
        assertTrue(validator.untilIssue("2026-02-30") != null)
        assertTrue(validator.untilIssue("2026-2-8") != null)
    }

    @Test
    fun `minimum version follows schema pattern`() {
        assertNull(validator.minimumVersionIssue(""))
        assertNull(validator.minimumVersionIssue("2.3"))
        assertNull(validator.minimumVersionIssue("2.3.1"))
        assertTrue(validator.minimumVersionIssue("v2.3.1") != null)
        assertTrue(validator.minimumVersionIssue("2.3.1.4") != null)
        assertNull(ConfigValidator(Fixtures.androidSchema).minimumVersionIssue(null))
    }
}
