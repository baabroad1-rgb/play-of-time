package online.playoftime.core.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import online.playoftime.BuildConfig
import online.playoftime.core.provideTimeDatabase
import online.playoftime.core.utils.defaultAimTime
import online.playoftime.database.TimeDatabase

/**
 * Отдаёт историю игрока наружу, только на чтение.
 *
 * Сегодня закрывает одну задачу: переезд на переименованное приложение.
 * Идентификатор пакета меняется с online.playoftime.quest на online.playoftime,
 * а для Android это другое приложение — оно ставится рядом, со своей пустой
 * базой. Новая версия при первом запуске стучится сюда и забирает историю.
 *
 * Поэтому отдаются СЫРЫЕ строки таблиц, один в один, включая id: новая база
 * должна получиться точной копией старой, а не пересказом. Причёсанная витрина
 * для стороннего дневника — отдельная задача, её формат ещё согласуется
 * (docs/CONTENT_PROVIDER_CONTRACT.md).
 *
 * Доступ закрыт разрешением уровня signature, то есть читать может только
 * приложение, подписанное тем же ключом. Проверяет система.
 */
class PlayDataProvider : ContentProvider() {

    private companion object {
        const val CODE_ATTEMPTS = 1
        const val CODE_SESSIONS = 2
        const val CODE_META = 3

        val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(PlayDataContract.AUTHORITY, PlayDataContract.PATH_EXPORT_ATTEMPTS, CODE_ATTEMPTS)
            addURI(PlayDataContract.AUTHORITY, PlayDataContract.PATH_EXPORT_SESSIONS, CODE_SESSIONS)
            addURI(PlayDataContract.AUTHORITY, PlayDataContract.PATH_ATTEMPTS, CODE_ATTEMPTS)
            addURI(PlayDataContract.AUTHORITY, PlayDataContract.PATH_SESSIONS, CODE_SESSIONS)
            addURI(PlayDataContract.AUTHORITY, PlayDataContract.PATH_META, CODE_META)
        }
    }

    // База берётся через общий provideTimeDatabase(), а не через Koin: этот
    // класс создаётся раньше Application.onCreate, и Koin к тому моменту ещё
    // не запущен. Экземпляр всё равно один на процесс — см. KoinModule.
    private val database: TimeDatabase
        get() = provideTimeDatabase(requireNotNull(context) { "ContentProvider без контекста" })

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = when (MATCHER.match(uri)) {
        // Сортировка по id, а не по времени: она задаёт порядок вставки в новую
        // базу, а sessionId в попытках ссылается на id сессии.
        // id AS _id — соглашение Android: курсорные адаптеры ищут колонку
        // именно с таким именем. Исходный id тоже остаётся.
        CODE_ATTEMPTS -> rawQuery("SELECT *, id AS _id FROM TryDatabaseModel ORDER BY id ASC", uri)
        CODE_SESSIONS -> rawQuery("SELECT *, id AS _id FROM SessionDatabaseModel ORDER BY id ASC", uri)
        CODE_META -> metaCursor(uri)
        else -> throw IllegalArgumentException("Неизвестный адрес: $uri")
    }

    private fun rawQuery(sql: String, uri: Uri): Cursor =
        database.query(SimpleSQLiteQuery(sql)).apply {
            setNotificationUri(requireNotNull(context).contentResolver, uri)
        }

    private fun metaCursor(uri: Uri): Cursor {
        val db = database
        val attempts = scalarLong(db, "SELECT COUNT(*) FROM TryDatabaseModel")
        val sessions = scalarLong(db, "SELECT COUNT(*) FROM SessionDatabaseModel")
        // COALESCE: на пустой базе MIN() возвращает NULL, а не ноль.
        val firstAt = scalarLong(db, "SELECT COALESCE(MIN(startTime), 0) FROM TryDatabaseModel")

        val columns = arrayOf(
            PlayDataContract.Meta.CONTRACT_VERSION,
            PlayDataContract.Meta.APP_VERSION_NAME,
            PlayDataContract.Meta.SCHEMA_VERSION,
            PlayDataContract.Meta.AIM_MS,
            PlayDataContract.Meta.ATTEMPTS_TOTAL,
            PlayDataContract.Meta.SESSIONS_TOTAL,
            PlayDataContract.Meta.FIRST_ATTEMPT_AT
        )
        return MatrixCursor(columns, 1).apply {
            addRow(
                arrayOf<Any>(
                    PlayDataContract.VERSION,
                    BuildConfig.VERSION_NAME,
                    db.openHelper.readableDatabase.version,
                    defaultAimTime,
                    attempts,
                    sessions,
                    firstAt
                )
            )
            setNotificationUri(requireNotNull(context).contentResolver, uri)
        }
    }

    private fun scalarLong(db: TimeDatabase, sql: String): Long =
        db.query(SimpleSQLiteQuery(sql)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }

    override fun getType(uri: Uri): String = when (MATCHER.match(uri)) {
        CODE_ATTEMPTS -> "vnd.android.cursor.dir/vnd.${PlayDataContract.AUTHORITY}.attempt"
        CODE_SESSIONS -> "vnd.android.cursor.dir/vnd.${PlayDataContract.AUTHORITY}.session"
        CODE_META -> "vnd.android.cursor.item/vnd.${PlayDataContract.AUTHORITY}.meta"
        else -> throw IllegalArgumentException("Неизвестный адрес: $uri")
    }

    // Провайдер только на чтение. Молча игнорировать запись нельзя: вызывающая
    // сторона решит, что данные сохранились.
    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("Провайдер только на чтение")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Провайдер только на чтение")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Провайдер только на чтение")
}
