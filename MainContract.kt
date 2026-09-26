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
import ru.iflex.timemvp.core.utils.defaultAimTime
import ru.iflex.timemvp.core.utils.questList
import ru.iflex.timemvp.database.SessionDatabaseModel
import ru.iflex.timemvp.features.main.domain.BalanceSide
import ru.iflex.timemvp.features.main.domain.DistribBucket
import ru.iflex.timemvp.features.main.domain.MainMetrics
import ru.iflex.timemvp.features.main.domain.QuestCompletionStats

class MainContract {
    data class State(
        val title: String = "",//"Главный экран"
        val yourAnr: String = "",
        val attempts: List<SessionStatisticItemState> = listOf(),
        val time: String = "00:00.000",
        val accuracy: Accuracy = Accuracy.MILLISECONDS,
        val taskTitle: String = "",
        val aimTime: String = String.format("%,.3f", defaultAimTime / 1000.0).replace(",", "."),

        //состояния
        val stopWatchState: StopWatchCycle = StopWatchCycle.ON_START,
        val isSoundMode: Boolean = false,
        val isVibrationMode: Boolean = false,
        val isStopButtonActive: Boolean = false,

        //попытка
        val startTime: Long = -1L,
        val endTime: Long = -1L,

        //сессия
        val currentSession: SessionDatabaseModel? = null,
        val displayGreetingView: Boolean = false,
        val greetingViewText: String = "",
        val questCompletionStats: QuestCompletionStats? = null,

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

        // Детектированный паттерн ошибок текущей сессии
        val errorPattern: ErrorPattern = ErrorPattern.NONE,

        // Cheer-Up
        val cheerUpTrigger: CheerUpTrigger = CheerUpTrigger.NONE,
        val cheerUpSpecificText: String = "",
        val cheerUpGeneralText: String = "",

        // Отложенный чирап — показывается когда закрывается GreetingView
        val pendingCheerUpTrigger: CheerUpTrigger = CheerUpTrigger.NONE

        ) : ViewState

    sealed class Event : ViewEvent {
        sealed class StopWatchButton : Event() {
            data object Press : StopWatchButton()
            data object Release : StopWatchButton()
        }

        sealed class GreetingView : Event() {
            data object Display: GreetingView()
            data object Close: GreetingView()
        }

        data class SelectHand(val hand: Hand) : Event()
        data class SelectPeriod(val filter: PeriodFilter) : Event()

        data object DismissCheerUp : Event()
    }

    sealed class Effect : ViewSideEffect
}
