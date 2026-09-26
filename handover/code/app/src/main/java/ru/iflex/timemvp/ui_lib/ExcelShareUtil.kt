package ru.iflex.timemvp.ui_lib

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.koin.compose.koinInject
import ru.iflex.timemvp.core.utils.StatisticExcelFields
import ru.iflex.timemvp.features.stats.domain.usecase.ExportToExcelUseCase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Генерирует Excel в кэше приложения и запускает системный share intent
 * (Intent.ACTION_SEND через chooser). Файл — временный, отдаётся через
 * FileProvider с authority `${applicationId}.fileprovider`.
 *
 * Вызывается из BottomNavBar → Export tab на любом экране.
 */
suspend fun shareExcelAsync(
    context: Context,
    scope: CoroutineScope,
    exportUseCase: ExportToExcelUseCase
) = scope.launch {
    runCatching {
        // Мгновенный отклик: на большой истории генерация занимает секунды —
        // без тоста казалось, что тап по Export не сработал.
        Toast.makeText(context, "Preparing export…", Toast.LENGTH_SHORT).show()

        // ВСЯ генерация — вне главного потока. Раньше в Dispatchers.IO была
        // обёрнута только запись файла, а построение воркбука на 20К+ строк
        // шло на Main → UI замерзал на секунды («экспорт долго открывается»).
        // NB: SXSSFWorkbook (streaming) на Android НЕ работает — его
        // AutoSizeColumnTracker тянет java.awt.FontRenderContext, которого в
        // Android нет (NoClassDefFoundError). Остаёмся на XSSF.
        val file = withContext(Dispatchers.IO) {
            val data = exportUseCase()
            val workbook = XSSFWorkbook().apply {
                val sheet = createSheet("Data Sheet")
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue(StatisticExcelFields.DATE.columnName)
                header.createCell(1).setCellValue(StatisticExcelFields.TIME.columnName)
                header.createCell(2).setCellValue(StatisticExcelFields.SESSION_NUMBER.columnName)
                header.createCell(3).setCellValue(StatisticExcelFields.ATTEMPT_NUMBER.columnName)
                header.createCell(4).setCellValue(StatisticExcelFields.ATTEMPT_RESULT.columnName)
                header.createCell(5).setCellValue(StatisticExcelFields.ERROR_TIME.columnName)
                header.createCell(6).setCellValue(StatisticExcelFields.ACCURACY.columnName)
                header.createCell(7).setCellValue("Hand")
                data.forEachIndexed { i, entity ->
                    val row = sheet.createRow(i + 1)
                    row.createCell(0).setCellValue(entity.date)
                    row.createCell(1).setCellValue(entity.time)
                    row.createCell(2).setCellValue(entity.sessionNumber.toDouble())
                    row.createCell(3).setCellValue(entity.attemptNumber.toDouble())
                    row.createCell(4).setCellValue(entity.attemptResult.toDouble())
                    row.createCell(5).setCellValue(entity.errorTime.toDouble())
                    row.createCell(6).setCellValue(entity.accuracy)
                    row.createCell(7).setCellValue(entity.hand)
                }
            }

            val excelDir = File(context.cacheDir, "excel").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = File(excelDir, "play_of_time_$stamp.xlsx")
            FileOutputStream(out).use { workbook.write(it) }
            workbook.close()
            out
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Play of Time — attempts export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Excel export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure { e ->
        // Раньше ошибка молча проглатывалась runCatching — экспорт «не открывался»
        // без единого следа в логах. Теперь пишем в logcat и показываем тост.
        android.util.Log.e("ExcelShare", "Export failed", e)
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Compose-scoped helper: возвращает лямбду для передачи в BottomNavBar.onExport.
 * Инжектит ExportToExcelUseCase из Koin, использует rememberCoroutineScope для
 * async работы (Excel-запись + IO).
 */
@Composable
fun rememberExcelShareAction(): () -> Unit {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val exportUseCase = koinInject<ExportToExcelUseCase>()
    return remember(context, exportUseCase) {
        {
            scope.launch { shareExcelAsync(context, scope, exportUseCase) }
            Unit
        }
    }
}
