package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.GroupMember
import jakarta.persistence.EntityManagerFactory

class MemberService(private val emf: EntityManagerFactory) {

    fun getGroupMembers(chatId: String): Map<Long, String> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong()).resultList
            return members.associate { it.userId to it.name }
        }
    }

    fun getAllChatIds(): List<Long> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("SELECT DISTINCT m.chatId FROM GroupMember m", Long::class.java).resultList
        } finally {
            em.close()
        }
    }

    fun getMirGhazabCounts(chatId: String): Map<Long, Int> {
        val em = emf.createEntityManager()
        try {
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong()).resultList
            return members.associate { it.userId to it.mirGhazabCount }
        } finally {
            em.close()
        }
    }

    fun incrementMirGhazabCount(chatId: String, userId: Long) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .singleResult
            member.mirGhazabCount++
            em.merge(member)
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

    fun recordUserActivity(chatId: Long, userId: Long, displayName: String) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId)
                .setParameter("userId", userId)
                .resultList.firstOrNull()

            if (member == null) {
                val newMember = GroupMember(
                    chatId = chatId,
                    userId = userId,
                    name = displayName,
                    mirGhazabCount = 0
                )
                em.persist(newMember)
            } else {
                if (member.name != displayName) {
                    member.name = displayName
                    em.merge(member)
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

    fun getMemberName(chatId: String, userId: Long): String? {
        val em = emf.createEntityManager()
        try {
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId.toLong())
                .setParameter("userId", userId)
                .resultList.firstOrNull()
            return member?.name
        } finally {
            em.close()
        }
    }
}