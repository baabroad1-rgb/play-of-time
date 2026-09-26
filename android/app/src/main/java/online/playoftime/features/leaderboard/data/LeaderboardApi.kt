package online.playoftime.features.leaderboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Тонкий клиент к тому же серверу, что и webapp (playoftime.online/api/).
 * Best-effort: никогда не бросает исключение наружу — любая сетевая ошибка
 * превращается в null, вызывающий код сам решает, ретраить или нет. Синк
 * никогда не должен блокировать геймплей (тот же принцип, что и в web —
 * "silently no-ops and retries on the next attempt").
 *
 * Никаких внешних HTTP-библиотек — обычный HttpURLConnection + org.json,
 * оба уже есть в Android SDK, лишняя зависимость не нужна ради нескольких
 * маленьких JSON-запросов раз в 1-2 секунды.
 */
class LeaderboardApi(
    private val baseUrl: String = "https://playoftime.online/api/"
) {
    private val timeoutMs = 10_000

    suspend fun sync(payload: JSONObject): JSONObject? =
        postJson("sync.php", payload)

    suspend fun setName(payload: JSONObject): JSONObject? =
        postJson("set_name.php", payload)

    /** "Settings → Erase all data" (Alex 2026-09-03): сообщает серверу, что
     *  игрок начал сначала — без этого его строка в Ranks (coins/level)
     *  оставалась бы на старых значениях после локального сброса. Тот же
     *  best-effort принцип: сеть недоступна — молча null, вызывающий код
     *  не блокирует сам Reset на этом. Ответ `{"ok":true,"epoch":N}` не
     *  обязателен вызывающей стороне — сервер сам решает, как разграничить
     *  до/после сброса попытки одного playerId. */
    suspend fun resetEpoch(payload: JSONObject): JSONObject? =
        postJson("reset_epoch.php", payload)

    suspend fun fetchLeaderboard(sortApiValue: String): JSONObject? =
        getJson("leaderboard.php?sort=" + URLEncoder.encode(sortApiValue, "UTF-8"))

    private suspend fun postJson(path: String, payload: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                readResponse(conn)
            }.getOrNull()
        }

    private suspend fun getJson(pathWithQuery: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(baseUrl + pathWithQuery).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                }
                readResponse(conn)
            }.getOrNull()
        }

    private fun readResponse(conn: HttpURLConnection): JSONObject? {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        conn.disconnect()
        if (code !in 200..299 || text == null) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }
}
