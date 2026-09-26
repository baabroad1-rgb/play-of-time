package online.playoftime.features.stats.domain.usecase

/**
 * Аггрегатор use-case'ов аналитики. Из ~16 полей после чистки 2026-07-14
 * остались только те, что реально дергаются из StatsViewModel.
 */
data class StatUseCases(
    val exportToExcelUseCase: ExportToExcelUseCase,
    val totalDaysCount: TotalDaysCount,
    val analyticsUseCase: AnalyticsUseCase
)
