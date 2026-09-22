package io.github.rudtjr1106.switchboard.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigSchemaTest {

    @Test
    fun `android schema has no minimum version and 35 screens`() {
        val schema = Fixtures.androidSchema
        assertEquals(1, schema.version)
        assertFalse(schema.supportsMinimumVersion)
        assertEquals(35, schema.screenIds.size)
        assertEquals("ALL", schema.screenIds.first())
        assertEquals("ReceivedCard", schema.screenIds.last())
        assertEquals(listOf("INFO", "BLOCKING"), schema.templateIds)
        assertEquals(40, schema.titleLimit)
        assertEquals(200, schema.bodyLimit)
        assertEquals("모든 화면", schema.catalog.label("ALL"))
        assertEquals("EmailSignUp", schema.catalog.label("EmailSignUp"))
        assertFalse(schema.catalog.hasMetadata)
    }

    @Test
    fun `ios schema supports minimum version`() {
        val schema = Fixtures.iosSchema
        assertTrue(schema.supportsMinimumVersion)
        assertTrue(schema.minimumVersionPattern.matches("2.3.0"))
        assertTrue(schema.minimumVersionPattern.matches(""))
        assertFalse(schema.minimumVersionPattern.matches("v2.3.0"))
        assertEquals(9, schema.screenIds.size)
    }

    @Test
    fun `x-switchboard metadata gives labels and groups`() {
        val text = SchemaRenderer.render(
            screens = listOf(
                ScreenInfo("ALL", "모든 화면", "전체"),
                ScreenInfo("Login", "로그인", "시작"),
                ScreenInfo("Home", "홈", "탭"),
                ScreenInfo("Raw"),
            ),
        )
        val schema = ConfigSchema.parse(text)
        assertEquals("로그인", schema.catalog.label("Login"))
        assertEquals("시작", schema.catalog.find("Login")?.group)
        assertEquals("Raw", schema.catalog.label("Raw"))
        assertNull(schema.catalog.find("Raw")?.group)
        assertEquals(listOf("전체", "시작", "탭", null), schema.catalog.groups.map { it.name })
    }

    @Test
    fun `invalid json is reported as format error`() {
        val error = runCatching { ConfigSchema.parse("{ not json") }.exceptionOrNull()
        assertTrue(error is ConfigFormatException)
    }
}
