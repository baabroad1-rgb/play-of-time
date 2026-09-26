package ru.iflex.timemvp.ui_lib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Чипы масштаба прогресс-бара (Alex 2026-07-25) — в Настройках.
 *
 * Тап по пресету задаёт полуокно бара: край = 3.000 ± значение. Крайний «0–4 s»
 * = полное окно [0..4] (несимметричное). По умолчанию ±100 = [2.900..3.100].
 * Тап (а не драг) — чтобы не конфликтовать с вертикальным скроллом настроек.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleChips(
    halfWindowMs: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = «Auto» (масштаб по уровню). Остальные — ручное полуокно в мс.
    val presets = listOf(0, 10, 20, 50, 100, 250, 500, 1000, 3000)
    val selected = if (halfWindowMs == 0) 0
                   else presets.minByOrNull { kotlin.math.abs(it - halfWindowMs) } ?: 100
    val accent = Color(0xFFF5C02E)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { v ->
            val isSel = v == selected
            val label = when {
                v == 0    -> "Auto"
                v >= 3000 -> "0–4 s"
                v == 1000 -> "2–4 s"
                else      -> "± $v"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSel) accent else Color(0x1FFFFFFF))
                    .clickable { onChange(v) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = if (isSel) Color(0xFF1A1300) else Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}
