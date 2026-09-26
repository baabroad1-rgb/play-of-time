package ru.iflex.timemvp.features.main.domain

import ru.iflex.timemvp.core.utils.CheerUpTrigger

/**
 * Данные для fail-карточки (Alex 2026-07-13). Показывается в момент
 * закрытия сессии после провала N-окна — до баbla. Симметричный ответ
 * на GreetingView с той же ролью «церемония», но с fail-семантикой:
 * без салюта, с сдержанной поддержкой, без «TAKE NEXT» — только «GOT IT»
 * или «REST».
 *
 * Стэшится в State при onQuestWindowFailure. Показывается по тапу RESET
 * из awaitingFailResult ветки. Закрывается новым Event.FailCard.Close,
 * который активирует бабл с recap-opener'ом и следующей фазой.
 */
data class FailCardData(
    val trigger: CheerUpTrigger,
    /** Сколько попыток было в проваленном окне. */
    val attempts: Int,
    /** Сколько побед было в проваленном окне. */
    val wins: Int,
    /** Форматированное описание проваленной фазы — «3.00 в 33 попытки», и т.п. */
    val prevPhaseDescription: String,
    /** Форматированное описание новой (упрощённой) фазы. */
    val newPhaseDescription: String,
    /** Уровень квеста, на котором игрок сейчас — для gate CTA. */
    val levelId: Int,
    /** Начало/конец провалившегося окна (getLocalTimeInMillis) — питает
     *  мордочку этой сессии на карточке (Alex 2026-09-22). 0 = неизвестно
     *  (например все попытки восстановлены из БД без таймстампов). */
    val fromMs: Long = 0L,
    val toMs: Long = 0L
)
