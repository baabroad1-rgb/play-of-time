package online.playoftime.core.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationService(
    private val context: Context
) {

    private val tickEffect = VibrationEffect.createOneShot(50, 200)
    private val heavyEffect = VibrationEffect.createOneShot(100, 255)

    // Кэшируем vibrator один раз — раньше это был get() с getSystemService
    // на каждый вызов, что добавляло переменный джиттер 0.5–5 мс к моменту
    // старта тактильной обратной связи. Стартовая точка вибрации должна
    // быть стабильной — восприятие «клик → вибрация» критично для игры.
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun tick(){
        vibrator.vibrate(tickEffect)
    }

    fun heavy(){
        vibrator.vibrate(heavyEffect)
    }

    fun cancel(){
        vibrator.cancel()
    }
}