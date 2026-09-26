package ru.iflex.timemvp.features.main.ui

import ru.iflex.timemvp.core.ViewEvent
import ru.iflex.timemvp.core.ViewSideEffect
import ru.iflex.timemvp.core.ViewState
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.ButtonResultType
import ru.iflex.timemvp.core.utils.Hand
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.core.utils.Quest
import ru.iflex.timemvp.core.utils.StopWatchCycle
import ru.iflex.timemvp.core.utils.CheerUpTrigger
import ru.iflex.timemvp.core.utils.ErrorPattern
import ru.iflex.timemvp.core.utils.questList
import ru.iflex.timemvp.database.SessionDatabaseModel
import ru.iflex.timemvp.features.main.domain.BalanceSide
import ru.iflex.timemvp.features.main.domain.DistribBucket
import ru.iflex.timemvp.features.main.domain.MainMetrics
import ru.iflex.timemvp.features.main.domain.FailCardData
import ru.iflex.timemvp.features.main.domain.QuestCompletionStats
import ru.iflex.timemvp.features.main.domain.SessionComparisonStats

class MainContract {
    data class State(
        val yourAnr: String = "",
        val attempts: List<SessionStatisticItemState> = listOf(),
        // NOTE: state.time УДАЛЕНО (Alex 2026-07-14). Отображение секундомера
        // теперь идёт через отдельный MainViewModel.stopwatchTime: State<String> —
        // тик × 16 мс больше не пересобирает весь MainScreen composable
        // (BaseViewModel.viewState использует structuralEqualityPolicy; смена
        // state.time при неизменных остальных полях всё равно инвалидировала
        // всех читателей viewState, потому что MainScreen читает viewState.value).
        val accuracy: Accuracy = Accuracy.MILLISECONDS,
        val taskTitle: String = "",

        //состояния
        val stopWatchState: StopWatchCycle = StopWatchCycle.ON_START,

        //попытка
        val startTime: Long = -1L,
        val endTime: Long = -1L,

        // Welcome-сообщение первого запуска: true пока пользователь не закрыл
        // большое модальное окно с описанием игры (показывается один раз).
        val showWelcome: Boolean = false,

        //сессия
        val currentSession: SessionDatabaseModel? = null,
        // Глобальный порядковый номер currentSession (Alex 2026-09-22) —
        // позиция сессии в СПИСКЕ ВСЕХ сессий, отсортированном по startTime
        // (oldest = #1), СЧИТАЕТСЯ ТАК ЖЕ, как в GetLogAttemptsUseCase и
        // AnalyticsUseCase. Раньше "Session #N" разделитель, вставляемый
        // ЖИВЬЁМ в лог во время игры, брал число прямо из currentSession.id
        // (сырой autoincrement) — почти всегда совпадает с датовым номером,
        // но НЕ гарантированно (гэпы в id из-за удалённых/фантомных сессий
        // ломают совпадение) — отсюда жалоба "номер не привязан к дате".
        // Считается ОДИН раз, сразу после addSession() (suspend-контекст, см.
        // Press-handler ON_START), а не заново на каждую попытку.
        val currentSessionNumber: Int? = null,
        val displayGreetingView: Boolean = false,
        val greetingViewText: String = "",
        val questCompletionStats: QuestCompletionStats? = null,
        // true после победы в квесте — кнопка показывает "HURRAY!", GreetingView откладывается
        val awaitingHurray: Boolean = false,
        // true после провала окна — кнопка держит результат, показывает "OK", чирап откладывается
        val awaitingFailResult: Boolean = false,
        // true после автозакрытия сессии — кнопка держит результат, показывает "OK", оверлей откладывается
        val awaitingSessionResult: Boolean = false,
        // true после субквестной победы с шагом назад (Rule 3 в onQuestWindowSuccess) —
        // кнопка держит результат победной попытки до тапа, затем стартует новая сессия
        // на родительском (менее индульгентном) уровне.
        val awaitingContractionResult: Boolean = false,

        //текущий уровень
        val currentQuest: Quest = questList[0],

        //выбранная рука
        val selectedHand: Hand = Hand.RIGHT,

        // --- Новые метрики (Фаза 1) ---
        // Serenity Coins
        val totalSerenityBalance: Double = 0.0,
        val todaySerenityDelta: Double = 0.0,

        // Balance (количество red/blue ошибок за период)
        val redCount: Int = 0,
        val blueCount: Int = 0,
        val balanceSide: BalanceSide = BalanceSide.NEUTRAL,

        // True Three Hit Rate
        val trueThreeCount: Int = 0,
        val trueThreeHitRate: Double = 0.0,

        // Geometric Mean (мс)
        val geomMeanEarlyMs: Double = 0.0,
        val geomMeanLateMs: Double = 0.0,
        val geomMeanAbsoluteOffsetMs: Double = 0.0,

        // Шкала и гистограмма
        val scaleMaxMs: Double = 200.0,
        val distribBuckets: List<DistribBucket> = emptyList(),

        // Фильтр периода
        val periodFilter: PeriodFilter = PeriodFilter.SESSION,

        // Тип результата последней попытки (цвет кнопки)
        val buttonResultType: ButtonResultType = ButtonResultType.NONE,

        // Попытки для отображения в логе (SESSION = копия attempts, TODAY/ALL_TIME = из БД)
        val displayedAttempts: List<SessionStatisticItemState> = emptyList(),

        // Персистентный лог — никогда не очищается, нумерация 1–33 циклически
        val logBuffer: List<SessionStatisticItemState> = emptyList(),

        // Детектированный паттерн ошибок текущей сессии
        val errorPattern: ErrorPattern = ErrorPattern.NONE,

        // Cheer-Up
        val cheerUpTrigger: CheerUpTrigger = CheerUpTrigger.NONE,
        val cheerUpSpecificText: String = "",
        val cheerUpGeneralText: String = "",
        val cheerUpAttemptsLeft: Int = 0,

        // Отложенный чирап — показывается когда закрывается GreetingView
        val pendingCheerUpTrigger: CheerUpTrigger = CheerUpTrigger.NONE,
        // Квест был IN_N-индульгированным (расширенное N), но обе/все победы легли
        // достаточно близко друг к другу, чтобы формально уложиться в baseline-окно
        // (Alex 2026-08-23, gap-based Rule 2 — см. onQuestWindowSuccess). Бабл
        // добавляет отдельный абзац «выполнил условие, не заметив индульгенции».
        val pendingCheerUpInNGapNote: Boolean = false,

        // Twin shot — короткое анимированное поздравление в области квеста (1 секунда).
        val twinShotVisible: Boolean = false,

        // "It was close!" — для 2.999 / 3.001 на миллисекундном уровне (1 секунда).
        val itWasCloseVisible: Boolean = false,

        // "Wow, neighbors!" — две подряд СОСЕДНИЕ ошибки (± одна клетка точности).
        val neighborShotVisible: Boolean = false,

        // Статистика сессии — показывается в оверлее при AFTER_SESSION
        val sessionComparisonStats: SessionComparisonStats? = null,


        // ── Данные для символической индикации прогресса квеста ──────────
        // Отформатированное aim time под точность текущего квеста.
        val questAimStr: String = "3.0",
        // "" (baseline) или "± 0.001" / "± 0.01" / "± 0.1" (в субквесте).
        val questToleranceStr: String = "",
        // Сырое значение допуска в мс (0 = baseline) — для зелёной зоны на баре.
        val questToleranceMs: Int = 0,
        // true если сейчас в индульгентной версии квеста (N > baseN OR t > baseT).
        val questIsSubquest: Boolean = false,
        // Ступень индульгенции субквеста (1/2/3); 0 = baseline. Для нумерации "N.k".
        val questSubStep: Int = 0,
        // Актуальное N для текущего окна (33/66/99). null = без лимита.
        val questWindowLimit: Int? = null,
        // Авто-масштаб прогресс-бара: полуокно в мс (край = 3.000 ± это).
        // Подстраивается под результаты игрока; MainScreen плавно анимирует к нему.
        val autoBarHalfMs: Int = 150,

        // Самообновление вне Google Play (Alex 2026-09-22): проверяется один раз
        // при старте (см. UpdateApi/MainViewModel.init). updateApkUrl != null —
        // сигнал MainScreen показать кнопку обновления в левом верхнем углу.
        val updateApkUrl: String? = null,

        // ── Fail-карточка (Alex 2026-07-13) ──────────────────────────────
        // Показывается ПОСЛЕ тапа RESET из awaitingFailResult, ДО активации
        // бабла. Симметричный аналог GreetingView для fail-пути.
        val displayFailCard: Boolean = false,
        val failCardData: FailCardData? = null

        ) : ViewState

    sealed class Event : ViewEvent {
        sealed class StopWatchButton : Event() {
            /**
             * touchWallMs / touchUptimeMs — метки времени kernel-level касания сенсора
             * (PointerInputChange.uptimeMillis), захваченные в pointerInput на
             * PointerEventPass.Initial pass'e ДО того как событие пройдёт через
             * Compose-диспетчер. attemptDuration считается из uptime (монотонный,
             * не подвержен NTP-скачкам), а wall-версия хранится в БД для
             * человеко-читаемой временной метки попытки.
             */
            data class Press(
                val touchWallMs: Long,
                val touchUptimeMs: Long
            ) : StopWatchButton()
        }

        sealed class GreetingView : Event() {
            data object Close: GreetingView()
        }

        sealed class FailCard : Event() {
            data object Close: FailCard()
        }

        data class SelectHand(val hand: Hand) : Event()
        data class SelectPeriod(val filter: PeriodFilter) : Event()

        data object DismissCheerUp : Event()

        /** Закрытие welcome-окна первого запуска (флаг пишется в LocalStorage). */
        data object DismissWelcome : Event()

        // ── Play-time трекинг: MainScreen отправляет по lifecycle ─────────────
        /** MainScreen перешёл в RESUMED — начать тикать Play Time (если сессия открыта). */
        data object OnScreenActive : Event()
        /** MainScreen перешёл в PAUSED (навигация или backgrounded) — flush тикер. */
        data object OnScreenInactive : Event()
    }

    sealed class Effect : ViewSideEffect
}
