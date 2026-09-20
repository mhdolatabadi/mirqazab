package ir.mhdolatabadi.handlers

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.matrix.MatrixLongPollingBot
import ir.mhdolatabadi.services.ActivityService
import ir.mhdolatabadi.services.MemberService
import ir.mhdolatabadi.utils.DateUtils
import ir.mhdolatabadi.utils.NumberUtils
import java.time.LocalDate

/** The daily "توسعه بودی یا واکنش سریع؟" question and its development/quick-reaction/vacation replies. */
class ActivityQuestionHandler(
    private val bot: MatrixLongPollingBot,
    private val activityService: ActivityService,
    private val memberService: MemberService
) {
    private val activityMessageDates = mutableMapOf<String, LocalDate>() // eventId -> date

    fun isActivityMessage(eventId: String): Boolean = activityMessageDates.containsKey(eventId)

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

    fun handleReaction(chatId: String, senderId: String, eventId: String, key: String) {
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
}
