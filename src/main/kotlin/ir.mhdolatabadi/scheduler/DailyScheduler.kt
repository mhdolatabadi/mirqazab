package ir.mhdolatabadi.scheduler

import ir.mhdolatabadi.handlers.CallbackQueryHandler
import ir.mhdolatabadi.services.*
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import kotlinx.coroutines.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class DailyScheduler(
    private val bot: TelegramLongPollingBot,
    private val reportService: ReportService,
    private val activityService: ActivityService,
    private val attendanceService: AttendanceService,
    private val memberService: MemberService,
    private val settingService: SettingService,
    private val callbackQueryHandler: CallbackQueryHandler
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tehranZone = ZoneId.of("Asia/Tehran")

    fun start() {
        scope.launch {
            while (true) {
                val now = LocalDateTime.now(tehranZone)
                val targetTimes = listOf(
                    8 to 0,   // 08:00 - گزارش ترکیبی روزانه
                    9 to 0,   // 09:00 - گزارش هفتگی/ماهانه
                    18 to 0,  // 18:00 - یادآوری جلسه روزانه
                    21 to 0   // 21:00 - سوال روزانه
                )

                val nextRuns = targetTimes.map { (hour, minute) ->
                    now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                }.filter { now.isBefore(it) }

                val nextRun = if (nextRuns.isNotEmpty()) {
                    nextRuns.minByOrNull { it }!!
                } else {
                    now.withHour(8).withMinute(0).withSecond(0).withNano(0).plusDays(1)
                }

                val delay = Duration.between(now, nextRun).toMillis()
                delay(delay)

                if (isHoliday()) {
                    continue
                }

                when (nextRun.hour) {
                    8 -> sendDailyReport()
                    9 -> checkAndSendReports()
                    18 -> sendDailyReminder()
                    21 -> sendDailyQuestion()
                }
            }
        }
    }

    private fun isHoliday(): Boolean {
        val today = DateUtils.today()
        return today.dayOfWeek.value == 4 || today.dayOfWeek.value == 5
    }

    private fun sendDailyQuestion() {
        if (isHoliday()) return

        val chatIds = memberService.getAllChatIds()
        val today = DateUtils.today()

        for (chatId in chatIds) {
            if (!settingService.isDailyQuestionEnabled(chatId.toString())) {
                continue
            }

            if (activityService.hasActivityToday(chatId.toString(), today)) {
                continue
            }

            val (text, keyboard) = callbackQueryHandler.buildActivityMessage(chatId.toString())
            val msg = SendMessage(chatId.toString(), text)
            msg.replyMarkup = keyboard
            msg.parseMode = "Markdown"
            try {
                bot.execute(msg)
            } catch (e: Exception) {
                println("ارسال سوال به $chatId ناموفق: ${e.message}")
            }
        }
    }

    fun sendDailyQuestionNow(chatId: String) {
        val (text, keyboard) = callbackQueryHandler.buildActivityMessage(chatId)
        val msg = SendMessage(chatId, text)
        msg.replyMarkup = keyboard
        msg.parseMode = "Markdown"
        bot.execute(msg)
        println("✅ سوال فوری به $chatId ارسال شد.")
    }

    private fun sendDailyReport() {
        if (isHoliday()) return

        val chatIds = memberService.getAllChatIds()
        for (chatId in chatIds) {
            val report = reportService.generateDailyReport(chatId.toString())
            val msg = SendMessage(chatId.toString(), report)
            msg.parseMode = "Markdown"
            try {
                bot.execute(msg)
            } catch (e: Exception) {
                println("ارسال گزارش به $chatId ناموفق: ${e.message}")
            }
        }
    }

    fun sendDailyReportNow(chatId: String) {
        val report = reportService.generateDailyReport(chatId)
        val fullMessage = "📊 *گزارش روزانه (ارسال فوری)*\n\n$report"
        val msg = SendMessage(chatId, fullMessage)
        msg.parseMode = "Markdown"
        bot.execute(msg)
        println("✅ گزارش فوری به $chatId ارسال شد.")
    }

    private fun sendDailyReminder() {
        if (isHoliday()) return

        val chatIds = memberService.getAllChatIds()
        val today = DateUtils.today()

        for (chatId in chatIds) {
            if (!attendanceService.hasAttendanceToday(chatId.toString(), today)) {
                val message = "🔔 *یادآوری روزانه*\n\nگزارش جلسه امروز هنوز ثبت نشده است.\nلطفاً با ارسال كلمه «میرغضب» گزارش را ثبت کنید."
                val msg = SendMessage(chatId.toString(), message)
                msg.parseMode = "Markdown"
                try {
                    bot.execute(msg)
                } catch (e: Exception) {
                    println("ارسال یادآوری به $chatId ناموفق: ${e.message}")
                }
            }
        }
    }

    private fun checkAndSendReports() {
        if (isHoliday()) return

        val today = DateUtils.today()
        val persianDate = DateUtils.toPersianDate(today)

        if (today.dayOfWeek.value == 5) {
            sendWeeklyReportToAllGroups()
        }

        if (persianDate.day == 1) {
            sendMonthlyReportToAllGroups()
        }
    }

    private fun sendWeeklyReportToAllGroups() {
        val chatIds = attendanceService.getDistinctChatIds()
        val endDate = DateUtils.today()
        val startDate = endDate.minusWeeks(1)
        val startPersian = DateUtils.toPersianDate(startDate)
        val endPersian = DateUtils.toPersianDate(endDate)
        val year1 = NumberUtils.toPersianNumber(startPersian.year)
        val month1 = NumberUtils.toPersianNumber(startPersian.month)
        val day1 = NumberUtils.toPersianNumber(startPersian.day)
        val year2 = NumberUtils.toPersianNumber(endPersian.year)
        val month2 = NumberUtils.toPersianNumber(endPersian.month)
        val day2 = NumberUtils.toPersianNumber(endPersian.day)
        val title = "📊 گزارش هفتگی حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"

        for (chatId in chatIds) {
            val report = reportService.generateWeeklyReport(chatId.toString(), startDate, endDate)
            val fullMessage = "$title\n\n$report"
            val msg = SendMessage(chatId.toString(), fullMessage)
            msg.parseMode = "Markdown"
            try {
                bot.execute(msg)
            } catch (e: Exception) {
                println("ارسال گزارش هفتگی به $chatId ناموفق: ${e.message}")
            }
        }
    }

    private fun sendMonthlyReportToAllGroups() {
        val chatIds = attendanceService.getDistinctChatIds()
        val endDate = DateUtils.today()
        val startDate = endDate.minusMonths(1)
        val startPersian = DateUtils.toPersianDate(startDate)
        val endPersian = DateUtils.toPersianDate(endDate)
        val year1 = NumberUtils.toPersianNumber(startPersian.year)
        val month1 = NumberUtils.toPersianNumber(startPersian.month)
        val day1 = NumberUtils.toPersianNumber(startPersian.day)
        val year2 = NumberUtils.toPersianNumber(endPersian.year)
        val month2 = NumberUtils.toPersianNumber(endPersian.month)
        val day2 = NumberUtils.toPersianNumber(endPersian.day)
        val title = "📊 گزارش ماهانه حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"

        for (chatId in chatIds) {
            val report = reportService.generateWeeklyReport(chatId.toString(), startDate, endDate)
            val fullMessage = "$title\n\n$report"
            val msg = SendMessage(chatId.toString(), fullMessage)
            msg.parseMode = "Markdown"
            try {
                bot.execute(msg)
            } catch (e: Exception) {
                println("ارسال گزارش ماهانه به $chatId ناموفق: ${e.message}")
            }
        }
    }
}