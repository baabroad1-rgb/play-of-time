# Play of Time — Web App workspace

Self-contained workspace to continue the **web version** of Play of Time so it
behaves the same way as the native Android app.

## What's here

```
webapp/
├── index.html              ← landing page + embedded game shell (#app)
├── app.js                  ← the game (vanilla JS, ~1770 lines) — the "previous attempt"
├── styles.css              ← all styling
├── manifest.webmanifest    ← PWA manifest
├── icon.svg                ← app icon
├── CLAUDE.md               ← project brief: rules/invariants for continuing (read first)
├── api/                    ← PHP-бэкенд (лидерборд, синк, отчёт)
└── handoff/                ← заметки веб-сессий по конкретным правкам
```

## Где определено поведение (порядок авторитета)

**живой Kotlin в `../android/` → `../docs/webapp_handoff.md` → `app.js`**

Раньше здесь лежало зеркало `reference/native-src/*.kt` — ручная копия нативных
исходников, потому что веб-сессия физически не видела папку с Android-проектом.
С 2026-09-26 оба проекта в одном репозитории, и зеркало удалено: живой код
не может отстать по определению. Зеркало отставало трижды (2026-08-13 — на 5 из
11 файлов, и на момент удаления — на 7 из 12, `AnalyticsUseCase.kt` на 1061 строку).
Старые копии при необходимости достаются из истории git.

| Что определяет | Живой файл |
|---|---|
| 15 квестов, условия победы, шаги/потолки индульгенции | `../android/app/src/main/java/ru/iflex/timemvp/core/utils/Quest.kt` |
| Формула Serenity Coins (+ множитель уровня) | `../android/app/src/main/java/ru/iflex/timemvp/use_case/CalculateScmUseCase.kt` |
| Пересчёт SCM по всей истории | `../android/app/src/main/java/ru/iflex/timemvp/use_case/RecalculateScmUseCase.kt` |
| Метрики аналитики (lucky, range, моды, геом. среднее) | `../android/app/src/main/java/ru/iflex/timemvp/features/stats/domain/usecase/AnalyticsUseCase.kt` |
| Метрики главного экрана (гистограмма, геом. средние) | `../android/app/src/main/java/ru/iflex/timemvp/features/main/domain/usecase/MainMetricsUseCase.kt` |
| Строки лога, близнецы/соседи, победа по допуску | `../android/app/src/main/java/ru/iflex/timemvp/features/main/domain/usecase/GetLogAttemptsUseCase.kt` |
| Тексты подбадриваний + describePhase() | `../android/app/src/main/java/ru/iflex/timemvp/core/utils/CheerUpMessages.kt` |
| Триггеры подбадриваний (вкл. AFTER_SUBQUEST_*) | `../android/app/src/main/java/ru/iflex/timemvp/core/utils/CheerUpTrigger.kt` |
| 7 ритмических паттернов ошибки | `../android/app/src/main/java/ru/iflex/timemvp/core/utils/ErrorPattern.kt` |
| Ключи сохранения квестов/сессии | `../android/app/src/main/java/ru/iflex/timemvp/core/data/LocalStorageService.kt` |
| Форма UI-состояния | `../android/app/src/main/java/ru/iflex/timemvp/features/main/ui/MainContract.kt` |
| Константы (ячейка дизеринга, aim и пр.) | `../android/app/src/main/java/ru/iflex/timemvp/core/utils/Utils.kt` |

Если поведение веба должно совпадать с нативом — читай Kotlin напрямую, а не пересказ.

## How to run

Pure static files — no build step. Serve the folder and open it:

```bash
python -m http.server 8000
```

Then open `http://localhost:8000/`. The game is at the bottom of the page
("Tap to launch"); it also runs full-screen in the `#app` shell. The game needs
no assets (audio is Web Audio synth, icons are inline SVG). The landing page's
video/images (`assets/…`) are not bundled — pull them from playoftime.online if
you want the full page.

## Parity status vs spec (v12.87)

`app.js` is versioned `20260712` — it predates the July timing/subquest-cheerup
work. Present: quests + indulgence, SCM + level factor, sessions/periods,
analytics (lucky/range/modes/geom mean), twins, log, export (JSON/CSV/Excel),
error patterns, base cheer-ups.

**Known gaps to close for full parity** (see CLAUDE.md for detail):

1. **§3 L3 dithering + echo/re-roll** — MISSING. Highest priority: without it,
   "exactly 3.000" on L3 is unfair (the browser input grid rarely lands on it).
   Needs `event.timeStamp`/`performance.now()` measurement +
   `getCoalescedEvents()` cell estimation.
2. **§8 Neighbor detection** (yellow oval, |errors| differ by exactly one cell) — MISSING.
3. **§10 `AFTER_SUBQUEST_*` cheer-up family + `describePhase()`** — MISSING (only base cheer-ups).
4. **§4 `questWasIndulged`** flag — MISSING (needed to pick AFTER_QUEST vs AFTER_SIMPLIFIED_QUEST_DONE).

Verify each gap on device against `reference/native-src/` before implementing.
