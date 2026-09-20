package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.AttendanceService
import ir.mhdolatabadi.services.ReportService
import ir.mhdolatabadi.utils.DateUtils
import ir.mhdolatabadi.utils.NumberUtils

/**
 * Owns the "لطفاً انتخاب کنید" root menu message: its text/reaction-legend,
 * the weekly/overall report actions, and the reset-attendance confirm/cancel
 * flow (which reuses the same message).
 */
class MainMenuHandler(
    private val bot: MatrixLongPollingBot,
    private val attendanceService: AttendanceService,
    private val reportService: ReportService,
    private val onOpenCandidateSelection: (chatId: String, eventId: String) -> Unit,
    private val onStartSession: (chatId: String, eventId: String, senderId: String) -> Unit,
    private val onResetAttendance: (chatId: String) -> Unit
) {
    private val resetConfirmations = mutableMapOf<String, Pair<String, String>>() // chatId -> (initiatorUserId, eventId)

    fun buildMainMenuText(chatId: String): String {
        val hasAttendanceToday = attendanceService.hasAttendanceToday(chatId, DateUtils.today())
        val reportLine = if (hasAttendanceToday) {
            "$KEY_DAILY_REPORT گزارش امروز ثبت شده (برای ریست کلیک کنید)"
        } else {
            "$KEY_DAILY_REPORT ثبت گزارش جلسه روزانه امروز"
        }
        return buildString {
            appendLine("لطفاً انتخاب کنید:")
            appendLine()
            appendLine("$KEY_RANDOM_MIRGHAZAB میرغضب تصادفی")
            appendLine(reportLine)
            appendLine("$KEY_WEEKLY_REPORT مشاهده گزارش هفتگی (۷ روز گذشته)")
            appendLine("$KEY_OVERALL_REPORT مشاهده وضعیت توسعه/واکنش سریع/مرخصی")
            appendLine("$KEY_RESET_ATTENDANCE حذف گزارش جلسه روزانه امروز")
        }
    }

    fun mainMenuKeys(): List<String> =
        listOf(KEY_RANDOM_MIRGHAZAB, KEY_DAILY_REPORT, KEY_WEEKLY_REPORT, KEY_OVERALL_REPORT, KEY_RESET_ATTENDANCE)

    fun showMainMenuOnExistingMessage(chatId: String, eventId: String) {
        bot.editMessage(chatId, eventId, buildMainMenuText(chatId))
        bot.reactAll(chatId, eventId, mainMenuKeys())
    }

    fun handleReaction(chatId: String, senderId: String, eventId: String, key: String) {
        if (resetConfirmations.containsKey(chatId)) {
            handleResetConfirmReaction(chatId, senderId, key)
            return
        }

        when (key) {
            KEY_RANDOM_MIRGHAZAB -> onOpenCandidateSelection(chatId, eventId)

            KEY_DAILY_REPORT -> {
                if (attendanceService.hasAttendanceToday(chatId, DateUtils.today())) return
                bot.editMessage(chatId, eventId, "🔄 شروع فرایند گزارش جلسه...")
                onStartSession(chatId, eventId, senderId)
            }

            KEY_WEEKLY_REPORT -> {
                bot.editMessage(chatId, eventId, "📊 در حال تولید گزارش هفتگی... لطفاً چند لحظه صبر کنید.")
                val endDate = DateUtils.today()
                val startDate = endDate.minusWeeks(1)
                val report = reportService.generateWeeklyReport(chatId, startDate, endDate)
                val startPersian = DateUtils.toPersianDate(startDate)
                val endPersian = DateUtils.toPersianDate(endDate)
                val year1 = NumberUtils.toPersianNumber(startPersian.year)
                val month1 = NumberUtils.toPersianNumber(startPersian.month)
                val day1 = NumberUtils.toPersianNumber(startPersian.day)
                val year2 = NumberUtils.toPersianNumber(endPersian.year)
                val month2 = NumberUtils.toPersianNumber(endPersian.month)
                val day2 = NumberUtils.toPersianNumber(endPersian.day)
                val title = "📊 گزارش هفتگی حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"
                bot.editMessage(chatId, eventId, "$title\n\n$report")
            }

            KEY_OVERALL_REPORT -> {
                bot.editMessage(chatId, eventId, "📊 در حال تولید گزارش وضعیت توسعه/واکنش سریع/مرخصی... لطفاً چند لحظه صبر کنید.")
                bot.editMessage(chatId, eventId, reportService.generateOverallActivityReport(chatId))
            }

            KEY_RESET_ATTENDANCE -> {
                resetConfirmations[chatId] = senderId to eventId
                bot.editMessage(
                    chatId, eventId,
                    "⚠️ هشدار!\n\nآیا مطمئن هستید که می‌خواهید تمام رکوردهای حضور/غیاب امروز را ریست کنید؟\nاین عمل غیرقابل بازگشت است.\n\n$KEY_CONFIRM بله، ریست کن   $KEY_CANCEL انصراف"
                )
                bot.reactAll(chatId, eventId, listOf(KEY_CONFIRM, KEY_CANCEL))
            }
        }
    }

    private fun handleResetConfirmReaction(chatId: String, senderId: String, key: String) {
        val (initiatorId, eventId) = resetConfirmations[chatId] ?: return
        when (key) {
            KEY_CONFIRM -> {
                if (initiatorId != senderId) return
                onResetAttendance(chatId)
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, eventId)
            }
            KEY_CANCEL -> {
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, eventId)
            }
        }
    }
}
