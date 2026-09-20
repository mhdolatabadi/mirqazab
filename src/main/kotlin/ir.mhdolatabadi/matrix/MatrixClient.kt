package ir.mhdolatabadi.matrix

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Thin wrapper around the Matrix Client-Server API (v3), authenticated with a
 * static long-lived access token. No login/refresh flow - the token is expected
 * to already be valid.
 */
class MatrixClient(
    private val homeserverUrl: String,
    private val accessToken: String
) {
    private val jsonMediaType = "application/json".toMediaType()
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val txnCounter = AtomicLong(System.currentTimeMillis())

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = homeserverUrl.trimEnd('/')

    private fun authedRequest(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")

    fun whoami(): String {
        val url = "${baseUrl()}/_matrix/client/v3/account/whoami"
        val request = authedRequest(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MatrixApiException("whoami failed: ${response.code} ${response.body?.string()}")
            }
            val body = response.body?.string().orEmpty()
            return mapper.readValue<WhoAmIResponse>(body).user_id
        }
    }

    fun sync(since: String?, timeoutMs: Long = 30_000): SyncResponse {
        val urlBuilder = "${baseUrl()}/_matrix/client/v3/sync".toHttpUrl().newBuilder()
            .addQueryParameter("timeout", timeoutMs.toString())
            .addQueryParameter("full_state", "false")
        if (since != null) {
            urlBuilder.addQueryParameter("since", since)
        }
        val request = authedRequest(urlBuilder.build().toString()).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MatrixApiException("sync failed: ${response.code} ${response.body?.string()}")
            }
            val body = response.body?.string().orEmpty()
            return mapper.readValue(body)
        }
    }

    fun joinRoom(roomId: String) {
        val url = "${baseUrl()}/_matrix/client/v3/join/${encode(roomId)}"
        val request = authedRequest(url).post("{}".toRequestBody(jsonMediaType)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MatrixApiException("joinRoom failed: ${response.code} ${response.body?.string()}")
            }
        }
    }

    fun sendMessage(roomId: String, body: String, formattedBody: String? = null): String {
        val content = mutableMapOf<String, Any?>(
            "msgtype" to "m.text",
            "body" to body
        )
        if (formattedBody != null) {
            content["format"] = "org.matrix.custom.html"
            content["formatted_body"] = formattedBody
        }
        return sendEvent(roomId, "m.room.message", content)
    }

    fun editMessage(roomId: String, rootEventId: String, newBody: String, newFormattedBody: String? = null): String {
        val newContent = mutableMapOf<String, Any?>(
            "msgtype" to "m.text",
            "body" to newBody
        )
        if (newFormattedBody != null) {
            newContent["format"] = "org.matrix.custom.html"
            newContent["formatted_body"] = newFormattedBody
        }
        val content = mutableMapOf<String, Any?>(
            "msgtype" to "m.text",
            "body" to "* $newBody",
            "m.new_content" to newContent,
            "m.relates_to" to mapOf(
                "rel_type" to "m.replace",
                "event_id" to rootEventId
            )
        )
        if (newFormattedBody != null) {
            content["format"] = "org.matrix.custom.html"
            content["formatted_body"] = "* $newFormattedBody"
        }
        return sendEvent(roomId, "m.room.message", content)
    }

    fun sendReaction(roomId: String, targetEventId: String, key: String): String {
        val content = mapOf(
            "m.relates_to" to mapOf(
                "rel_type" to "m.annotation",
                "event_id" to targetEventId,
                "key" to key
            )
        )
        return sendEvent(roomId, "m.reaction", content)
    }

    private fun sendEvent(roomId: String, eventType: String, content: Map<String, Any?>): String {
        val txnId = txnCounter.incrementAndGet()
        val url = "${baseUrl()}/_matrix/client/v3/rooms/${encode(roomId)}/send/${encode(eventType)}/$txnId"
        val payload = mapper.writeValueAsString(content)
        val request = authedRequest(url).put(payload.toRequestBody(jsonMediaType)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MatrixApiException("sendEvent($eventType) failed: ${response.code} ${response.body?.string()}")
            }
            val respBody = response.body?.string().orEmpty()
            return mapper.readValue<SendEventResponse>(respBody).event_id
        }
    }

    private fun encode(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8")
}

class MatrixApiException(message: String) : Exception(message)
