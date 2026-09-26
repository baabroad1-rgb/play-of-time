package ru.iflex.timemvp.ui_lib

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.koinInject
import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.features.main.ui.FaceIcon
import ru.iflex.timemvp.features.main.ui.FaceKind

private const val SESSION_FACE_REPORT_URL = "https://playoftime.online/report.html"

/** Мост страницы -> приложение: страница сообщает, сколько попыток нашла в диапазоне. */
private class SessionFacePotBridge(private val onReady: (Int) -> Unit) {
    @JavascriptInterface
    fun ready(count: Int) {
        Handler(Looper.getMainLooper()).post { onReady(count) }
    }
}

/**
 * Мордочка (Chernoff-face) диапазона [fromMs, toMs] — тот же report.html
 * ?mode=face движок, что и ReportFaceCard на Analytics (Alex 2026-09-21),
 * вынесенный сюда как маленький неинтерактивный анкер для других экранов
 * (FailCard — Alex 2026-09-22). Пока страница не ответила (нет сети / 0мс
 * диапазон / старый report.html на сервере) — показывается нейтральный
 * смайлик-заглушка, никакого тапа/диалога здесь нет.
 */
@Composable
fun SessionFaceView(
    fromMs: Long,
    toMs: Long,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val storage  = koinInject<LocalStorageService>()
    val playerId = remember { storage.getOrCreatePlayerId() }
    val token    = remember { storage.getOrCreateSyncToken() }

    val faceUrl = remember(fromMs, toMs) {
        "$SESSION_FACE_REPORT_URL?playerId=$playerId&token=$token&mode=face&fromMs=$fromMs&toMs=$toMs&min=1"
    }

    var ready by remember(faceUrl) { mutableStateOf(false) }
    val onReady = rememberUpdatedState<(Int) -> Unit> { _ -> ready = true }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (fromMs <= 0L || toMs <= 0L) {
            // Нет валидного диапазона (например все попытки окна пришли из БД
            // без таймстампа) — смайлик-заглушка навсегда, без сетевого запроса.
            FaceIcon(kind = FaceKind.NICE, size = size * 0.7f)
            return@Box
        }
        if (!ready) FaceIcon(kind = FaceKind.NICE, size = size * 0.7f)
        AndroidView(
            modifier = Modifier.size(size).alpha(if (ready) 1f else 0f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled   = true
                    settings.domStorageEnabled   = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled   = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    setOnTouchListener { _, _ -> true }
                    addJavascriptInterface(SessionFacePotBridge { c -> onReady.value(c) }, "PotFace")
                }
            },
            update = { wv ->
                if (wv.tag != faceUrl) {
                    wv.tag = faceUrl
                    wv.loadUrl(faceUrl)
                }
            }
        )
    }
}
