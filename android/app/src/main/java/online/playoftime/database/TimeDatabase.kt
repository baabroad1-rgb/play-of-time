package online.playoftime.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import online.playoftime.core.utils.Hand
import online.playoftime.core.utils.yourAnrConst
import online.playoftime.features.stats.domain.entity.DailyStatisticsEntity
import online.playoftime.features.stats.domain.entity.ExcelStatisticEntity
import java.util.Date


@Database(entities = [TryDatabaseModel::class, SessionDatabaseModel::class], version = 7)
@TypeConverters(Converters::class)
abstract class TimeDatabase : RoomDatabase() {
    abstract fun tryDao(): TryDao
    abstract fun sessionDao(): SessionDao
    abstract fun statisticDao(): StatisticDao
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@Entity
data class TryDatabaseModel(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val sessionId: Int,
    val startTime: Long,
    val endTime: Long,
    val deflection: Long?,
    val accuracy: Int,
    val hand: String = Hand.RIGHT.value,
    val scm: Double? = null,
    /** Допуск ±t (в мс), который был активен в момент попытки. Нужен, чтобы при загрузке
     *  лога из БД корректно определить isWin — победа на индульгентном допуске ±0.01 не
     *  должна терять зелёный цвет, когда квест уже схлопнулся обратно на baseline.
     *  Default 0 для строк, записанных до миграции 5→6. */
    val toleranceMs: Int = 0
)

@Entity
data class SessionDatabaseModel(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val startTime: Long,
    val endTime: Long,
    /** true — в этой сессии был завершён квест */
    val isQuestComplete: Boolean = false,
    /** Уровень квеста, активного в начале сессии (0 = неизвестно) */
    val questLevelId: Int = 0,
    /** Задача квеста, активной в начале сессии (0 = неизвестно) */
    val questTaskId: Int = 0,
    /** Реальное время (мс), которое пользователь провёл на MainScreen в этой сессии.
     *  Отличается от (endTime − startTime): последнее — wall-clock от первого RUN до
     *  последнего STOP, включая время когда игрок ушёл на другой экран или свернул
     *  приложение. `activeTimeMs` тикает только пока MainScreen в состоянии RESUMED.
     *  Обновляется на ON_PAUSE и при закрытии сессии. Default 0 для строк до миграции 6→7
     *  (для них при миграции backfill'ится значением `endTime − startTime`). */
    val activeTimeMs: Long = 0
)

@Dao
interface TryDao {
    @Insert
    suspend fun insert(value: TryDatabaseModel)

    /** Doppelgänger (Alex 2026-11-08): вставка с возвратом id — для SCM соседей. */
    @Insert
    suspend fun insertReturningId(value: TryDatabaseModel): Long

    /** Doppelgänger: попытки игрока после курсора, по id — для ответа «попытка на попытку». */
    @Query("SELECT * FROM TryDatabaseModel WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getAttemptsAfterId(afterId: Int, limit: Int): List<TryDatabaseModel>

    /** Doppelgänger: в его базе `deflection` = id попытки игрока, на которую он
     *  ответил (столбец больше ни для чего не используется) — максимум и есть курсор. */
    @Query("SELECT COALESCE(MAX(deflection), 0) FROM TryDatabaseModel")
    suspend fun getMaxDeflection(): Long

    @Query("SELECT * FROM TryDatabaseModel")
    suspend fun getAllAttempts(): List<TryDatabaseModel>

    @Query("UPDATE TryDatabaseModel SET scm = :scm WHERE id = :id")
    suspend fun updateScm(id: Int, scm: Double)

    @Query("SELECT * FROM TryDatabaseModel ORDER BY startTime DESC LIMIT 3")
    suspend fun getLastThreeAttempts(): List<TryDatabaseModel>

    @Query("SELECT * FROM TryDatabaseModel WHERE startTime >= :fromTime ORDER BY startTime ASC")
    suspend fun getAttemptsSince(fromTime: Long): List<TryDatabaseModel>

    /** Попытки в [fromTime, toTime) — для листания дня/недели/месяца назад-вперёд
     *  на экране аналитики (Alex 2026-09-09), где нужна и нижняя, и верхняя
     *  граница (getAttemptsSince() ограничена только снизу — этого достаточно
     *  для "текущего" периода, но не для архивного, где всё, что после верхней
     *  границы, должно быть отсечено). */
    @Query("SELECT * FROM TryDatabaseModel WHERE startTime >= :fromTime AND startTime < :toTime ORDER BY startTime ASC")
    suspend fun getAttemptsInRange(fromTime: Long, toTime: Long): List<TryDatabaseModel>

    @Query("SELECT * FROM TryDatabaseModel WHERE sessionId = :sessionId ORDER BY startTime ASC")
    suspend fun getAttemptsBySessionId(sessionId: Int): List<TryDatabaseModel>

    /** Батч попыток для leaderboard-синка (Alex 2026-08-12), в порядке id ASC,
     *  с квестом сессии-родителя (JOIN) — сервер хранит levelId/taskId per-attempt.
     *
     *  `id >= :afterId`, а не `>` (Alex 2026-09-03, парный фикс к веб-багу
     *  "coins mismatch"): SCM попытки N считается РЕТРОСПЕКТИВНО, только когда
     *  появляется попытка N+1 (см. SaveScmAfterAttemptUseCase) — то есть в
     *  момент, когда попытка N уже отправлена на сервер (синк дебaунсится
     *  через 1200мс, а следующая попытка обычно приходит секунды через 3), её
     *  финальный scm ещё не посчитан. Раньше курсор `id > afterId` навсегда
     *  пропускал повторную отправку этой попытки, и сервер получал только
     *  временное/заниженное значение. Теперь последняя уже отправленная
     *  попытка переотправляется ЕЩЁ РАЗ вместе с новыми — ровно как это же
     *  чинится на вебе в sync.php (UPDATE, если sc реально изменилось). */
    @Query(
        """
        SELECT t.id AS id, t.sessionId AS sessionId, t.startTime AS startTime,
               t.endTime AS endTime, t.accuracy AS accuracy, t.toleranceMs AS toleranceMs,
               t.scm AS scm, s.questLevelId AS questLevelId, s.questTaskId AS questTaskId
        FROM TryDatabaseModel t
        LEFT JOIN SessionDatabaseModel s ON s.id = t.sessionId
        WHERE t.id >= :afterId
        ORDER BY t.id ASC
        LIMIT :limit
        """
    )
    suspend fun getAttemptsForSync(afterId: Int, limit: Int): List<SyncAttemptRow>
}

/** Проекция одной попытки для отправки на leaderboard-сервер (не Entity —
 *  просто Room-совместимый data class для результата @Query). */
data class SyncAttemptRow(
    val id: Int,
    val sessionId: Int,
    val startTime: Long,
    val endTime: Long,
    val accuracy: Int,
    val toleranceMs: Int,
    val scm: Double?,
    val questLevelId: Int?,
    val questTaskId: Int?
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(value: SessionDatabaseModel)

    /** Doppelgänger (Alex 2026-11-08): вставка с возвратом id новой сессии. */
    @Insert
    suspend fun insertReturningId(value: SessionDatabaseModel): Long

    @Query("SELECT * FROM SessionDatabaseModel")
    suspend fun getAllSessions(): List<SessionDatabaseModel>

    @Query("SELECT * FROM SessionDatabaseModel ORDER BY id DESC LIMIT 1")
    suspend fun getCurrentSession(): SessionDatabaseModel

    @Query("SELECT * FROM SessionDatabaseModel WHERE startTime >= :fromTime ORDER BY startTime DESC")
    suspend fun getSessionsSince(fromTime: Long): List<SessionDatabaseModel>

    @Update
    suspend fun updateSession(value: SessionDatabaseModel)

    /** Обновляет ТОЛЬКО тайминги сессии. Все рабочие апдейты по ходу сессии
     *  (endTime после каждого STOP, activeTimeMs при flush play-time тикера)
     *  обязаны идти через этот метод, а не через updateSession(value): полный
     *  @Update пишет весь объект из state и затирает isQuestComplete=true,
     *  который markSessionQuestComplete записал асинхронно. Из-за этой гонки
     *  завершённая сессия оставалась isQuestComplete=false, init-restore
     *  подтягивал её попытки в state нового квеста → «дурит с нумерацией». */
    @Query("UPDATE SessionDatabaseModel SET endTime = :endTime, activeTimeMs = :activeTimeMs WHERE id = :id")
    suspend fun updateSessionTimes(id: Int, endTime: Long, activeTimeMs: Long)

    @Query("UPDATE SessionDatabaseModel SET isQuestComplete = :complete WHERE id = :id")
    suspend fun updateQuestComplete(id: Int, complete: Boolean)

    @Query("SELECT * FROM SessionDatabaseModel WHERE questLevelId = :levelId AND questTaskId = :taskId ORDER BY startTime ASC")
    suspend fun getSessionsByQuest(levelId: Int, taskId: Int): List<SessionDatabaseModel>

    @Query("SELECT * FROM SessionDatabaseModel WHERE questLevelId = :levelId ORDER BY startTime ASC")
    suspend fun getSessionsByLevel(levelId: Int): List<SessionDatabaseModel>
}

@Dao
interface StatisticDao {
    @Query("SELECT MIN(startTime) FROM SessionDatabaseModel")
    suspend fun getTotalSessionsDate(): Long

    @Query("SELECT COUNT(*) FROM SessionDatabaseModel")
    suspend fun getTotalSessionsCount(): Long

    @Query("SELECT COUNT(*) FROM TryDatabaseModel")
    suspend fun getTotalAttemptsCount(): Long

    @Query("SELECT ROUND(SUM( $yourAnrConst - (ABS(:aimTime - (endTime - startTime)))/(cast(:aimTime as Double)/100.0)), 2) FROM TryDatabaseModel")
    suspend fun getTotalARN(aimTime: Int): Double

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE (ROUND(:aimTime/1000.0, accuracy) = ROUND((endTime - startTime)/1000.0, accuracy))")
    suspend fun getTotalWinsCount(aimTime: Int): Long

    /** Total real Play Time (мс) — сумма activeTimeMs по всем сессиям.
     *  До миграции 6→7 activeTimeMs был бэкфилл'ен из (endTime − startTime), так что
     *  для старых сессий возвращаются исторические (переоценённые) значения; новые
     *  сессии тикают только когда MainScreen действительно активен. */
    @Query("SELECT COALESCE(SUM(activeTimeMs), 0) FROM SessionDatabaseModel")
    suspend fun getTotalTime(): Long

    @Query("SELECT AVG(:aimTime - (endTime - startTime))/1000.0 FROM TryDatabaseModel WHERE (endTime - startTime) < :aimTime")
    suspend fun getAverageLess(aimTime: Int): Double

    @Query("SELECT AVG((endTime - startTime) - :aimTime)/1000.0 FROM TryDatabaseModel WHERE (endTime - startTime) > :aimTime")
    suspend fun getAverageMore(aimTime: Int): Double

    @Query("Select 1 number," +
            " date(startTime/1000,'unixepoch') date," +
            "count(*) attempts," +
            "sum(case when ROUND((endTime - startTime)/1000.0, accuracy) = ROUND(:aimTime/1000.0, accuracy) then 1 else 0 end) as wins from TryDatabaseModel  group by date(startTime/1000,'unixepoch') order by 2 desc")
    suspend fun getDailyStatistic(aimTime: Int): List<DailyStatisticsEntity>

    @Query("Select date(startTime/1000,'unixepoch') date," +
            " time(startTime/1000,'unixepoch') time," +
            "sessionId sessionNumber," +
            "1 attemptNumber," +
            "endTime - startTime attemptResult," +
            "(endTime - startTime) - :aimTime errorTime," +
            " accuracy accuracy," +
            " hand hand," +
            // Для новых колонок выгрузки (Alex 2026-10-25): пауза, SC, зелёная,
            // допуск, баланс сессии — считаются в ExportToExcelUseCase.
            " startTime startTime, endTime endTime, scm scm, toleranceMs toleranceMs" +
            " from TryDatabaseModel order by startTime")
    suspend fun exportToExcel(aimTime: Int): List<ExcelStatisticEntity>

    @Query("Select COUNT(*) from(Select 1 from TryDatabaseModel  group by date(startTime/1000,'unixepoch'))")
    suspend fun getTotalDaysCount(): Long

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE hand = :hand")
    suspend fun getAttemptsByHand(hand: String): Long

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE hand = :hand AND (ROUND(:aimTime/1000.0, accuracy) = ROUND((endTime - startTime)/1000.0, accuracy))")
    suspend fun getWinsByHand(hand: String, aimTime: Int): Long

    @Query("SELECT AVG(ABS((endTime - startTime) - :aimTime))/1000.0 FROM TryDatabaseModel WHERE hand = :hand")
    suspend fun getAvgErrorByHand(hand: String, aimTime: Int): Double

    @Query("SELECT COALESCE(MIN(ABS((endTime - startTime) - 3000)), 9999999) FROM TryDatabaseModel WHERE sessionId != :excludeSessionId")
    suspend fun getBestAccuracyMsExcluding(excludeSessionId: Int): Long

    @Query("SELECT COALESCE(MIN(ABS((endTime - startTime) - 3000)), 9999999) FROM TryDatabaseModel WHERE sessionId = :sessionId")
    suspend fun getBestAccuracyMsBySession(sessionId: Int): Long

    @Query("SELECT COALESCE(SUM(scm), 0.0) FROM TryDatabaseModel WHERE scm IS NOT NULL")
    suspend fun getTotalSerenityBalance(): Double

    @Query("SELECT COALESCE(SUM(scm), 0.0) FROM TryDatabaseModel WHERE scm IS NOT NULL AND startTime >= :startOfDay")
    suspend fun getDaySerenityBalance(startOfDay: Long): Double

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE startTime >= :fromTime AND (endTime - startTime) < :aimTime")
    suspend fun getRedCount(fromTime: Long, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE startTime >= :fromTime AND (endTime - startTime) > :aimTime")
    suspend fun getBlueCount(fromTime: Long, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE sessionId = :sessionId AND (endTime - startTime) < :aimTime")
    suspend fun getRedCountBySession(sessionId: Int, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE sessionId = :sessionId AND (endTime - startTime) > :aimTime")
    suspend fun getBlueCountBySession(sessionId: Int, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE startTime >= :fromTime AND (ROUND(:aimTime/1000.0, accuracy) = ROUND((endTime - startTime)/1000.0, accuracy))")
    suspend fun getTrueThreeCountSince(fromTime: Long, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE startTime >= :fromTime")
    suspend fun getTotalCountSince(fromTime: Long): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE sessionId = :sessionId AND (ROUND(:aimTime/1000.0, accuracy) = ROUND((endTime - startTime)/1000.0, accuracy))")
    suspend fun getTrueThreeCountBySession(sessionId: Int, aimTime: Int): Int

    @Query("SELECT COUNT(*) FROM TryDatabaseModel WHERE sessionId = :sessionId")
    suspend fun getTotalCountBySession(sessionId: Int): Int
}

