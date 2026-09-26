package ru.iflex.timemvp.core.export

/**
 * Адреса и имена, по которым данные игрока читает стороннее приложение.
 *
 * Значения здесь — ЛИТЕРАЛЫ, а не BuildConfig.APPLICATION_ID, и это сделано
 * намеренно. Читающая сторона (в первую очередь — переименованная версия
 * Play of Time, которая при первом запуске забирает историю у старой) обращается
 * к конкретной строке. Подставь сюда applicationId — и после переименования
 * пакета адрес уедет вместе с ним, а старое приложение останется недостижимым
 * ровно тогда, когда оно нужнее всего.
 *
 * Полное описание для внешней команды — docs/CONTENT_PROVIDER_CONTRACT.md.
 */
object PlayDataContract {

    /** Authority старого (и текущего) приложения. После переименования пакета
     *  НЕ меняется: это адрес источника данных для переезда. */
    const val AUTHORITY = "ru.iflex.timemvp.quest.provider"

    /**
     * Разрешение на выгрузку сырых данных. Уровень signature: его получает
     * только приложение, подписанное тем же ключом. Проверку делает сама
     * система, подделать подпись нельзя, и игрока ни о чём спрашивать не надо —
     * это разговор приложения с самим собой, просто под другим именем пакета.
     */
    const val PERMISSION_EXPORT = "ru.iflex.timemvp.quest.permission.EXPORT_PLAY_DATA"

    const val VERSION = 1

    /** Попытки, как они лежат в базе: все колонки, без переименований. */
    const val PATH_EXPORT_ATTEMPTS = "v1/export/attempts"

    /** Сессии, как они лежат в базе. */
    const val PATH_EXPORT_SESSIONS = "v1/export/sessions"

    /** Одна строка: версия контракта, версия приложения, объём данных. */
    const val PATH_META = "v1/meta"

    object Meta {
        const val CONTRACT_VERSION = "contract_version"
        const val APP_VERSION_NAME = "app_version_name"
        const val SCHEMA_VERSION = "schema_version"
        const val AIM_MS = "aim_ms"
        const val ATTEMPTS_TOTAL = "attempts_total"
        const val SESSIONS_TOTAL = "sessions_total"
        const val FIRST_ATTEMPT_AT = "first_attempt_at"
    }
}
