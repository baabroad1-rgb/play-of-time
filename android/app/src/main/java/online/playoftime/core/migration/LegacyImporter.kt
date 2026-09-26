package online.playoftime.core.migration

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import online.playoftime.core.export.PlayDataContract
import online.playoftime.core.provideTimeDatabase

/**
 * Переносит историю игрока из предыдущего приложения (ru.iflex.timemvp.quest)
 * в это. После переименования пакета Android считает нас другим приложением:
 * мы ставимся рядом со старым и получаем пустую базу.
 *
 * Как это устроено. Старое приложение начиная с версии 14.16 отдаёт свои
 * таблицы через ContentProvider, закрытый разрешением уровня signature —
 * то есть читать их может только приложение с тем же ключом подписи. Мы
 * подписаны тем же ключом, поэтому разрешение выдаётся системой само,
 * игрока ни о чём спрашивать не надо.
 *
 * Почему проверка идёт при КАЖДОМ запуске, а не только при первом. Игрок
 * может поставить новое приложение раньше, чем обновит старое до 14.16 —
 * тогда при первом запуске переносить будет нечего. Если бы мы отметили
 * попытку как выполненную, история пропала бы навсегда. Вместо этого мы
 * молча пробуем снова, пока не получится: как только старое приложение
 * обновится, ближайший запуск подхватит данные.
 *
 * Переносим только в ПУСТУЮ базу. Если игрок уже успел поиграть в новом
 * приложении, вставлять поверх нельзя: идентификаторы столкнутся, а какие
 * из попыток настоящие — решать не нам.
 */
object LegacyImporter {

    private const val TAG = "LegacyImporter"
    private const val PREFS = "legacy_import"
    private const val KEY_DONE = "done"
    // Отдельный признак для настроек. Первая версия переноса их не копировала:
    // переехали попытки, а прогресс по квестам и личность игрока для
    // лидерборда остались в старом приложении, и игрок оказался на первом
    // уровне. У кого перенос уже прошёл, признак ниже не выставлен — значит
    // настройки подтянутся при ближайшем запуске.
    private const val KEY_PREFS_DONE = "prefs_done"

    sealed interface Result {
        /** Перенесено столько-то. */
        data class Imported(val attempts: Int, val sessions: Int) : Result
        /** Переносить нечего или уже перенесено — ничего не делали. */
        data object Skipped : Result
        /** Старое приложение стоит, но данные отдать не может: версия ниже 14.16. */
        data object LegacyTooOld : Result
        /** Не получилось. База не тронута. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Переносит настройки: прогресс по квестам, личность игрока для лидерборда,
     * выбранную руку и прочее. Вынесено отдельно от таблиц и вызывается
     * синхронно до старта приложения — их читают при инициализации, и
     * подтянуть их позже значит показать игроку первый уровень.
     *
     * Свои настройки не затираются: пишутся только ключи, которых у нас нет.
     */
    fun importPreferences(context: Context): Result {
        val marker = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(KEY_PREFS_DONE, false)) return Result.Skipped
        if (!isLegacyInstalled(context)) {
            marker.edit().putBoolean(KEY_PREFS_DONE, true).apply()
            return Result.Skipped
        }

        val cursor = try {
            context.contentResolver.query(
                Uri.parse(PlayDataContract.Legacy.URI_PREFS), null, null, null, null
            ) ?: return Result.LegacyTooOld
        } catch (e: SecurityException) {
            return Result.Failed("нет доступа: разные подписи")
        } catch (e: Exception) {
            return Result.Failed(e.message ?: e::class.java.simpleName)
        }

        val target = context.getSharedPreferences(PlayDataContract.PREFS_FILE, Context.MODE_PRIVATE)
        var copied = 0
        cursor.use { c ->
            val iKey = c.getColumnIndexOrThrow(PlayDataContract.Prefs.KEY)
            val iType = c.getColumnIndexOrThrow(PlayDataContract.Prefs.TYPE)
            val iValue = c.getColumnIndexOrThrow(PlayDataContract.Prefs.VALUE)
            val editor = target.edit()
            while (c.moveToNext()) {
                val key = c.getString(iKey) ?: continue
                if (target.contains(key)) continue
                val raw = c.getString(iValue) ?: continue
                when (c.getString(iType)) {
                    "string" -> editor.putString(key, raw)
                    "int" -> raw.toIntOrNull()?.let { editor.putInt(key, it) }
                    "long" -> raw.toLongOrNull()?.let { editor.putLong(key, it) }
                    "float" -> raw.toFloatOrNull()?.let { editor.putFloat(key, it) }
                    "boolean" -> editor.putBoolean(key, raw.toBoolean())
                    "stringset" -> editor.putStringSet(key, raw.split("\n").toSet())
                    else -> continue
                }
                copied++
            }
            // commit, а не apply: дальше сразу стартует приложение и читает
            // эти же настройки, отложенная запись не успела бы.
            editor.commit()
        }
        marker.edit().putBoolean(KEY_PREFS_DONE, true).commit()
        Log.i(TAG, "Перенесено настроек: $copied")
        return Result.Imported(attempts = 0, sessions = copied)
    }

    fun run(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return Result.Skipped

        if (!isLegacyInstalled(context)) {
            // Старого приложения нет и не будет — больше не проверяем.
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return Result.Skipped
        }

        val db = provideTimeDatabase(context)
        val alreadyHaveData = db.query(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM TryDatabaseModel")
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L } > 0
        if (alreadyHaveData) {
            Log.i(TAG, "В новой базе уже есть попытки — перенос пропущен")
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return Result.Skipped
        }

        val resolver = context.contentResolver

        // Пробный запрос к meta: он же проверяет, что старое приложение
        // достаточно свежее. На версиях до 14.16 провайдера нет, и resolver
        // возвращает null — без исключения, молча.
        val legacyAttempts = try {
            resolver.query(Uri.parse(PlayDataContract.Legacy.URI_META), null, null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getLong(c.getColumnIndexOrThrow(PlayDataContract.Meta.ATTEMPTS_TOTAL))
                    } else 0L
                } ?: return Result.LegacyTooOld
        } catch (e: SecurityException) {
            // Подписи разошлись — разрешение signature не выдано.
            Log.w(TAG, "Нет доступа к данным старого приложения", e)
            return Result.Failed("нет доступа: разные подписи")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать meta старого приложения", e)
            return Result.Failed(e.message ?: e::class.java.simpleName)
        }

        if (legacyAttempts == 0L) {
            Log.i(TAG, "В старом приложении нет попыток — переносить нечего")
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return Result.Skipped
        }

        return try {
            var sessions = 0
            var attempts = 0
            // Всё одной транзакцией: при сбое на середине база остаётся
            // пустой, и следующий запуск попробует заново с чистого листа.
            db.runInTransaction {
                sessions = copyTable(resolver, PlayDataContract.Legacy.URI_SESSIONS, "SessionDatabaseModel", db)
                attempts = copyTable(resolver, PlayDataContract.Legacy.URI_ATTEMPTS, "TryDatabaseModel", db)
            }
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            Log.i(TAG, "Перенесено: попыток $attempts, сессий $sessions")
            Result.Imported(attempts = attempts, sessions = sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Перенос не удался, база не тронута", e)
            Result.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Копирует таблицу как есть, сохраняя идентификаторы: попытки ссылаются
     * на сессии по sessionId, и перенумеровать их нельзя.
     *
     * Колонки берутся из самого курсора, а не из списка в коде: схема у обоих
     * приложений одна, и так перенос не сломается, если в таблицу добавится
     * поле. Служебный _id (копия id, соглашение Android) пропускается — такой
     * колонки в таблице нет.
     */
    private fun copyTable(
        resolver: android.content.ContentResolver,
        uri: String,
        table: String,
        db: online.playoftime.database.TimeDatabase
    ): Int {
        val cursor = resolver.query(Uri.parse(uri), null, null, null, null)
            ?: throw IllegalStateException("Старое приложение не отдало $table")
        cursor.use { c ->
            val columns = c.columnNames.filter { it != "_id" }
            if (columns.isEmpty()) throw IllegalStateException("Пустой набор колонок для $table")
            val placeholders = columns.joinToString(",") { "?" }
            val sql = "INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)"
            var count = 0
            val statement = db.openHelper.writableDatabase.compileStatement(sql)
            while (c.moveToNext()) {
                statement.clearBindings()
                columns.forEachIndexed { i, name ->
                    bind(statement, i + 1, c, c.getColumnIndexOrThrow(name))
                }
                statement.executeInsert()
                count++
            }
            return count
        }
    }

    private fun bind(
        statement: androidx.sqlite.db.SupportSQLiteStatement,
        index: Int,
        cursor: Cursor,
        column: Int
    ) = when (cursor.getType(column)) {
        Cursor.FIELD_TYPE_NULL -> statement.bindNull(index)
        Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(index, cursor.getLong(column))
        Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(index, cursor.getDouble(column))
        Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(index, cursor.getBlob(column))
        else -> statement.bindString(index, cursor.getString(column) ?: "")
    }

    private fun isLegacyInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PlayDataContract.Legacy.PACKAGE, 0)
        true
    }.getOrDefault(false)
}
