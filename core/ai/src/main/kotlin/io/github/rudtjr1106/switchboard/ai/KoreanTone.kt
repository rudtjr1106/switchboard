package io.github.rudtjr1106.switchboard.ai

/**
 * 합쇼체(~합니다)를 해요체(~해요)로 바꾸고 인사말을 걷어낸다 (하네스의 결정적 후처리)
 *
 * 작은 모델은 "~해요로 써" 를 여러 번 말해도 "~합니다" 로 돌아가는 일이 잦다. 끝말은 규칙이 뚜렷해서
 * 모델에게 다시 묻는 것보다 코드로 바꾸는 편이 빠르고 정확하다. 뜻을 바꾸지 않는 끝말만 건드린다.
 */
object KoreanTone {

    fun toHaeyo(text: String): String {
        var out = text
        for ((formal, polite) in PHRASES) out = out.replace(formal, polite)
        out = HONORIFIC.replace(out) { "세요" }
        out = COPULA.replace(out) { m -> copula(m.groupValues[1]) }
        // '습' 도 받침이 ㅂ 이라 ㅂ니다 규칙보다 먼저 푼다
        out = SEUPNIDA.replace(out) { m -> seupnida(m.groupValues[1][0]) }
        out = B_NIDA.replace(out) { m -> bNida(m.groupValues[1][0]) ?: m.value }
        return out
    }

    /** 제목이 인사말뿐인지. 모델이 제목 자리에 인사를 넣는 일이 있다 */
    fun isGreeting(text: String): Boolean = LEADING_GREETING.replace(text, "").isBlank()

    /** 본문 앞뒤의 인사말. 안내 문구에는 필요 없고 글자 수만 먹는다 */
    fun stripGreetings(text: String): String = text
        .replace(LEADING_GREETING, "")
        .replace(TRAILING_THANKS, "")
        .trim()

    // ---- 규칙 ----

    /** 긴 것부터. 활용 규칙으로 풀기 어려운 굳은 표현 */
    private val PHRASES = listOf(
        "주시면 감사하겠습니다" to "주세요",
        "감사하겠습니다" to "고마워요",
        "드리겠습니다" to "드릴게요",
        "하겠습니다" to "할게요",
        "하시기 바랍니다" to "해 주세요",
        "하시길 바랍니다" to "해 주세요",
        "부탁드립니다" to "부탁드려요",
        "바랍니다" to "부탁해요",
        "죄송합니다" to "죄송해요",
        "감사합니다" to "고마워요",
        "드립니다" to "드려요",
        "주십시오" to "주세요",
        "하십시오" to "하세요",
        "십시오" to "세요",
        "아닙니다" to "아니에요",
        "됩니다" to "돼요",
        "합니다" to "해요",
    )

    /** 하십니다, 오십니다 → 하세요, 오세요 */
    private val HONORIFIC = Regex("십니다")

    /** 명사 + 입니다 → 이에요 / 예요 */
    private val COPULA = Regex("([가-힣])입니다")

    /** 받침 ㅂ + 니다 (갑니다, 기다립니다, 봅니다) */
    private val B_NIDA = Regex("([가-힣])니다")

    /** 받침 있는 줄기 + 습니다 (있습니다, 많습니다, 했습니다) */
    private val SEUPNIDA = Regex("([가-힣])습니다")

    private val LEADING_GREETING = Regex("^\\s*안녕하세요[,!.~ ]*((여러분|회원님|사용자님|챌린저)(들)?[,!.~ ]*)?")
    private val TRAILING_THANKS = Regex("\\s*(고마워요|감사해요|감사드려요)[!.~]*\\s*$")

    private fun copula(prev: String): String {
        val c = prev[0]
        return if (jong(c) != 0) "${prev}이에요" else "${prev}예요"
    }

    /** 받침 ㅂ 을 떼고 모음에 맞춰 '-아요/-어요' 를 줄여 붙인다. 받침이 ㅂ 이 아니면 null (그대로 둔다) */
    private fun bNida(c: Char): String? {
        if (!isSyllable(c) || jong(c) != JONG_B) return null
        val cho = cho(c)
        return when (val v = jung(c)) {
            V_A, V_EO, V_AE, V_E, V_YEO, V_YA -> compose(cho, v, 0) + "요"
            V_O -> compose(cho, V_WA, 0) + "요"
            V_U -> compose(cho, V_WEO, 0) + "요"
            V_I -> compose(cho, V_YEO, 0) + "요"
            V_EU -> compose(cho, V_EO, 0) + "요"
            V_OE -> compose(cho, V_WAE, 0) + "요"
            else -> compose(cho, v, 0) + "어요"
        }
    }

    /** 과거(ㅆ 받침)는 '-어요', 양성 모음(ㅏ, ㅑ, ㅗ)은 '-아요', 나머지는 '-어요' */
    private fun seupnida(c: Char): String {
        if (!isSyllable(c)) return "${c}습니다"
        val ending = when {
            jong(c) == JONG_SS -> "어요"
            jung(c) in setOf(V_A, V_YA, V_O) -> "아요"
            else -> "어요"
        }
        return "$c$ending"
    }

    // ---- 한글 자모 ----

    private const val BASE = 0xAC00
    private const val JONG_B = 17
    private const val JONG_SS = 20
    private const val V_A = 0
    private const val V_AE = 1
    private const val V_YA = 2
    private const val V_EO = 4
    private const val V_E = 5
    private const val V_YEO = 6
    private const val V_O = 8
    private const val V_WA = 9
    private const val V_WAE = 10
    private const val V_OE = 11
    private const val V_U = 13
    private const val V_WEO = 14
    private const val V_EU = 18
    private const val V_I = 20

    private fun isSyllable(c: Char) = c in '가'..'힣'
    private fun cho(c: Char) = (c.code - BASE) / 588
    private fun jung(c: Char) = ((c.code - BASE) % 588) / 28
    private fun jong(c: Char) = (c.code - BASE) % 28
    private fun compose(cho: Int, jung: Int, jong: Int) = (BASE + cho * 588 + jung * 28 + jong).toChar()
}
