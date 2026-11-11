package online.playoftime.features.stats.ui

import online.playoftime.core.ViewEvent
import online.playoftime.core.ViewSideEffect
import online.playoftime.core.ViewState
import online.playoftime.core.utils.PeriodFilter
import online.playoftime.features.stats.domain.entity.AnalyticsData

class StatsContract {
    data class State(
        val selectedPeriod: PeriodFilter = PeriodFilter.ALL_TIME,
        // Аналитика
        val analytics: AnalyticsData? = null,
        val analyticsLoading: Boolean = true,
        // Дерево: какие узлы раскрыты
        val expandedYears: Set<Int> = emptySet(),
        val treeDefaultApplied: Boolean = false,
        val expandedMonths: Set<String> = emptySet(),
        val expandedWeeks: Set<String> = emptySet(),
        val expandedDays: Set<String> = emptySet(),
        val expandedSessions: Set<Int> = emptySet(),
        // ExportToExcel
        val displayExportToExcelResultDialog: Boolean = false,
        val exportToExcelResultDialogText: String = "",
        val displayAppSpinner: Boolean = false,
        // ── Doppelgänger (Alex 2026-11-08) ──────────────────────────────────
        /** Переключатель You | AI: true — весь экран показывает соперника. */
        val showRival: Boolean = false,
        /** Чьи данные сейчас в [analytics] — чтобы при переключении показать спиннер. */
        val analyticsIsRival: Boolean = false,
        /** Дни соперника за выбранный период (одна запись на день), старые первыми. */
        val rivalDays: List<online.playoftime.features.rival.RivalEngine.DayDigest> = emptyList(),
        /** Его попытки для страницы отчёта (морда и отчёт — его, не игрока). */
        val rivalReportJson: String? = null,
        /** Текущий квест соперника, "2.3". */
        val rivalQuest: String = "",
        val diaryExpanded: Boolean = true,
        val expandedDiaryDays: Set<String> = emptySet(),
        /** Записи языковой модели по дням (ключ yyyyMMdd). */
        val diaryLlm: Map<String, online.playoftime.features.rival.RivalEngine.LlmEntry> = emptyMap(),
        /** Дни, для которых запись сейчас пишется / не удалась / слишком мало попыток. */
        val diaryWriting: Set<String> = emptySet(),
        val diaryFailed: Set<String> = emptySet(),
        val diaryTooFew: Set<String> = emptySet(),
        /** Сервер модели не ответил — до перезапуска больше не просим (хостинг в
         *  России, OpenRouter его не пускает; Alex 08.11: «пусть генерится шаблонами»). */
        val diaryWriterDown: Boolean = false,
        // ── Круг 11.11.2026: экран AI — только сравнения ─────────────────────
        /** Имя, введённое игроком руками; null — «You». */
        val ownName: String? = null,
        /** Аналитика игрока за тот же период — правый столбец сравнения в режиме AI. */
        val playerAnalytics: AnalyticsData? = null,
        /** Квест, достигнутый к каждой попытке (индекс в questList): игрок, соперник. */
        val curveYou: List<Int> = emptyList(),
        val curveAi: List<Int> = emptyList(),
        /** Текущий квест игрока, "2.3". */
        val playerQuest: String = "",
        /** Квест крупно с индульгенцией и условие — игрок и соперник. */
        val playerBadge: Pair<String, String> = "" to "",
        val rivalBadge: Pair<String, String> = "" to "",
        /** Недельные размышления соперника, ключ "год-неделя" — в дереве сессий. */
        val weeklyNotes: Map<String, String> = emptyMap(),
    ) : ViewState

    sealed class Event : ViewEvent {
        data object ExportToExel : Event()
        data class SelectPeriod(val period: PeriodFilter) : Event()
        /** Клювики влево/вправо под фильтром (Alex 2026-09-09): delta=+1 — на
         *  период назад (старше), delta=-1 — на период вперёд (к "сейчас"). */
        data class ShiftPeriod(val delta: Int) : Event()
        /** Перезагрузка данных под текущий период — StatScreen шлёт при каждом
         *  выходе на передний план (HorizontalPager держит экран живым, init
         *  отработал один раз — без Refresh данные протухали). */
        data object Refresh : Event()
        data class ToggleYear(val year: Int) : Event()
        data class ToggleMonth(val key: String) : Event()
        data class ToggleWeek(val key: String) : Event()
        data class ToggleDay(val key: String) : Event()
        data class ToggleSession(val sessionId: Int) : Event()
        /** You | AI (Doppelgänger). */
        data class SelectWho(val rival: Boolean) : Event()
        data object ToggleDiary : Event()
        data class ToggleDiaryDay(val key: String) : Event()
        /** Открыт день дневника — попросить запись языковой модели (если её нет
         *  или сегодняшняя устарела). Повтор после неудачи — тот же event. */
        data class RequestDiaryEntry(val key: String, val retry: Boolean = false) : Event()

        sealed class Dialog : Event() {
            data class Display(val text: String) : Dialog()
            data object Close : Dialog()
        }

        sealed class AppSpinner : Event() {
            data object Display : AppSpinner()
            data object Close : AppSpinner()
        }
    }

    sealed class Effect : ViewSideEffect
}
