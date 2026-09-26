---
name: feedback-working-style
description: "Alex's durable working preferences — communication mode, iteration style, terminology rules, and precedence of fixes"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 0a189dfe-905e-4ed9-983f-0c1f416bf451
---

# Working style — durable feedback from Alex

## Written communication over voice
Alex reviews technical work through visual output (screenshots, rendered mockups) and text. Never propose voice/spoken review; keep everything textual.

**Why:** Confirmed multiple times, including in the CLAUDE.md project briefing.
**How to apply:** Default to plain text explanations + rendered mockups (via visualize module) for design work. Skip audio-focused UX suggestions.

## Row-by-row confirmation on bulk edits
When we're editing a table of items (cheer-up texts, quest params, columns), Alex reviews one row at a time and confirms before I move on. Never propose a full sheet at once when we're in tuning mode.

**Why:** Reinforced twice on 2 July 2026 — first on cheer-up rewrite ("выводи строку таблицы за строчкой будо каждую отдельно согласовывать"), then implicitly during the quest-progress mockup work (8 mockup iterations, each with one specific tweak). Same pattern applies to any list where quality of individual entries matters.
**How to apply:** When bulk-changing entries, ask "which row do you want to start with?" or show one row + wait. Not applicable to code refactors — only to text/data/design tuning.

## Iterative design via visualize mockups
Big visual redesigns go through 5-10 rounds of the visualize tool BEFORE any Compose code gets written. Alex will react to each mockup with 1-2 short specific edits ("параллельно, не сходятся", "белый outline", "без чёрных гэпов"). Never assume the first mockup will be accepted.

**Why:** Confirmed on 2 July 2026 quest-progress design — went through 8 rounds before Alex said "кодь" and we moved to Compose implementation.
**How to apply:** Even after Alex says "выглядит хорошо", offer to iterate on one detail he mentioned. Don't rush to code. Also: when a mockup goes wrong, ask what specifically to change — don't guess two things at once.

## Domain-native vocabulary in user-facing text
In-app strings (cheer-ups, transition messages, empty states) must use game-domain terms: **precision, focus, threes, rhythm, Serenity, cluster, pass, ladder**. NEVER: window, attempt limit, adaptive parameter, indulgence, tolerance, IN_N, ROW.

**Why:** Alex called this out sharply during the cheer-up rewrite (2 July 2026): "мы ж играем в serenity а не в окно возможностей". Applied retroactively to all approved texts.
**How to apply:** Every user-visible string gets a sniff test — does this sound like the game or does it sound like the code? If the latter, rewrite.

## Numbers with thousands separator (space, not comma)
Any user-visible number ≥ 1000 renders with a SPACE as the thousands separator: `10 770`, not `10,770` or `10770`.

**Why:** Explicit request 2 July 2026; also matches Russian/European convention Alex prefers.
**How to apply:** Use `"%,d".format(n).replace(',', ' ')`. Applies to counts in analytics, mode repetitions, level-total stats in cheer-up texts, everywhere.

## Bugs beat features
If a formula error surfaces during UI polish work, STOP and fix the formula before continuing the visual work.

**Why:** 2 July 2026 — I made Lucky Index red when > 1.0 without questioning the underlying formula direction. Alex noticed the formula was inverted (I had `dispersion/empirical` when Lucky Index should be `empirical/dispersion` so higher = better). He was displeased about coloring red without checking the math first. Similar risk on any derived metric.
**How to apply:** When adding coloring/formatting rules that depend on threshold comparisons ("red if > 1"), first verify the metric direction from spec docs (measures.docx, parametr_table.docx) — not from intuition.

## PowerShell syntax on Windows
Alex's environment is Windows + PowerShell (not CMD, not WSL). All terminal commands must use PowerShell syntax.

**Why:** Baked into CLAUDE.md briefing. Confirmed repeatedly.
**How to apply:** No `&&` chaining on Windows PowerShell — use `;` sequencing or `if ($?) { ... }`. Bash tool commands still work (they run inside git-bash), but any command I hand back to Alex to run himself must be PowerShell-clean.

## Precision of visual iteration
When Alex describes a visual change, take it VERY literally. If he says "beak strictly between the stripes", that means visually equidistant — not "in the general gap area with other elements between". If he says "range of deviation caption above the strip", it means above THAT strip, even if that pushes another element out of position.

**Why:** Multiple rounds of "переделай" during the RANGE OF DEVIATIONS card work and the quest-progress arrow bundle on 2 July 2026, both because I put an element "roughly there" instead of exactly where he pointed.
**How to apply:** If a placement request seems to conflict with something else, either ask before doing it OR restructure the surrounding layout to make the literal placement work. Don't compromise on where he pointed.

## Version tracking honesty — APK on disk vs `version.properties`
Alex checks the APK filename himself. The auto-bumper writes the *next* build's version into `app/version.properties` AFTER a successful build, so the APK on disk uses the version from just before the bump. If `versionPatch=64`, the file on disk is `play-of-time-v11.63.apk` (not v11.64).

**Why:** He caught me on 2 July 2026 reporting `v11.53` when the actual APK on disk was `v11.53` while properties said 54 — always confusing. Reinforced again with quest-progress work.
**How to apply:** After every build, run `ls -t app/build/outputs/apk/debug/` and report exactly what's there. Never report the version derived from `version.properties`.

## When Alex says "кодь!" — go to Compose
After the design mockup phase concludes with Alex's "давай, кодь!" or similar green light, immediately move to Compose implementation. Don't propose one more mockup — that's a signal he's done with visuals and wants a working artifact.

**Why:** Explicit 2 July 2026 during quest-progress work. After 8 mockup rounds, he said "да, давай, кодь!" and expected code, not another visual tweak.
**How to apply:** Green-light phrases like "кодь", "поехали", "начинай", "ок делай" = stop mockups, start writing files.
