package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.services.*
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import kotlin.random.Random

class CallbackQueryHandler(
    private val bot: TelegramLongPollingBot,
    private val sessionManager: SessionManager,
    private val activityService: ActivityService,
    private val reportService: ReportService,
    private val memberService: MemberService,
    private val attendanceService: AttendanceService
) {
    private val resetConfirmations = mutableMapOf<String, Long>()
    private val candidateSelections = mutableMapOf<String, MutableSet<Long>>()

    fun buildActivityMessage(chatId: String): Pair<String, InlineKeyboardMarkup> {
        val today = DateUtils.today()
        val persianDate = DateUtils.toPersianDate(today)
        val dateStr = "${NumberUtils.toPersianNumber(persianDate.year)}/${NumberUtils.toPersianNumber(persianDate.month)}/${NumberUtils.toPersianNumber(persianDate.day)}"

        val todayActivities = activityService.getTodayActivities(chatId)
        val members = memberService.getGroupMembers(chatId)

        val messageText = buildString {
            appendLine("📋 *ثبت وضعیت روزانه*")
            appendLine("تاریخ: $dateStr")
            appendLine("")
            appendLine("امروز توسعه بودی یا واکنش سریع؟")
            appendLine("")
            if (todayActivities.isEmpty()) {
                appendLine("📭 هنوز کسی پاسخی نداده است.")
            } else {
                appendLine("*📊 لیست پاسخ‌ها:*")
                for ((userId, status) in todayActivities) {
                    val name = members[userId] ?: "کاربر $userId"
                    val (emoji, statusName) = when (status) {
                        ActivityStatus.DEVELOPMENT -> "🛠️" to "توسعه"
                        ActivityStatus.QUICK_REACTION -> "⚡" to "واکنش سریع"
                        ActivityStatus.VACATION -> "🏖️" to "مرخصی"
                    }
                    appendLine("• $name: $emoji $statusName")
                }
            }
        }

        val devBtn = InlineKeyboardButton().apply {
            text = "🛠️ توسعه"
            callbackData = "activity_development"
        }
        val reactBtn = InlineKeyboardButton().apply {
            text = "⚡ واکنش سریع"
            callbackData = "activity_quick_reaction"
        }
        val vacationBtn = InlineKeyboardButton().apply {
            text = "🏖️ مرخصی"
            callbackData = "activity_vacation"
        }
        val keyboard = InlineKeyboardMarkup(listOf(listOf(devBtn, reactBtn, vacationBtn)))

        return Pair(messageText, keyboard)
    }

    fun handleCallbackQuery(update: Update) {
        val callback = update.callbackQuery
        val chatId = callback.message.chatId.toString()
        val data = callback.data
        val currentMsgId = callback.message.messageId
        val userId = callback.from.id

        when {
            data == "activity_development" || data == "activity_quick_reaction" || data == "activity_vacation" -> {
                val newStatus = when (data) {
                    "activity_development" -> ActivityStatus.DEVELOPMENT
                    "activity_quick_reaction" -> ActivityStatus.QUICK_REACTION
                    else -> ActivityStatus.VACATION
                }

                val currentStatus = activityService.getUserTodayStatus(chatId, userId)

                when (currentStatus) {
                    null -> {
                        activityService.saveDailyActivity(chatId, userId, newStatus)
                    }
                    newStatus -> {
                        activityService.deleteUserTodayActivity(chatId, userId)
                    }
                    else -> {
                        activityService.deleteUserTodayActivity(chatId, userId)
                        activityService.saveDailyActivity(chatId, userId, newStatus)
                    }
                }

                val (text, keyboard) = buildActivityMessage(chatId)

                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = text
                    this.replyMarkup = keyboard
                    this.parseMode = "Markdown"
                }
                bot.execute(edit)

                val answer = AnswerCallbackQuery().apply {
                    this.callbackQueryId = callback.id
                    this.text = when (currentStatus) {
                        null -> "✅ وضعیت شما ثبت شد."
                        newStatus -> "❌ وضعیت شما لغو شد."
                        else -> "✅ وضعیت شما تغییر کرد."
                    }
                    this.showAlert = false
                }
                bot.execute(answer)
            }

            // ==================== ادامه کدهای قبلی ====================
            data == "reset_today_attendance" -> {
                resetConfirmations[chatId] = userId
                val confirmBtn = InlineKeyboardButton().apply {
                    text = "✅ بله، ریست کن"
                    callbackData = "confirm_reset"
                }
                val cancelBtn = InlineKeyboardButton().apply {
                    text = "❌ انصراف"
                    callbackData = "cancel_reset"
                }
                val keyboard = InlineKeyboardMarkup(listOf(listOf(confirmBtn, cancelBtn)))
                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "⚠️ *هشدار!*\n\nآیا مطمئن هستید که می‌خواهید تمام رکوردهای حضور/غیاب امروز را ریست کنید؟\n\nاین عمل غیرقابل بازگشت است."
                    this.replyMarkup = keyboard
                    this.parseMode = "Markdown"
                }
                bot.execute(edit)
            }

            data == "confirm_reset" -> {
                val initiatorId = resetConfirmations[chatId]
                if (initiatorId != userId) {
                    val answer = AnswerCallbackQuery().apply {
                        this.callbackQueryId = callback.id
                        this.text = "⚠️ فقط شخصی که درخواست ریست را داده می‌تواند تأیید کند."
                        this.showAlert = true
                    }
                    bot.execute(answer)
                    return
                }
                attendanceService.resetTodayAttendance(chatId, DateUtils.today())
                sessionManager.resetSession(chatId)
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, currentMsgId)
            }

            data == "cancel_reset" -> {
                resetConfirmations.remove(chatId)
                showMainMenuOnExistingMessage(chatId, currentMsgId)
            }

            data == "overall_activity_report" -> {
                val loadingMsg = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "📊 در حال تولید گزارش وضعیت توسعه/واکنش سریع/مرخصی... لطفاً چند لحظه صبر کنید."
                    this.replyMarkup = null
                }
                bot.execute(loadingMsg)
                val report = reportService.generateOverallActivityReport(chatId)
                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = report
                    this.parseMode = "Markdown"
                    this.replyMarkup = null
                }
                bot.execute(edit)
            }

            data == "reset_and_resend_question" -> {
                activityService.deleteAllTodayActivities(chatId)

                val answer = AnswerCallbackQuery().apply {
                    this.callbackQueryId = callback.id
                    this.text = "✅ پاسخ‌ها پاک شدند. سوال جدید ارسال می‌شود..."
                    this.showAlert = false
                }
                bot.execute(answer)

                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "🔄 پاسخ‌ها پاک شدند. در حال ارسال سوال جدید..."
                    this.replyMarkup = null
                }
                bot.execute(edit)

                val (text, keyboard) = buildActivityMessage(chatId)
                val msg = org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId, text)
                msg.replyMarkup = keyboard
                msg.parseMode = "Markdown"
                bot.execute(msg)
            }

            data == "random_mirghazab_menu" -> showCandidateSelection(chatId, currentMsgId)
            data == "daily_report" -> {
                val hasAttendanceToday = attendanceService.hasAttendanceToday(chatId, DateUtils.today())
                if (hasAttendanceToday) {
                    val answer = AnswerCallbackQuery().apply {
                        this.callbackQueryId = callback.id
                        this.text = "⚠️ گزارش جلسه امروز قبلاً ثبت شده است.\nبرای ثبت مجدد ابتدا از دکمه «حذف گزارش جلسه روزانه امروز» استفاده کنید."
                        this.showAlert = true
                    }
                    bot.execute(answer)
                    return
                }

                val activeSession = sessionManager.getActiveSession(chatId)
                if (activeSession != null) {
                    val answer = AnswerCallbackQuery().apply {
                        this.callbackQueryId = callback.id
                        this.text = "⚠️ در حال حاضر یک جلسه گزارش در این گروه در حال اجراست.\nلطفاً ابتدا آن را تکمیل کنید."
                        this.showAlert = true
                    }
                    bot.execute(answer)
                    return
                }

                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "🔄 شروع فرایند گزارش جلسه..."
                    this.replyMarkup = null
                }
                bot.execute(edit)
                sessionManager.startSession(chatId, currentMsgId, userId)
            }
            data == "weekly_report_now" -> {
                val loadingMsg = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = "📊 در حال تولید گزارش هفتگی... لطفاً چند لحظه صبر کنید."
                    this.replyMarkup = null
                }
                bot.execute(loadingMsg)
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
                val fullMessage = "$title\n\n$report"
                val edit = EditMessageText().apply {
                    this.chatId = chatId
                    this.messageId = currentMsgId
                    this.text = fullMessage
                    this.parseMode = "Markdown"
                    this.replyMarkup = null
                }
                bot.execute(edit)
            }
            data.startsWith("toggle_cand_") -> {
                val uid = data.removePrefix("toggle_cand_").toLong()
                toggleCandidate(chatId, currentMsgId, uid)
            }
            data == "do_random_from_candidates" -> performRandomFromCandidates(chatId, currentMsgId)
            data == "cancel_candidate_selection" -> cancelSelection(chatId, currentMsgId)
            else -> {
                val session = sessionManager.getActiveSession(chatId)
                if (session == null || session.messageId != currentMsgId) return

                if (userId != session.initiatorUserId) {
                    val answer = AnswerCallbackQuery().apply {
                        this.callbackQueryId = callback.id
                        this.text = "⚠️ فقط شخصی که جلسه را شروع کرده می‌تواند روی دکمه‌ها کلیک کند."
                        this.showAlert = false
                    }
                    bot.execute(answer)
                    return
                }

                when {
                    data.startsWith("user_") -> {
                        val uid = data.removePrefix("user_").toLong()
                        sessionManager.toggleUserStatus(chatId, uid)
                    }
                    data == "done_present" -> {
                        sessionManager.advanceSession(chatId)
                    }
                    data == "done_final" -> {
                        sessionManager.finishSession(chatId)
                    }
                }
            }
        }
    }

    // ==================== توابع کمکی (بدون تغییر) ====================
    private fun showCandidateSelection(chatId: String, messageId: Int) {
        val members = memberService.getGroupMembers(chatId)
        if (members.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ هنوز عضوی شناسایی نشده است."
                this.replyMarkup = null
            }
            bot.execute(edit)
            return
        }
        val key = "$chatId-$messageId"
        candidateSelections[key] = members.keys.toMutableSet()
        val keyboard = buildCandidateKeyboard(chatId, messageId)
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "کیا می‌تونن میرغضب وایستن؟"
            this.replyMarkup = keyboard
        }
        bot.execute(edit)
    }

    private fun buildCandidateKeyboard(chatId: String, messageId: Int): InlineKeyboardMarkup {
        val members = memberService.getGroupMembers(chatId)
        val key = "$chatId-$messageId"
        val selectedSet = candidateSelections[key] ?: members.keys.toMutableSet()
        val counts = memberService.getMirGhazabCounts(chatId)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for ((userId, name) in members) {
            val isSelected = userId in selectedSet
            val count = counts[userId] ?: 0
            val countPersian = NumberUtils.toPersianNumber(count)
            val buttonText = "${if (isSelected) "✅" else "❌"} $name ($countPersian بار)"
            val button = InlineKeyboardButton().apply {
                this.text = buttonText
                callbackData = "toggle_cand_$userId"
            }
            rows.add(listOf(button))
        }
        val randomBtn = InlineKeyboardButton().apply {
            text = "🎲 شانسی انتخاب کن"
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
        bot.execute(edit)
    }

    private fun performRandomFromCandidates(chatId: String, messageId: Int) {
        val key = "$chatId-$messageId"
        val candidates = candidateSelections[key]?.toList() ?: emptyList()
        if (candidates.isEmpty()) {
            val edit = EditMessageText().apply {
                this.chatId = chatId
                this.messageId = messageId
                this.text = "⚠️ هیچکس انتخاب نشده است.ً حداقل یک نفر را انتخاب کن"
                this.replyMarkup = buildCandidateKeyboard(chatId, messageId)
            }
            bot.execute(edit)
            return
        }
        val randomUserId = candidates.random(Random)
        val members = memberService.getGroupMembers(chatId)
        val name = members[randomUserId] ?: "کاربر"
        memberService.incrementMirGhazabCount(chatId, randomUserId)
        val newCount = memberService.getMirGhazabCounts(chatId)[randomUserId] ?: 0
        val newCountPersian = NumberUtils.toPersianNumber(newCount)
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "🎭 میرغضب امروز: *$name*\n\nتا حالا $newCountPersian بار میرغضب شده است."
            this.replyMarkup = null
            this.parseMode = "Markdown"
        }
        bot.execute(edit)
        candidateSelections.remove(key)
    }

    private fun cancelSelection(chatId: String, messageId: Int) {
        candidateSelections.remove("$chatId-$messageId")
        showMainMenuOnExistingMessage(chatId, messageId)
    }

    private fun showMainMenuOnExistingMessage(chatId: String, messageId: Int) {
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
            text = "📊 مشاهده وضعیت توسعه/واکنش سریع/مرخصی"
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
        val edit = EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            this.text = "انتخاب کن:"
            this.replyMarkup = keyboard
        }
        bot.execute(edit)
    }
}