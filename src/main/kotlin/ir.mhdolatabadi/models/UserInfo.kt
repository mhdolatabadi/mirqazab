package ir.mhdolatabadi.models

import ir.mhdolatabadi.enums.AttendanceStatus

data class UserInfo(
    val id: String,
    val name: String,
    var status: AttendanceStatus = AttendanceStatus.UNKNOWN
)
