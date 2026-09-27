package online.playoftime.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import online.playoftime.ui_theme.helpAccentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseTextColor

// Перенесено из LeaderboardScreen.kt 2026-09-21: отчёт с мордочками теперь
// открывается с экрана Analytics (кнопка "VIEW MY PROGRESS REPORT"), а не из
// таблицы Ranks. Тело функции не менялось.

// Отчёт-страница внутри приложения (Alex 2026-09-11, третий заход по этой
// фиче): было "открой в браузере", попросили "окошечко внутри приложения".
// WebView вместо нативного порта движка narrate()/PHRASES/чарта — тот же
// самый report.html, тот же JS, один источник правды для формулировок
// (см. Narrative Feature Decision Log) вместо дублирования кода на Kotlin.
// Почти на весь экран (usePlatformDefaultWidth=false + fillMaxSize минус
// небольшие поля) — отчёту нужно реальное место под диаграмму и текст,
// не компактный попап как у ComparePlayersDialog.
@Composable
fun ReportDialog(url: String, onDismiss: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    var loading by remember { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Alex 2026-09-15: "черную подложку тоже сделай минимальной,
                // пусть начинается там где все остальные экраны начинаются
                // по высоте" — every other screen sits at Scaffold's own
                // default content padding (WindowInsets.safeDrawing, i.e.
                // just the status-bar inset, no extra manual offset -- see
                // ui_lib/Screen.kt). The old flat 24.dp vertical padding was
                // ON TOP of that, an artificial gap this dialog alone had.
                // statusBarsPadding() reproduces the same top-only inset
                // Scaffold already gives every other screen; horizontal
                // margin explicitly halved per "по ширине уменьши поля в
                // два раза" (was 8.dp).
                .padding(horizontal = 4.dp)
                .statusBarsPadding()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Alex 2026-09-15: "уменьши расстояние от начала бежевой
                    // плашки до начала черной на 30" — this row's own height
                    // WAS the gap (the WebView's cream page starts right
                    // below it, at the WebView's own y=0). Vertical padding
                    // 10.dp -> 2.dp and the button 24.dp -> 18.dp cuts the
                    // row from 44dp to 22dp (-22dp); the other -8dp comes
                    // from report.html's own stray default body margin, now
                    // reset alongside this (see html,body{margin:0} there).
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    // Жёлтый, как крестик выхода из справки и как бабл подсказки
                    // (Alex 2026-09-27) — чтобы «закрыть» везде выглядело одинаково.
                    BasicText(text = "×", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.helpAccentColor))
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            // Страница отчёта живёт на сервере и меняется чаще,
                            // чем приложение: выложил — и должно быть видно
                            // сразу, без пересборки APK. По умолчанию WebView
                            // берёт её из кэша, и Алекс 27.09.2026 увидел
                            // старые морды через час после выкладки новых.
                            // Управления кэшем тут не было вообще — это
                            // недоделка, а не настройка. Страница лёгкая и
                            // открывается редко, так что берём всегда свежую.
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView, finishedUrl: String) {
                                    loading = false
                                }
                            }
                            loadUrl(url)
                        }
                    }
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.accentColor
                    )
                }
            }
        }
    }
}
