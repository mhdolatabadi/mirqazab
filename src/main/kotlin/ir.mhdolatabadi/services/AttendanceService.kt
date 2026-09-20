package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.entities.AttendanceRecord
import ir.mhdolatabadi.models.UserInfo
import ir.mhdolatabadi.utils.readOnly
import ir.mhdolatabadi.utils.transaction
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class AttendanceService(private val emf: EntityManagerFactory) {

    fun saveAttendanceRecords(chatId: String, users: Map<String, UserInfo>, date: LocalDate) = emf.transaction { em ->
        for ((userId, userInfo) in users) {
            if (userInfo.status != AttendanceStatus.UNKNOWN) {
                em.persist(
                    AttendanceRecord(
                        userId = userId,
                        chatId = chatId,
                        date = date,
                        status = userInfo.status
                    )
                )
            }
        }
    }

    fun hasAttendanceToday(chatId: String, date: LocalDate): Boolean = emf.readOnly { em ->
        val count = em.createQuery(
            "SELECT COUNT(a) FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
            Long::class.java
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .singleResult
        count > 0
    }

    fun getAttendanceRecords(chatId: String, date: LocalDate): List<AttendanceRecord> = emf.readOnly { em ->
        em.createQuery(
            "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
            AttendanceRecord::class.java
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .resultList
    }

    fun resetTodayAttendance(chatId: String, date: LocalDate) = emf.transaction { em ->
        em.createQuery(
            "DELETE FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date"
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .executeUpdate()
        Unit
    }

    fun getAttendanceReport(chatId: String, startDate: LocalDate, endDate: LocalDate): List<AttendanceRecord> = emf.readOnly { em ->
        em.createQuery(
            "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date BETWEEN :start AND :end ORDER BY a.userId, a.date",
            AttendanceRecord::class.java
        ).setParameter("chatId", chatId)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .resultList
    }

    fun getDistinctChatIds(): List<String> = emf.readOnly { em ->
        em.createQuery("SELECT DISTINCT a.chatId FROM AttendanceRecord a", String::class.java).resultList
    }
}
