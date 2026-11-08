package online.playoftime.features.rival

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks our server to write one day of Doppelgänger's diary with a language
 * model (webapp/api/doppelganger_diary.php → OpenRouter → Claude Haiku).
 * The OpenRouter key never leaves the server; the app sends only what its
 * engine counted for that day and his memory. Plain HttpURLConnection +
 * org.json, like LeaderboardApi.
 */
class DiaryWriter(private val baseUrl: String = "https://playoftime.online/api/") {

    data class Written(val entry: String, val memory: String, val model: String)

    /** Null on any failure: offline, server not configured, limit reached. */
    suspend fun write(playerId: String, day: String, facts: JSONObject, memory: String): Written? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("playerId", playerId)
                    .put("day", day)
                    .put("facts", facts)
                    .put("memory", memory)
                val conn = (URL(baseUrl + "doppelganger_diary.php").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 60_000          // the model needs a few seconds
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                conn.disconnect()
                if (code !in 200..299 || text == null) return@runCatching null
                val o = JSONObject(text)
                val entry = o.optString("entry").trim()
                if (entry.isEmpty()) null
                else Written(entry, o.optString("memory").trim(), o.optString("model"))
            }.getOrNull()
        }
}
