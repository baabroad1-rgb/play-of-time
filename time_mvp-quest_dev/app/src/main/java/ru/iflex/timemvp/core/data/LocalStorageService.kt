package ru.iflex.timemvp.core.data

import android.content.Context
import ru.iflex.timemvp.core.utils.Accuracy
import ru.iflex.timemvp.core.utils.Hand
import ru.iflex.timemvp.core.utils.PeriodFilter
import ru.iflex.timemvp.core.utils.defaultAimTime
import ru.iflex.timemvp.core.utils.defaultBeatsPerSecond

class LocalStorageException : Exception() {
}

interface LocalStorageServiceContract {
    fun putSoundOn(value: Boolean)
    fun getSoundOn(): Boolean

    fun putVibrationOn(value: Boolean)
    fun getVibrationOn(): Boolean

    fun putBeatsPerSecond(value: Int)
    fun getBeatsPerSecond(): Int

    fun putAimTime(value: Int)
    fun getAimTime(): Int

    fun putAccuracy(value: Accuracy)
    fun getAccuracy(): Accuracy

    fun putCurrentLevelId(value: Int)
    fun getCurrentLevelId(): Int

    fun putCurrentTaskId(value: Int)
    fun getCurrentTaskId(): Int

    fun putSelectedHand(value: Hand)
    fun getSelectedHand(): Hand

    // ── Адаптивные параметры квеста ─────────────────────────────────────────
    /** Текущее N (лимит попыток). -1 = без лимита. */
    fun putQuestCurrentN(value: Int)
    fun getQuestCurrentN(): Int

    /** Текущее t (допуск, мс). 0 = точное попадание. */
    fun putQuestCurrentToleranceMs(value: Int)
    fun getQuestCurrentToleranceMs(): Int

    /** Кол-во попыток, использованных в текущем N-окне. */
    fun putQuestWindowAttempts(value: Int)
    fun getQuestWindowAttempts(): Int

    /** Кол-во побед в текущем N-окне (для условия IN_N). */
    fun putQuestWindowWins(value: Int)
    fun getQuestWindowWins(): Int

    /** Максимальная |error| (мс) среди ПОБЕДНЫХ попыток в текущем окне — для ROW квестов. */
    fun putQuestWindowMaxErrorMs(value: Int)
    fun getQuestWindowMaxErrorMs(): Int

    /** Позиция последней победной попытки в текущем окне (1..N) — для IN_N квестов. */
    fun putQuestWindowLastWinIndex(value: Int)
    fun getQuestWindowLastWinIndex(): Int

    /**
     * Позиция ПЕРВОЙ победной попытки в текущем окне (1..N) — для IN_N квестов
     * (Alex 2026-08-23). Вместе с [getQuestWindowLastWinIndex] даёт РЕАЛЬНЫЙ
     * промежуток между победами: `lastWinIndex − firstWinIndex + 1`. Правило
     * Alex: если этот промежуток укладывается в baseN — квест формально
     * пройден на baseline-условиях, даже если N был расширен индульгенцией
     * (игрок «выполнил условие, забыв о нём»). НЕ сбрасывается промахом между
     * победами — только сбросом самого окна (в отличие от worst-win трекера
     * ROW-квестов, который сбрасывается на промахе).
     */
    fun putQuestWindowFirstWinIndex(value: Int)
    fun getQuestWindowFirstWinIndex(): Int

    /**
     * true, если текущий квест В ЭТОМ прохождении хоть раз уходил в субквест
     * (окно провалилось, N/t были ослаблены). Сбрасывается в false при advance
     * на следующий квест. Используется для разделения cheer-up'ов:
     *   AFTER_QUEST — квест взят с первого раза (флаг false)
     *   AFTER_SIMPLIFIED_QUEST_DONE — квест взят после хождения по субквестам (флаг true)
     */
    fun putQuestWasIndulged(value: Boolean)
    fun getQuestWasIndulged(): Boolean

    // ── Настройки чирапов ───────────────────────────────────────────────────
    fun getCheerUpsEnabled(): Boolean
    fun putCheerUpsEnabled(value: Boolean)

    /** Звуки салюта (фейерверк на GreetingView) + HOORAY! + sorry (провал
     *  квеста). Отдельно от [getCheerUpsEnabled] — та настройка про текст/
     *  подсветку, эта чисто про звук. Default true (Alex 2026-09-10). */
    fun getCelebrationSoundsEnabled(): Boolean
    fun putCelebrationSoundsEnabled(value: Boolean)

    /** Обводка близнецов/соседей (freehand-овал вокруг строк лога с похожими
     *  ошибками). Default true (Alex 2026-09-10). */
    fun getTwinOutlineEnabled(): Boolean
    fun putTwinOutlineEnabled(value: Boolean)

    /** Прогресс-бар секунд (1–4) на кнопке секундомера во время бега. */
    fun getShowProgressBar(): Boolean
    fun putShowProgressBar(value: Boolean)
    /** Полуокно масштаба прогресс-бара в мс (край = 3.000 ± это; default 100). */
    fun getBarHalfWindowMs(): Int
    fun putBarHalfWindowMs(value: Int)
    /** true — масштаб бара авто по уровню (деци/центи/милли); false — ручной. Default true. */
    fun getBarScaleAuto(): Boolean
    fun putBarScaleAuto(value: Boolean)
    /** Последний баланс red/blue — чтобы индикатор в нижнем меню жил на всех экранах. */
    fun getBalanceRed(): Int
    fun getBalanceBlue(): Int
    fun putBalance(red: Int, blue: Int)
    /** Последняя измеренная ячейка опроса тача (мс) — фоллбэк дизеринга до замера в сессии. */
    fun getTouchCellMs(): Float
    fun putTouchCellMs(value: Float)
    /** Числовой отсчёт на кнопке во время бега (результат после STOP показывается всегда). */
    fun getShowNumericRun(): Boolean
    fun putShowNumericRun(value: Boolean)

    /**
     * Возвращает следующий индекс recap-opener'а для данного триггера
     * и инкрементирует его в persistent storage. Ротация фраз (Alex 2026-07-13):
     * одинаковый триггер два раза подряд получит разный вводный кивок.
     * Ключ хранится per-trigger — независимые ротации.
     */
    fun nextCheerUpPhraseIndex(triggerName: String): Int

    // ── Выбранный фильтр периода (общий для главного и аналитического экранов) ──
    /** Период, выбранный пользователем (Session / Today / Month / All time).
     *  Общее значение между MainViewModel и StatsViewModel — оба экрана должны
     *  показывать данные за один и тот же период, чтобы геом средние совпадали. */
    fun getPeriodFilter(): PeriodFilter
    fun putPeriodFilter(value: PeriodFilter)

    // ── Флаги миграции ──────────────────────────────────────────────────────
    /** true = SCM пересчитан в мс-единицах (v3). */
    fun isScmRecalculated(): Boolean
    fun markScmRecalculated()

    // ── Первый запуск ────────────────────────────────────────────────────────
    /** true = welcome-сообщение первого запуска уже показано. */
    fun isWelcomeShown(): Boolean
    fun markWelcomeShown()

    // ── Полный сброс прогресса («начать сначала», Alex 2026-07-15) ──────────
    /**
     * Квест-прогресс → L1.1, адаптивные параметры → baseline, точность —
     * по квесту L1.1. Пишется ОДНИМ editor'ом через СИНХРОННЫЙ commit():
     * вызывается прямо перед Process.killProcess() — apply() (async) не
     * успевал доехать до диска, и после рестарта игрок оказывался на
     * СТАРОМ уровне (баг v12.35).
     */
    fun resetQuestProgressSync(accuracy: Accuracy)

    // ── Leaderboard-идентичность (Alex 2026-08-12) ───────────────────────────
    // Анонимный playerId (UUID) + парный секрет syncToken — генерируются один
    // раз и хранятся навсегда (как в webapp): playerId — ключ строки в
    // лидерборде, syncToken доказывает последующим запросам (rename/sync), что
    // они пришли с того же устройства.
    fun getOrCreatePlayerId(): String
    fun getOrCreateSyncToken(): String
    fun getDisplayName(): String?
    fun putDisplayName(value: String?)
    /** id последней попытки (TryDatabaseModel.id), уже отправленной на сервер. */
    fun getLastSyncedAttemptId(): Int
    fun putLastSyncedAttemptId(value: Int)
    /** Разовая переотправка всей истории, чтобы сервер получил время попыток
     *  с точностью до мс (раньше хранил только до секунды). */
    fun isRecordedMsResendStarted(): Boolean
    fun markRecordedMsResendStarted()
}

class LocalStorageService(
    val context: Context
) : LocalStorageServiceContract {

    private val appPreferences = "Application Preferences"
    private val preferences = context.getSharedPreferences(appPreferences, Context.MODE_PRIVATE)

    private val soundOnKey = "soundOnKey"
    private val vibrationOnKey = "vibrationOnKey"
    private val beatsPerSecondKey = "beatsPerSecondKey"
    private val accuracyKey = "accuracyKey"
    private val aimTimeKey = "aimTimeKey" // 3 секунды, заложили на будущее, что пользователь может их менять, сейчас это константа

    private val currentLevelId = "currentLevelId"
    private val currentTaskId = "currentTaskId"

    override fun putSoundOn(value: Boolean) {
        preferences.edit().putBoolean(soundOnKey, value).apply()
    }

    override fun getSoundOn(): Boolean {
        try {
            return preferences.getBoolean(soundOnKey, false)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putVibrationOn(value: Boolean) {
        preferences.edit().putBoolean(vibrationOnKey, value).apply()
    }

    override fun getVibrationOn(): Boolean {
        try {
            return preferences.getBoolean(vibrationOnKey, false)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putBeatsPerSecond(value: Int) {
        preferences.edit().putInt(beatsPerSecondKey, value).apply()
    }


    override fun getBeatsPerSecond(): Int {
        try {
            return preferences.getInt(beatsPerSecondKey, defaultBeatsPerSecond)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putAccuracy(value: Accuracy) {
        preferences.edit().putInt(accuracyKey, value.ordinal).apply()
    }

    override fun getAccuracy(): Accuracy {
        try {
            val index = preferences.getInt(accuracyKey, 1)
            return Accuracy.entries[index]
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putAimTime(value: Int) {
        preferences.edit().putInt(aimTimeKey, value).apply()
    }


    override fun getAimTime(): Int {
        try {
            return preferences.getInt(aimTimeKey, defaultAimTime)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putCurrentLevelId(value: Int) {
        preferences.edit().putInt(currentLevelId, value).apply()
    }


    override fun getCurrentLevelId(): Int {
        try {
            return preferences.getInt(currentLevelId, 1)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    override fun putCurrentTaskId(value: Int) {
        preferences.edit().putInt(currentTaskId, value).apply()
    }


    override fun getCurrentTaskId(): Int {
        try {
            return preferences.getInt(currentTaskId, 1)
        } catch (_: ClassCastException) {
            throw LocalStorageException()
        }
    }

    private val selectedHandKey = "selectedHandKey"

    override fun putSelectedHand(value: Hand) {
        preferences.edit().putString(selectedHandKey, value.value).apply()
    }

    override fun getSelectedHand(): Hand {
        return try {
            val value = preferences.getString(selectedHandKey, Hand.RIGHT.value)
            Hand.entries.find { it.value == value } ?: Hand.RIGHT
        } catch (_: ClassCastException) {
            Hand.RIGHT
        }
    }

    // ── Адаптивные параметры квеста ─────────────────────────────────────────
    private val questCurrentNKey           = "questCurrentN"
    private val questCurrentToleranceMsKey = "questCurrentToleranceMs"
    private val questWindowAttemptsKey         = "questWindowAttempts"
    private val questWindowWinsKey             = "questWindowWins"
    private val questWindowMaxErrorMsKey       = "questWindowMaxErrorMs"
    private val questWindowLastWinIndexKey     = "questWindowLastWinIndex"
    private val questWindowFirstWinIndexKey    = "questWindowFirstWinIndex"
    private val questWasIndulgedKey            = "questWasIndulged"

    override fun putQuestCurrentN(value: Int) {
        preferences.edit().putInt(questCurrentNKey, value).apply()
    }
    override fun getQuestCurrentN(): Int =
        preferences.getInt(questCurrentNKey, -1)   // -1 = не инициализировано

    override fun putQuestCurrentToleranceMs(value: Int) {
        preferences.edit().putInt(questCurrentToleranceMsKey, value).apply()
    }
    override fun getQuestCurrentToleranceMs(): Int =
        preferences.getInt(questCurrentToleranceMsKey, 0)

    override fun putQuestWindowAttempts(value: Int) {
        preferences.edit().putInt(questWindowAttemptsKey, value).apply()
    }
    override fun getQuestWindowAttempts(): Int =
        preferences.getInt(questWindowAttemptsKey, 0)

    override fun putQuestWindowWins(value: Int) {
        preferences.edit().putInt(questWindowWinsKey, value).apply()
    }
    override fun getQuestWindowWins(): Int =
        preferences.getInt(questWindowWinsKey, 0)

    override fun putQuestWindowMaxErrorMs(value: Int) {
        preferences.edit().putInt(questWindowMaxErrorMsKey, value).apply()
    }
    override fun getQuestWindowMaxErrorMs(): Int =
        preferences.getInt(questWindowMaxErrorMsKey, 0)

    override fun putQuestWindowLastWinIndex(value: Int) {
        preferences.edit().putInt(questWindowLastWinIndexKey, value).apply()
    }
    override fun getQuestWindowLastWinIndex(): Int =
        preferences.getInt(questWindowLastWinIndexKey, 0)

    override fun putQuestWindowFirstWinIndex(value: Int) {
        preferences.edit().putInt(questWindowFirstWinIndexKey, value).apply()
    }
    override fun getQuestWindowFirstWinIndex(): Int =
        preferences.getInt(questWindowFirstWinIndexKey, 0)

    override fun putQuestWasIndulged(value: Boolean) {
        preferences.edit().putBoolean(questWasIndulgedKey, value).apply()
    }
    override fun getQuestWasIndulged(): Boolean =
        preferences.getBoolean(questWasIndulgedKey, false)

    // ── Настройки чирапов ───────────────────────────────────────────────────
    private val cheerUpsEnabledKey = "cheerUpsEnabled"

    override fun getCheerUpsEnabled(): Boolean =
        preferences.getBoolean(cheerUpsEnabledKey, true)

    override fun putCheerUpsEnabled(value: Boolean) {
        preferences.edit().putBoolean(cheerUpsEnabledKey, value).apply()
    }

    private val celebrationSoundsEnabledKey = "celebrationSoundsEnabled"

    override fun getCelebrationSoundsEnabled(): Boolean =
        preferences.getBoolean(celebrationSoundsEnabledKey, true)

    override fun putCelebrationSoundsEnabled(value: Boolean) {
        preferences.edit().putBoolean(celebrationSoundsEnabledKey, value).apply()
    }

    private val twinOutlineEnabledKey = "twinOutlineEnabled"

    override fun getTwinOutlineEnabled(): Boolean =
        preferences.getBoolean(twinOutlineEnabledKey, true)

    override fun putTwinOutlineEnabled(value: Boolean) {
        preferences.edit().putBoolean(twinOutlineEnabledKey, value).apply()
    }

    // ── Представление кнопки секундомера (Alex 2026-07-23) ─────────────────
    private val showProgressBarKey = "showProgressBar"
    private val showNumericRunKey  = "showNumericRun"

    override fun getShowProgressBar(): Boolean =
        preferences.getBoolean(showProgressBarKey, true)

    override fun putShowProgressBar(value: Boolean) {
        preferences.edit().putBoolean(showProgressBarKey, value).apply()
    }

    private val barHalfWindowMsKey = "barHalfWindowMs"
    override fun getBarHalfWindowMs(): Int =
        preferences.getInt(barHalfWindowMsKey, 100).coerceIn(10, 3000)
    override fun putBarHalfWindowMs(value: Int) {
        preferences.edit().putInt(barHalfWindowMsKey, value.coerceIn(10, 3000)).apply()
    }

    private val barScaleAutoKey = "barScaleAuto"
    override fun getBarScaleAuto(): Boolean = preferences.getBoolean(barScaleAutoKey, true)
    override fun putBarScaleAuto(value: Boolean) {
        preferences.edit().putBoolean(barScaleAutoKey, value).apply()
    }

    private val balanceRedKey = "balanceRed"
    private val balanceBlueKey = "balanceBlue"
    override fun getBalanceRed(): Int = preferences.getInt(balanceRedKey, 0)
    override fun getBalanceBlue(): Int = preferences.getInt(balanceBlueKey, 0)
    // Индикатор баланса в нижнем меню (Alex 31.08: «дёргается и врёт») читался
    // через getBalanceRed/Blue() — обычный synchronous-геттер из SharedPreferences,
    // без какого-либо Compose-состояния. BottomNavBar пересчитывал их только
    // когда пересобирался ПО ДРУГОЙ причине (смена вкладки и т.п.), а не при
    // каждом putBalance() — отсюда «дёргано и с опозданием». Теперь баланс
    // живёт ещё и в StateFlow, обновляется синхронно с записью в prefs —
    // BottomNavBar подписывается через collectAsState() и получает каждое
    // изменение сразу, независимо от того, что ещё пересобирается на экране.
    private val _balanceFlow = kotlinx.coroutines.flow.MutableStateFlow(
        getBalanceRed() to getBalanceBlue()
    )
    val balanceFlow: kotlinx.coroutines.flow.StateFlow<Pair<Int, Int>> = _balanceFlow
    override fun putBalance(red: Int, blue: Int) {
        preferences.edit().putInt(balanceRedKey, red).putInt(balanceBlueKey, blue).apply()
        _balanceFlow.value = red to blue
    }

    // Измеренная ячейка опроса тача. Default 4.0 мс (≈250 Гц) — как у OnePlus 10 Pro.
    private val touchCellMsKey = "touchCellMs"
    override fun getTouchCellMs(): Float =
        preferences.getFloat(touchCellMsKey, 4.0f).coerceIn(0.5f, 20.0f)
    override fun putTouchCellMs(value: Float) {
        preferences.edit().putFloat(touchCellMsKey, value.coerceIn(0.5f, 20.0f)).apply()
    }

    override fun getShowNumericRun(): Boolean =
        preferences.getBoolean(showNumericRunKey, true)

    override fun putShowNumericRun(value: Boolean) {
        preferences.edit().putBoolean(showNumericRunKey, value).apply()
    }

    override fun nextCheerUpPhraseIndex(triggerName: String): Int {
        val key = "cheerup_phrase_idx_$triggerName"
        val current = preferences.getInt(key, 0)
        preferences.edit().putInt(key, current + 1).apply()
        return current
    }

    // ── Общий фильтр периода (Session / Today / Month / All time) ──────────
    private val periodFilterKey = "periodFilter"

    override fun getPeriodFilter(): PeriodFilter {
        val ordinal = preferences.getInt(periodFilterKey, PeriodFilter.SESSION.ordinal)
        return PeriodFilter.entries.getOrNull(ordinal) ?: PeriodFilter.SESSION
    }

    override fun putPeriodFilter(value: PeriodFilter) {
        preferences.edit().putInt(periodFilterKey, value.ordinal).apply()
    }

    // ── Флаги миграции ──────────────────────────────────────────────────────
    // v4 (2026-07-05): применён level-factor к SC (÷100 на L1, ÷10 на L2, ×1 на L3).
    // Все существующие игроки при следующем запуске получат один прогон RecalculateScm
    // и увидят «честный» скорректированный баланс.
    private val scmRecalculatedV4Key = "scm_recalculated_v4"

    override fun isScmRecalculated(): Boolean =
        preferences.getBoolean(scmRecalculatedV4Key, false)

    private val welcomeShownKey = "welcome_shown"

    override fun isWelcomeShown(): Boolean =
        preferences.getBoolean(welcomeShownKey, false)

    override fun markWelcomeShown() {
        preferences.edit().putBoolean(welcomeShownKey, true).apply()
    }

    override fun markScmRecalculated() {
        preferences.edit().putBoolean(scmRecalculatedV4Key, true).apply()
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    override fun resetQuestProgressSync(accuracy: Accuracy) {
        // ОДИН editor + commit() (блокирующая запись НА ДИСК). apply() здесь
        // недопустим: сразу после этого вызова процесс убивается
        // (Process.killProcess в SettingsViewModel.restartApp), и его
        // фоновая запись пропадает — новый процесс прочитал бы старый уровень.
        preferences.edit()
            .putInt(currentLevelId, 1)
            .putInt(currentTaskId, 1)
            .putInt(accuracyKey, accuracy.ordinal)
            .putInt(questCurrentNKey, -1)
            .putInt(questCurrentToleranceMsKey, 0)
            .putInt(questWindowAttemptsKey, 0)
            .putInt(questWindowWinsKey, 0)
            .putInt(questWindowMaxErrorMsKey, 0)
            .putInt(questWindowLastWinIndexKey, 0)
            .putInt(questWindowFirstWinIndexKey, 0)
            .putBoolean(questWasIndulgedKey, false)
            .commit()
    }

    // ── Leaderboard-идентичность ─────────────────────────────────────────────
    private val playerIdKey            = "leaderboardPlayerId"
    private val syncTokenKey           = "leaderboardSyncToken"
    private val displayNameKey         = "leaderboardDisplayName"
    private val lastSyncedAttemptIdKey = "leaderboardLastSyncedAttemptId"

    override fun getOrCreatePlayerId(): String {
        val existing = preferences.getString(playerIdKey, null)
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        preferences.edit().putString(playerIdKey, fresh).apply()
        return fresh
    }

    override fun getOrCreateSyncToken(): String {
        val existing = preferences.getString(syncTokenKey, null)
        if (existing != null) return existing
        // 32 hex chars — matches server's looks_like_token() regex.
        val fresh = java.util.UUID.randomUUID().toString().replace("-", "")
        preferences.edit().putString(syncTokenKey, fresh).apply()
        return fresh
    }

    override fun getDisplayName(): String? = preferences.getString(displayNameKey, null)

    override fun putDisplayName(value: String?) {
        val editor = preferences.edit()
        if (value.isNullOrBlank()) editor.remove(displayNameKey) else editor.putString(displayNameKey, value)
        editor.apply()
    }

    override fun getLastSyncedAttemptId(): Int = preferences.getInt(lastSyncedAttemptIdKey, 0)

    override fun putLastSyncedAttemptId(value: Int) {
        preferences.edit().putInt(lastSyncedAttemptIdKey, value).apply()
    }

    private val recordedMsResendKey = "leaderboardRecordedMsResendStarted"

    override fun isRecordedMsResendStarted(): Boolean = preferences.getBoolean(recordedMsResendKey, false)

    override fun markRecordedMsResendStarted() {
        preferences.edit().putBoolean(recordedMsResendKey, true).apply()
    }
}