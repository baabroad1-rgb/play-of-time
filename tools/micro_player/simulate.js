// simulate.js — быстрый прогон микро-игроков без браузера.
//
//   node tools/micro_player/simulate.js                 # все характеры, 300+300+600 попыток
//   node tools/micro_player/simulate.js kalman 2000     # один характер, по 2000 на уровень
//
// Правила как в игре: счётчик округляет до клетки уровня половиной вверх
// (roundedCells, docs/webapp_handoff.md §2), чистая тройка — показано ровно 3,
// без допуска. Метрики — те же, что в docs/FACE_V3_THREE_TRAITS.md, рядом —
// пятёрка лучших живых игроков для сравнения.
"use strict";
const { MicroPlayer, PRESETS } = require("./micro_player.js");

const CELL = { 1: 100, 2: 10, 3: 1 };
const cells = (ms, acc) => Math.floor((ms + CELL[acc] / 2) / CELL[acc]);
const shownText = (ms, acc) => (cells(ms, acc) / Math.pow(10, acc)).toFixed(acc);

function median(a) { const s = [...a].sort((x, y) => x - y); const m = s.length >> 1; return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; }
const Phi = (x) => 0.5 * (1 + erf(x / Math.SQRT2));
function erf(x) { // Abramowitz–Stegun 7.1.26
  const t = 1 / (1 + 0.3275911 * Math.abs(x));
  const y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
  return x >= 0 ? y : -y;
}

function playLevel(bot, acc, n) {
  const rows = [];
  for (let i = 0; i < n; i++) {
    const ms = bot.nextDurationMs();
    const shown = shownText(ms, acc);
    const errCells = cells(ms, acc) - cells(3000, acc);
    const colour = errCells === 0 ? "win" : errCells < 0 ? "early" : "late";
    bot.feedback(shown, colour);
    rows.push({ ms, errCells, colour });
  }
  return rows;
}

function metrics(rows, acc) {
  const c = CELL[acc];
  const e = rows.map((r) => r.ms - 3000);
  const settle = e.slice(Math.floor(e.length / 3));            // после первой трети — когда уже «приладился»
  const med = median(settle);
  const sigma = 1.4826 * median(settle.map((x) => Math.abs(x - med)));
  const misses = rows.filter((r) => r.errCells !== 0).map((r) => r.ms - 3000);
  const B = misses.reduce((s, x) => s + x, 0) / Math.max(1, misses.reduce((s, x) => s + Math.abs(x), 0));
  let tw = 0, nb = 0;
  for (let i = 1; i < rows.length; i++) {
    const a = rows[i - 1], b = rows[i];
    if (a.errCells === 0 || b.errCells === 0) continue;
    const d = Math.abs(Math.abs(a.errCells) - Math.abs(b.errCells));
    if (d === 0) tw++; else if (d === 1) nb++;
  }
  const G = rows.filter((r) => r.errCells === 0).length;
  const s = Math.max(1, sigma);
  const E = settle.length ? rows.length * (Phi((c / 2 - med) / s) - Phi((-c / 2 - med) / s)) : 0;
  return {
    hit: 100 * G / rows.length,
    hitLate: 100 * rows.slice(-Math.floor(rows.length / 3)).filter((r) => r.errCells === 0).length / Math.max(1, Math.floor(rows.length / 3)),
    sigma, B, tw100: 100 * tw / rows.length, nb100: 100 * nb / rows.length, luck: (G + 3) / (E + 3),
  };
}

const REF = { // пятёрка лучших, медиана дня (FACE_V3_THREE_TRAITS.md)
  2: { sigma: 52, B: -0.17, tw100: 9.1, nb100: 14.8 },
  3: { sigma: 31, B: 0.03, tw100: 3.7, nb100: 1.9 },
};

const only = process.argv[2];
const perLevel = Number(process.argv[3]) || 0;
const plan = perLevel ? [[1, perLevel], [2, perLevel], [3, perLevel]] : [[1, 300], [2, 300], [3, 600]];
const names = only ? [only] : Object.keys(PRESETS);
const f = (x, d = 0) => (x >= 0 && d ? "+" : "") + x.toFixed(d);
console.log("уровень · попыток | чистых, % (за всё / в конце) | разброс σ, мс | баланс B | близн./сос. на 100 | везение ×");
for (const name of names) {
  const bot = new MicroPlayer({ preset: name, seed: 11 });
  console.log(`\n${name} — ${PRESETS[name].label}: сдвиг ${f(PRESETS[name].bias)} мс, шум ${(PRESETS[name].cv0 * 100).toFixed(1)}% → ${(PRESETS[name].cvBest * 100).toFixed(1)}%`);
  for (const [acc, n] of plan) {
    const m = metrics(playLevel(bot, acc, n), acc);
    const ref = REF[acc] ? `   (лучшие: σ ${REF[acc].sigma}, B ${f(REF[acc].B, 2)}, ${REF[acc].tw100}/${REF[acc].nb100})` : "";
    console.log(`  L${acc} · ${String(n).padStart(4)} | ${m.hit.toFixed(1).padStart(5)} / ${m.hitLate.toFixed(1).padStart(5)} | ${m.sigma.toFixed(0).padStart(4)} | ${f(m.B, 2).padStart(5)} | ${m.tw100.toFixed(1)}/${m.nb100.toFixed(1)} | ${m.luck.toFixed(2)}${ref}`);
  }
}
