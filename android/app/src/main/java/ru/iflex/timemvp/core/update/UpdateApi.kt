package ru.iflex.timemvp.core.update

import ru.iflex.timemvp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ⚠️ ВРЕМЕННАЯ ЗАПЛАТКА — УДАЛИТЬ ПРИ ВЫХОДЕ В GOOGLE PLAY (Alex 2026-09-22).
 *
 * Это самодельный механизм обновления вне Google Play, нужен ТОЛЬКО пока
 * приложение распространяется сайдлоадом (playoftime.online) и Google Play
 * ещё не подключён. Как только релиз уходит в Play Store — вся эта фича
 * (UpdateApi.kt, ui_lib/AppUpdateButton.kt, вызов fetchIfNewer() в
 * MainViewModel.init, разрешение REQUEST_INSTALL_PACKAGES в манифесте,
 * cache-path "app_update" в file_provider_paths.xml) — УДАЛЯЕТСЯ ЦЕЛИКОМ.
 * Play сам обновляет приложение, этот велосипед больше не нужен.
 *
 * НИКАКОГО отдельного JSON, НИКАКОГО переименования APK в "latest" (Alex
 * 2026-09-22: "именование файлов сохраняется как есть... определи
 * актуальность по имени единственного файла в папке"). Реализовано через уже
 * существующий на сервере `api/latest_apk.php` (его написала параллельная
 * веб-сессия в тот же день для той же цели — ссылки "download app apk" на
 * сайте): он сам находит единственный `.apk` в `downloads/` и отдаёт его как
 * есть, под его настоящим именем, через `Content-Disposition`. Здесь этот же
 * URL переиспользуется — сайт и приложение проверяют версию по одному и тому
 * же файлу, ничего не дублируется.
 *
 * Как работает:
 *   1. HEAD-запрос на `api/latest_apk.php` (без скачивания тела — только
 *      заголовки).
 *   2. Имя настоящего файла достаётся из `Content-Disposition: attachment;
 *      filename="play-of-time-vX.Y.apk"`.
 *   3. Версия (X, Y) сравнивается с версией самой сборки
 *      ([BuildConfig.VERSION_NAME]) — если на сервере строго больше, кнопка
 *      обновления показывает apkUrl = тот же `api/latest_apk.php` (он и
 *      отдаёт реальные байты apk на GET).
 *
 * Единственный ручной шаг после каждого релиза — залить новый APK (под
 * настоящим именем сборки) в `downloads/`, при желании убрав старый — не
 * обязательно, `latest_apk.php` сам возьмёт файл посвежее, если их вдруг
 * несколько.
 *
 * Best-effort, тот же принцип что и LeaderboardApi — любая сетевая ошибка
 * превращается в null, апдейтер никогда не мешает игре.
 */
class UpdateApi(
    private val checkUrl: String = "https://playoftime.online/api/latest_apk.php"
) {
    private val timeoutMs = 8_000

    data class UpdateInfo(val versionName: String, val apkUrl: String)

    private val fileNameRegex = Regex("""play-of-time-v(\d+)\.(\d+)\.apk""")
    private val contentDispositionFileNameRegex = Regex("""filename="?([^";]+)"?""")

    /** ⚠️ ДИАГНОСТИКА (Alex 2026-09-22, временно) — почему последний fetchIfNewer()
     *  вернул null: "not-newer" (реально последняя версия) или конкретная причина
     *  сбоя. Убрать вместе с остальной заплаткой. Читается MainViewModel для тоста. */
    var lastResultDebug: String = "not run yet"
        private set

    /** null, если сети нет, эндпоинт недоступен/без файла, или установленная версия уже последняя. */
    suspend fun fetchIfNewer(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = fetchServerApkFileName()
            if (fileName == null) {
                lastResultDebug = "no Content-Disposition filename from $checkUrl"
                return@runCatching null
            }
            val serverVersion = fileNameRegex.find(fileName)
            if (serverVersion == null) {
                lastResultDebug = "filename '$fileName' didn't match play-of-time-vX.Y.apk"
                return@runCatching null
            }
            val serverMajor = serverVersion.groupValues[1].toInt()
            val serverMinor = serverVersion.groupValues[2].toInt()

            val my = parseVersionName(BuildConfig.VERSION_NAME)
            if (my == null) {
                lastResultDebug = "couldn't parse own VERSION_NAME '${BuildConfig.VERSION_NAME}'"
                return@runCatching null
            }
            val (myMajor, myMinor) = my
            val isNewer = serverMajor > myMajor || (serverMajor == myMajor && serverMinor > myMinor)
            if (!isNewer) {
                lastResultDebug = "server $serverMajor.$serverMinor not newer than installed $myMajor.$myMinor"
                return@runCatching null
            }

            lastResultDebug = "found newer $serverMajor.$serverMinor"
            UpdateInfo(versionName = "$serverMajor.$serverMinor", apkUrl = checkUrl)
        }.getOrElse { e ->
            lastResultDebug = "exception: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    /** HEAD-запрос — только заголовки, тело (сам apk) не скачивается. */
    private fun fetchServerApkFileName(): String? {
        val conn = (URL(checkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }
        val code = conn.responseCode
        val disposition = if (code in 200..299) conn.getHeaderField("Content-Disposition") else null
        conn.disconnect()
        return disposition
            ?.let { contentDispositionFileNameRegex.find(it) }
            ?.groupValues?.get(1)
    }

    /** "13.97" -> (13, 97). null если формат неожиданный (не должно случаться — сборка сама пишет versionName). */
    private fun parseVersionName(name: String): Pair<Int, Int>? {
        val parts = name.split(".")
        if (parts.size != 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        return major to minor
    }
}
