package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.models.UserAttendance
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils

class ReportService(
    private val attendanceService: AttendanceService,
    private val activityService: ActivityService,
    private val memberService: MemberService
) {

    fun generateOverallActivityReport(chatId: String): String {
        val records = activityService.getAllUserActivities(chatId)
        if (records.isEmpty()) {
            return "📊 *گزارش وضعیت توسعه/واکنش سریع/مرخصی*\n\nهیچ داده‌ای یافت نشد."
        }

        val members = memberService.getGroupMembers(chatId)
        val userStats = mutableMapOf<String, MutableMap<ActivityStatus, Int>>()

        for (rec in records) {
            val stats = userStats.getOrPut(rec.userId) { mutableMapOf() }
            stats[rec.status] = stats.getOrDefault(rec.status, 0) + 1
        }

        val sortedUsers = userStats.entries
            .map { entry ->
                val userId = entry.key
                val stats = entry.value
                val name = members[userId] ?: "کاربر $userId"
                val devCount = stats[ActivityStatus.DEVELOPMENT] ?: 0
                val reactCount = stats[ActivityStatus.QUICK_REACTION] ?: 0
                val vacationCount = stats[ActivityStatus.VACATION] ?: 0
                Quad(name, devCount, reactCount, vacationCount)
            }
            .sortedWith(compareBy { it.name })

        val totalDevUsers = sortedUsers.count { it.devCount > 0 }
        val totalReactUsers = sortedUsers.count { it.reactCount > 0 }
        val totalVacationUsers = sortedUsers.count { it.vacationCount > 0 }

        return buildString {
            appendLine("#اعلام_وضعیت: توسعه/واکنش سریع")
            appendLine("📊 *گزارش وضعیت توسعه/واکنش سریع/مرخصی*")
            appendLine("")
            appendLine("*🔹 به‌ازای هر کاربر:*")
            appendLine("")
            for ((name, devCount, reactCount, vacationCount) in sortedUsers) {
                appendLine("👤 $name: 🛠️ ${NumberUtils.toPersianNumber(devCount)} بار | ⚡ ${NumberUtils.toPersianNumber(reactCount)} بار | 🏖️ ${NumberUtils.toPersianNumber(vacationCount)} بار")
            }
            appendLine("")
            appendLine("*🔹 جمع کل:*")
            appendLine("🛠️ تعداد کاربران با حداقل یک «توسعه»: ${NumberUtils.toPersianNumber(totalDevUsers)} نفر")
            appendLine("⚡ تعداد کاربران با حداقل یک «واکنش سریع»: ${NumberUtils.toPersianNumber(totalReactUsers)} نفر")
            appendLine("🏖️ تعداد کاربران با حداقل یک «مرخصی»: ${NumberUtils.toPersianNumber(totalVacationUsers)} نفر")
        }
    }

    // ==================== گزارش روزانه ترکیبی ====================
    fun generateDailyReport(chatId: String): String {
        val yesterday = DateUtils.yesterday()
        val persianDate = DateUtils.toPersianDate(yesterday)
        val members = memberService.getGroupMembers(chatId)
        val totalMembers = members.size

        val activityStats = activityService.getActivityStats(chatId, yesterday)
        val devCount = activityStats[ActivityStatus.DEVELOPMENT] ?: 0
        val reactCount = activityStats[ActivityStatus.QUICK_REACTION] ?: 0
        val vacationCount = activityStats[ActivityStatus.VACATION] ?: 0
        val responded = devCount + reactCount + vacationCount

        val attendanceRecords = attendanceService.getAttendanceRecords(chatId, yesterday)
        var presentCount = 0
        var excusedCount = 0
        var unexcusedCount = 0
        val presentNames = mutableListOf<String>()
        val excusedNames = mutableListOf<String>()
        val unexcusedNames = mutableListOf<String>()

        for (rec in attendanceRecords) {
            val name = members[rec.userId] ?: "کاربر ${rec.userId}"
            when (rec.status) {
                AttendanceStatus.PRESENT -> {
                    presentCount++
                    presentNames.add(name)
                }
                AttendanceStatus.ABSENT_EXCUSED -> {
                    excusedCount++
                    excusedNames.add(name)
                }
                AttendanceStatus.ABSENT_UNEXCUSED -> {
                    unexcusedCount++
                    unexcusedNames.add(name)
                }
                else -> {}
            }
        }

        val dateStr = "${NumberUtils.toPersianNumber(persianDate.year)}/${NumberUtils.toPersianNumber(persianDate.month)}/${NumberUtils.toPersianNumber(persianDate.day)}"
        return buildString {
            appendLine("📊 *گزارش روزانه*")
            appendLine("تاریخ: $dateStr")
            appendLine("")
            appendLine("*📋 گزارش وضعیت روزانه:*")
            appendLine("")
            appendLine("🛠️ توسعه: ${NumberUtils.toPersianNumber(devCount)} نفر")
            appendLine("⚡ واکنش سریع: ${NumberUtils.toPersianNumber(reactCount)} نفر")
            appendLine("🏖️ مرخصی: ${NumberUtils.toPersianNumber(vacationCount)} نفر")
            appendLine("")
            appendLine("❌ پاسخ ندادند: ${NumberUtils.toPersianNumber(totalMembers - responded)} نفر")
            appendLine("")
        }
    }

    fun generateWeeklyReport(chatId: String, startDate: java.time.LocalDate, endDate: java.time.LocalDate): String {
        val records = attendanceService.getAttendanceReport(chatId, startDate, endDate)
        if (records.isEmpty()) {
            return "هیچ داده‌ای برای این گروه در بازه مورد نظر وجود ندارد."
        }

        val members = memberService.getGroupMembers(chatId)
        val userStats = mutableMapOf<String, MutableMap<AttendanceStatus, Int>>()
        for (rec in records) {
            val stats = userStats.getOrPut(rec.userId) { mutableMapOf() }
            stats[rec.status] = stats.getOrDefault(rec.status, 0) + 1
        }

        val sortedUsers = userStats.entries
            .map { entry ->
                val userId = entry.key
                val stats = entry.value
                val name = members[userId] ?: "کاربر $userId"
                val present = stats[AttendanceStatus.PRESENT] ?: 0
                val excused = stats[AttendanceStatus.ABSENT_EXCUSED] ?: 0
                val unexcused = stats[AttendanceStatus.ABSENT_UNEXCUSED] ?: 0
                UserAttendance(name, present, excused, unexcused)
            }
            .sortedWith(
                compareByDescending<UserAttendance> { it.present }
                    .thenByDescending { it.excused }
                    .thenBy { it.name }
            )

        val report = StringBuilder()
        for (user in sortedUsers) {
            val presentPersian = NumberUtils.toPersianNumber(user.present)
            val excusedPersian = NumberUtils.toPersianNumber(user.excused)
            val unexcusedPersian = NumberUtils.toPersianNumber(user.unexcused)
            report.appendLine("👤 ${user.name}: ✅ $presentPersian بار | 🔔 $excusedPersian بار | 🔕 $unexcusedPersian بار")
        }
        return report.toString()
    }

    private data class Quad(
        val name: String,
        val devCount: Int,
        val reactCount: Int,
        val vacationCount: Int
    )
}