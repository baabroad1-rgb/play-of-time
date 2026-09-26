package online.playoftime.ui_lib

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.playoftime.core.telemetry.CrashReporter
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseBackgroundColor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * ⚠️ ВРЕМЕННАЯ ЗАПЛАТКА — УДАЛИТЬ ПРИ ВЫХОДЕ В GOOGLE PLAY.
 * Часть сайдлоад-обновлятора, см. полный комментарий в [online.playoftime.core.update.UpdateApi].
 *
 * Кнопка обновления в левом верхнем углу главного экрана (Alex 2026-09-22).
 * Рендерится ТОЛЬКО когда MainViewModel обнаружил на сервере версию новее
 * установленной (см. UpdateApi) — родитель передаёт apkUrl уже проверенным,
 * сама кнопка ничего не решает про "нужно ли обновление".
 *
 * Тап (по всей плашке, не только по кружку-иконке): скачивает APK в кэш
 * приложения (тот же FileProvider authority, что у Excel-экспорта) и сразу
 * запускает системный ACTION_VIEW на файл — Android сам покажет диалог
 * установки (при первом разе — ещё и запрос разрешения "Install unknown
 * apps" для этого приложения, стандартный системный флоу, отдельного
 * runtime-permission кода не нужно). Во время закачки иконка заменяется на
 * спиннер, подпись — на "Downloading…"; на любой сбой — тост, плашка
 * возвращается в исходное состояние, можно попробовать снова.
 *
 * Подпись рядом с иконкой (Alex 2026-09-22, следом за фиксом видимости) —
 * без неё кружок-стрелка сам по себе ничего не объяснял.
 */
@Composable
fun AppUpdateButton(
    apkUrl: String,
    crashReporter: CrashReporter,
    modifier: Modifier = Modifier,
    // Подпись вынесена в параметр: той же кнопкой мостовая версия предлагает
    // переехать на переименованное приложение, и слово "Update" там врало бы —
    // это не обновление, а установка второго приложения рядом.
    label: String = "Update",
    labelWhileDownloading: String = "Updating…"
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.accentColor

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.baseBackgroundColor)
            .border(width = 1.5.dp, color = accent, shape = RoundedCornerShape(999.dp))
            .clickable(enabled = !downloading) {
                downloading = true
                scope.launch {
                    val ok = downloadAndInstall(context, apkUrl, crashReporter)
                    downloading = false
                    if (!ok) {
                        Toast.makeText(context, "Update failed — try again later", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
            } else {
                UpdateArrowIcon(color = accent)
            }
        }
        BasicText(
            // Alex 2026-09-22 (сразу после этого фикса): короче — полный текст
            // "Download new version" делал плашку шире, чем зазор слева от
            // ЦЕНТРИРОВАННОГО номера квеста ("Quest L.T"), и заезжал на него.
            // Номер квеста трогать НЕЛЬЗЯ (явный запрет Alex) — сужена только
            // эта плашка. "Update"/"Updating…" короткие, в тот же зазор влезают.
            text = if (downloading) labelWhileDownloading else label,
            modifier = Modifier.padding(start = 6.dp, end = 2.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = accent)
        )
    }
}

/** Простая стрелка "скачать/обновить" — вниз-в-лоток, тем же стилем, что и
 *  остальные рисованные от руки иконки в проекте (HandDrawnCheck и т.п.),
 *  без новой зависимости на иконки Material. */
@Composable
private fun UpdateArrowIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        // Вертикальный стержень стрелки
        drawLine(color, Offset(w * 0.5f, h * 0.05f), Offset(w * 0.5f, h * 0.62f), stroke.width, StrokeCap.Round)
        // Голова стрелки — галочка-домиком вниз
        val head = Path().apply {
            moveTo(w * 0.2f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.8f, h * 0.38f)
        }
        drawPath(head, color, style = stroke)
        // Лоток снизу
        drawLine(color, Offset(w * 0.1f, h * 0.92f), Offset(w * 0.9f, h * 0.92f), stroke.width, StrokeCap.Round)
    }
}

private suspend fun downloadAndInstall(
    context: Context,
    apkUrl: String,
    crashReporter: CrashReporter
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val updateDir = File(context.cacheDir, "update").apply { mkdirs() }
        val out = File(updateDir, "play-of-time-update.apk")
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error("HTTP $code")
        }
        conn.inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        conn.disconnect()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) { context.startActivity(installIntent) }
        true
    }.onFailure { e ->
        android.util.Log.e("AppUpdate", "Download/install failed", e)
        crashReporter.recordException(e)
    }.getOrDefault(false)
}
