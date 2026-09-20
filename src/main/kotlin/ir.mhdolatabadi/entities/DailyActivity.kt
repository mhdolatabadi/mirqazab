package ir.mhdolatabadi.entities

import ir.mhdolatabadi.enums.ActivityStatus
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_activity", uniqueConstraints = [
    UniqueConstraint(columnNames = ["user_id", "chat_id", "activity_date"])
])
class DailyActivity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: String = "",
    @Column(name = "chat_id", nullable = false)
    var chatId: String = "",
    @Column(name = "activity_date", nullable = false)
    var date: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ActivityStatus = ActivityStatus.DEVELOPMENT
) {
    constructor() : this(null, "", "", LocalDate.now(), ActivityStatus.DEVELOPMENT)
}
