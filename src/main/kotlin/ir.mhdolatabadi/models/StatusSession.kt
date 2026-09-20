package ir.mhdolatabadi.models

data class StatusSession(
    val rootEventId: String,
    val users: MutableMap<String, UserInfo>,
    val orderedUserIds: List<String>,
    var state: String, // "present" or "absent_excused"
    val initiatorUserId: String
)
