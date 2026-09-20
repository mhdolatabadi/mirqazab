package ir.mhdolatabadi.scheduler

import ir.mhdolatabadi.handlers.ReactionRouter
import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.*
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import ir.mhdolatabadi.utils.logger
import kotlinx.coroutines.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class DailyScheduler(
    private val bot: MatrixLongPollingBot,
    private val reportService: ReportService,
    private val activityService: ActivityService,
    private val attendanceService: AttendanceService,
    private val memberService: MemberService,
    private val settingService: SettingService,
    private val reactionRouter: ReactionRouter
) {
    private val log = logger()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tehranZone = ZoneId.of("Asia/Tehran")

    fun start() {
        scope.launch {
            while (true) {
                val now = LocalDateTime.now(tehranZone)
                val targetTimes = listOf(
                    8 to 0,
                    9 to 0,
                    18 to 0,
                    21 to 0
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


    private fun sendDailyReport() {
        if (isHoliday()) return

        val chatIds = memberService.getAllChatIds()
        for (chatId in chatIds) {
            val report = reportService.generateDailyReport(chatId)
            try {
                bot.sendMessage(chatId, report)
            } catch (e: Exception) {
                log.warn("Failed sending daily report to {}: {}", chatId, e.message)
            }
        }
    }

    fun sendDailyReportNow(chatId: String) {
        val report = reportService.generateDailyReport(chatId)
        val fullMessage = "📊 گزارش روزانه (ارسال فوری)\n\n$report"
        bot.sendMessage(chatId, fullMessage)
        log.info("Sent on-demand daily report to {}", chatId)
    }

    private fun sendDailyReminder() {
        if (isHoliday()) return

        val chatIds = memberService.getAllChatIds()
        val today = DateUtils.today()

        for (chatId in chatIds) {
            if (!attendanceService.hasAttendanceToday(chatId, today)) {
                val message = "🔔 یادآوری روزانه\n\nگزارش جلسه امروز هنوز ثبت نشده است.\nلطفاً با ارسال كلمه «میرغضب» گزارش را ثبت کنید."
                try {
                    bot.sendMessage(chatId, message)
                } catch (e: Exception) {
                    log.warn("Failed sending reminder to {}: {}", chatId, e.message)
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
            val report = reportService.generateWeeklyReport(chatId, startDate, endDate)
            val fullMessage = "$title\n\n$report"
            try {
                bot.sendMessage(chatId, fullMessage)
            } catch (e: Exception) {
                log.warn("Failed sending weekly report to {}: {}", chatId, e.message)
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
            val report = reportService.generateWeeklyReport(chatId, startDate, endDate)
            val fullMessage = "$title\n\n$report"
            try {
                bot.sendMessage(chatId, fullMessage)
            } catch (e: Exception) {
                log.warn("Failed sending monthly report to {}: {}", chatId, e.message)
            }
        }
    }

    private fun sendDailyQuestion() {
        if (isHoliday()) return
        val chatIds = memberService.getAllChatIds()
        val today = DateUtils.today()

        for (chatId in chatIds) {
            if (!settingService.isDailyQuestionEnabled(chatId)) continue
            if (activityService.hasActivityOn(chatId, today)) continue

            reactionRouter.sendActivityMessage(chatId, today)
        }
    }

    fun sendDailyQuestionNow(chatId: String) {
        reactionRouter.sendActivityMessage(chatId, DateUtils.today())
    }
}
