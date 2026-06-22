package ir.mhdolatabadi.entities

import ir.mhdolatabadi.AttendanceStatus
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "attendance_record")
data class AttendanceRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "chat_id", nullable = false)
    var chatId: Long = 0,
    @Column(name = "attendance_date", nullable = false)
    var date: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AttendanceStatus = AttendanceStatus.ABSENT_UNEXCUSED
)
