package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.GroupMember
import ir.mhdolatabadi.utils.readOnly
import ir.mhdolatabadi.utils.transaction
import jakarta.persistence.EntityManagerFactory

class MemberService(private val emf: EntityManagerFactory) {

    fun getGroupMembers(chatId: String): Map<String, String> = emf.readOnly { em ->
        val members = em.createQuery(
            "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
            GroupMember::class.java
        ).setParameter("chatId", chatId).resultList
        members.associate { it.userId to it.name }
    }

    fun getAllChatIds(): List<String> = emf.readOnly { em ->
        em.createQuery("SELECT DISTINCT m.chatId FROM GroupMember m", String::class.java).resultList
    }

    fun getMirGhazabCounts(chatId: String): Map<String, Int> = emf.readOnly { em ->
        val members = em.createQuery(
            "SELECT m FROM GroupMember m WHERE m.chatId = :chatId",
            GroupMember::class.java
        ).setParameter("chatId", chatId).resultList
        members.associate { it.userId to it.mirGhazabCount }
    }

    fun incrementMirGhazabCount(chatId: String, userId: String) = emf.transaction { em ->
        val member = em.createQuery(
            "SELECT m FROM GroupMember m WHERE m.chatId = :chatId AND m.userId = :userId",
            GroupMember::class.java
        ).setParameter("chatId", chatId)
            .setParameter("userId", userId)
            .singleResult
        member.mirGhazabCount++
        em.merge(member)
        Unit
    }

    fun recordUserActivity(chatId: String, userId: String, displayName: String) = emf.transaction { em ->
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
        Unit
    }
}
