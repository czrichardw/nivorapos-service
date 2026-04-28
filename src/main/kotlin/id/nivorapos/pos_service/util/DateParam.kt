package id.nivorapos.pos_service.util

import java.time.LocalDate
import java.time.LocalDateTime

object DateParam {
    fun parseStart(value: String?): LocalDateTime? = parse(value, endOfDay = false)
    fun parseEnd(value: String?): LocalDateTime? = parse(value, endOfDay = true)

    private fun parse(value: String?, endOfDay: Boolean): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(value) }.getOrNull()
            ?: runCatching {
                val date = LocalDate.parse(value)
                if (endOfDay) date.atTime(23, 59, 59) else date.atStartOfDay()
            }.getOrElse {
                throw IllegalArgumentException("Invalid date format: '$value'. Expected ISO date (yyyy-MM-dd) or date-time (yyyy-MM-ddTHH:mm:ss).")
            }
    }
}
