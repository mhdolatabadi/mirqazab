package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.models.UserInfo
import ir.mhdolatabadi.models.StatusSession
import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.services.MemberService
import ir.mhdolatabadi.services.AttendanceService
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import ir.mhdolatabadi.utils.ReactionKeys

/**
 * Runs the roll-call session on its own message (sent by [startSession], not
 * an edit of the caller's message) so its numbered-keycap reactions never
 * collide with a key the same user already used on a different flow sharing
 * that event id - Matrix reactions are per (user, key, event), so a repeat
 * key would just un-react instead of firing again.
 */
class SessionManager(
    private val bot: MatrixLongPollingBot,
    private val memberService: MemberService,
    private val attendanceService: AttendanceService
) {
    private val activeSessions = mutableMapOf<String, StatusSession>()

    fun getActiveSession(chatId: String): StatusSession? = activeSessions[chatId]

    fun isSessionMessage(chatId: String, eventId: String): Boolean =
        activeSessions[chatId]?.rootEventId == eventId

    fun handleReaction(chatId: String, senderId: String, key: String) {
        val session = activeSessions[chatId] ?: return
        if (senderId != session.initiatorUserId) return
        when (key) {
            KEY_SESSION_DONE_PRESENT -> advanceSession(chatId)
            KEY_SESSION_DONE_FINAL -> finishSession(chatId)
            else -> {
                val targetUserId = session.orderedUserIds.withIndex()
                    .firstOrNull { (i, _) -> ReactionKeys.forIndex(i + 1) == key }?.value
                if (targetUserId != null) toggleUserStatus(chatId, targetUserId)
            }
        }
    }

    fun startSession(chatId: String, initiatorUserId: String): StatusSession? {
        if (attendanceService.hasAttendanceToday(chatId, DateUtils.today())) {
            bot.sendMessage(
                chatId,
                "⚠️ گزارش جلسه امروز قبلاً ثبت شده است.\nبرای ثبت مجدد ابتدا از دکمه «حذف گزارش جلسه روزانه امروز» استفاده کنید."
            )
            return null
        }

        if (activeSessions.containsKey(chatId)) {
            bot.sendMessage(
                chatId,
                "⚠️ در حال حاضر یک جلسه گزارش در این گروه در حال اجراست.\nلطفاً ابتدا آن را تکمیل کنید."
            )
            return null
        }

        val members = memberService.getGroupMembers(chatId)
        if (members.isEmpty()) {
            bot.sendMessage(chatId, "هنوز هیچ عضوی شناسایی نشده است. لطفاً اعضا یک پیام بفرستند.")
            return null
        }

        val orderedUserIds = members.keys.toList()
        val sessionUsers = members.mapValues { UserInfo(it.key, it.value) }.toMutableMap()

        val rootEventId = bot.sendMessage(chatId, "در حال آماده‌سازی...")
        val session = StatusSession(rootEventId, sessionUsers, orderedUserIds, "present", initiatorUserId)
        activeSessions[chatId] = session

        val text = buildStatusText(session, "کیا تو جلسه حضور داشتن؟")
        bot.editMessage(chatId, rootEventId, text)
        val keys = orderedUserIds.indices.map { ReactionKeys.forIndex(it + 1) } + KEY_SESSION_DONE_PRESENT
        bot.reactAll(chatId, rootEventId, keys)

        return session
    }

    fun toggleUserStatus(chatId: String, userId: String) {
        val session = activeSessions[chatId] ?: return
        val user = session.users[userId] ?: return

        if (session.state == "present") {
            user.status = if (user.status == AttendanceStatus.PRESENT) AttendanceStatus.UNKNOWN else AttendanceStatus.PRESENT
        } else {
            if (user.status == AttendanceStatus.PRESENT) return // not part of this stage anymore
            user.status = if (user.status == AttendanceStatus.ABSENT_EXCUSED) AttendanceStatus.UNKNOWN else AttendanceStatus.ABSENT_EXCUSED
        }
        val prompt = if (session.state == "present") "کیا تو جلسه حاضر بودن؟" else "کیا خبر داده بودن؟"
        bot.editMessage(chatId, session.rootEventId, buildStatusText(session, prompt))
    }

    fun advanceSession(chatId: String) {
        val session = activeSessions[chatId] ?: return
        session.state = "absent_excused"
        bot.editMessage(chatId, session.rootEventId, buildStatusText(session, "کیا خبر داده بودن؟"))
        bot.react(chatId, session.rootEventId, KEY_SESSION_DONE_FINAL)
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

        bot.editMessage(chatId, session.rootEventId, report)
        return session
    }

    fun resetSession(chatId: String) {
        activeSessions.remove(chatId)
    }

    private fun buildStatusText(session: StatusSession, prompt: String): String = buildString {
        appendLine(prompt)
        appendLine()
        for ((index, userId) in session.orderedUserIds.withIndex()) {
            val userInfo = session.users[userId] ?: continue
            if (session.state == "absent_excused" && userInfo.status == AttendanceStatus.PRESENT) continue
            val key = ReactionKeys.forIndex(index + 1)
            val statusMark = when (userInfo.status) {
                AttendanceStatus.PRESENT -> "✅ "
                AttendanceStatus.ABSENT_EXCUSED -> "🔔 "
                else -> ""
            }
            appendLine("$key $statusMark${userInfo.name}")
        }
        appendLine()
        if (session.state == "present") {
            appendLine("$KEY_SESSION_DONE_PRESENT = همینا بودن، برو مرحله بعد")
        } else {
            appendLine("$KEY_SESSION_DONE_FINAL = بفرست بره")
        }
    }
}
