package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.ChatSetting
import jakarta.persistence.EntityManagerFactory

class SettingService(private val emf: EntityManagerFactory) {
    fun isDailyQuestionEnabled(chatId: String): Boolean {
        val entityManager = emf.createEntityManager()
        entityManager.use { em ->
            val setting = em.find(ChatSetting::class.java, chatId)
            return setting?.dailyQuestionEnabled ?: true
        }
    }

    fun setDailyQuestionEnabled(chatId: String, enabled: Boolean) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val setting = em.find(ChatSetting::class.java, chatId)
            if (setting == null) {
                val newSetting = ChatSetting(chatId = chatId, dailyQuestionEnabled = enabled)
                em.persist(newSetting)
            } else {
                setting.dailyQuestionEnabled = enabled
                em.merge(setting)
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
