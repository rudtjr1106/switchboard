package io.github.rudtjr1106.switchboard.config

/** `2.3.1` 같은 점으로 나뉜 숫자 버전. 앞에 붙은 `v` 는 무시한다 */
data class VersionNumber(val parts: List<Int>) : Comparable<VersionNumber> {

    override fun compareTo(other: VersionNumber): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val diff = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        fun parse(text: String): VersionNumber? {
            val trimmed = text.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split('.').map { it.toIntOrNull() ?: return null }
            return VersionNumber(parts)
        }
    }
}
