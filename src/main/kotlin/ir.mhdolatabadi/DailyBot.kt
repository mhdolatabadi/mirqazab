package ir.mhdolatabadi

import ir.mhdolatabadi.handlers.CallbackQueryHandler
import ir.mhdolatabadi.handlers.SessionManager
import ir.mhdolatabadi.scheduler.DailyScheduler
import ir.mhdolatabadi.services.*
import ir.mhdolatabadi.utils.DateUtils
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import jakarta.persistence.EntityManagerFactory
import kotlinx.coroutines.runBlocking

class DailyBot(
    private val botToken: String,
    private val botUsername: String,
    options: DefaultBotOptions = DefaultBotOptions(),
    private val entityManagerFactory: EntityManagerFactory
) : TelegramLongPollingBot(options, botToken) {

    // ==================== سرویس‌ها ====================
    private val memberService = MemberService(entityManagerFactory)
    private val attendanceService = AttendanceService(entityManagerFactory)
    private val activityService = ActivityService(entityManagerFactory)
    private val settingService = SettingService(entityManagerFactory)

    private val reportService = ReportService(
        entityManagerFactory,
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

    init {
        scheduler.start()
    }

    override fun getBotUsername(): String = botUsername

    override fun onUpdateReceived(update: Update) {
        try {
            recordUserActivity(update)

            if (update.hasMessage() && update.message.hasText()) {
                handleTextMessage(update)
            } else if (update.hasCallbackQuery()) {
                callbackQueryHandler.handleCallbackQuery(update)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== ثبت کاربر جدید ====================
    private fun recordUserActivity(update: Update) {
        val (message, user) = when {
            update.hasMessage() -> update.message to update.message.from
            update.hasCallbackQuery() -> update.callbackQuery.message to update.callbackQuery.from
            else -> return
        }
        if (user.isBot) return
        val chatId = message.chatId.toLong()
        val fullName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
        val displayName = if (fullName.isNotEmpty()) fullName else user.userName ?: "کاربر ${user.id}"
        memberService.recordUserActivity(chatId, user.id, displayName)
    }

    // ==================== مدیریت پیام‌های متنی ====================
    private fun handleTextMessage(update: Update) {
        val chatId = update.message.chatId.toString()
        val text = update.message.text
        val replyToMsgId = update.message.messageId

        try {
            when {
                isMirGhazab(text) -> showMainMenu(chatId, replyToMsgId)

                text.equals("/toggle_daily_question", ignoreCase = true) -> {
                    toggleDailyQuestion(chatId)
                }
                text.equals("/question_now", ignoreCase = true) -> {
                    runBlocking {
                        try {
                            scheduler.sendDailyQuestionNow(chatId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sendMessage(chatId, "❌ خطا در ارسال سوال فوری: ${e.message}")
                        }
                    }
                }
                text.equals("/report_now", ignoreCase = true) -> {
                    runBlocking {
                        try {
                            scheduler.sendDailyReportNow(chatId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sendMessage(chatId, "❌ خطا در ارسال گزارش فوری: ${e.message}")
                        }
                    }
                }
                text.equals("/reset_activity_today", ignoreCase = true) -> {
                    try {
                        activityService.deleteAllTodayActivities(chatId)
                        sendMessage(chatId, "✅ تمام پاسخ‌های امروز با موفقیت پاک شدند.\nاکنون می‌توانید با /question_now سوال جدید بفرستید.")
                    } catch (e: Exception) {
                        sendMessage(chatId, "❌ خطا در پاک کردن پاسخ‌ها: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendMessage(chatId, "❌ خطا: ${e.message}")
        }
    }

    // ==================== فعال/غیرفعال کردن سوال روزانه ====================
    private fun toggleDailyQuestion(chatId: String) {
        val currentStatus = settingService.isDailyQuestionEnabled(chatId)
        val newStatus = !currentStatus
        settingService.setDailyQuestionEnabled(chatId, newStatus)

        val statusText = if (newStatus) "✅ **فعال**" else "❌ **غیرفعال**"
        val message = """
            🔄 **تغییر وضعیت سوال روزانه**
            
            وضعیت جدید: $statusText
            ${if (newStatus) "از این پس سوال روزانه در ساعت ۲۱ ارسال خواهد شد." else "سوال روزانه دیگر به صورت خودکار ارسال نمی‌شود."}
            
            برای ارسال فوری سوال از دستور /question_now استفاده کنید.
            برای پاک کردن پاسخ‌های امروز از /reset_activity_today استفاده کنید.
        """.trimIndent()

        sendMessage(chatId, message)
    }

    // ==================== تشخیص دستور میرغضب ====================
    private fun isMirGhazab(input: String): Boolean {
        val normalized = normalizePersian(input.trim())
        val validForms = setOf("میرغضب", "میر غضب", "ميرغضب", "میر غضب", "میرغضب", "میر غضب", "مير غضب")
        return validForms.contains(normalized)
    }

    private fun normalizePersian(text: String) = text
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("\\s+"), " ").trim()

    // ==================== منوی اصلی با نمایش وضعیت گزارش امروز ====================
    private fun showMainMenu(chatId: String, replyToMessageId: Int) {
        val hasAttendanceToday = attendanceService.hasAttendanceToday(chatId, DateUtils.today())

        val randomBtn = InlineKeyboardButton().apply {
            text = "🎲 میرغضب تصادفی"
            callbackData = "random_mirghazab_menu"
        }
        val reportBtn = InlineKeyboardButton().apply {
            text = if (hasAttendanceToday) {
                "✅ گزارش امروز ثبت شده (برای ریست کلیک کنید)"
            } else {
                "📋 ثبت گزارش جلسه روزانه امروز"
            }
            callbackData = "daily_report"
        }
        val weeklyBtn = InlineKeyboardButton().apply {
            text = "📊 مشاهده گزارش هفتگی (۷ روز گذشته)"
            callbackData = "weekly_report_now"
        }
        val overallBtn = InlineKeyboardButton().apply {
            text = "📊 مشاهده وضعیت توسعه/واکنش سریع"
            callbackData = "overall_activity_report"
        }
        val resetBtn = InlineKeyboardButton().apply {
            text = "🔄 حذف گزارش جلسه روزانه امروز"
            callbackData = "reset_today_attendance"
        }
        val keyboard = InlineKeyboardMarkup(listOf(
            listOf(randomBtn),
            listOf(reportBtn),
            listOf(weeklyBtn),
            listOf(overallBtn),
            listOf(resetBtn)
        ))
        val message = SendMessage(chatId, "لطفاً انتخاب کنید:")
        message.replyMarkup = keyboard
        message.replyToMessageId = replyToMessageId
        execute(message)
    }

    // ==================== ارسال پیام ساده ====================
    private fun sendMessage(chatId: String, text: String) {
        val msg = SendMessage(chatId, text)
        msg.parseMode = "Markdown"
        try {
            execute(msg)
        } catch (e: Exception) {
            println("ارسال پیام به $chatId ناموفق: ${e.message}")
        }
    }
}