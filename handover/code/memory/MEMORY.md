# Memory Index

- [Project Overview](project_overview.md) — Play of Time Android app: stack, layout, domain shorthand, active focus areas, sensitive files
- [Working Style](feedback_working_style.md) — Alex's durable feedback: row-by-row confirmation, domain vocabulary, precision on visual placement, PowerShell-only, bugs beat features
- [Session 2026-07-06 State](session_2026-07-06_state.md) — v11.84→v11.87: play-time трекер (activeTimeMs, миграция 6→7), session-header фикс лога, isQuestComplete root cause, tidyLineBreaks, дни квеста; APK именуется ДО бампа
- [Stopwatch Latency Fix](perf_stopwatch_latency.md) — v12.22→v12.24 (14 июля 2026): 9 слоёв латентности кнопки — SharedFlow bypass, kernel timestamps через pointerInput, uptime-длительность, изоляция state.time от 60Hz recompose, @Volatile, drift-корректный тик
