package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.GroupMember
import jakarta.persistence.EntityManagerFactory

class MemberService(private val emf: EntityManagerFactory) {

    fun getGroupMembers(chatId: String): Map<String, String> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId).resultList
            return members.associate { it.userId to it.name }
        }
    }

    fun getAllChatIds(): List<String> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            return em.createQuery("SELECT DISTINCT m.chatId FROM GroupMember m", String::class.java).resultList
        }
    }

    fun getMirGhazabCounts(chatId: String): Map<String, Int> {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val members = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
                GroupMember::class.java
            ).setParameter("chatId", chatId).resultList
            return members.associate { it.userId to it.mirGhazabCount }
        }
    }

    fun incrementMirGhazabCount(chatId: String, userId: String) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val member = em.createQuery(
                "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
                GroupMember::class.java
            ).setParameter("chatId", chatId)
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

    fun recordUserActivity(chatId: String, userId: String, displayName: String) {
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
            }
            em.transaction.commit()
        } catch (e: Exception) {
            em.transaction.rollback()
            throw e
        } finally {
            em.close()
        }
    }

}
