package ir.mhdolatabadi

import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import kotlinx.coroutines.*
import ir.mhdolatabadi.entities.AttendanceRecord
import ir.mhdolatabadi.entities.GroupMember
import jakarta.persistence.EntityManagerFactory
import ir.huri.jcal.JalaliCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

// ========================== Enums & Data Classes ==========================
enum class AttendanceStatus { UNKNOWN, PRESENT, ABSENT_EXCUSED, ABSENT_UNEXCUSED }

data class UserInfo(val id: Long, val name: String, var status: AttendanceStatus = AttendanceStatus.UNKNOWN)

data class StatusSession(
    val messageId: Int,
    val users: MutableMap<Long, UserInfo>,
    var state: String
)

private data class UserAttendance(
    val name: String,
    val present: Int,
    val excused: Int,
    val unexcused: Int
)

// ========================== DailyBot ==========================
class DailyBot(
    botToken: String,
    private val botUsername: String,
    options: DefaultBotOptions = DefaultBotOptions(),
    private val entityManagerFactory: EntityManagerFactory
) : TelegramLongPollingBot(options, botToken) {

    private val activeSessions = mutableMapOf<String, StatusSession>()
    private val candidateSelections = mutableMapOf<String, MutableSet<Long>>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tehranZone = ZoneId.of("Asia/Tehran")

    init {
        startScheduler()
    }

    override fun getBotUsername(): String = botUsername

    override fun onUpdateReceived(update: Update) {
        try {
            recordUserActivity(update)
            if (update.hasMessage() && update.message.hasText()) {
                handleTextMessage(update)
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ==================== ابزار تبدیل اعداد به فارسی ====================
    private fun toPersianNumber(input: Any?): String {
        val englishDigits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val persianDigits = listOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return input.toString().map { char ->
            val index = englishDigits.indexOf(char)
            if (index != -1) persianDigits[index] else char
        }.joinToString("")
    }

    // ==================== ثبت فعالیت کاربر در دیتابیس ====================
    private fun recordUserActivity(update: Update) {
        val (message, user) = when {
            update.hasMessage() -> update.message to update.message.from
            update.hasCallbackQuery() -> update.callbackQuery.message to update.callbackQuery.from
            else -> return
        }
        if (user.isBot) return
        val chatId = message.chatId.toLong()
        val fullName = "${user.firstName} ${user.lastName ?: ""}".trim()
        val displayName = fullName.ifEmpty { user.userName ?: "کاربر ${user.id}" }

        val em = entityManagerFactory.createEntityManager()
        try {
            em.transaction.begin()
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId)
                .setParameter("userId", user.id)
                .resultList.firstOrNull()

            if (member == null) {
                val newMember = GroupMember(chatId = chatId, userId = user.id, name = displayName, mirGhazabCount = 0)
                em.persist(newMember)
            } else {
                if (member.name != displayName) {
                    member.name = displayName
                    em.merge(member)
                }
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            e.printStackTrace()
        } finally {
            em.close()
        }
    }

    // ==================== دریافت اعضای گروه از دیتابیس ====================
    private fun getGroupMembers(chatId: String): Map<Long, String> {
        val entityManager = entityManagerFactory.createEntityManager()
        entityManager.use { em ->
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong()).resultList
            return members.associate { it.userId to it.name }
        }
    }

    // ==================== دریافت تعداد میرغضب شدن از دیتابیس ====================
    private fun getMirGhazabCounts(chatId: String): Map<Long, Int> {
        val em = entityManagerFactory.createEntityManager()
        em.use {
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong()).resultList
            return members.associate { it.userId to it.mirGhazabCount }
        }
    }

    // ==================== افزایش تعداد میرغضب برای یک کاربر ====================
    private fun incrementMirGhazabCount(chatId: String, userId: Long) {
        val em = entityManagerFactory.createEntityManager()
        try {
            em.transaction.begin()
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .singleResult
            member.mirGhazabCount++
            em.merge(member)
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            e.printStackTrace()
        } finally {
            em.close()
        }
    }

    // ==================== تشخیص دستور میرغضب ====================
    private fun handleTextMessage(update: Update) {
        val chatId = update.message.chatId.toString()
        val text = update.message.text
        val replyToMsgId = update.message.messageId
        if (isMirGhazab(text)) {
            showMainMenu(chatId, replyToMsgId)
        }
    }

    private fun isMirGhazab(input: String): Boolean {
        val normalized = normalizePersian(input.trim())
        val validForms = setOf("میرغضب", "میر غضب", "ميرغضب", "میر غضب", "میرغضب", "میر غضب", "مير غضب")
        return validForms.contains(normalized)
    }

    private fun normalizePersian(text: String) = text
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("\\s+"), " ").trim()

    // ==================== منوی اصلی با سه دکمه ====================
    private fun showMainMenu(chatId: String, replyToMessageId: Int) {
        val randomBtn = InlineKeyboardButton().apply {
            text = "🎲 انتخاب میرغضب تصادفی (با کاندید)"
            callbackData = "random_mirghazab_menu"
        }
        val reportBtn = InlineKeyboardButton().apply {
            text = "📋 گزارش جلسه روزانه"
            callbackData = "daily_report"
        }
        val weeklyBtn = InlineKeyboardButton().apply {
            text = "📊 گزارش هفتگی (هم اکنون)"
            callbackData = "weekly_report_now"
        }
        val keyboard = InlineKeyboardMarkup(listOf(listOf(randomBtn), listOf(reportBtn), listOf(weeklyBtn)))
        val message = SendMessage(chatId, "لطفاً انتخاب کنید:")
        message.replyMarkup = keyboard
        message.replyToMessageId = replyToMessageId
        execute(message)
    }

    private fun handleCallbackQuery(update: Update) {
        val callback = update.callbackQuery
        val chatId = callback.message.chatId.toString()
        val data = callback.data
        val currentMsgId = callback.message.messageId

        when {
            data == "random_mirghazab_menu" -> showCandidateSelection(chatId, currentMsgId)
            data == "daily_report" -> {
                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "🔄 شروع فرایند گزارش جلسه..."
                    this.replyMarkup = null
                }
                execute(edit)
                startStatusSessionOnExistingMessage(chatId, currentMsgId)
            }
            data == "weekly_report_now" -> {
                val loadingMsg = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "📊 در حال تولید گزارش هفتگی... لطفاً چند لحظه صبر کنید."
                    this.replyMarkup = null
                }
                execute(loadingMsg)
                generateAndSendWeeklyReportForChat(chatId, currentMsgId)
            }
            data.startsWith("toggle_cand_") -> {
                val userId = data.removePrefix("toggle_cand_").toLong()
                toggleCandidate(chatId, currentMsgId, userId)
            }
            data == "do_random_from_candidates" -> performRandomFromCandidates(chatId, currentMsgId)
            data == "cancel_candidate_selection" -> cancelSelection(chatId, currentMsgId)
            else -> {
                val session = activeSessions[chatId] ?: return
                if (session.messageId != currentMsgId) return
                when {
                    data.startsWith("user_") -> {
                        val userId = data.removePrefix("user_").toLong()
                        toggleUserStatus(chatId, session, userId)
                    }
                    data == "done_present" -> {
                        session.state = "absent_excused"
                        updateSessionMessage(chatId, session, "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
                    }
                    data == "done_final" -> finishSession(chatId, session)
                }
            }
        }
    }

    // ==================== انتخاب کاندیدا (میرغضب رندوم) ====================
    private fun showCandidateSelection(chatId: String, messageId: Int) {
        val members = getGroupMembers(chatId)
        if (members.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ هنوز عضوی شناسایی نشده است."
                this.replyMarkup = null
            }
            execute(edit)
            return
        }
        val key = "$chatId-$messageId"
        candidateSelections[key] = members.keys.toMutableSet()
        val keyboard = buildCandidateKeyboard(chatId, messageId)
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "✅ اعضای کاندید برای انتخاب میرغضب (با کلیک می‌توانید حذف/افزودن کنید):\n\n📊 تعداد دفعات میرغضب شدن قبلی در کنار هر نام آمده است."
            this.replyMarkup = keyboard
        }
        execute(edit)
    }

    private fun buildCandidateKeyboard(chatId: String, messageId: Int): InlineKeyboardMarkup {
        val members = getGroupMembers(chatId)
        val key = "$chatId-$messageId"
        val selectedSet = candidateSelections[key] ?: members.keys.toMutableSet()
        val counts = getMirGhazabCounts(chatId)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for ((userId, name) in members) {
            val isSelected = userId in selectedSet
            val count = counts[userId] ?: 0
            val countPersian = toPersianNumber(count)
            val buttonText = "${if (isSelected) "✅" else "❌"} $name ($countPersian بار)"
            val button = InlineKeyboardButton().apply {
                this.text = buttonText
                callbackData = "toggle_cand_$userId"
            }
            rows.add(listOf(button))
        }
        val randomBtn = InlineKeyboardButton().apply {
            text = "🎲 انتخاب رندوم از کاندیدها"
            callbackData = "do_random_from_candidates"
        }
        val cancelBtn = InlineKeyboardButton().apply {
            text = "❌ انصراف"
            callbackData = "cancel_candidate_selection"
        }
        rows.add(listOf(randomBtn))
        rows.add(listOf(cancelBtn))
        return InlineKeyboardMarkup(rows)
    }

    private fun toggleCandidate(chatId: String, messageId: Int, userId: Long) {
        val key = "$chatId-$messageId"
        val currentSet = candidateSelections[key] ?: return
        if (userId in currentSet) currentSet.remove(userId) else currentSet.add(userId)
        val newKeyboard = buildCandidateKeyboard(chatId, messageId)
        val edit = EditMessageReplyMarkup().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.replyMarkup = newKeyboard
        }
        execute(edit)
    }

    private fun performRandomFromCandidates(chatId: String, messageId: Int) {
        val key = "$chatId-$messageId"
        val candidates = candidateSelections[key]?.toList() ?: emptyList()
        if (candidates.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ هیچ کاندیدی انتخاب نشده است. لطفاً حداقل یک عضو را انتخاب کنید."
                this.replyMarkup = buildCandidateKeyboard(chatId, messageId)
            }
            execute(edit)
            return
        }
        val randomUserId = candidates.random(Random)
        val members = getGroupMembers(chatId)
        val name = members[randomUserId] ?: "کاربر"
        incrementMirGhazabCount(chatId, randomUserId)
        val newCount = getMirGhazabCounts(chatId)[randomUserId] ?: 0
        val newCountPersian = toPersianNumber(newCount)
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "🎭 میرغضب امروز: **$name**\n\nاین کاربر $newCountPersian بار میرغضب شده است."
            this.replyMarkup = null
            this.parseMode = "Markdown"
        }
        execute(edit)
        candidateSelections.remove(key)
    }

    private fun cancelSelection(chatId: String, messageId: Int) {
        candidateSelections.remove("$chatId-$messageId")
        val randomBtn = InlineKeyboardButton().apply {
            text = "🎲 انتخاب میرغضب تصادفی (با کاندید)"
            callbackData = "random_mirghazab_menu"
        }
        val reportBtn = InlineKeyboardButton().apply {
            text = "📋 گزارش جلسه روزانه"
            callbackData = "daily_report"
        }
        val weeklyBtn = InlineKeyboardButton().apply {
            text = "📊 گزارش هفتگی (هم اکنون)"
            callbackData = "weekly_report_now"
        }
        val keyboard = InlineKeyboardMarkup(listOf(listOf(randomBtn), listOf(reportBtn), listOf(weeklyBtn)))
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "لطفاً انتخاب کنید:"
            this.replyMarkup = keyboard
        }
        execute(edit)
    }

    // ==================== گزارش جلسه روزانه ====================
    private fun startStatusSessionOnExistingMessage(chatId: String, messageId: Int) {
        val members = getGroupMembers(chatId)
        if (members.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "هنوز هیچ عضوی شناسایی نشده است. لطفاً اعضا یک پیام بفرستند."
                this.replyMarkup = null
            }
            execute(edit)
            return
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
        execute(edit)
        activeSessions[chatId] = StatusSession(messageId, sessionUsers, "present")
    }

    private fun buildStatusKeyboard(users: Map<Long, UserInfo>, state: String): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        var currentRow = mutableListOf<InlineKeyboardButton>()
        for ((userId, userInfo) in users) {
            if (state == "absent_excused" && userInfo.status == AttendanceStatus.PRESENT) continue
            val buttonText = when (userInfo.status) {
                AttendanceStatus.PRESENT -> "✅ ${userInfo.name}"
                AttendanceStatus.ABSENT_EXCUSED -> "❌ ${userInfo.name}"
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

    private fun toggleUserStatus(chatId: String, session: StatusSession, userId: Long) {
        val user = session.users[userId] ?: return
        if (session.state == "present") {
            user.status = if (user.status == AttendanceStatus.PRESENT) AttendanceStatus.UNKNOWN else AttendanceStatus.PRESENT
        } else {
            user.status = if (user.status == AttendanceStatus.ABSENT_EXCUSED) AttendanceStatus.UNKNOWN else AttendanceStatus.ABSENT_EXCUSED
        }
        updateSessionMessage(chatId, session, if (session.state == "present") "مرحله ۱: چه کسانی در جلسه **حاضر** هستند؟" else "مرحله ۲: چه کسانی **غایب موجه** هستند؟")
    }

    private fun updateSessionMessage(chatId: String, session: StatusSession, text: String) {
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = text
            this.replyMarkup = buildStatusKeyboard(session.users, session.state)
            this.parseMode = "Markdown"
        }
        execute(edit)
    }

    private fun finishSession(chatId: String, session: StatusSession) {
        session.users.values.forEach {
            if (it.status == AttendanceStatus.UNKNOWN) {
                it.status = AttendanceStatus.ABSENT_UNEXCUSED
            }
        }

        val em = entityManagerFactory.createEntityManager()
        try {
            em.transaction.begin()
            val today = LocalDate.now(tehranZone)
            for ((userId, userInfo) in session.users) {
                if (userInfo.status != AttendanceStatus.UNKNOWN) {
                    val record = AttendanceRecord(
                        userId = userId,
                        chatId = chatId.toLong(),
                        date = today,
                        status = userInfo.status
                    )
                    em.persist(record)
                }
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            e.printStackTrace()
        } finally {
            em.close()
        }

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
            val presentPersian = toPersianNumber(presentCount + excusedCount)
            val totalPersian = toPersianNumber(total)
            appendLine("\nوضعیت: $presentPersian/$totalPersian")
        }
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = session.messageId
            this.text = report
            this.replyMarkup = null
            this.parseMode = "Markdown"
        }
        execute(edit)

        activeSessions.remove(chatId)
    }

    // ==================== زمان‌بندی خودکار (۹ صبح و ۱۸ عصر) ====================
    private fun startScheduler() {
        scope.launch {
            while (true) {
                val now = LocalDateTime.now(tehranZone)
                val targetHour9 = 9
                val targetHour18 = 18
                val targetMinute = 0

                val nextRun9 = now.withHour(targetHour9).withMinute(targetMinute).withSecond(0).withNano(0)
                val nextRun18 = now.withHour(targetHour18).withMinute(targetMinute).withSecond(0).withNano(0)

                val nextRun = when {
                    now.isBefore(nextRun9) -> nextRun9
                    now.isBefore(nextRun18) -> nextRun18
                    else -> nextRun9.plusDays(1)
                }

                val delay = java.time.Duration.between(now, nextRun).toMillis()
                delay(delay)

                if (nextRun.hour == targetHour9) {
                    checkAndSendReports()  // گزارش‌های هفتگی/ماهانه
                } else if (nextRun.hour == targetHour18) {
                    sendDailyReminder()     // یادآوری روزانه
                }
            }
        }
    }

    // ==================== گزارش‌های هفتگی و ماهانه خودکار ====================
    private fun checkAndSendReports() {
        val today = LocalDate.now(tehranZone)
        val persianDate = toPersianDate(today)

        if (today.dayOfWeek.value == 5) { // جمعه
            sendWeeklyReportToAllGroups()
        }

        if (persianDate.day == 1) {
            sendMonthlyReportToAllGroups()
        }
    }

    // ==================== یادآوری روزانه ساعت ۱۸ ====================
    private fun sendDailyReminder() {
        val em = entityManagerFactory.createEntityManager()
        try {
            val chatIds = em.createQuery("SELECT DISTINCT m.chatId FROM GroupMember m", Long::class.java).resultList
            val today = LocalDate.now(tehranZone)

            for (chatId in chatIds) {
                val count = em.createQuery(
                    "SELECT COUNT(a) FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :today",
                    Long::class.java
                ).setParameter("chatId", chatId)
                    .setParameter("today", today)
                    .singleResult

                if (count == 0L && today.dayOfWeek.value != 4 && today.dayOfWeek.value != 5) {
                    val message = "🔔 **یادآوری روزانه**\n\nگزارش جلسه امروز هنوز ثبت نشده است.\nلطفاً با ارسال كلمه «میرغضب» گزارش را ثبت کنید."
                    sendMessage(chatId.toString(), message)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            em.close()
        }
    }

    // ==================== گزارش هفتگی فوری (ویرایش پیام اصلی) ====================
    private fun generateAndSendWeeklyReportForChat(chatId: String, originalMessageId: Int) {
        val em = entityManagerFactory.createEntityManager()
        em.use {
            val endDate = LocalDate.now(tehranZone)
            val startDate = endDate.minusWeeks(1)
            val report = generateReportForChat(chatId, startDate, endDate)
            val startPersian = toPersianDate(startDate)
            val endPersian = toPersianDate(endDate)
            val year1 = toPersianNumber(startPersian.year)
            val month1 = toPersianNumber(startPersian.month)
            val day1 = toPersianNumber(startPersian.day)
            val year2 = toPersianNumber(endPersian.year)
            val month2 = toPersianNumber(endPersian.month)
            val day2 = toPersianNumber(endPersian.day)
            val title = "📊 گزارش هفتگی حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"
            val fullMessage = "$title\n\n$report"

            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = originalMessageId
                this.text = fullMessage
                this.parseMode = "Markdown"
                this.replyMarkup = null
            }
            execute(edit)
        }
    }

    // ==================== گزارش برای یک چت خاص ====================
    private fun generateReportForChat(chatId: String, startDate: LocalDate, endDate: LocalDate): String {
        val em = entityManagerFactory.createEntityManager()
        em.use {
            val chatIdLong = chatId.toLong()
            val query = em.createQuery(
                "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date BETWEEN :start AND :end ORDER BY a.userId, a.date",
                AttendanceRecord::class.java
            )
            query.setParameter("chatId", chatIdLong)
            query.setParameter("start", startDate)
            query.setParameter("end", endDate)
            val records = query.resultList

            val userStats = mutableMapOf<Long, MutableMap<AttendanceStatus, Int>>()
            for (rec in records) {
                val stats = userStats.getOrPut(rec.userId) { mutableMapOf() }
                stats[rec.status] = stats.getOrDefault(rec.status, 0) + 1
            }

            if (userStats.isEmpty()) {
                return "هیچ داده‌ای برای این گروه در بازه مورد نظر وجود ندارد."
            }

            val members = getGroupMembers(chatId)

            val sortedUsers = userStats.entries.map { entry ->
                val userId = entry.key
                val stats = entry.value
                val name = members[userId] ?: "کاربر $userId"
                val present = stats[AttendanceStatus.PRESENT] ?: 0
                val excused = stats[AttendanceStatus.ABSENT_EXCUSED] ?: 0
                val unexcused = stats[AttendanceStatus.ABSENT_UNEXCUSED] ?: 0
                UserAttendance(name, present, excused, unexcused)
            }.sortedWith(compareByDescending<UserAttendance> { it.present }
                .thenByDescending { it.excused }
                .thenBy { it.name })

            val report = StringBuilder()
            for (user in sortedUsers) {
                val presentPersian = toPersianNumber(user.present)
                val excusedPersian = toPersianNumber(user.excused)
                val unexcusedPersian = toPersianNumber(user.unexcused)
                report.appendLine("👤 ${user.name}: ✅ $presentPersian بار | 🔔 $excusedPersian بار | 🔕 $unexcusedPersian بار")
            }
            return report.toString()
        }
    }

    // ==================== گزارش هفتگی خودکار برای همه گروه‌ها ====================
    private fun sendWeeklyReportToAllGroups() {
        val em = entityManagerFactory.createEntityManager()
        em.use {
            val endDate = LocalDate.now(tehranZone)
            val startDate = endDate.minusWeeks(1)
            val startPersian = toPersianDate(startDate)
            val endPersian = toPersianDate(endDate)
            val year1 = toPersianNumber(startPersian.year)
            val month1 = toPersianNumber(startPersian.month)
            val day1 = toPersianNumber(startPersian.day)
            val year2 = toPersianNumber(endPersian.year)
            val month2 = toPersianNumber(endPersian.month)
            val day2 = toPersianNumber(endPersian.day)
            val title = "📊 گزارش هفتگی حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"

            val chatIds = em.createQuery("SELECT DISTINCT a.chatId FROM AttendanceRecord a", Long::class.java).resultList
            for (chatId in chatIds) {
                val report = generateReportForChat(chatId.toString(), startDate, endDate)
                sendMessage(chatId.toString(), "$title\n\n$report")
            }
        }
    }

    // ==================== گزارش ماهانه خودکار برای همه گروه‌ها ====================
    private fun sendMonthlyReportToAllGroups() {
        val em = entityManagerFactory.createEntityManager()
        em.use {
            val endDate = LocalDate.now(tehranZone)
            val startDate = endDate.minusMonths(1)
            val startPersian = toPersianDate(startDate)
            val endPersian = toPersianDate(endDate)
            val year1 = toPersianNumber(startPersian.year)
            val month1 = toPersianNumber(startPersian.month)
            val day1 = toPersianNumber(startPersian.day)
            val year2 = toPersianNumber(endPersian.year)
            val month2 = toPersianNumber(endPersian.month)
            val day2 = toPersianNumber(endPersian.day)
            val title = "📊 گزارش ماهانه حضور و غیاب\n(از $year1/$month1/$day1 تا $year2/$month2/$day2)"

            val chatIds = em.createQuery("SELECT DISTINCT a.chatId FROM AttendanceRecord a", Long::class.java).resultList
            for (chatId in chatIds) {
                val report = generateReportForChat(chatId.toString(), startDate, endDate)
                sendMessage(chatId.toString(), "$title\n\n$report")
            }
        }
    }

    // ==================== تبدیل تاریخ میلادی به شمسی ====================
    private fun toPersianDate(gregorian: LocalDate): PersianDate {
        val gregorianCalendar = java.util.GregorianCalendar.from(gregorian.atStartOfDay(tehranZone))
        val jalali = JalaliCalendar(gregorianCalendar)
        return PersianDate(jalali.year, jalali.month, jalali.day)
    }

    data class PersianDate(val year: Int, val month: Int, val day: Int)

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