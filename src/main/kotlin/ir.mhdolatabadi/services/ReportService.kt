package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.models.UserAttendance
import ir.mhdolatabadi.utils.NumberUtils
import ir.mhdolatabadi.utils.DateUtils
import jakarta.persistence.EntityManagerFactory

class ReportService(
    private val emf: EntityManagerFactory,
    private val attendanceService: AttendanceService,
    private val activityService: ActivityService,
    private val memberService: MemberService
) {
    fun generateOverallActivityReport(chatId: String): String {
        val records = activityService.getAllUserActivities(chatId)
        if (records.isEmpty()) {
            return "📊 **گزارش وضعیت توسعه/واکنش سریع**\n\nهیچ داده‌ای یافت نشد."
        }

        val members = memberService.getGroupMembers(chatId)
        val userStats = mutableMapOf<Long, Pair<Int, Int>>()

        for (rec in records) {
            val current = userStats.getOrPut(rec.userId) { 0 to 0 }
            when (rec.status) {
                ActivityStatus.DEVELOPMENT -> userStats[rec.userId] = current.first + 1 to current.second
                ActivityStatus.QUICK_REACTION -> userStats[rec.userId] = current.first to current.second + 1
            }
        }

        val sortedUsers = userStats.entries
            .map { entry ->
                val userId = entry.key
                val (devCount, reactCount) = entry.value
                val name = members[userId] ?: "کاربر $userId"
                Triple(name, devCount, reactCount)
            }
            .sortedBy { it.first }

        val totalDevDays = userStats.values.sumOf { it.first }
        val totalReactDays = userStats.values.sumOf { it.second }

        return buildString {
            appendLine("📊 **گزارش وضعیت توسعه/واکنش سریع**")
            appendLine("")
            appendLine("**🔹 به‌ازای هر کاربر:**")
            appendLine("")
            for ((name, devCount, reactCount) in sortedUsers) {
                appendLine("👤 $name: 🛠️ ${NumberUtils.toPersianNumber(devCount)} بار | ⚡ ${NumberUtils.toPersianNumber(reactCount)} بار")
            }
            appendLine("")
            appendLine("**🔹 جمع کل:**")
            appendLine("🛠️ تعداد روزهای «توسعه»: ${NumberUtils.toPersianNumber(totalDevDays)} روز")
            appendLine("⚡ تعداد روزهای «واکنش سریع»: ${NumberUtils.toPersianNumber(totalReactDays)} روز")
        }
    }

    fun generateDailyReport(chatId: String): String {
        val yesterday = DateUtils.yesterday()
        val persianDate = DateUtils.toPersianDate(yesterday)
        val members = memberService.getGroupMembers(chatId)
        val totalMembers = members.size

        val activityStats = activityService.getActivityStats(chatId, yesterday)
        val devCount = activityStats[ActivityStatus.DEVELOPMENT] ?: 0
        val reactCount = activityStats[ActivityStatus.QUICK_REACTION] ?: 0
        val responded = devCount + reactCount

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
            appendLine("━━━━━━━━━━━━━━━━━━")
            appendLine("*📋 وضعیت واکنش سریع / توسعه:*")
            appendLine("")
            appendLine("🛠️ توسعه: ${NumberUtils.toPersianNumber(devCount)} نفر")
            appendLine("⚡ واکنش سریع: ${NumberUtils.toPersianNumber(reactCount)} نفر")
            appendLine("")
            val unresponded = totalMembers - responded
            appendLine("❌ پاسخ ندادند: ${NumberUtils.toPersianNumber(unresponded)} نفر")
            appendLine("")
            appendLine("━━━━━━━━━━━━━━━━━━")
            appendLine("**📋 گزارش جلسه روزانه:**")
            appendLine("")
            appendLine("🫡 حاضرین (${NumberUtils.toPersianNumber(presentCount)} نفر):")
            if (presentNames.isEmpty()) appendLine("هیچ‌کس") else presentNames.forEach { appendLine("- $it") }
            appendLine("")
            appendLine("🔔 غایب موجه (${NumberUtils.toPersianNumber(excusedCount)} نفر):")
            if (excusedNames.isEmpty()) appendLine("هیچ‌کس") else excusedNames.forEach { appendLine("- $it") }
            appendLine("")
            appendLine("🔕 غایب غیرموجه (${NumberUtils.toPersianNumber(unexcusedCount)} نفر):")
            if (unexcusedNames.isEmpty()) appendLine("هیچ‌کس") else unexcusedNames.forEach { appendLine("- $it") }
            appendLine("")
            val totalAttended = presentCount + excusedCount
            appendLine("وضعیت: ${NumberUtils.toPersianNumber(totalAttended)}/${NumberUtils.toPersianNumber(totalMembers)}")
        }
    }

    fun generateWeeklyReport(chatId: String, startDate: java.time.LocalDate, endDate: java.time.LocalDate): String {
        val records = attendanceService.getAttendanceReport(chatId, startDate, endDate)
        if (records.isEmpty()) {
            return "هیچ داده‌ای برای این گروه در بازه مورد نظر وجود ندارد."
        }

        val members = memberService.getGroupMembers(chatId)
        val userStats = mutableMapOf<Long, MutableMap<AttendanceStatus, Int>>()
        for (rec in records) {
            val stats = userStats.getOrPut(rec.userId) { mutableMapOf() }
            stats[rec.status] = stats.getOrDefault(rec.status, 0) + 1
        }

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
            val presentPersian = NumberUtils.toPersianNumber(user.present)
            val excusedPersian = NumberUtils.toPersianNumber(user.excused)
            val unexcusedPersian = NumberUtils.toPersianNumber(user.unexcused)
            report.appendLine("👤 ${user.name}: ✅ $presentPersian بار | 🔔 $excusedPersian بار | 🔕 $unexcusedPersian بار")
        }
        return report.toString()
    }
}