package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.entities.DailyActivity
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class ActivityService(private val emf: EntityManagerFactory) {

    fun saveDailyActivity(chatId: String, userId: Long, status: ActivityStatus) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val today = LocalDate.now()
            val existing = em.createQuery(
                "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
                DailyActivity::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .setParameter("date", today)
                .resultList.firstOrNull()

            if (existing == null) {
                val activity = DailyActivity(
                    userId = userId,
                    chatId = chatId.toLong(),
                    date = today,
                    status = status
                )
                em.persist(activity)
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun hasActivityToday(chatId: String, date: LocalDate): Boolean {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val count = em.createQuery(
                "SELECT COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
                Long::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", date)
                .singleResult
            return count > 0
        }
    }

    fun getUserTodayStatus(chatId: String, userId: Long): ActivityStatus? {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val today = LocalDate.now()
            val result = em.createQuery(
                "SELECT a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
                ActivityStatus::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .setParameter("date", today)
                .resultList.firstOrNull()
            return result
        }
    }

    fun deleteUserTodayActivity(chatId: String, userId: Long) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val today = LocalDate.now()
            em.createQuery(
                "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date"
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .setParameter("date", today)
                .executeUpdate()
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun deleteAllTodayActivities(chatId: String) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val today = LocalDate.now()
            em.createQuery(
                "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date"
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", today)
                .executeUpdate()
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun getTodayActivities(chatId: String): Map<Long, ActivityStatus> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val today = LocalDate.now()
            val results = em.createQuery(
                "SELECT a.userId, a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
                Array<Any>::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("date", today)
                .resultList
            return results.associate { it[0] as Long to it[1] as ActivityStatus }
        }
    }

    fun getActivityStats(chatId: String, date: LocalDate): Map<ActivityStatus, Long> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val results = em.createQuery(
                "SELECT a.status, COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date GROUP BY a.status",
                Array<Any>::class.java
            ).setParameter("chatId", chatId.toLong())
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

    fun getAllUserActivities(chatId: String): List<DailyActivity> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery(
                "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId ORDER BY a.userId, a.date",
                DailyActivity::class.java
            ).setParameter("chatId", chatId.toLong())
                .resultList
        }
    }
}