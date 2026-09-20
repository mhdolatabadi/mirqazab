package ir.mhdolatabadi.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_settings")
class ChatSetting(
    @Id
    @Column(name = "chat_id", nullable = false)
    var chatId: String = "",

    @Column(name = "daily_question_enabled", nullable = false)
    var dailyQuestionEnabled: Boolean = true,

    @Column(name = "question_sent_today", nullable = false)
    var questionSentToday: Boolean = false,

    @Column(name = "report_sent_today", nullable = false)
    var reportSentToday: Boolean = false,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    constructor() : this("", true, false, false, LocalDateTime.now())
}
