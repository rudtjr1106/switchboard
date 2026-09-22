package io.github.rudtjr1106.switchboard.scanner.parse

/**
 * Gradle 스크립트·Kotlin 소스를 정규식으로 읽기 전에 주석과 문자열을 가려 주는 도구
 *
 * 결과는 항상 원문과 길이가 같다 (가린 자리는 공백, 줄바꿈은 그대로). 그래서 가린 문장에서 찾은 위치를 원문에 그대로 쓸 수 있다.
 */
internal object SourceText {

    /** 주석을 공백으로 바꾼다. 문자열 안의 `//` (URL 등) 는 건드리지 않는다 */
    fun stripComments(text: String): String = mask(text, comments = true, strings = false)

    /** 문자열 리터럴 내용을 공백으로 바꾼다 (따옴표는 남긴다). 중괄호 짝을 맞출 때 문자열 안의 괄호에 속지 않으려고 쓴다 */
    fun maskStrings(text: String): String = mask(text, comments = false, strings = true)

    /** [open] 위치의 여는 괄호와 짝이 되는 닫는 괄호 위치. 없으면 -1 */
    fun matching(masked: String, open: Int, openCh: Char = '{', closeCh: Char = '}'): Int {
        var depth = 0
        for (i in open until masked.length) {
            when (masked[i]) {
                openCh -> depth++
                closeCh -> if (--depth == 0) return i
            }
        }
        return -1
    }

    /** 각 위치의 중괄호 깊이 (그 위치의 문자를 처리하기 전 값) */
    fun braceDepths(masked: String): IntArray {
        val depths = IntArray(masked.length)
        var depth = 0
        for (i in masked.indices) {
            depths[i] = depth
            when (masked[i]) {
                '{' -> depth++
                '}' -> depth = maxOf(0, depth - 1)
            }
        }
        return depths
    }

    /** `keyword {` 블록들의 안쪽 범위 (중괄호 제외) */
    fun blocks(masked: String, keyword: String): List<IntRange> {
        val regex = Regex("""(?<![\w.])$keyword\s*\{""")
        return regex.findAll(masked).mapNotNull { match ->
            val open = match.range.last
            val close = matching(masked, open)
            if (close < 0) null else (open + 1) until close
        }.toList()
    }

    /** [ranges] 를 공백으로 지운 사본. 의존성 블록을 뺀 나머지에서 플러그인만 찾을 때 쓴다 */
    fun blank(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val chars = text.toCharArray()
        for (range in ranges) for (i in range) if (chars[i] != '\n' && chars[i] != '\r') chars[i] = ' '
        return String(chars)
    }

    private fun blankChar(c: Char): Char = if (c == '\n' || c == '\r') c else ' '

    private fun mask(text: String, comments: Boolean, strings: Boolean): String {
        val out = StringBuilder(text.length)
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                    for (j in i until end) out.append(if (comments) ' ' else text[j])
                    i = end
                }

                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    // Kotlin 은 블록 주석이 중첩된다
                    var depth = 1
                    var j = i + 2
                    while (j < n && depth > 0) {
                        when {
                            text.startsWith("/*", j) -> { depth++; j += 2 }
                            text.startsWith("*/", j) -> { depth--; j += 2 }
                            else -> j++
                        }
                    }
                    for (k in i until j) out.append(if (comments) blankChar(text[k]) else text[k])
                    i = j
                }

                c == '"' && text.startsWith("\"\"\"", i) -> {
                    val closeAt = text.indexOf("\"\"\"", i + 3)
                    val contentEnd = if (closeAt < 0) n else closeAt
                    out.append("\"\"\"")
                    for (k in i + 3 until contentEnd) out.append(if (strings) blankChar(text[k]) else text[k])
                    if (closeAt >= 0) out.append("\"\"\"")
                    i = if (closeAt < 0) n else closeAt + 3
                }

                c == '"' || c == '\'' -> {
                    out.append(c)
                    var j = i + 1
                    while (j < n && text[j] != c && text[j] != '\n') {
                        when {
                            text[j] == '\\' && j + 1 < n -> {
                                out.append(if (strings) "  " else text.substring(j, j + 2))
                                j += 2
                            }
                            // "${ ... }" 템플릿 안의 따옴표·중괄호는 문자열의 일부다
                            c == '"' && text[j] == '$' && j + 1 < n && text[j + 1] == '{' -> {
                                val end = templateEnd(text, j + 1)
                                for (k in j until end) out.append(if (strings) blankChar(text[k]) else text[k])
                                j = end
                            }
                            else -> {
                                out.append(if (strings) ' ' else text[j])
                                j++
                            }
                        }
                    }
                    if (j < n) {
                        out.append(text[j])
                        j++
                    }
                    i = j
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** `${` 의 `{` 위치를 받아 짝이 되는 `}` 다음 위치를 돌려준다. 안쪽 문자열은 통째로 건너뛴다 */
    private fun templateEnd(text: String, open: Int): Int {
        var depth = 0
        var j = open
        while (j < text.length) {
            when (text[j]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return j + 1
                '"' -> {
                    j++
                    while (j < text.length && text[j] != '"') j += if (text[j] == '\\') 2 else 1
                }
            }
            j++
        }
        return text.length
    }

    /** XML 주석을 같은 길이의 공백으로 바꾼다. 위치(인덱스)가 원문과 같아야 해서 지우지 않는다 */
    fun stripXmlComments(text: String): String = xmlComment.replace(text) { " ".repeat(it.value.length) }

    private val xmlComment = Regex("<!--[\\s\\S]*?-->")
}
