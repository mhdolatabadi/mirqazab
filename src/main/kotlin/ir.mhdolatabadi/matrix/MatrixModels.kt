package ir.mhdolatabadi.matrix

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncResponse(
    val next_batch: String = "",
    val rooms: Rooms = Rooms()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rooms(
    val join: Map<String, JoinedRoom> = emptyMap(),
    val invite: Map<String, Any?> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JoinedRoom(
    val timeline: Timeline = Timeline()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Timeline(
    val events: List<RoomEvent> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoomEvent(
    val type: String = "",
    val sender: String = "",
    val event_id: String = "",
    val content: Map<String, Any?> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhoAmIResponse(
    val user_id: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SendEventResponse(
    val event_id: String = ""
)
