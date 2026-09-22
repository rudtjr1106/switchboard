package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigCodecTest {

    @Test
    fun `android config round trips byte for byte`() {
        val text = Fixtures.androidConfigText
        val config = ConfigCodec.decode(text, Fixtures.androidSchema)
        assertEquals(1, config.version)
        assertNull(config.minimumVersion)
        assertEquals(1, config.notices.size)
        assertEquals("Act", config.notices[0].screen)
        assertEquals("2026-09-17", config.notices[0].until)
        assertEquals(text, ConfigCodec.encode(config))
    }

    @Test
    fun `ios config round trips byte for byte`() {
        val text = Fixtures.iosConfigText
        val config = ConfigCodec.decode(text, Fixtures.iosSchema)
        assertEquals("", config.minimumVersion)
        assertEquals("BLOCKING", config.notices[0].template)
        assertNull(config.notices[0].until)
        assertEquals(text, ConfigCodec.encode(config))
    }

    @Test
    fun `unknown fields survive a round trip`() {
        val text = """
            {
              "${'$'}schema": "./schema.json",
              "version": 1,
              "notices": [
                {
                  "screen": "ALL",
                  "enabled": true,
                  "template": "INFO",
                  "title": "t",
                  "body": "b",
                  "priority": 3
                }
              ],
              "featureFlags": {
                "newHome": true
              }
            }

        """.trimIndent()
        val config = ConfigCodec.decode(text, Fixtures.androidSchema)
        assertEquals(JsonPrimitive(3), config.notices[0].extras["priority"])
        assertTrue("featureFlags" in config.extras)
        assertEquals(text, ConfigCodec.encode(config))
    }

    @Test
    fun `empty notices are written inline`() {
        val config = AppConfig(version = 1, minimumVersion = "1.2", notices = emptyList())
        assertEquals(
            "{\n  \"\$schema\": \"./schema.json\",\n  \"version\": 1,\n  \"minimumVersion\": \"1.2\",\n  \"notices\": []\n}\n",
            ConfigCodec.encode(config),
        )
    }

    @Test
    fun `special characters are escaped like json`() {
        val config = AppConfig(notices = listOf(Notice(title = "따옴표 \"안녕\"", body = "줄\n바꿈")))
        val encoded = ConfigCodec.encode(config)
        assertTrue(encoded.contains("\"title\": \"따옴표 \\\"안녕\\\"\""))
        assertTrue(encoded.contains("\"body\": \"줄\\n바꿈\""))
        assertEquals(config.notices[0].title, ConfigCodec.decode(encoded, Fixtures.androidSchema).notices[0].title)
    }

    @Test
    fun `missing minimum version defaults to empty when schema supports it`() {
        val config = ConfigCodec.decode("{\"version\": 1, \"notices\": []}", Fixtures.iosSchema)
        assertEquals("", config.minimumVersion)
        assertNull(config.schemaRef)
    }
}
