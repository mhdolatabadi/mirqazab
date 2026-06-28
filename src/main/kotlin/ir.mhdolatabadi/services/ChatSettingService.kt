package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.ChatSetting
import jakarta.persistence.EntityManagerFactory

class SettingService(private val emf: EntityManagerFactory) {
    fun isDailyQuestionEnabled(chatId: String): Boolean {
        val em = emf.createEntityManager()
        try {
            val setting = em.find(ChatSetting::class.java, chatId.toLong())
            return setting?.dailyQuestionEnabled ?: true
        } finally {
            em.close()
        }
    }

    fun setDailyQuestionEnabled(chatId: String, enabled: Boolean) {
        val em = emf.createEntityManager()
        try {
            em.transaction.begin()
            val setting = em.find(ChatSetting::class.java, chatId.toLong())
            if (setting == null) {
                val newSetting = ChatSetting(chatId = chatId.toLong(), dailyQuestionEnabled = enabled)
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