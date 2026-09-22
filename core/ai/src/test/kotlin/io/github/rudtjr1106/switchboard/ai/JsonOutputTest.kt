package io.github.rudtjr1106.switchboard.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonOutputTest {

    @Serializable
    private data class Row(val id: String, val label: String = "")

    @Test
    fun `plain object`() {
        assertEquals("""{"a":1}""", JsonOutput.extract("""{"a":1}"""))
    }

    @Test
    fun `strips json fence and leading prose`() {
        val text = "결과는 다음과 같아요:\n```json\n{\"title\": \"안녕\", \"body\": \"본문\"}\n```\n끝."
        assertEquals("""{"title": "안녕", "body": "본문"}""", JsonOutput.extract(text))
    }

    @Test
    fun `prose before a bare object`() {
        assertEquals("""{"id": "Home"}""", JsonOutput.extract("물론이죠! {\"id\": \"Home\"} 이렇게요."))
    }

    @Test
    fun `first array with brackets inside strings`() {
        val text = """[{"id":"A","label":"괄호 } 와 ] 가 있는 \" 라벨"},{"id":"B"}] 그리고 {"other":1}"""
        val extracted = JsonOutput.extract(text)
        assertEquals("""[{"id":"A","label":"괄호 } 와 ] 가 있는 \" 라벨"},{"id":"B"}]""", extracted)
        assertIs<JsonArray>(JsonOutput.parseElement(text))
    }

    @Test
    fun `truncated output has no json`() {
        assertNull(JsonOutput.extract("""{"title": "잘린"""))
        assertNull(JsonOutput.extract("JSON 이 없어요"))
    }

    @Test
    fun `parse ignores unknown keys and tolerates lenient quoting`() {
        val rows = JsonOutput.parse<List<Row>>("""[{"id":"Login","label":"로그인","extra":true},{id:"Home"}]""")
        assertEquals(listOf(Row("Login", "로그인"), Row("Home")), rows)
        assertNull(JsonOutput.parse<List<Row>>("""{"id":"객체인데 배열을 기대"}"""))
    }

    @Test
    fun `stripFences keeps content without a closing fence`() {
        assertEquals("package a\n\nclass B", ModelText.stripFences("```kotlin\npackage a\n\nclass B"))
        assertEquals("그대로", ModelText.stripFences("  그대로  "))
        assertEquals("x", ModelText.stripFences("```\nx\n```"))
    }
}
