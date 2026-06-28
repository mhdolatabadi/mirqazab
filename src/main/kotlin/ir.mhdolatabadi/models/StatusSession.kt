package ir.mhdolatabadi.models

data class StatusSession(
    val messageId: Int,
    val users: MutableMap<Long, UserInfo>,
    var state: String, // "present" or "absent_excused"
    val initiatorUserId: Long
)