package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Firebase Remote Config 처럼 쓰는 자유 값 (`values`) */
class ConfigValuesTest {

    private val schemaText = """
        {
          "type": "object",
          "properties": {
            "version": { "const": 1 },
            "notices": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "screen": { "enum": ["ALL", "Home"] },
                  "template": { "enum": ["INFO", "BLOCKING"] },
                  "title": { "maxLength": 40 },
                  "body": { "maxLength": 200 }
                }
              }
            },
            "values": {
              "type": "object",
              "properties": {
                "eventBannerOn": { "type": "boolean", "default": false, "x-switchboard": { "label": "이벤트 배너" } },
                "maxUploadCount": { "type": "integer", "default": 5, "minimum": 1, "maximum": 10 },
                "homeTabOrder": { "type": "string", "enum": ["recent", "popular"], "default": "recent" },
                "noticeUrl": { "type": "string", "default": "", "maxLength": 20 }
              }
            }
          }
        }
    """.trimIndent()

    private val schema = ConfigSchema.parse(schemaText)

    @Test
    fun `스키마에서 값 정의를 읽는다`() {
        assertTrue(schema.supportsValues)
        assertEquals(listOf("eventBannerOn", "maxUploadCount", "homeTabOrder", "noticeUrl"), schema.values.map { it.key })

        val banner = assertNotNull(schema.value("eventBannerOn"))
        assertEquals(ValueType.BOOLEAN, banner.type)
        assertEquals("이벤트 배너", banner.displayLabel)

        val count = assertNotNull(schema.value("maxUploadCount"))
        assertEquals(ValueType.INTEGER, count.type)
        assertEquals(1.0, count.minimum)
        assertEquals(10.0, count.maximum)

        // 라벨이 없으면 키를 그대로 보여준다
        assertEquals("noticeUrl", assertNotNull(schema.value("noticeUrl")).displayLabel)
        assertEquals(listOf("recent", "popular"), assertNotNull(schema.value("homeTabOrder")).options)
    }

    @Test
    fun `값이 없는 스키마는 값 편집을 켜지 않는다`() {
        val plain = ConfigSchema.parse(schemaText.replace("\"values\"", "\"unused\""))
        assertTrue(plain.values.isEmpty())
        assertTrue(!plain.supportsValues)
    }

    @Test
    fun `타입에 맞지 않는 값을 잡아낸다`() {
        val validator = ConfigValidator(schema)
        fun problem(key: String, value: JsonPrimitive): String? =
            validator.valueIssue(assertNotNull(schema.value(key)), value)?.message

        assertNull(problem("eventBannerOn", JsonPrimitive(true)))
        assertEquals("켜고 끄기 값이어야 해요", problem("eventBannerOn", JsonPrimitive("true")))

        assertNull(problem("maxUploadCount", JsonPrimitive(5)))
        assertEquals("1 이상이어야 해요", problem("maxUploadCount", JsonPrimitive(0)))
        assertEquals("10 이하여야 해요", problem("maxUploadCount", JsonPrimitive(11)))
        assertEquals("정수여야 해요", problem("maxUploadCount", JsonPrimitive("다섯")))

        assertNull(problem("homeTabOrder", JsonPrimitive("popular")))
        assertEquals("고를 수 있는 값은 recent, popular 예요", problem("homeTabOrder", JsonPrimitive("oldest")))

        assertNull(problem("noticeUrl", JsonPrimitive("스무 자를 넘지 않는 글")))
        assertEquals("20자를 넘었어요", problem("noticeUrl", JsonPrimitive("가".repeat(21))))
    }

    @Test
    fun `스키마에 없는 키는 지우지 않고 알리기만 한다`() {
        val config = AppConfig(version = 1, values = mapOf("사라진키" to JsonPrimitive(1)))
        val issues = ConfigValidator(schema).valueIssues(config)
        assertEquals(1, issues.size)
        assertTrue("schema.json 에 없는" in issues.single().message, issues.toString())
        // 값 자체는 그대로 남아 다시 쓰인다
        assertTrue("사라진키" in ConfigCodec.encode(config))
    }

    @Test
    fun `값을 읽고 쓰고 다시 읽어도 같다`() {
        val text = """
            {
              "${'$'}schema": "./schema.json",
              "version": 1,
              "notices": [],
              "values": {
                "eventBannerOn": true,
                "maxUploadCount": 3,
                "homeTabOrder": "popular",
                "noticeUrl": "https://example.com"
              }
            }

        """.trimIndent()
        val config = ConfigCodec.decode(text, schema)
        assertEquals(JsonPrimitive(true), config.value(assertNotNull(schema.value("eventBannerOn"))))
        assertEquals(JsonPrimitive(3), config.value(assertNotNull(schema.value("maxUploadCount"))))
        assertEquals(text, ConfigCodec.encode(config), "읽고 다시 쓰면 글자 그대로여야 한다")
    }

    @Test
    fun `파일에 없는 값은 스키마 기본값으로 읽힌다`() {
        val config = AppConfig(version = 1)
        assertEquals(JsonPrimitive(5), config.value(assertNotNull(schema.value("maxUploadCount"))))
        // 값이 하나도 없으면 values 키를 새로 만들지 않는다 (기존 저장소 파일을 건드리지 않으려고)
        assertTrue("values" !in ConfigCodec.encode(config))
    }

    @Test
    fun `값을 더하면 스키마에도 정의가 생긴다`() {
        val added = schema.values + ValueSpec(
            key = "showRanking",
            type = ValueType.BOOLEAN,
            label = "랭킹 보이기",
            default = JsonPrimitive(false),
        )
        val updated = ConfigSchema.parse(SchemaRenderer.withValues(schemaText, added))
        assertEquals(5, updated.values.size)
        val spec = assertNotNull(updated.value("showRanking"))
        assertEquals("랭킹 보이기", spec.label)
        assertEquals(JsonPrimitive(false), spec.default)
        // 기존 정의도 그대로 남는다
        assertEquals(10.0, assertNotNull(updated.value("maxUploadCount")).maximum)
        assertEquals(listOf("recent", "popular"), assertNotNull(updated.value("homeTabOrder")).options)
    }

    @Test
    fun `값을 모두 빼면 스키마에서 values 가 사라진다`() {
        val updated = ConfigSchema.parse(SchemaRenderer.withValues(schemaText, emptyList()))
        assertTrue(updated.values.isEmpty())
        assertTrue("\"values\"" !in updated.text)
        // 안내 쪽 규칙은 그대로다
        assertEquals(40, updated.titleLimit)
        assertEquals(listOf("ALL", "Home"), updated.screenIds)
    }
}
