package ir.mhdolatabadi

import ir.mhdolatabadi.handlers.CallbackQueryHandler
import ir.mhdolatabadi.handlers.SessionManager
import ir.mhdolatabadi.matrix.MatrixClient
import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.scheduler.DailyScheduler
import ir.mhdolatabadi.services.*
import jakarta.persistence.EntityManagerFactory

class DailyBot(
    client: MatrixClient,
    entityManagerFactory: EntityManagerFactory
) : MatrixLongPollingBot(client) {

    private val memberService = MemberService(entityManagerFactory)
    private val attendanceService = AttendanceService(entityManagerFactory)
    private val activityService = ActivityService(entityManagerFactory)
    private val settingService = SettingService(entityManagerFactory)

    private val reportService = ReportService(
        attendanceService,
        activityService,
        memberService
    )

    private val sessionManager = SessionManager(this, memberService, attendanceService)

    private val callbackQueryHandler = CallbackQueryHandler(
        this,
        sessionManager,
        activityService,
        reportService,
        memberService,
        attendanceService
    )

    private val scheduler = DailyScheduler(
        this,
        reportService,
        activityService,
        attendanceService,
        memberService,
        settingService,
        callbackQueryHandler
    )

    fun startBot() {
        start()
        scheduler.start()
    }

    override fun onRoomMessage(roomId: String, senderId: String, body: String, eventId: String) {
        try {
            recordUserActivity(roomId, senderId)
            handleTextMessage(roomId, senderId, body)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onReaction(roomId: String, senderId: String, targetEventId: String, key: String) {
        try {
            recordUserActivity(roomId, senderId)
            callbackQueryHandler.handleReaction(roomId, senderId, targetEventId, key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== ثبت کاربر جدید ====================
    private fun recordUserActivity(chatId: String, senderId: String) {
        val displayName = senderId.removePrefix("@").substringBefore(":")
        memberService.recordUserActivity(chatId, senderId, displayName)
    }

    private fun handleTextMessage(chatId: String, senderId: String, text: String) {
        try {
            when {
                isMirGhazab(text) -> showMainMenu(chatId)

                text.equals("/toggle_daily_question", ignoreCase = true) -> {
                    toggleDailyQuestion(chatId)
                }
                text.equals("/question_now", ignoreCase = true) -> {
                    try {
                        scheduler.sendDailyQuestionNow(chatId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        notify(chatId, "❌ خطا در ارسال سوال فوری: ${e.message}")
                    }
                }
                text.equals("/report_now", ignoreCase = true) -> {
                    try {
                        scheduler.sendDailyReportNow(chatId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        notify(chatId, "❌ خطا در ارسال گزارش فوری: ${e.message}")
                    }
                }
                text.equals("/reset_activity_today", ignoreCase = true) -> {
                    try {
                        activityService.deleteAllTodayActivities(chatId)
                        notify(chatId, "✅ تمام پاسخ‌های امروز با موفقیت پاک شدند.\nاکنون می‌توانید با /question_now سوال جدید بفرستید.")
                    } catch (e: Exception) {
                        notify(chatId, "❌ خطا در پاک کردن پاسخ‌ها: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            notify(chatId, "❌ خطا: ${e.message}")
        }
    }

    private fun toggleDailyQuestion(chatId: String) {
        val currentStatus = settingService.isDailyQuestionEnabled(chatId)
        val newStatus = !currentStatus
        settingService.setDailyQuestionEnabled(chatId, newStatus)

        val statusText = if (newStatus) "✅ فعال" else "❌ غیرفعال"
        val message = """
            🔄 تغییر وضعیت سوال روزانه

            وضعیت جدید: $statusText
            ${if (newStatus) "از این پس سوال روزانه در ساعت ۲۱ ارسال خواهد شد." else "سوال روزانه دیگر به صورت خودکار ارسال نمی‌شود."}

            برای ارسال فوری سوال از دستور /question_now استفاده کنید.
            برای پاک کردن پاسخ‌های امروز از /reset_activity_today استفاده کنید.
        """.trimIndent()

        notify(chatId, message)
    }

    private fun isMirGhazab(input: String): Boolean {
        val normalized = normalizePersian(input.trim())
        val validForms = setOf("میرغضب", "میر غضب", "ميرغضب", "میر غضب", "میرغضب", "میر غضب", "مير غضب")
        return validForms.contains(normalized)
    }

    private fun normalizePersian(text: String) = text
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("\\s+"), " ").trim()

    private fun showMainMenu(chatId: String) {
        val text = callbackQueryHandler.buildMainMenuText(chatId)
        val eventId = sendMessage(chatId, text)
        reactAll(chatId, eventId, callbackQueryHandler.mainMenuKeys())
    }

    private fun notify(chatId: String, text: String) {
        try {
            sendMessage(chatId, text)
        } catch (e: Exception) {
            println("ارسال پیام به $chatId ناموفق: ${e.message}")
        }
    }
}
