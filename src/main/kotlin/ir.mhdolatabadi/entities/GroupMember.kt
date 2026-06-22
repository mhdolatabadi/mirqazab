package ir.mhdolatabadi.entities

import jakarta.persistence.*

@Entity
@Table(name = "group_member", uniqueConstraints = [UniqueConstraint(columnNames = ["chat_id", "user_id"])])
data class GroupMember(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "chat_id", nullable = false)
    var chatId: Long = 0,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "name", nullable = false)
    var name: String = "",
    @Column(name = "mir_ghazab_count", nullable = false)
    var mirGhazabCount: Int = 0
) {
    constructor() : this(null, 0, 0, "", 0)
}