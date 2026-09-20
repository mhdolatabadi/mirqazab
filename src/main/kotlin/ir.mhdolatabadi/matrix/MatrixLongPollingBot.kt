package ir.mhdolatabadi.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the /sync long-polling loop and dispatches room messages and reactions.
 * Mirrors the role TelegramLongPollingBot played before the Matrix migration.
 */
abstract class MatrixLongPollingBot(protected val client: MatrixClient) {

    lateinit var selfUserId: String
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    abstract fun onRoomMessage(roomId: String, senderId: String, body: String, eventId: String)
    abstract fun onReaction(roomId: String, senderId: String, targetEventId: String, key: String)

    fun start() {
        selfUserId = client.whoami()
        scope.launch { syncLoop() }
    }

    private suspend fun syncLoop() {
        var since: String? = null
        while (true) {
            try {
                val response = client.sync(since, timeoutMs = 30_000)
                since = response.next_batch

                for (roomId in response.rooms.invite.keys) {
                    runCatching { client.joinRoom(roomId) }
                        .onFailure { it.printStackTrace() }
                }

                for ((roomId, joinedRoom) in response.rooms.join) {
                    for (event in joinedRoom.timeline.events) {
                        if (event.sender == selfUserId) continue
                        dispatch(roomId, event)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                delay(5_000)
            }
        }
    }

    private fun dispatch(roomId: String, event: RoomEvent) {
        try {
            when (event.type) {
                "m.room.message" -> {
                    val body = event.content["body"] as? String ?: return
                    if (event.content.containsKey("m.relates_to")) return
                    onRoomMessage(roomId, event.sender, body, event.event_id)
                }
                "m.reaction" -> {
                    val relatesTo = event.content["m.relates_to"] as? Map<*, *> ?: return
                    if (relatesTo["rel_type"] != "m.annotation") return
                    val targetEventId = relatesTo["event_id"] as? String ?: return
                    val key = relatesTo["key"] as? String ?: return
                    onReaction(roomId, event.sender, targetEventId, key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(roomId: String, text: String): String = client.sendMessage(roomId, text)

    fun editMessage(roomId: String, rootEventId: String, text: String): String =
        client.editMessage(roomId, rootEventId, text)

    fun react(roomId: String, targetEventId: String, key: String) {
        runCatching { client.sendReaction(roomId, targetEventId, key) }
            .onFailure { it.printStackTrace() }
    }

    fun reactAll(roomId: String, targetEventId: String, keys: List<String>) {
        keys.forEach { react(roomId, targetEventId, it) }
    }
}
