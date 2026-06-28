package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.models.UserInfo
import ir.mhdolatabadi.models.StatusSession
import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.services.MemberService
import ir.mhdolatabadi.services.AttendanceService
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

class SessionManager(
    private val bot: TelegramLongPollingBot,
    private val memberService: MemberService,
    private val attendanceService: AttendanceService
) {
    private val activeSessions = mutableMapOf<String, StatusSession>()

    fun getActiveSession(chatId: String): StatusSession? = activeSessions[chatId]

    fun startSession(chatId: String, messageId: Int, initiatorUserId: Long): StatusSession? {
        // بررسی اینکه آیا امروز گزارش ثبت شده است
        if (attendanceService.hasAttendanceToday(chatId, DateUtils.today())) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ گزارش جلسه امروز قبلاً ثبت شده است.\nبرای ثبت مجدد ابتدا از دکمه «حذف گزارش جلسه روزانه امروز» استفاده کنید."
                this.replyMarkup = null
            }
            bot.execute(edit)
            return null
        }

        // بررسی اینکه آیا جلسه فعالی در حال اجراست
        if (activeSessions.containsKey(chatId)) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ در حال حاضر یک جلسه گزارش در این گروه در حال اجراست.\nلطفاً ابتدا آن را تکمیل کنید."
                this.replyMarkup = null
            }
            bot.execute(edit)
            return null
        }

        val members = memberService.getGroupMembers(chatId)
        if (members.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "هنوز هیچ عضوی شناسایی نشده است. لطفاً اعضا یک پیام بفرستند."
                this.replyMarkup = null
            }
            bot.execute(edit)
            return null
        }

        val sessionUsers = members.mapValues { UserInfo(it.key, it.value) }.toMutableMap()
        val keyboard = buildStatusKeyboard(sessionUsers, "present")
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "مرحله ۱: چه کسانی در جلسه **حاضر** هستند؟\n(روی اسامی کلیک کنید)"
            this.replyMarkup = keyboard
            this.parseMode = "Markdown"
        }
        bot.execute(edit)

        val session = StatusSession(messageId, sessionUsers, "present", initiatorUserId)
        activeSessions[chatId] = session
        return session
    }

    fun toggleUserStatus(chatId: String, userId: Long) {
        val session = activeSessions[chatId] ?: return
        val user = session.users[userId] ?: return

        if (session.state == "present") {
            user.status = if (user.status == AttendanceStatus.PRESENT) AttendanceStatus.UNKNOWN else AttendanceStatus.PRESENT
        } else {
            user.status = if (user.status == AttendanceStatus.ABSENT_EXCUSED) AttendanceStatus.UNKNOWN else AttendanceStatus.ABSENT_EXCUSED
        }
        updateSessionMessage(chatId, if (session.state == "present") "مرحله ۱: چه کسانی در جلسه **حاضر** هستند؟" else "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
    }

    fun advanceSession(chatId: String) {
        val session = activeSessions[chatId] ?: return
        session.state = "absent_excused"
        updateSessionMessage(chatId, "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
    }

    fun finishSession(chatId: String): StatusSession? {
        val session = activeSessions.remove(chatId) ?: return null

        session.users.values.forEach {
            if (it.status == AttendanceStatus.UNKNOWN) {
                it.status = AttendanceStatus.ABSENT_UNEXCUSED
            }
        }

        attendanceService.saveAttendanceRecords(chatId, session.users, DateUtils.today())

        val presents = session.users.values.filter { it.status == AttendanceStatus.PRESENT }
        val absentExcused = session.users.values.filter { it.status == AttendanceStatus.ABSENT_EXCUSED }
        val absentUnexcused = session.users.values.filter { it.status == AttendanceStatus.ABSENT_UNEXCUSED }
        val total = session.users.values.size
        val presentCount = presents.size
        val excusedCount = absentExcused.size

        val report = buildString {
            appendLine("#اعلام_وضعیت: 📆 جلسه روزانه")
            appendLine("\n🫡 حاضرین:")
            if (presents.isEmpty()) appendLine("هیچ‌کس") else presents.forEach { appendLine("- ${it.name}") }
            appendLine("\n😱 غایبین:")
            if (absentExcused.isNotEmpty()) absentExcused.forEach { appendLine("- ${it.name} 🔔") }
            if (absentUnexcused.isNotEmpty()) absentUnexcused.forEach { appendLine("- ${it.name} 🔕") }
            if (absentExcused.isEmpty() && absentUnexcused.isEmpty()) appendLine("هیچ‌کس")
            val presentPersian = NumberUtils.toPersianNumber(presentCount + excusedCount)
            val totalPersian = NumberUtils.toPersianNumber(total)
            appendLine("\nوضعیت: $presentPersian/$totalPersian")
        }

        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = report
            this.replyMarkup = null
            this.parseMode = "Markdown"
        }
        bot.execute(edit)

        return session
    }

    fun resetSession(chatId: String) {
        activeSessions.remove(chatId)
    }

    private fun updateSessionMessage(chatId: String, text: String) {
        val session = activeSessions[chatId] ?: return
        val keyboard = buildStatusKeyboard(session.users, session.state)
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = text
            this.replyMarkup = keyboard
            this.parseMode = "Markdown"
        }
        bot.execute(edit)
    }

    private fun buildStatusKeyboard(users: Map<Long, UserInfo>, state: String): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        var currentRow = mutableListOf<InlineKeyboardButton>()
        for ((userId, userInfo) in users) {
            if (state == "absent_excused" && userInfo.status == AttendanceStatus.PRESENT) continue
            val buttonText = when (userInfo.status) {
                AttendanceStatus.PRESENT -> "✅ ${userInfo.name}"
                AttendanceStatus.ABSENT_EXCUSED -> "\uD83D\uDD14 ${userInfo.name}"
                else -> userInfo.name
            }
            val button = InlineKeyboardButton().apply {
                this.text = buttonText
                callbackData = "user_$userId"
            }
            currentRow.add(button)
            if (currentRow.size == 2) {
                rows.add(currentRow)
                currentRow = mutableListOf()
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        val doneText = if (state == "present") "➡️ تایید حاضران و مرحله بعد" else "✔️ تایید نهایی و ارسال گزارش"
        val doneButton = InlineKeyboardButton().apply {
            text = doneText
            callbackData = if (state == "present") "done_present" else "done_final"
        }
        rows.add(listOf(doneButton))
        return InlineKeyboardMarkup(rows)
    }
}