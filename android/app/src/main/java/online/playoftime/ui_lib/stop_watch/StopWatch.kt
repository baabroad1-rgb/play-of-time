package online.playoftime.ui_lib.stop_watch

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.playoftime.core.data.LocalStorageService
import online.playoftime.core.services.VibrationService
import online.playoftime.core.utils.Accuracy
import online.playoftime.core.utils.StopWatchCycle
import online.playoftime.core.utils.maxAttemptDuration
import online.playoftime.use_case.ContextUseCase


class StopWatch(
    private val localStorage: LocalStorageService,
    private val context: ContextUseCase,
    private val vibrationService: VibrationService,
    val setTime: (String, Long) -> Unit
) {

    private val stopwatchScope = CoroutineScope(Dispatchers.Default)
    private val soundScope = CoroutineScope(Dispatchers.Default)
    // @Volatile: тик-луп читает stopWatchState на Dispatchers.Default,
    // а Main-поток (или pointerInput coroutine) его меняет.  Без @Volatile
    // на многоядерном CPU запись из одного потока может быть НЕ ВИДНА
    // тику несколько миллисекунд — и лишний setTime с уже неактуальным
    // временем успевал попасть в state.time между STOP и заморозкой.
    @Volatile
    private var stopWatchState: StopWatchCycle = StopWatchCycle.ON_START

    /**
     * Лок, закрывающий TOCTOU-гонку тика с заморозкой (Alex 2026-07-15,
     * баг «на кнопке 3.059, в логе 3.052, ~1 раз на 10 попыток»):
     *
     *   1. Тик прошёл guard (state ещё IS_RUNNING), замерил время T+7мс;
     *   2. Main: setState(ON_STOP) → freeze _stopwatchTime = точное время тапа;
     *   3. Тик, уже прошедший guard, вызывает setTime(T+7мс) — и
     *      ПЕРЕЗАПИСЫВАЕТ заморозку опоздавшим значением.
     *
     * @Volatile гарантирует видимость, но не атомарность пары
     * «проверить state → записать setTime». Оба участка (guard+setTime в тике
     * и смена state) берут этот монитор: setState(ON_STOP) не вернётся, пока
     * тик находится между guard'ом и setTime. После возврата setState любой
     * последующий тик увидит ON_STOP и выйдет — заморозка, записанная ПОСЛЕ
     * setState, гарантированно финальна.
     */
    private val tickLock = Any()

    fun setState(value: StopWatchCycle) {
        synchronized(tickLock) {
            stopWatchState = value
        }
    }

    /**
     * @param startUptimeMs — SystemClock.uptimeMillis() момента START-тапа
     *   (kernel-timestamp касания сенсора, тот же что уехал в БД). Тик-дисплей
     *   считает elapsed = SystemClock.uptimeMillis() - startUptimeMs → видимый
     *   счётчик синхронизирован с реальным моментом касания.
     *
     * ── Равномерность тика (Alex 2026-07-14) ───────────────────────────────
     * Каждый тик планируется на АБСОЛЮТНЫЙ deadline = startUptime + N*16 мс,
     * а не относительно предыдущего тика. Если один delay(16) вернулся позже
     * (система была под нагрузкой), следующий уходит спать на меньший
     * промежуток и догоняет расписание. Средний шаг — ровно 16 мс, дрейф
     * не накапливается.
     *
     * SystemClock.uptimeMillis — монотонный, не зависит от NTP-коррекций
     * wall-clock. Это важно для симметрии START/STOP.
     */
    fun start(startUptimeMs: Long = SystemClock.uptimeMillis()) {
        stopwatchScope.launch {
            val startUptime = startUptimeMs
            var tickIndex = 0L
            val tickPeriodMs = 16L
            var time = 0L

            //region отображение таймера
            while (stopWatchState == StopWatchCycle.IS_RUNNING) {
                tickIndex++
                val nextDeadline = startUptime + tickIndex * tickPeriodMs
                val sleepMs = (nextDeadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                delay(sleepMs)

                // Guard + setTime под ОДНИМ монитором с setState (см. tickLock):
                // проверка состояния и запись дисплея атомарны относительно
                // смены состояния. Тик, начавший запись до STOP, завершит её
                // ДО того как setState(ON_STOP) вернётся — а заморозка пишется
                // ПОСЛЕ setState и потому всегда перекрывает опоздавший тик.
                val stopped = synchronized(tickLock) {
                    if (stopWatchState != StopWatchCycle.IS_RUNNING) {
                        true
                    } else {
                        time = SystemClock.uptimeMillis() - startUptime

                        val seconds = (time.div(1000)).toInt()
                        val milliseconds = (time % 1000).toInt()

                        val formattedTime = when (localStorage.getAccuracy()) {
                            Accuracy.SECONDS -> String.format("%d", seconds)

                            Accuracy.DECISECONDS -> String.format(
                                "%d.%01d", seconds, milliseconds / 100
                            )

                            Accuracy.CENTISECONDS -> String.format(
                                "%d.%02d", seconds, milliseconds / 10
                            )

                            Accuracy.MILLISECONDS -> String.format(
                                "%d.%03d", seconds, milliseconds
                            )
                        }
                        setTime(formattedTime, time)
                        false
                    }
                }
                if (stopped) break
                if (time >= maxAttemptDuration){
                    setState(StopWatchCycle.ON_START)
                }
            }
            //endregion
        }
    }
    val metronomeTime = (1000 / localStorage.getBeatsPerSecond()).toLong()
    val audioManager =
        context.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun startMetronome() {

        if (localStorage.getSoundOn()) {
            soundScope.launch {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 10.0f)
                delay(metronomeTime - 10L)
                while (stopWatchState == StopWatchCycle.IS_RUNNING){
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 10.0f)
                    delay(metronomeTime)
                }
                //endregion
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun startVibration() {
        stopwatchScope.launch {
            val metronomeTime = 1000 / localStorage.getBeatsPerSecond()

            //region вибратор
            if (localStorage.getVibrationOn()) {
                while (stopWatchState == StopWatchCycle.IS_RUNNING) {
                    delay(metronomeTime.toLong())
                    vibrationService.tick()
                }
                vibrationService.cancel()
            }
            //endregion
        }
    }
}
