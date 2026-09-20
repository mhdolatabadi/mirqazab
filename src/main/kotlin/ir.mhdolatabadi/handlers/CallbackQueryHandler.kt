package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.*
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import ir.mhdolatabadi.utils.ReactionKeys
import java.time.LocalDate
import kotlin.random.Random

private const val KEY_RANDOM_MIRGHAZAB = "🎲"
private const val KEY_DAILY_REPORT = "📋"
private const val KEY_WEEKLY_REPORT = "📅"
private const val KEY_OVERALL_REPORT = "📈"
private const val KEY_RESET_ATTENDANCE = "🔄"
private const val KEY_CONFIRM = "✅"
private const val KEY_CANCEL = "❌"

private const val KEY_ACTIVITY_DEVELOPMENT = "🛠️"
private const val KEY_ACTIVITY_QUICK_REACTION = "⚡"
private const val KEY_ACTIVITY_VACATION = "🏖️"

/**
 * Matrix has no inline-keyboard/callback-query concept, so every action that used
 * to be a button is now a reaction key on a message. Which "flow" a reaction belongs
 * to is decided by which tracked map contains the message's root event id - not by
 * a self-describing callback data string like Telegram had.
 */
class CallbackQueryHandler(
    private val bot: MatrixLongPollingBot,
    private val sessionManager: SessionManager,
    private val activityService: ActivityService,
    private val reportService: ReportService,
    private val memberService: MemberService,
    private val attendanceService: AttendanceService
) {
    private val resetConfirmations = mutableMapOf<String, Pair<String, String>>() // chatId -> (initiatorUserId, eventId)
    private val candidateSelections = mutableMapOf<String, MutableSet<String>>() // "chatId-eventId" -> candidate userIds
    private val activityMessageDates = mutableMapOf<String, LocalDate>() // eventId -> date

    // ==================== منوی اصلی ====================
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

    private fun showMainMenuOnExistingMessage(chatId: String, eventId: String) {
        bot.editMessage(chatId, eventId, buildMainMenuText(chatId))
        bot.reactAll(chatId, eventId, mainMenuKeys())
    }

    // ==================== سوال روزانه توسعه/واکنش سریع ====================
    fun buildActivityMessage(chatId: String, date: LocalDate = DateUtils.today()): Pair<String, List<String>> {
        val persianDate = DateUtils.toPersianDate(date)
        val dateStr = "${NumberUtils.toPersianNumber(persianDate.year)}/${NumberUtils.toPersianNumber(persianDate.month)}/${NumberUtils.toPersianNumber(persianDate.day)}"

        val todayActivities = activityService.getActivitiesOn(chatId, date)
        val members = memberService.getGroupMembers(chatId)

        val messageText = buildString {
            appendLine("📋 ثبت وضعیت توسعه/واکنش سریع")
            appendLine("تاریخ: $dateStr")
            appendLine()
            appendLine("امروز توسعه بودی یا واکنش سریع؟")
            appendLine()
            appendLine("$KEY_ACTIVITY_DEVELOPMENT توسعه   $KEY_ACTIVITY_QUICK_REACTION واکنش سریع   $KEY_ACTIVITY_VACATION مرخصی")
            appendLine()
            if (todayActivities.isEmpty()) {
                appendLine("📭 هنوز کسی پاسخی نداده است.")
            } else {
                appendLine("📊 لیست پاسخ‌ها:")
                for ((userId, status) in todayActivities) {
                    val name = members[userId] ?: "کاربر $userId"
                    val (emoji, statusName) = when (status) {
                        ActivityStatus.DEVELOPMENT -> KEY_ACTIVITY_DEVELOPMENT to "توسعه"
                        ActivityStatus.QUICK_REACTION -> KEY_ACTIVITY_QUICK_REACTION to "واکنش سریع"
                        ActivityStatus.VACATION -> KEY_ACTIVITY_VACATION to "مرخصی"
                    }
                    appendLine("• $name: $emoji $statusName")
                }
            }
        }

        val keys = listOf(KEY_ACTIVITY_DEVELOPMENT, KEY_ACTIVITY_QUICK_REACTION, KEY_ACTIVITY_VACATION)
        return Pair(messageText, keys)
    }

    fun sendActivityMessage(chatId: String, date: LocalDate = DateUtils.today()): String {
        val (text, keys) = buildActivityMessage(chatId, date)
        val eventId = bot.sendMessage(chatId, text)
        activityMessageDates[eventId] = date
        bot.reactAll(chatId, eventId, keys)
        return eventId
    }

    // ==================== نقطه ورود واکنش‌ها ====================
    fun handleReaction(chatId: String, senderId: String, eventId: String, key: String) {
        val candidateKey = "$chatId-$eventId"
        val activeSession = sessionManager.getActiveSession(chatId)

        when {
            candidateSelections.containsKey(candidateKey) -> handleCandidateReaction(chatId, eventId, key)

            activityMessageDates.containsKey(eventId) -> handleActivityReaction(chatId, senderId, eventId, key)

            activeSession != null && activeSession.rootEventId == eventId -> {
                if (senderId != activeSession.initiatorUserId) return
                when (key) {
                    "➡️" -> sessionManager.advanceSession(chatId)
                    "✔️" -> sessionManager.finishSession(chatId)
                    else -> {
                        val targetUserId = activeSession.orderedUserIds.withIndex()
                            .firstOrNull { (i, _) -> ReactionKeys.forIndex(i + 1) == key }?.value
                        if (targetUserId != null) sessionManager.toggleUserStatus(chatId, targetUserId)
                    }
                }
            }

            resetConfirmations.containsKey(chatId) -> handleResetConfirmReaction(chatId, senderId, key)

            else -> handleMenuReaction(chatId, senderId, eventId, key)
        }
    }

    private fun handleMenuReaction(chatId: String, senderId: String, eventId: String, key: String) {
        when (key) {
            KEY_RANDOM_MIRGHAZAB -> showCandidateSelection(chatId, eventId)

            KEY_DAILY_REPORT -> {
                if (attendanceService.hasAttendanceToday(chatId, DateUtils.today())) return
                if (sessionManager.getActiveSession(chatId) != null) return
                bot.editMessage(chatId, eventId, "🔄 شروع فرایند گزارش جلسه...")
                sessionManager.startSession(chatId, eventId, senderId)
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
                val report = reportService.generateOverallActivityReport(chatId)
                bot.editMessage(chatId, eventId, report)
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
                attendanceService.resetTodayAttendance(chatId, DateUtils.today())
                sessionManager.resetSession(chatId)
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, eventId)
            }
            KEY_CANCEL -> {
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, eventId)
            }
        }
    }

    private fun handleActivityReaction(chatId: String, senderId: String, eventId: String, key: String) {
        val newStatus = when (key) {
            KEY_ACTIVITY_DEVELOPMENT -> ActivityStatus.DEVELOPMENT
            KEY_ACTIVITY_QUICK_REACTION -> ActivityStatus.QUICK_REACTION
            KEY_ACTIVITY_VACATION -> ActivityStatus.VACATION
            else -> return
        }
        val date = activityMessageDates[eventId] ?: DateUtils.today()
        val currentStatus = activityService.getUserStatusOn(chatId, senderId, date)

        when (currentStatus) {
            null -> activityService.saveActivity(chatId, senderId, newStatus, date)
            newStatus -> activityService.deleteUserActivityOn(chatId, senderId, date)
            else -> {
                activityService.deleteUserActivityOn(chatId, senderId, date)
                activityService.saveActivity(chatId, senderId, newStatus, date)
            }
        }

        val (text, _) = buildActivityMessage(chatId, date)
        bot.editMessage(chatId, eventId, text)
    }

    // ==================== انتخاب کاندیدا (میرغضب رندوم) ====================
    private fun showCandidateSelection(chatId: String, eventId: String) {
        val members = memberService.getGroupMembers(chatId)
        if (members.isEmpty()) {
            bot.editMessage(chatId, eventId, "⚠️ هنوز عضوی شناسایی نشده است.")
            return
        }
        val key = "$chatId-$eventId"
        candidateSelections[key] = members.keys.toMutableSet()
        bot.editMessage(chatId, eventId, buildCandidateText(chatId, eventId))
        val keys = members.keys.indices.map { ReactionKeys.forIndex(it + 1) } + listOf(KEY_RANDOM_MIRGHAZAB, KEY_CANCEL)
        bot.reactAll(chatId, eventId, keys)
    }

    private fun buildCandidateText(chatId: String, eventId: String): String {
        val members = memberService.getGroupMembers(chatId)
        val orderedUserIds = members.keys.toList()
        val key = "$chatId-$eventId"
        val selectedSet = candidateSelections[key] ?: members.keys.toMutableSet()
        val counts = memberService.getMirGhazabCounts(chatId)
        return buildString {
            appendLine("کیا می‌تونن میرغضب وایستن؟")
            appendLine()
            for ((index, userId) in orderedUserIds.withIndex()) {
                val name = members[userId] ?: continue
                val isSelected = userId in selectedSet
                val count = counts[userId] ?: 0
                val countPersian = NumberUtils.toPersianNumber(count)
                val mark = if (isSelected) "✅" else "❌"
                appendLine("${ReactionKeys.forIndex(index + 1)} $mark $name ($countPersian بار)")
            }
            appendLine()
            appendLine("$KEY_RANDOM_MIRGHAZAB شانسی انتخاب کن   $KEY_CANCEL انصراف")
        }
    }

    private fun handleCandidateReaction(chatId: String, eventId: String, key: String) {
        when (key) {
            KEY_RANDOM_MIRGHAZAB -> performRandomFromCandidates(chatId, eventId)
            KEY_CANCEL -> cancelSelection(chatId, eventId)
            else -> {
                val members = memberService.getGroupMembers(chatId)
                val orderedUserIds = members.keys.toList()
                val targetUserId = orderedUserIds.withIndex()
                    .firstOrNull { (i, _) -> ReactionKeys.forIndex(i + 1) == key }?.value
                if (targetUserId != null) toggleCandidate(chatId, eventId, targetUserId)
            }
        }
    }

    private fun toggleCandidate(chatId: String, eventId: String, userId: String) {
        val key = "$chatId-$eventId"
        val currentSet = candidateSelections[key] ?: return
        if (userId in currentSet) currentSet.remove(userId) else currentSet.add(userId)
        bot.editMessage(chatId, eventId, buildCandidateText(chatId, eventId))
    }

    private fun performRandomFromCandidates(chatId: String, eventId: String) {
        val key = "$chatId-$eventId"
        val candidates = candidateSelections[key]?.toList() ?: emptyList()
        if (candidates.isEmpty()) {
            bot.editMessage(chatId, eventId, "⚠️ هیچ کاندیدی انتخاب نشده است. لطفاً حداقل یک عضو را انتخاب کنید.\n\n" + buildCandidateText(chatId, eventId))
            return
        }
        val randomUserId = candidates.random(Random)
        val members = memberService.getGroupMembers(chatId)
        val name = members[randomUserId] ?: "کاربر"
        memberService.incrementMirGhazabCount(chatId, randomUserId)
        val newCount = memberService.getMirGhazabCounts(chatId)[randomUserId] ?: 0
        val newCountPersian = NumberUtils.toPersianNumber(newCount)
        bot.editMessage(chatId, eventId, "🎭 میرغضب امروز: $name\n\nتا حالا $newCountPersian بار میرغضب شده است.")
        candidateSelections.remove(key)
    }

    private fun cancelSelection(chatId: String, eventId: String) {
        candidateSelections.remove("$chatId-$eventId")
        showMainMenuOnExistingMessage(chatId, eventId)
    }
}
