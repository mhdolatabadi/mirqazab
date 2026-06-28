package ir.mhdolatabadi.utils

import ir.huri.jcal.JalaliCalendar
import java.time.LocalDate
import java.time.ZoneId

object DateUtils {
    private val tehranZone = ZoneId.of("Asia/Tehran")

    data class PersianDate(val year: Int, val month: Int, val day: Int)

    fun toPersianDate(gregorian: LocalDate): PersianDate {
        val gregorianCalendar = java.util.GregorianCalendar.from(gregorian.atStartOfDay(tehranZone))
        val jalali = JalaliCalendar(gregorianCalendar)
        return PersianDate(jalali.getYear(), jalali.getMonth(), jalali.getDay())
    }

    fun today(): LocalDate = LocalDate.now(tehranZone)
    fun yesterday(): LocalDate = today().minusDays(1)
}