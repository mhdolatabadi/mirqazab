package ir.mhdolatabadi.entities

import ir.mhdolatabadi.enums.AttendanceStatus
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "attendance_record")
data class AttendanceRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: String = "",
    @Column(name = "chat_id", nullable = false)
    var chatId: String = "",
    @Column(name = "attendance_date", nullable = false)
    var date: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AttendanceStatus = AttendanceStatus.ABSENT_UNEXCUSED
)
