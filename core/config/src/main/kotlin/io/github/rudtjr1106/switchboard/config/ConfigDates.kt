package io.github.rudtjr1106.switchboard.config

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** `YYYY-MM-DD` 하나만 쓴다. validate 워크플로의 jsonschema `format: date` 와 같은 기준이다 */
object ConfigDates {
    private val strictPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun parse(text: String): LocalDate? {
        if (!strictPattern.matches(text)) return null
        return try {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun format(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
}
