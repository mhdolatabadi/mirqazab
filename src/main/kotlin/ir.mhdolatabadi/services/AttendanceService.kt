package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.AttendanceStatus
import ir.mhdolatabadi.entities.AttendanceRecord
import ir.mhdolatabadi.models.UserInfo
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class AttendanceService(private val emf: EntityManagerFactory) {
    fun saveAttendanceRecords(chatId: String, users: Map<String, UserInfo>, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            for ((userId, userInfo) in users) {
                if (userInfo.status != AttendanceStatus.UNKNOWN) {
                    val record = AttendanceRecord(
                        userId = userId,
                        chatId = chatId,
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
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val count = em.createQuery(
                "SELECT COUNT(a) FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
                Long::class.java
            ).setParameter("chatId", chatId)
                .setParameter("date", date)
                .singleResult
            return count > 0
        }
    }

    fun getAttendanceRecords(chatId: String, date: LocalDate): List<AttendanceRecord> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery(
                "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date",
                AttendanceRecord::class.java
            ).setParameter("chatId", chatId)
                .setParameter("date", date)
                .resultList
        }
    }

    fun resetTodayAttendance(chatId: String, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            em.createQuery(
                "DELETE FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date = :date"
            ).setParameter("chatId", chatId)
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
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery(
                "SELECT a FROM AttendanceRecord a WHERE a.chatId = :chatId AND a.date BETWEEN :start AND :end ORDER BY a.userId, a.date",
                AttendanceRecord::class.java
            ).setParameter("chatId", chatId)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .resultList
        }
    }

    fun getDistinctChatIds(): List<String> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery("SELECT DISTINCT a.chatId FROM AttendanceRecord a", String::class.java).resultList
        }
    }
}
