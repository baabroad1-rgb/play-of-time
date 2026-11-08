package online.playoftime.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
fun ReportDialog(url: String, onDismiss: () -> Unit, localReportJson: String? = null) {
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
            // Крестик закрытия переехал ВНУТРЬ страницы (Alex 2026-09-27, по
            // присланному эскизу): справа вверху кремового листа, на одной
            // линии с заголовком, крупный и тонкий.
            //
            // Раньше он жил в отдельной чёрной полоске НАД страницей: 18dp,
            // цветом baseTextColor, то есть в светлой теме — тёмно-серым по
            // почти чёрному фону. Его было и не видно, и он съедал 22dp
            // высоты полоской, которой на эскизе нет вовсе.
            //
            // Полоска убрана целиком, крестик лежит поверх WebView в его
            // правом верхнем углу. Отступы подобраны под поля самой страницы,
            // чтобы он встал вровень с заголовком.
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
                            // Отчёт Doppelgänger'а (Alex 2026-11-08): его попыток на
                            // сервере нет — страница с ?local=1 берёт их отсюда.
                            if (localReportJson != null) {
                                addJavascriptInterface(PotLocalBridge { localReportJson }, "PotLocal")
                            }
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
                // Крестик поверх страницы, как на эскизе: правый верхний угол
                // кремового листа, вровень с заголовком, крупный и тонкий.
                //
                // Цвет — чернила самой страницы, не жёлтый. Жёлтый просили для
                // крестика ВЫХОДА ИЗ СПРАВКИ, и там он лежит на тёмном; на
                // кремовом листе тот же жёлтый почти не виден. На эскизе
                // крестик тоже тёмный. Скажешь — перекрашу одной строкой.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 10.dp)
                        .size(40.dp)                       // палец, а не глиф
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text  = "✕",
                        style = TextStyle(
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Normal,
                            color      = Color(0xFF2A1E00)  // --text-primary страницы отчёта
                        )
                    )
                }
            }
        }
    }
}

/**
 * Попытки, которые страница отчёта (report.html?local=1) берёт не с сервера, а
 * у приложения — в формате api/report_data.php. Нужно для Doppelgänger'а: его
 * попытки живут только в телефоне (Alex 2026-11-08: «а морда в аналитикс чья?»).
 */
internal class PotLocalBridge(private val json: () -> String?) {
    @android.webkit.JavascriptInterface
    fun reportData(): String = json() ?: ""
}

