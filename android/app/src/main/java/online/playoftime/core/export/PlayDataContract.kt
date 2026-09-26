package online.playoftime.core.export

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

    /** Authority этого приложения. */
    const val AUTHORITY = "online.playoftime.provider"

    /**
     * Разрешение на выгрузку сырых данных. Уровень signature: его получает
     * только приложение, подписанное тем же ключом. Проверку делает сама
     * система, подделать подпись нельзя, и игрока ни о чём спрашивать не надо —
     * это разговор приложения с самим собой, просто под другим именем пакета.
     */
    const val PERMISSION_EXPORT = "online.playoftime.permission.EXPORT_PLAY_DATA"

    /**
     * Разрешение для стороннего приложения (дневник психологического
     * состояния). Уровень dangerous: Android спросит игрока при первом
     * обращении. Отдельное от EXPORT_PLAY_DATA, потому что то — signature,
     * его получает только приложение с тем же ключом подписи.
     */
    const val PERMISSION_READ = "online.playoftime.permission.READ_PLAY_DATA"

    const val VERSION = 1

    /** Попытки, как они лежат в базе: все колонки, без переименований. */
    const val PATH_EXPORT_ATTEMPTS = "v1/export/attempts"

    /** Сессии, как они лежат в базе. */
    const val PATH_EXPORT_SESSIONS = "v1/export/sessions"

    /** Попытки для стороннего приложения. Данные те же, что в export —
     *  отличается только разрешение, по которому пускают. */
    const val PATH_ATTEMPTS = "v1/attempts"

    /** Сессии для стороннего приложения. */
    const val PATH_SESSIONS = "v1/sessions"

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

    /**
     * Адреса ПРЕДЫДУЩЕГО приложения, до переименования пакета. Отсюда при
     * первом запуске забирается история игрока: для Android приложение с новым
     * идентификатором — другое приложение, со своей пустой базой.
     *
     * Строки записаны литералами и меняться не должны никогда: это адрес
     * чужого, уже установленного на телефонах приложения, которое мы больше
     * не пересобираем.
     */
    object Legacy {
        const val PACKAGE = "ru.iflex.timemvp.quest"
        const val AUTHORITY = "ru.iflex.timemvp.quest.provider"
        const val PERMISSION_EXPORT = "ru.iflex.timemvp.quest.permission.EXPORT_PLAY_DATA"

        const val URI_ATTEMPTS = "content://$AUTHORITY/v1/export/attempts"
        const val URI_SESSIONS = "content://$AUTHORITY/v1/export/sessions"
        const val URI_META = "content://$AUTHORITY/v1/meta"
    }
}
