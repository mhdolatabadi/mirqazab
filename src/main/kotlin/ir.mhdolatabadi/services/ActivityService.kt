package ir.mhdolatabadi.services

import ir.mhdolatabadi.enums.ActivityStatus
import ir.mhdolatabadi.entities.DailyActivity
import ir.mhdolatabadi.utils.readOnly
import ir.mhdolatabadi.utils.transaction
import jakarta.persistence.EntityManagerFactory
import java.time.LocalDate

class ActivityService(private val emf: EntityManagerFactory) {

    fun deleteAllTodayActivities(chatId: String) {
        deleteAllActivitiesOn(chatId, LocalDate.now())
    }

    fun saveActivity(chatId: String, userId: String, status: ActivityStatus, date: LocalDate) = emf.transaction { em ->
        val existing = em.createQuery(
            "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
            DailyActivity::class.java
        ).setParameter("chatId", chatId)
            .setParameter("userId", userId)
            .setParameter("date", date)
            .resultList.firstOrNull()

        if (existing == null) {
            em.persist(DailyActivity(userId = userId, chatId = chatId, date = date, status = status))
        } else {
            existing.status = status
            em.merge(existing)
        }
        Unit
    }

    fun getUserStatusOn(chatId: String, userId: String, date: LocalDate): ActivityStatus? = emf.readOnly { em ->
        em.createQuery(
            "SELECT a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date",
            ActivityStatus::class.java
        ).setParameter("chatId", chatId)
            .setParameter("userId", userId)
            .setParameter("date", date)
            .resultList.firstOrNull()
    }

    fun deleteUserActivityOn(chatId: String, userId: String, date: LocalDate) = emf.transaction { em ->
        em.createQuery(
            "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.userId = :userId AND a.date = :date"
        ).setParameter("chatId", chatId)
            .setParameter("userId", userId)
            .setParameter("date", date)
            .executeUpdate()
        Unit
    }

    fun getActivitiesOn(chatId: String, date: LocalDate): Map<String, ActivityStatus> = emf.readOnly { em ->
        val results = em.createQuery(
            "SELECT a.userId, a.status FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
            Array<Any>::class.java
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .resultList
        results.associate { it[0] as String to it[1] as ActivityStatus }
    }

    fun hasActivityOn(chatId: String, date: LocalDate): Boolean = emf.readOnly { em ->
        val count = em.createQuery(
            "SELECT COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date",
            Long::class.java
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .singleResult
        count > 0
    }

    fun deleteAllActivitiesOn(chatId: String, date: LocalDate) = emf.transaction { em ->
        em.createQuery(
            "DELETE FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date"
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .executeUpdate()
        Unit
    }

    fun getAllUserActivities(chatId: String): List<DailyActivity> = emf.readOnly { em ->
        em.createQuery(
            "SELECT a FROM DailyActivity a WHERE a.chatId = :chatId ORDER BY a.userId, a.date",
            DailyActivity::class.java
        ).setParameter("chatId", chatId).resultList
    }

    fun getActivityStats(chatId: String, date: LocalDate): Map<ActivityStatus, Long> = emf.readOnly { em ->
        val results = em.createQuery(
            "SELECT a.status, COUNT(a) FROM DailyActivity a WHERE a.chatId = :chatId AND a.date = :date GROUP BY a.status",
            Array<Any>::class.java
        ).setParameter("chatId", chatId)
            .setParameter("date", date)
            .resultList

        val stats = mutableMapOf<ActivityStatus, Long>()
        for (row in results) {
            stats[row[0] as ActivityStatus] = row[1] as Long
        }
        stats
    }
}
