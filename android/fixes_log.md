# Fixes Log — 2026-06-21

---

## 1. Cheer-up messages translated to English
**File:** `app/src/main/java/ru/iflex/timemvp/core/utils/CheerUpMessages.kt`

All 27 cheer-up texts replaced with English translations:
- 6 in-session specific messages (NINE_RED, NINE_BLUE, GROWING_ERROR, TRIPLE_IDENTICAL, PAUSE_SHORT, PAUSE_LONG)
- 16 general theory tips (random rotation with any in-session trigger)
- 5 post-event messages (AFTER_SESSION, AFTER_QUEST, AFTER_FAIL, AFTER_LEVEL, AFTER_GAME)

---

## 2. Cheer-up bubble redesigned to match prototype
**File:** `app/src/main/java/ru/iflex/timemvp/ui_lib/CheerUpBubble.kt`

Changes:
- Background color changed from grey to amber/yellow `#F5C02E`
- Added 👋 emoji icon on the left side
- Specific text (specific advice) rendered **bold** — acts as the title
- General text rendered below in regular weight
- Text color changed to dark `#1A1200` (warm near-black, readable on yellow)
- Layout: `Row(emoji | Column(title, body))` + `×` close button top-right

---

## 3. Stopwatch trembling eliminated
**Files:**
- `app/src/main/java/ru/iflex/timemvp/ui_lib/stop_watch/StopWatch.kt`
- `app/src/main/java/ru/iflex/timemvp/ui_lib/stop_watch/StopWatchButton.kt`

### StopWatch.kt
- Update interval changed from `delay(10)` → `delay(16)` to match 60 fps display refresh and avoid excess recompositions.

### StopWatchButton.kt
- Replaced `BasicText` time display with a new `StopwatchTimeText` composable.
- Each character is rendered in its own fixed-width `Canvas` cell using Android `TextPaint` with `Align.CENTER`.
- Cell width is measured once from `paint.measureText("0")` (monospace → all glyphs same advance width).
- Total `Row` width = `charCount × cellWidth` — a constant — so the Row centering offset never changes and X-axis position is perfectly stable.
- Bold restored via `Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)`.

**Root cause of trembling:** Compose text layout re-measures the string on every recomposition; the centering offset `(containerWidth − textWidth) / 2` can differ by 1 sub-pixel due to rounding at 70 sp, causing the text to visually shift left/right at 60 fps. Per-character Canvas cells bypass Compose text measurement entirely.

---

## 4. Error precision matches current accuracy level
**File:** `app/src/main/java/ru/iflex/timemvp/features/main/ui/SessionStatisticItem.kt`

Added `accuracy: Accuracy` parameter to `SessionStatisticItem`. Error text now formats with the number of decimals matching the current level:
- CENTISECONDS → `"%.2f"` → `.09`
- MILLISECONDS → `"%.3f"` → `.091`

**File:** `app/src/main/java/ru/iflex/timemvp/features/main/ui/MainScreen.kt`

Added `accuracy = state.accuracy` to the `SessionStatisticItem` call.

---

## 5. Reduced gap between distribution bars and GeomMean axis
**Files:**
- `app/src/main/java/ru/iflex/timemvp/features/main/ui/DistribChart.kt`
- `app/src/main/java/ru/iflex/timemvp/features/main/ui/GeomMeanRow.kt`

- `DistribChart`: axis labels canvas height reduced from `16.dp` → `10.dp`
- `GeomMeanRow`: canvas height reduced from `28.dp` → `20.dp`; axis line moved from `axisY = 20.dp` → `axisY = 12.dp` (saves ~14dp of visual gap total)

---

## 6. Narrower dot cell in stopwatch digits
**File:** `app/src/main/java/ru/iflex/timemvp/ui_lib/stop_watch/StopWatchButton.kt`

In `StopwatchTimeText`, added `dotWidthDp = paint.measureText(".")`. The "." character now gets its own natural cell width instead of the same width as digits (`"0"`), eliminating the excess space around the decimal point.
