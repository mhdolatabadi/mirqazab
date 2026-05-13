import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

// وضعیت‌های ممکن برای هر کاربر در جلسه
enum class AttendanceStatus { UNKNOWN, PRESENT, ABSENT_EXCUSED, ABSENT_UNEXCUSED }

// ساختار داده برای نگهداری وضعیت هر فرد در جلسه جاری
data class UserInfo(val id: Long, val name: String, var status: AttendanceStatus = AttendanceStatus.UNKNOWN)

// ساختار داده برای نگهداری وضعیت کلی یک جلسه گزارش‌گیری در یک گروه
data class StatusSession(
    val messageId: Int,
    val users: MutableMap<Long, UserInfo>,
    var state: String // مقادیر: "present", "absent_excused"
)

class DailyBot(
    private val botToken: String,
    private val botUsername: String,
    options: DefaultBotOptions = DefaultBotOptions()
) : TelegramLongPollingBot(options, botToken) {

    // ذخیره اعضای گروه: Map<ChatId, Map<UserId, FullName>>
    private val groupMembers = mutableMapOf<String, MutableMap<Long, String>>()

    // ذخیره جلسات فعال: Map<ChatId, StatusSession>
    private val activeSessions = mutableMapOf<String, StatusSession>()

    override fun getBotUsername(): String = botUsername

    override fun onUpdateReceived(update: Update) {
        try {
            // ۱. ثبت اطلاعات کاربر برای داینامیک شدن لیست اعضا
            recordUserActivity(update)

            if (update.hasMessage() && update.message.hasText()) {
                handleTextMessage(update)
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recordUserActivity(update: Update) {
        val (message, user) = when {
            update.hasMessage() -> update.message to update.message.from
            update.hasCallbackQuery() -> update.callbackQuery.message to update.callbackQuery.from
            else -> return
        }

        if (user.isBot) return // ربات‌ها را در لیست گزارش نمی‌آوریم

        val chatId = message.chatId.toString()
        val fullName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
        val displayName = fullName.ifEmpty { user.userName ?: "کاربر ${user.id}" }

        groupMembers.getOrPut(chatId) { mutableMapOf() }[user.id] = displayName
    }

    private fun handleTextMessage(update: Update) {
        val chatId = update.message.chatId.toString()
        val text = update.message.text

        if (text.startsWith("/status")) {
            startStatusSession(chatId)
        }
    }

    private fun startStatusSession(chatId: String) {
        val membersInThisGroup = groupMembers[chatId] ?: mutableMapOf()

        if (membersInThisGroup.isEmpty()) {
            sendMessage(chatId, "هنوز هیچ عضوی توسط ربات شناسایی نشده است. لطفاً اعضا یک پیام بفرستند تا لیست تکمیل شود.")
            return
        }

        // ایجاد یک نمونه جدید از کاربران برای این جلسه
        val sessionUsers = membersInThisGroup.mapValues { UserInfo(it.key, it.value) }.toMutableMap()

        val keyboard = buildKeyboard(sessionUsers, "present")
        val text = "مرحله ۱: چه کسانی در جلسه **حاضر** هستند؟\n(روی اسامی کلیک کنید)"

        val sentMessage = sendMessageWithKeyboard(chatId, text, keyboard)
        if (sentMessage != null) {
            activeSessions[chatId] = StatusSession(sentMessage.messageId, sessionUsers, "present")
        }
    }

    private fun handleCallbackQuery(update: Update) {
        val callbackQuery = update.callbackQuery
        val chatId = callbackQuery.message.chatId.toString()
        val data = callbackQuery.data
        val messageId = callbackQuery.message.messageId

        val session = activeSessions[chatId] ?: return

        // فقط به کلیک‌های مربوط به پیام آخرین جلسه پاسخ می‌دهیم
        if (session.messageId != messageId) return

        when {
            data.startsWith("user_") -> {
                val userId = data.removePrefix("user_").toLong()
                toggleUserStatus(chatId, session, userId)
            }
            data == "done_present" -> {
                session.state = "absent_excused"
                updateSessionMessage(chatId, session, "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
            }
            data == "done_final" -> {
                finishSession(chatId, session)
            }
        }
    }

    private fun toggleUserStatus(chatId: String, session: StatusSession, userId: Long) {
        val user = session.users[userId] ?: return

        if (session.state == "present") {
            user.status = if (user.status == AttendanceStatus.PRESENT) AttendanceStatus.UNKNOWN else AttendanceStatus.PRESENT
        } else if (session.state == "absent_excused") {
            user.status = if (user.status == AttendanceStatus.ABSENT_EXCUSED) AttendanceStatus.UNKNOWN else AttendanceStatus.ABSENT_EXCUSED
        }

        updateSessionMessage(chatId, session, if (session.state == "present") "مرحله ۱: چه کسانی در جلسه **حاضر** هستند؟" else "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
    }

    private fun updateSessionMessage(chatId: String, session: StatusSession, text: String) {
        val editMessage = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = text
            this.replyMarkup = buildKeyboard(session.users, session.state)
        }
        execute(editMessage)
    }

    private fun buildKeyboard(users: Map<Long, UserInfo>, state: String): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup()
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        var currentRow = mutableListOf<InlineKeyboardButton>()

        for ((userId, userInfo) in users) {
            // در مرحله دوم (غایب موجه) کسانی که حاضر بوده‌اند را دیگر نمایش نمی‌دهیم
            if (state == "absent_excused" && userInfo.status == AttendanceStatus.PRESENT) continue

            val buttonText = when {
                userInfo.status == AttendanceStatus.PRESENT -> "✅ ${userInfo.name}"
                userInfo.status == AttendanceStatus.ABSENT_EXCUSED -> "❌ ${userInfo.name}"
                else -> userInfo.name
            }

            val button = InlineKeyboardButton().apply {
                text = buttonText
                callbackData = "user_$userId"
            }

            currentRow.add(button)
            if (currentRow.size == 2) { // دو دکمه در هر ردیف
                rows.add(currentRow)
                currentRow = mutableListOf()
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // دکمه تایید مرحله
        val doneButton = InlineKeyboardButton().apply {
            text = if (state == "present") "➡️ تایید حاضران و مرحله بعد" else "✔️ تایید نهایی و ارسال گزارش"
            callbackData = if (state == "present") "done_present" else "done_final"
        }
        rows.add(listOf(doneButton))

        keyboard.keyboard = rows
        return keyboard
    }

    private fun finishSession(chatId: String, session: StatusSession) {
        // کسانی که وضعیتشان هنوز نامشخص است، غایب غیرموجه محسوب می‌شوند
        session.users.values.forEach {
            if (it.status == AttendanceStatus.UNKNOWN) {
                it.status = AttendanceStatus.ABSENT_UNEXCUSED
            }
        }

        val presents = session.users.values.filter { it.status == AttendanceStatus.PRESENT }
        val absentExcused = session.users.values.filter { it.status == AttendanceStatus.ABSENT_EXCUSED }
        val absentUnexcused = session.users.values.filter { it.status == AttendanceStatus.ABSENT_UNEXCUSED }

        val report = buildString {
            appendLine("#اعلام_وضعیت: \uD83D\uDCC6 جلسه روزانه")
            appendLine("")
            appendLine("\uD83E\uDEE1حاضرین:")
            if (presents.isEmpty()) appendLine("هیچ‌کس") else presents.forEach { appendLine("- ${it.name}") }
            appendLine("")
            appendLine("\uD83D\uDE31 غایبین:")
            if (absentExcused.isNotEmpty()) absentExcused.forEach { appendLine("- ${it.name} \uD83D\uDD14") }
            if (absentUnexcused.isNotEmpty()) absentUnexcused.forEach { appendLine("- ${it.name} \uD83D\uDD15") }
            if (absentUnexcused.isEmpty() and absentExcused.isEmpty()) appendLine("هیچ‌کس")
            appendLine("")
            appendLine("وضعیت: ${presents.size + absentExcused.size}/${session.users.values.size}")
        }

        // حذف کیبورد شیشه‌ای آخرین مرحله و نمایش پیغام پایان
        val editMessage = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = "✅ ثبت وضعیت جلسه به پایان رسید."
        }
        execute(editMessage)

        // ارسال گزارش نهایی
        sendMessage(chatId, report)

        // پاک کردن جلسه از حافظه
        activeSessions.remove(chatId)
    }

    private fun sendMessage(chatId: String, text: String) {
        val message = SendMessage().apply {
            this.chatId = chatId
            this.text = text
        }
        execute(message)
    }

    private fun sendMessageWithKeyboard(chatId: String, text: String, keyboard: InlineKeyboardMarkup): org.telegram.telegrambots.meta.api.objects.Message? {
        val message = SendMessage().apply {
            this.chatId = chatId
            this.text = text
            this.replyMarkup = keyboard
        }
        return execute(message)
    }
}
