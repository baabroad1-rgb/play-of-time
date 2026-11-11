package online.playoftime.features.leaderboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import online.playoftime.features.leaderboard.domain.entity.LeaderboardRow
import online.playoftime.features.leaderboard.domain.entity.LeaderboardSort
import online.playoftime.features.leaderboard.domain.entity.sortedBestFirst
import online.playoftime.features.leaderboard.ui.LeaderboardContract.Event
import online.playoftime.features.main.ui.HelpTapOverlayVisual
import online.playoftime.features.main.ui.HelpTopic
import online.playoftime.features.main.ui.helpTapGestures
import online.playoftime.features.main.ui.helpZoneDisposable
import online.playoftime.ui_lib.CoinIcon
import online.playoftime.ui_theme.Dimens
import online.playoftime.ui_theme.accentColor
import online.playoftime.ui_theme.helpAccentColor
import online.playoftime.ui_theme.baseBackgroundColor
import online.playoftime.ui_theme.baseTextColor

private val panelBg = Color(0xFF1A1A1A)

@Composable
fun LeaderboardScreen(
    currentPage: Int,
    onSelectPage: (Int) -> Unit
) {
    val viewModel = koinViewModel<LeaderboardViewModel>()
    val state = viewModel.viewState.value
    val setEvent: (Event) -> Unit = { viewModel.setEvent(it) }

    // HorizontalPager держит экран живым — освежаем при каждом возврате на
    // вкладку (та же логика, что у StatScreen: init отработал один раз при
    // старте приложения, дальше рейтинги других игроков протухают).
    androidx.compose.runtime.LaunchedEffect(currentPage) {
        if (currentPage == 1) setEvent(Event.Refresh)
    }

    // Интерактивная справка (Alex 2026-09-10: "добавыить хелп аналогично
    // главному и аналитикс") — тот же механизм, что на Main/Analytics:
    // online.playoftime.features.main.ui.HelpTapOverlay.kt. Два зоны — карточка
    // имени и сама таблица (RANKS_NAME / RANKS_TABLE).
    var helpMode by remember { mutableStateOf(false) }
    var helpTopic by remember {
        mutableStateOf<HelpTopic?>(null)
    }
    val helpZones = remember {
        androidx.compose.runtime.mutableStateMapOf<HelpTopic, androidx.compose.ui.geometry.Rect>()
    }
    var helpQuestionRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }

    // Тап по строке → сравнение (Alex 2026-09-11): "тапнуть по любому
    // произвольному игроку и в ответ всплывало бы окно со сравнительными
    // характеристиками". Храним только чужую строку — своя ищется в уже
    // загруженном state.rows по myPlayerId (см. ComparePlayersDialog ниже).
    var compareRow by remember { mutableStateOf<LeaderboardRow?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.baseBackgroundColor)
    ) {
        var scrollBoxOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var scrollBoxSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    scrollBoxOrigin = it.boundsInRoot().topLeft
                    scrollBoxSize   = it.boundsInRoot().size
                }
                // Хит-тестинг режима справки — на том же Box, что оборачивает
                // скролл-контент ниже (должен быть настоящим ПРЕДКОМ, не
                // соседом — см. helpTapGestures doc-comment в HelpTapOverlay.kt).
                .then(
                    if (helpMode) Modifier.helpTapGestures(
                        zones        = helpZones,
                        questionRect = helpQuestionRect,
                        origin       = { scrollBoxOrigin },
                        onSelect     = { helpTopic = it },
                        onExit       = { helpMode = false; helpTopic = null }
                    ) else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Уже LOCAL горизонтальный паддинг (Alex 2026-09-10: "слева и
                    // справа пространства дохера, внутри жмёшься") — таблица с
                    // 6 колонками нуждается в ширине; Dimens.horizontalPadding
                    // (16dp) был рассчитан на текстовые экраны, не на плотную
                    // таблицу. Не трогаем сам Dimens — только этот экран.
                    .padding(horizontal = 8.dp, vertical = Dimens.verticalPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.RANKS_NAME)) {
                    NameCard(state = state, onValueChange = { setEvent(Event.NameInputChanged(it)) }, onSave = { setEvent(Event.SaveName) }, helpMode = helpMode)
                }
                Box(Modifier.helpZoneDisposable(helpZones, HelpTopic.RANKS_TABLE)) {
                    RowsPanel(
                        state          = state,
                        onSelectSort   = { setEvent(Event.SelectSort(it)) },
                        onRetry        = { setEvent(Event.Refresh) },
                        // Отчёт с мордочками переехал на экран Analytics (Alex
                        // 2026-09-21) — тап по СВОЕЙ строке больше ничего не открывает.
                        onSelectRow    = { row ->
                            if (row.playerId != state.myPlayerId) compareRow = row
                        },
                        helpActive     = helpMode,
                        onHelpToggle   = { helpTopic = null; helpMode = !helpMode },
                        onQuestionRect = { helpQuestionRect = it }
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (helpMode) {
                HelpTapOverlayVisual(
                    zones        = helpZones,
                    questionRect = helpQuestionRect,
                    selected     = helpTopic,
                    onSelect     = { helpTopic = it },
                    origin       = scrollBoxOrigin,
                    overlaySize  = scrollBoxSize,
                    modifier     = Modifier.matchParentSize()
                )
            }
        }
        // Sticky нижняя навигация вынесена в MainActivity (Alex 2026-08-12).
    }

    compareRow?.let { other ->
        ComparePlayersDialog(
            mine = state.rows.find { it.playerId == state.myPlayerId },
            other = other,
            onDismiss = { compareRow = null }
        )
    }

}

@Composable
private fun NameCard(
    state: LeaderboardContract.State,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    helpMode: Boolean = false
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent = MaterialTheme.colorScheme.accentColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(panelBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicText(
            text = "YOUR NAME",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.63f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (state.nameInput.isEmpty()) {
                    BasicText(
                        text = "Anonymous",
                        style = TextStyle(fontSize = 14.sp, color = textColor.copy(alpha = 0.43f))
                    )
                }
                BasicTextField(
                    value = state.nameInput,
                    onValueChange = { onValueChange(it.take(24)) },
                    enabled = !helpMode,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = textColor),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
                    .clickable(enabled = !helpMode, onClick = onSave)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicText(
                    text = "Save",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                )
            }
        }
        BasicText(
            text = state.nameStatus.ifEmpty { "Shown next to your rank below. Leave blank to keep your auto-assigned name." },
            style = TextStyle(fontSize = 11.sp, color = textColor.copy(alpha = 0.53f))
        )
    }
}

// ── Единая таблица лидерборда (Alex 2026-09-10) ─────────────────────────────
// Раньше — pill-тумблер (SortToggle) переключал, КАКАЯ ОДНА метрика видна в
// строке. Теперь все 4 метрики (Progress/Coins/Accuracy/Combined) — отдельные
// КОЛОНКИ одной таблицы, видны одновременно; тап по заголовку колонки сортирует
// по ней (▲ у активной). Сортировка осталась СЕРВЕРНОЙ (как и раньше — тап по
// заголовку это тот же Event.SelectSort, что раньше слал pill-тумблер): сервер
// может отдавать РАЗНЫЙ top-N набор игроков под разные метрики, поэтому смена
// колонки — это новый fetch, а не переупорядочивание уже загруженного списка.

// Ширины колонок расширены (Alex 2026-09-10: "выровняй все заголовки
// относительно своих колонок") — с прошлого шага заголовки выросли до 12sp
// bold + стрелка "▲" у активной колонки, и при старых узких ширинах текст
// заголовка вылезал ЗА пределы своей колонки (переполнение уходит влево,
// т.к. у заголовка Arrangement.End и Compose не клипует контент по
// умолчанию) — из-за этого заголовок визуально съезжал относительно чисел
// под ним. Ширины подобраны с запасом под "Acc% ▲"/самый длинный вариант.
private val ColRank  = 24.dp
private val ColLevel = 44.dp
private val ColCoins = 52.dp
private val ColAcc   = 48.dp
private val ColLuck  = 48.dp

/** Короткая подпись колонки — полные LeaderboardSort.label ("Progress" и т.п.)
 *  не помещаются в компактную колонку таблицы. SC — пустая строка: в шапке
 *  вместо текста рисуется CoinIcon (Alex 2026-09-10, "SC заменить на
 *  монетку, ту что на первом экране"). */
private fun LeaderboardSort.shortLabel(): String = when (this) {
    LeaderboardSort.LEVEL    -> "Lvl"
    LeaderboardSort.SC       -> ""
    LeaderboardSort.HITRATE  -> "Acc%"
    LeaderboardSort.COMBINED -> "Luck"
}

// COMBINED column shows Lucky Index, not attemptsTotal/combinedScore (Alex
// 2026-09-10: "lucky coificient вместо score") — server-computed, see
// leaderboard.php's doc-comment for the formula and why it's a simplified
// (untrimmed-range) version of the app's headline Lucky Index. Column stays
// bound to the COMBINED server-side sort when tapped, only the displayed
// value changed (same precedent as the earlier attemptsTotal swap).
private fun LeaderboardRow.metricFor(sort: LeaderboardSort): String = when (sort) {
    LeaderboardSort.LEVEL    -> "$levelId.$taskId"
    LeaderboardSort.SC       -> formatOneDecimal(scTotal)
    LeaderboardSort.HITRATE  -> "%.1f".format(hitRatePct)
    LeaderboardSort.COMBINED -> "%.2f".format(luckyIndex)
}

@Composable
private fun RowsPanel(
    state: LeaderboardContract.State,
    onSelectSort: (LeaderboardSort) -> Unit,
    onRetry: () -> Unit,
    onSelectRow: (LeaderboardRow) -> Unit = {},
    helpActive: Boolean = false,
    onHelpToggle: () -> Unit = {},
    onQuestionRect: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent = MaterialTheme.colorScheme.accentColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(panelBg)
            // Горизонтальный паддинг ужат отдельно от вертикального (Alex
            // 2026-09-10) — таблице нужна ширина, вертикальный воздух не мешает.
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                modifier = Modifier.padding(start = 6.dp),
                text = "LEADERBOARD",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.63f))
            )
            // "?" — та же интерактивная справка, что на Main/Analytics (Alex 2026-09-10).
            Box(
                modifier = Modifier
                    .size(22.dp)
                    // Жёлтый в режиме подсказок — см. MainScreen (Alex 2026-09-27).
                    .border(
                        width = 1.5.dp,
                        color = (if (helpActive) MaterialTheme.colorScheme.helpAccentColor else accent)
                            .copy(alpha = if (helpActive) 1f else 0.75f),
                        shape = CircleShape
                    )
                    .onGloballyPositioned { onQuestionRect(it.boundsInRoot()) }
                    .clickable { onHelpToggle() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (helpActive) "×" else "?",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = (if (helpActive) MaterialTheme.colorScheme.helpAccentColor else accent).copy(alpha = 0.95f))
                )
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            state.error -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = "Could not reach the leaderboard right now.",
                    style = TextStyle(fontSize = 13.sp, color = textColor.copy(alpha = 0.63f))
                )
                BasicText(
                    text = "Try again",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent),
                    modifier = Modifier.clickable(enabled = !helpActive, onClick = onRetry)
                )
            }
            state.rows.isEmpty() -> BasicText(
                text = "No players yet — be the first!",
                style = TextStyle(fontSize = 13.sp, color = textColor.copy(alpha = 0.63f))
            )
            else -> Column {
                LeaderboardHeaderRow(selected = state.sort, descending = state.descending, onSelectSort = onSelectSort, helpMode = helpActive)
                val bestFirst = remember(state.rows, state.sort) { state.rows.sortedBestFirst(state.sort) }
                val shown = if (state.descending) bestFirst else bestFirst.asReversed()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEachIndexed { index, row ->
                        LeaderboardRowItem(
                            // Номер — реальное место в рейтинге по этой колонке, а не
                            // позиция на экране: при возрастании худший не «№1».
                            index = if (state.descending) index else shown.size - 1 - index,
                            row = row,
                            sort = state.sort,
                            mine = row.playerId == state.myPlayerId,
                            onClick = { onSelectRow(row) },
                            helpMode = helpActive
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardHeaderRow(selected: LeaderboardSort, descending: Boolean, onSelectSort: (LeaderboardSort) -> Unit, helpMode: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent = MaterialTheme.colorScheme.accentColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(ColRank))
        Spacer(Modifier.weight(1f))
        LeaderboardSort.entries.forEach { sort ->
            val isSelected = sort == selected
            val colWidth = when (sort) {
                LeaderboardSort.LEVEL    -> ColLevel
                LeaderboardSort.SC       -> ColCoins
                LeaderboardSort.HITRATE  -> ColAcc
                LeaderboardSort.COMBINED -> ColLuck
            }
            val labelColor = if (isSelected) accent else textColor.copy(alpha = 0.5f)
            if (sort == LeaderboardSort.SC) {
                // Монетка вместо подписи "SC" (Alex 2026-09-10) — фиксированный
                // маленький размер, стрелка сортировки рядом с ней, а не внутри
                // общего текстового блока: колонка достаточно широкая (под числа
                // значений ниже), так что ни один вариант не переполняет её.
                // Монетка строго по центру колонки (Alex 2026-09-19); стрелка
                // отдельным слоем у правого края, чтобы не сдвигать центр.
                Box(
                    modifier = Modifier
                        .width(colWidth)
                        .clickable(enabled = !helpMode) { onSelectSort(sort) },
                    contentAlignment = Alignment.Center
                ) {
                    CoinIcon(modifier = Modifier.size(13.dp))
                    if (isSelected) {
                        BasicText(
                            text = if (descending) "▼" else "▲",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            style = TextStyle(fontSize = 10.sp, color = accent)
                        )
                    }
                }
            } else {
                // Один Text на всю ячейку (label + стрелка вместе, а не два
                // отдельных composable в Row) — так же, как значения строк ниже
                // (BasicText.width(colWidth) + TextAlign.End). Раньше это был Row
                // из двух Text с Arrangement.End: если их суммарная ширина
                // превышала colWidth, Row не клипует лишнее — оно молча вылезало
                // ЗА левый край колонки (Compose не обрезает контент по
                // умолчанию), и заголовок визуально съезжал относительно чисел
                // под ним. Единый Text с фиксированной шириной либо переносится
                // на вторую строку, либо ужимается — но никогда не съезжает.
                BasicText(
                    text = if (isSelected) "${sort.shortLabel()} ${if (descending) "▼" else "▲"}" else sort.shortLabel(),
                    modifier = Modifier
                        .width(colWidth)
                        .clickable(enabled = !helpMode) { onSelectSort(sort) },
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelColor,
                        textAlign = TextAlign.End
                    )
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRowItem(index: Int, row: LeaderboardRow, sort: LeaderboardSort, mine: Boolean, onClick: () -> Unit = {}, helpMode: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent = MaterialTheme.colorScheme.accentColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (mine) accent.copy(alpha = 0.12f) else Color.Transparent)
            // Alex 2026-09-15: "при тюке на лидборд в режиме хелпа отрабатывает
            // сам лидборд, а должен хелп" — same root cause as StatScreen's
            // tree rows (see .clickable(enabled = !helpMode) there): Compose's
            // Main pass processes children BEFORE the ancestor Box's own
            // helpTapGestures pointerInput, so an always-enabled row click
            // consumed the tap-up first and helpTapGestures silently bailed
            // (`if (event.changes.any { it.isConsumed }) break`) without ever
            // selecting a help zone. Disabling the click during help mode lets
            // the tap reach the ancestor unconsumed.
            .clickable(enabled = !helpMode, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "${index + 1}",
            modifier = Modifier.width(ColRank),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.53f))
        )
        BasicText(
            text = (row.displayName ?: "Anonymous") + if (mine) " (you)" else "",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                color = if (mine) accent else textColor
            )
        )
        LeaderboardSort.entries.forEach { col ->
            val colWidth = when (col) {
                LeaderboardSort.LEVEL    -> ColLevel
                LeaderboardSort.SC       -> ColCoins
                LeaderboardSort.HITRATE  -> ColAcc
                LeaderboardSort.COMBINED -> ColLuck
            }
            val isActiveCol = col == sort
            BasicText(
                text = online.playoftime.features.stats.ui.groupDigits(row.metricFor(col)),   // разделители разрядов везде (Alex 11.11)
                modifier = Modifier.width(colWidth),
                style = TextStyle(
                    fontSize = 12.5.sp,
                    fontWeight = if (isActiveCol) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActiveCol) accent else textColor.copy(alpha = 0.85f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            )
        }
    }
}

// ── Сравнение с игроком (Alex 2026-09-11) ───────────────────────────────────
// Тап по любой строке таблицы → окно "я vs он". Метрики взяты из первых трёх
// карточек Analytics (CHANCE OF WIN / GEOMETRIC MEAN ÷ SPREAD / RANGE OF
// DEVIATIONS), но реально показаны только те, что можно честно посчитать по
// данным, которые СЕРВЕР уже отдаёт для ЛЮБОГО игрока (LeaderboardRow —
// агрегаты, не сырые попытки): Hit rate, Attempts per win и Lucky Index из
// CHANCE OF WIN целиком совпадают 1:1 с формулами на экране Analytics.
// GEOMETRIC MEAN ÷ SPREAD и RANGE OF DEVIATIONS нужны P3/P97 и geometric mean
// по сырым ошибкам — этого leaderboard.php сейчас не отдаёт ни для кого,
// кроме себя (сырые попытки других игроков API не возвращает) — честнее
// вообще не показывать эти карточки, чем подделать цифры. Вместо них —
// Progress (Lvl.Task) и общий SC, тоже уже в LeaderboardRow, и не менее
// показательны для "кто вообще успешнее".
private data class CompareMetric(
    val label: String,
    val mineDisplay: String,
    val otherDisplay: String,
    val mineBetter: Boolean?, // null = ничья/не сравнивается направленно
    val note: String? = null
)

@Composable
private fun ComparePlayersDialog(
    mine: LeaderboardRow?,
    other: LeaderboardRow,
    onDismiss: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.baseTextColor
    val accent = MaterialTheme.colorScheme.accentColor
    val dimColor = textColor.copy(alpha = 0.55f)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(panelBg)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = "COMPARE",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.63f))
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(text = "×", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor))
                }
            }
            // Переключатель "на одном уровне" (Alex 2026-09-19): если я на 3-м, а он
            // на 2-м — показать МОЮ статистику тоже за 2-й уровень. Имеет смысл
            // только когда уровни различаются.
            val sharedLevel = if (mine != null && mine.levelId != other.levelId) minOf(mine.levelId, other.levelId) else null
            var sameLevel by remember { mutableStateOf(false) }
            if (sharedLevel != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(false to "All history", true to "Same level ($sharedLevel)").forEach { (value, label) ->
                        val on = sameLevel == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { sameLevel = value }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = label,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color = if (on) accent else dimColor
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Шапка с именами — сразу видно, какая колонка чья, до самих строк.
            Row(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = "You",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
                Spacer(Modifier.width(58.dp))
                BasicText(
                    text = other.displayName ?: "Anonymous",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                )
            }
            Spacer(Modifier.height(14.dp))

            if (mine == null) {
                BasicText(
                    text = "Your own stats aren't in this sorted view right now — try a different sort, or Save your name once to make sure you're tracked.",
                    style = TextStyle(fontSize = 12.sp, color = dimColor)
                )
            } else {
                val metrics = buildCompareMetrics(mine, other, if (sameLevel) sharedLevel else null)
                metrics.forEachIndexed { i, m ->
                    CompareMetricRow(m, accent, textColor, dimColor)
                    if (i != metrics.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** "Начал N дн. назад" из ISO-UTC метки; null -> "—". */
private fun startedAgo(iso: String?): String {
    if (iso == null) return "—"
    val t = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return "—"
    val days = ((System.currentTimeMillis() - t) / 86_400_000L).coerceAtLeast(0)
    return when {
        days == 0L -> "today"
        days < 60  -> "$days d ago"
        else       -> "${days / 30} mo ago"
    }
}

/** Одинаковый набор цифр игрока: за всю историю или за один уровень. */
private data class CompareSide(
    val progress: String?, val progressScore: Double?,
    val attempts: Int, val wins: Int, val sc: Double,
    val hit: Double, val lucky: Double, val startedIso: String?
)

private fun LeaderboardRow.side(tier: Int?): CompareSide? {
    if (tier == null) return CompareSide(
        "$levelId.$taskId", combinedScore, attemptsTotal, winsTotal, scTotal, hitRatePct, luckyIndex, firstAttemptAt
    )
    val t = tiers[tier] ?: return null
    return CompareSide(null, null, t.attempts, t.wins, t.scTotal, t.hitRatePct, t.luckyIndex, t.firstAttemptAt)
}

private fun buildCompareMetrics(mine: LeaderboardRow, other: LeaderboardRow, tier: Int? = null): List<CompareMetric> {
    val m = mine.side(tier)
    val o = other.side(tier)
    fun apw(s: CompareSide?) = if (s != null && s.wins > 0) s.attempts.toDouble() / s.wins else null
    val myApw = apw(m)
    val otApw = apw(o)
    fun <T : Comparable<T>> better(a: T?, b: T?): Boolean? = if (a == null || b == null || a == b) null else a > b
    val list = mutableListOf<CompareMetric>()
    if (tier == null) {
        list += CompareMetric("Progress", m?.progress ?: "—", o?.progress ?: "—", better(m?.progressScore, o?.progressScore))
    }
    list += CompareMetric(
        "Attempts", m?.attempts?.let { online.playoftime.features.stats.ui.groupDigits(it.toString()) } ?: "—", o?.attempts?.let { online.playoftime.features.stats.ui.groupDigits(it.toString()) } ?: "—", better(m?.attempts, o?.attempts)
    )
    list += CompareMetric(
        if (tier == null) "Started" else "Started level $tier",
        startedAgo(m?.startedIso), startedAgo(o?.startedIso),
        null // "давно" не лучше и не хуже — просто факт
    )
    list += CompareMetric(
        "Serenity Coins", m?.sc?.let { online.playoftime.features.stats.ui.groupDigits(formatOneDecimal(it)) } ?: "—", o?.sc?.let { online.playoftime.features.stats.ui.groupDigits(formatOneDecimal(it)) } ?: "—", better(m?.sc, o?.sc)
    )
    list += CompareMetric(
        "Hit rate", m?.let { "%.1f%%".format(it.hit) } ?: "—", o?.let { "%.1f%%".format(it.hit) } ?: "—", better(m?.hit, o?.hit)
    )
    list += CompareMetric(
        "Attempts per win",
        myApw?.let { "%.1f".format(it) } ?: "—", otApw?.let { "%.1f".format(it) } ?: "—",
        // Меньше — лучше (эффективнее выбивает чистые тройки).
        if (myApw == null || otApw == null || myApw == otApw) null else myApw < otApw
    )
    list += CompareMetric(
        "Lucky Index", m?.let { "%.2f".format(it.lucky) } ?: "—", o?.let { "%.2f".format(it.lucky) } ?: "—", better(m?.lucky, o?.lucky)
    )
    return list
}

@Composable
private fun CompareMetricRow(m: CompareMetric, accent: Color, textColor: Color, dimColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = m.label,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontSize = 10.5.sp, color = dimColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = m.mineDisplay,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = if (m.mineBetter == true) FontWeight.Bold else FontWeight.Normal,
                    color = if (m.mineBetter == true) accent else if (m.mineBetter == false) dimColor else textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
            Box(
                modifier = Modifier.width(58.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(text = "vs", style = TextStyle(fontSize = 10.sp, color = dimColor))
            }
            BasicText(
                text = m.otherDisplay,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = if (m.mineBetter == false) FontWeight.Bold else FontWeight.Normal,
                    color = if (m.mineBetter == false) accent else if (m.mineBetter == true) dimColor else textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
        }
    }
}

// Locale.US закреплён явно (Alex 04.09: «в числах нет разделителя, отступ
// есть а разделителя нет»): без Locale здесь используется locale устройства
// — на любом не-английском (ru, de, fr, ...) запятая в "%,.1f" оказывается
// ДЕСЯТИЧНЫМ разделителем ("384,4"), а не разрядным, и .replace(",", " ")
// стирал именно её — 384.4 превращалось в "384 4" без всякого разделителя.
// С Locale.US запятая гарантированно только разрядная, точка — десятичная.
private fun formatOneDecimal(v: Double): String =
    String.format(java.util.Locale.US, "%,.1f", v).replace(",", " ")
