package online.playoftime.ui_lib

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.playoftime.R
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.baseTextColor
import online.playoftime.ui_theme.resultBlueColor
import online.playoftime.ui_theme.resultGreenColor
import online.playoftime.ui_theme.resultRedColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sticky нижняя навигационная панель — 5 вкладок: Play / Ranks / Analytics /
 * Settings / About. Активная подсвечена accentColor, остальные приглушены.
 * Тап отправляет `onSelectPage(index)` — MainActivity анимирует HorizontalPager
 * к выбранной странице.
 *
 * Индексы совпадают И с порядком страниц в pager, И с визуальным порядком
 * вкладок ниже (Alex 2026-09-10 — раньше это было не так: pager шёл
 * Play/Analytics/Settings/About/Ranks, а вкладки визуально стояли
 * Play/Ranks/Analytics/Settings/About, из-за чего свайп «не совпадал с
 * меню». Теперь единый порядок: 0=Play, 1=Ranks, 2=Analytics, 3=Settings, 4=About.
 *
 * Визуальный порядок Play–Ranks–Analytics–Settings–About (Alex 2026-08-22) —
 * так индикатор Analytics (спидометр баланса) оказывается СТРОГО ПОСЕРЕДИНЕ
 * пяти вкладок, а не третьим из четырёх, как раньше. Export убран отсюда
 * (переехал в Settings, рядом с «Erase all data» — см. SettingScreen.kt).
 *
 * Balance (Alex 2026-07-26): если переданы [balanceRed]/[balanceBlue], вкладка
 * Analytics вместо статичной иконки рисует стрелочный индикатор баланса
 * (red/blue) — верхняя строка метрик убрана, а её функция баланса переехала сюда.
 */
@Composable
fun BottomNavBar(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Баланс читаем из LocalStorage — тогда индикатор на вкладке Analytics
    // одинаково живёт на ВСЕХ экранах (не только на Play), Alex 2026-07-26.
    // Подписка на StateFlow (не разовый геттер, Alex 31.08 — «дёргается и
    // врёт»): гейдж обновляется СИНХРОННО с каждым putBalance(), а не только
    // когда BottomNavBar пересобирается по другой причине.
    val storage = org.koin.compose.koinInject<online.playoftime.core.data.LocalStorageService>()
    val balance by storage.balanceFlow.collectAsState()
    val balanceRed  = balance.first
    val balanceBlue = balance.second
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.baseBackgroundColor)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play: брендовая иконка (жёлтый треугольник + "t", без тёмного круга
        // из эскиза — Alex 2026-08-08). Собственные цвета, БЕЗ tint-фильтра
        // (customIcon игнорирует iconTint активной/неактивной вкладки).
        NavTab(
            iconRes  = R.drawable.ic_run,
            label    = "Play",
            selected = currentPage == 0,
            onClick  = { if (currentPage != 0) onSelectPage(0) },
            customIcon = {
                Image(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(R.drawable.ic_play_t),
                    contentDescription = "Play"
                )
            }
        )
        // Ranks (Alex 2026-08-12): онлайн-лидерборд, тот же сервер, что у webapp.
        // Стоит ВТОРЫМ визуально (Alex 2026-08-22), pager-индекс тоже 1
        // (выровнено с визуальным порядком 2026-09-10).
        NavTab(R.drawable.ic_ranks,    "Ranks",    currentPage == 1) { if (currentPage != 1) onSelectPage(1) }
        // Analytics: индикатор баланса поверх (если есть данные), иначе обычная иконка.
        // Строго посередине пяти вкладок (Alex 2026-08-22).
        NavTab(
            iconRes  = R.drawable.ic_stat,
            label    = "Analytics",
            selected = currentPage == 2,
            onClick  = { if (currentPage != 2) onSelectPage(2) },
            // Крупнее прежнего 24×12dp (Alex 2026-08-22: «попробовать сделать
            // сам индикатор крупнее, подпись — тем же размером»); подпись
            // (fontSize 10.sp в NavTab) не тронута.
            //
            // НЕ сдвигаем вкладку в сторону (был опыт с contentOffsetX=16.dp,
            // откачен тем же днём): Alex явно попросил меню оставаться
            // равномерным и симметричным относительно центральной пиктограммы.
            // Вместо сдвига меню — гистограмма/лог/бары теперь сами
            // отцентрованы на истинный центр экрана (CHART_SC_PAD в
            // DistribChart.kt), так что естественный центр этой вкладки уже
            // совпадает с их «0» без каких-либо трюков.
            customIcon = { BalanceGauge(balanceRed, balanceBlue, Modifier.size(width = 32.dp, height = 16.dp)) }
        )
        NavTab(R.drawable.ic_settings, "Settings", currentPage == 3) { if (currentPage != 3) onSelectPage(3) }
        NavTab(R.drawable.ic_about,    "About",    currentPage == 4) { if (currentPage != 4) onSelectPage(4) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val accent   = MaterialTheme.colorScheme.accentColor
    val baseText = MaterialTheme.colorScheme.baseTextColor
    val iconTint = if (selected) accent else baseText.copy(alpha = 0.63f)
    val textColor = if (selected) accent else baseText.copy(alpha = 0.63f)
    val bg = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(bg, shape = RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (customIcon != null) {
            // Индикатор занимает ту же вертикаль, что и 16dp-иконка.
            Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) { customIcon() }
        } else {
            Image(
                modifier = Modifier.size(16.dp),
                painter = painterResource(iconRes),
                contentDescription = label,
                colorFilter = ColorFilter.tint(iconTint)
            )
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text  = label,
            style = TextStyle(
                fontSize   = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = textColor,
                textAlign  = TextAlign.Center
            )
        )
    }
}

/**
 * Balance gauge: полукруглая шкала, красная слева / синяя справа, белая стрелка.
 * Перенесена из бывшей TopMetricsBar (Alex 2026-07-26). Публичная — переиспользуется
 * также в StatScreen.PeriodPill (Alex 2026-08-08), чтобы иконка в строке периода
 * совпадала с индикатором на вкладке Analytics нижнего меню.
 */
@Composable
fun BalanceGauge(
    redCount: Int,
    blueCount: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeW    = 3.dp.toPx()
        val inset      = strokeW / 2f
        val arcTopLeft = Offset(inset, inset)
        val arcSize    = Size(size.width - strokeW, size.width - strokeW)

        drawArc(
            color = resultRedColor.copy(alpha = 0.85f), startAngle = 180f, sweepAngle = 90f,
            useCenter = false, topLeft = arcTopLeft, size = arcSize,
            style = Stroke(strokeW, cap = StrokeCap.Round)
        )
        drawArc(
            color = resultBlueColor.copy(alpha = 0.85f), startAngle = 270f, sweepAngle = 90f,
            useCenter = false, topLeft = arcTopLeft, size = arcSize,
            style = Stroke(strokeW, cap = StrokeCap.Round)
        )

        val total  = (redCount + blueCount).toFloat()
        val ratio  = if (total == 0f) 0f else ((blueCount - redCount) / total).coerceIn(-1f, 1f)
        val angleRad   = Math.toRadians((270f + ratio * 80f).toDouble())
        val effectiveR = (size.width - strokeW) / 2f
        val cx = size.width / 2f
        val cy = size.height
        val endX = cx + (effectiveR * cos(angleRad)).toFloat()
        val endY = cy + (effectiveR * sin(angleRad)).toFloat()

        drawLine(Color.White, Offset(cx, cy), Offset(endX, endY), 1.5.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.90f), 1.6.dp.toPx(), Offset(cx, cy))
        val tipColor = when {
            ratio < -0.15f -> resultRedColor
            ratio >  0.15f -> resultBlueColor
            else           -> resultGreenColor
        }
        drawCircle(tipColor, 2.4.dp.toPx(), Offset(endX, endY))
    }
}
