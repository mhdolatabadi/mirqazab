package ir.mhdolatabadi.services

import ir.mhdolatabadi.entities.ChatSetting
import ir.mhdolatabadi.utils.readOnly
import ir.mhdolatabadi.utils.transaction
import jakarta.persistence.EntityManagerFactory

class SettingService(private val emf: EntityManagerFactory) {
    fun isDailyQuestionEnabled(chatId: String): Boolean = emf.readOnly { em ->
        em.find(ChatSetting::class.java, chatId)?.dailyQuestionEnabled ?: true
    }

    fun setDailyQuestionEnabled(chatId: String, enabled: Boolean) = emf.transaction { em ->
        val setting = em.find(ChatSetting::class.java, chatId)
        if (setting == null) {
            em.persist(ChatSetting(chatId = chatId, dailyQuestionEnabled = enabled))
        } else {
            setting.dailyQuestionEnabled = enabled
            em.merge(setting)
        }
        Unit
    }
}
