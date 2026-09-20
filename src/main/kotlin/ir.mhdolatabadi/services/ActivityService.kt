package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.entities.DailyActivity
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class ActivityService(private val emf: EntityManagerFactory) {

    fun deleteAllTodayActivities(chatId: String) {
        deleteAllActivitiesOn(chatId, LocalDate.now())
    }

    fun saveActivity(chatId: String, userId: String, status: ActivityStatus, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val existing = em.createQuery(
                "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
                DailyActivity::class.java
            ).setParameter("chatId", chatId)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .resultList.firstOrNull()

            if (existing == null) {
                val activity = DailyActivity(
                    userId = userId,
                    chatId = chatId,
                    date = date,
                    status = status
                )
                em.persist(activity)
            } else {
                // اگر قبلاً وجود داشت، به‌روزرسانی (تغییر وضعیت)
                existing.status = status
                em.merge(existing)
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun getUserStatusOn(chatId: String, userId: String, date: LocalDate): ActivityStatus? {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val result = em.createQuery(
                "SELECT a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
                ActivityStatus::class.java
            ).setParameter("chatId", chatId)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .resultList.firstOrNull()
            return result
        }
    }

    fun deleteUserActivityOn(chatId: String, userId: String, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            em.createQuery(
                "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date"
            ).setParameter("chatId", chatId)
                .setParameter("userId", userId)
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

    fun getActivitiesOn(chatId: String, date: LocalDate): Map<String, ActivityStatus> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val results = em.createQuery(
                "SELECT a.userId, a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
                Array<Any>::class.java
            ).setParameter("chatId", chatId)
                .setParameter("date", date)
                .resultList
            return results.associate { it[0] as String to it[1] as ActivityStatus }
        }
    }

    fun hasActivityOn(chatId: String, date: LocalDate): Boolean {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val count = em.createQuery(
                "SELECT COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
                Long::class.java
            ).setParameter("chatId", chatId)
                .setParameter("date", date)
                .singleResult
            return count > 0
        }
    }

    fun deleteAllActivitiesOn(chatId: String, date: LocalDate) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            em.createQuery(
                "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date"
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

    fun getAllUserActivities(chatId: String): List<DailyActivity> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery(
                "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId ORDER BY a.userId, a.date",
                DailyActivity::class.java
            ).setParameter("chatId", chatId)
                .resultList
        }
    }

    fun getActivityStats(chatId: String, date: LocalDate): Map<ActivityStatus, Long> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val results = em.createQuery(
                "SELECT a.status, COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date GROUP BY a.status",
                Array<Any>::class.java
            ).setParameter("chatId", chatId)
                .setParameter("date", date)
                .resultList

            val stats = mutableMapOf<ActivityStatus, Long>()
            for (row in results) {
                val status = row[0] as ActivityStatus
                val count = row[1] as Long
                stats[status] = count
            }
            return stats
        }
    }
}
