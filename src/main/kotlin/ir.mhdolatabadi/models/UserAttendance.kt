package ir.mhdolatabadi.models

data class UserAttendance(
    val name: String,
    val present: Int,
    val excused: Int,
    val unexcused: Int
)