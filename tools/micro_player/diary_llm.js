// diary_llm.js — дневник Doppelgänger'а пишет языковая модель (OpenRouter).
//
//   node tools/micro_player/diary_llm.js [model] [out_dir]
//   node tools/micro_player/diary_llm.js anthropic/claude-haiku-5.5 /tmp/out
//
// Модель ничего не помнит между запросами, поэтому каждый раз получает:
//   • «файл характера» — webapp/api/doppelganger_character.txt (кто он, игра, как писать);
//   • свою память — короткое «что я о себе знаю», которое она сама переписывает
//     после каждой записи;
//   • факты дня — только числа и события от движка («муха» в симуляторе).
// Цифры и события берёт у движка — приврать про успехи ей не из чего.
//
// Ключ — у прокси окружения (Authorization подставляется сам); в приложении ключ
// будет только на нашем сервере, не в APK.
"use strict";
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const { MicroPlayer } = require("./micro_player.js");

const model = process.argv[2] || "anthropic/claude-haiku-5.5";
const outDir = process.argv[3] || null;
// Тот же файл, что читает сервер (webapp/api/doppelganger_diary.php).
const character = fs.readFileSync(path.join(__dirname, "../../webapp/api/doppelganger_character.txt"), "utf8");

const CELL = { 1: 100, 2: 10, 3: 1 };
const cells = (ms, a) => Math.floor((ms + CELL[a] / 2) / CELL[a]);
const LEVEL = { 1: "tenths (3.0)", 2: "hundredths (3.00)", 3: "thousandths (3.000)" };

// Пять «дней» игры: сколько попыток и на каком уровне.
const DAYS = [[1, 120], [1, 150], [1, 80, 2, 80], [2, 220], [2, 260]];

function ask(messages) {
  const body = JSON.stringify({ model, messages, temperature: 0.7, max_tokens: 2500,
    response_format: { type: "json_object" } });
  const raw = execFileSync("curl", ["-sS", "https://openrouter.ai/api/v1/chat/completions",
    "-H", "Authorization: Bearer proxy-injected", "-H", "Content-Type: application/json",
    "--data-binary", "@-"], { input: body, maxBuffer: 1 << 20 }).toString();
  const r = JSON.parse(raw);
  if (r.error) throw new Error(JSON.stringify(r.error));
  const c = r.choices[0];
  const text = c.message.content.trim().replace(/^```(json)?/, "").replace(/```$/, "");
  try { return { out: JSON.parse(text), usage: r.usage, cost: r.usage && r.usage.cost }; }
  catch (e) { throw new Error(`finish=${c.finish_reason}; ${text.slice(0, 200)}…`); }
}

const bot = new MicroPlayer({ preset: "fly", seed: 11, lang: "en" });
let memory = "Nothing yet. I have never played.";
let total = 0, cleanEver = 0;
const log = [];
for (let d = 0; d < DAYS.length; d++) {
  const plan = DAYS[d];
  const habitsBefore = bot.mb ? bot.mb.habits() : null;
  const day = { attempts: 0, exact: 0, early: 0, late: 0, errs: [], bestRun: 0, moments: [], levels: [] };
  let run = 0;
  for (let k = 0; k < plan.length; k += 2) {
    const acc = plan[k], n = plan[k + 1];
    day.levels.push(LEVEL[acc]);
    for (let i = 0; i < n; i++) {
      const ms = bot.nextDurationMs();
      const ec = cells(ms, acc) - cells(3000, acc);
      const fb = bot.feedback((cells(ms, acc) / 10 ** acc).toFixed(acc), ec === 0 ? "win" : ec < 0 ? "early" : "late");
      total++; day.attempts++;
      day.errs.push(ec * CELL[acc]);
      if (ec === 0) { day.exact++; cleanEver++; run++; day.bestRun = Math.max(day.bestRun, run); } else run = 0;
      if (ec < 0) day.early++; if (ec > 0) day.late++;
      // заметные моменты — те же правила, что у дневника-шаблона, но только как факты
      for (const t of fb.notes) if (!/^Last 25/.test(t)) day.moments.push(`attempt ${total}: ${t}`);
    }
  }
  const m = day.errs.reduce((a, b) => a + b, 0) / day.errs.length;
  const sd = Math.sqrt(day.errs.reduce((a, b) => a + (b - m) ** 2, 0) / day.errs.length);
  const pc = (h) => h && Object.fromEntries(Object.entries(h).map(([k, v]) => [k === "repeat" ? "same_side" : k, v == null ? "not enough cases" : Math.round(v * 100) + "%"]));
  const facts = {
    day: d + 1,
    precision_today: day.levels.join(", then "),
    attempts_today: day.attempts, attempts_total: total,
    exact_threes_today: day.exact, exact_threes_ever: cleanEver,
    early_today: day.early, late_today: day.late,
    average_miss_ms: Math.round(m), spread_ms: Math.round(sd),
    longest_run_of_exact_today: day.bestRun,
    correction_habits_at_start_of_day: pc(habitsBefore) || "none yet",
    correction_habits_now: pc(bot.mb.habits()),
    habits_legend: "share of a miss I correct: same_side = after a second miss on the same side; switched = after jumping to the other side; near = when within one step of three",
    notable_moments: day.moments.slice(-8),
  };
  const res = ask([
    { role: "system", content: character },
    { role: "user", content: `MEMORY:\n${memory}\n\nFACTS:\n${JSON.stringify(facts, null, 1)}` },
  ]);
  memory = res.out.memory;
  log.push({ facts, entry: res.out.entry, memory, usage: res.usage });
  console.log(`\n── Day ${d + 1} (${day.attempts} attempts, ${day.exact} exact) — tokens in ${res.usage.prompt_tokens}, out ${res.usage.completion_tokens}`);
  console.log(res.out.entry);
  console.log(`  [memory] ${memory}`);
}
if (outDir) fs.writeFileSync(path.join(outDir, `diary_llm_${model.replace(/\W+/g, "_")}.json`), JSON.stringify(log, null, 1));
