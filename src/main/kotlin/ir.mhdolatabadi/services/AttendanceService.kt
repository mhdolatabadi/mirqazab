package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.entities.AttendanceRecord
import ir.mhdolatabadi.models.UserInfo
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class AttendanceService(private val emf: EntityManagerFactory) {
    fun saveAttendanceRecords(chatId: String, users: Map<Long, UserInfo>, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            for ((userId, userInfo) in users) {
                if (userInfo.status != AttendanceStatus.UNKNOWN) {
                    val record = AttendanceRecord(
                        userId = userId,
                        chatId = chatId.toLong(),
                        date = date,
                        status = userInfo.status
                    )
                    em.persist(record)
                }
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun hasAttendanceToday(chatId: String, date: LocalDate): Boolean {
        val em = emf.createEntityManager()
        try {
            val count = em.createQuery(
                "SELECT COUNT(a) FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
                Long::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", date)
                .singleResult
            return count > 0
        } finally {
            em.close()
        }
    }

    fun getAttendanceRecords(chatId: String, date: LocalDate): List<AttendanceRecord> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery(
                "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
                AttendanceRecord::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", date)
                .resultList
        } finally {
            em.close()
        }
    }

    fun resetTodayAttendance(chatId: String, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            em.createQuery(
                "DELETE FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date"
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", date)
                .executeUpdate()
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun getAttendanceReport(chatId: String, startDate: LocalDate, endDate: LocalDate): List<AttendanceRecord> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery(
                "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date BETWEEN :start AND :end ORDER BY a.userId, a.date",
                AttendanceRecord::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .resultList
        } finally {
            em.close()
        }
    }

    fun getDistinctChatIds(): List<Long> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("SELECT DISTINCT a.chatId FROM AttendanceRecord a", Long::class.java).resultList
        } finally {
            em.close()
        }
    }
}