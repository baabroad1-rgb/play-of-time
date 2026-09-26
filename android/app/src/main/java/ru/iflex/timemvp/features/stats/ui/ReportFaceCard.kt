package ru.iflex.timemvp.features.stats.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.koinInject
import ru.iflex.timemvp.core.data.LocalStorageService
import ru.iflex.timemvp.features.main.ui.FaceIcon
import ru.iflex.timemvp.features.main.ui.FaceKind
import ru.iflex.timemvp.features.stats.domain.entity.AnalyticsData
import ru.iflex.timemvp.ui_lib.ReportDialog
import ru.iflex.timemvp.ui_theme.accentColor

/** Минимум попыток периода, при котором отчёт считается осмысленным. */
private const val MIN_FACE_ATTEMPTS = 30

private const val REPORT_URL = "https://playoftime.online/report.html"

/** Мост страницы -> приложение: страница сообщает, сколько попыток нашла в периоде. */
private class PotFaceBridge(private val onReady: (Int) -> Unit) {
    @JavascriptInterface
    fun ready(count: Int) {
        Handler(Looper.getMainLooper()).post { onReady(count) }
    }
}

/**
 * Карточка вызова отчёта на Analytics (Alex 2026-09-21). Вместо смайлика —
 * мордочка ВЫБРАННОГО на странице периода: тот же report.html в режиме
 * ?mode=face, маленьким WebView. Пока страница не ответила (нет сети / старый
 * report.html на сервере) — показывается смайлик. Если попыток периода
 * меньше [MIN_FACE_ATTEMPTS] — мордочка размыта (размывает сама страница),
 * вместо надписи «VIEW MY PROGRESS REPORT» — подсказка, сколько ещё сыграть;
 * тап в этом состоянии не открывает отчёт.
 */
@Composable
internal fun ReportFaceCard(
    data: AnalyticsData,
    helpMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accent   = MaterialTheme.colorScheme.accentColor
    val storage  = koinInject<LocalStorageService>()
    val playerId = remember { storage.getOrCreatePlayerId() }
    val token    = remember { storage.getOrCreateSyncToken() }

    val query    = "playerId=$playerId&token=$token"
    val faceUrl  = remember(data.periodFromMs, data.periodToMs) {
        "$REPORT_URL?$query&mode=face&fromMs=${data.periodFromMs}&toMs=${data.periodToMs}&min=$MIN_FACE_ATTEMPTS"
    }

    var readyCount by remember(faceUrl) { mutableStateOf<Int?>(null) }
    val onReady = rememberUpdatedState<(Int) -> Unit> { c -> readyCount = c }
    var reportUrl by remember { mutableStateOf<String?>(null) }

    val count   = readyCount
    val enough  = count == null || count >= MIN_FACE_ATTEMPTS
    val missing = if (count == null) 0 else (MIN_FACE_ATTEMPTS - count).coerceAtLeast(0)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                if (count == null) FaceIcon(kind = FaceKind.NICE, size = 58.dp)
                AndroidView(
                    modifier = Modifier.size(84.dp).alpha(if (count == null) 0f else 1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled  = true
                            settings.domStorageEnabled  = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled  = false
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER
                            setOnTouchListener { _, _ -> true }
                            addJavascriptInterface(PotFaceBridge { c -> onReady.value(c) }, "PotFace")
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
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = if (enough) "VIEW MY PROGRESS REPORT"
                       else "Please make $missing more ${if (missing == 1) "attempt" else "attempts"} to see the report",
                style = TextStyle(
                    fontSize   = if (enough) 14.sp else 12.sp,
                    lineHeight = if (enough) 19.sp else 16.sp,
                    fontWeight = if (enough) FontWeight.Bold else FontWeight.SemiBold,
                    color      = accent,
                    textAlign  = TextAlign.Center
                )
            )
        }
        // Прозрачный слой поверх WebView: тап принимает Compose, а не WebView.
        // В режиме справки и при нехватке попыток отчёт не открывается.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = !helpMode && enough) {
                    reportUrl = "$REPORT_URL?$query"
                }
        )
    }

    reportUrl?.let { url ->
        ReportDialog(url = url, onDismiss = { reportUrl = null })
    }
}
