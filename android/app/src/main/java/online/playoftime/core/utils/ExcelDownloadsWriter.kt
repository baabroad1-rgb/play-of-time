package online.playoftime.core.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import online.playoftime.features.stats.ui.ExcelStatisticState
import java.io.FileOutputStream

/**
 * Запись выгрузки в системную папку Downloads (без share-диалога).
 * Используется:
 *   • StatsViewModel — экспорт по кнопке (легаси-путь);
 *   • SettingsViewModel — ПРИНУДИТЕЛЬНЫЙ экспорт перед wipe данных
 *     («начать сначала», Alex 2026-07-15) — данные сохраняются без
 *     возможности отменить диалогом.
 */
fun writeExcelToDownloads(
    context: Context,
    fileName: String,
    dataList: List<ExcelStatisticState>
): Boolean {
    val workbook = buildStatWorkbook(dataList)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — MediaStore, permissions не нужны
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv
            ) ?: return false
            resolver.openOutputStream(uri)?.use { workbook.write(it) }
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
            true
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            FileOutputStream(java.io.File(dir, fileName)).use { workbook.write(it) }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        workbook.close()
    }
}

private fun buildStatWorkbook(dataList: List<ExcelStatisticState>): XSSFWorkbook {
    val workbook = XSSFWorkbook()
    val sheet    = workbook.createSheet("Data Sheet")
    val header   = sheet.createRow(0)
    header.createCell(0).setCellValue(StatisticExcelFields.DATE.columnName)
    header.createCell(1).setCellValue(StatisticExcelFields.TIME.columnName)
    header.createCell(2).setCellValue(StatisticExcelFields.SESSION_NUMBER.columnName)
    header.createCell(3).setCellValue(StatisticExcelFields.ATTEMPT_NUMBER.columnName)
    header.createCell(4).setCellValue(StatisticExcelFields.ATTEMPT_RESULT.columnName)
    header.createCell(5).setCellValue(StatisticExcelFields.ERROR_TIME.columnName)
    header.createCell(6).setCellValue(StatisticExcelFields.ACCURACY.columnName)
    header.createCell(7).setCellValue("Hand")
    dataList.forEachIndexed { index, entity ->
        val row = sheet.createRow(index + 1)
        row.createCell(0).setCellValue(entity.date)
        row.createCell(1).setCellValue(entity.time)
        row.createCell(2).setCellValue(entity.sessionNumber.toDouble())
        row.createCell(3).setCellValue(entity.attemptNumber.toDouble())
        row.createCell(4).setCellValue(entity.attemptResult.toDouble())
        row.createCell(5).setCellValue(entity.errorTime.toDouble())
        row.createCell(6).setCellValue(entity.accuracy)
        row.createCell(7).setCellValue(entity.hand)
    }
    return workbook
}
