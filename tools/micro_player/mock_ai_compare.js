// mock_ai_compare.js — макет экрана AI «только сравнения» и недельного дневника
// в логе аналитики (Алекс 11.11.2026):
//   «на экране AI надо выводить только сравнения you/ai; в явном виде указать
//    текущий уровень; вместо всех графиков — сравнительный график скорости
//    обучения пользователя и „мухи“; дневник перенеси в лог на аналитикс,
//    сделай еженедельным… размышления человека в концепции ВПФ»;
//   «если пользователь ввёл своё имя — везде вместо you пиши его».
//
//   node tools/micro_player/mock_ai_compare.js <play_novice.json> <play_fly.json> <out_dir>
//
// Данные — два настоящих прогона через веб (play_web.js): «человек» (novice) и
// «муха» (fly), квесты и уровни считала сама игра.
"use strict";
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");
const { MicroPlayer } = require("./micro_player.js");
const { weeklyEntry, mainIssue } = require("./weekly_reflection.js");

const [, , youFile, flyFile, outDir] = process.argv;
const NAME = "Alex";
const you = JSON.parse(fs.readFileSync(youFile, "utf8"));
const fly = JSON.parse(fs.readFileSync(flyFile, "utf8"));
const N = Math.min(you.length, fly.length);

const ORDER = ["1.1", "1.2", "1.3", "1.4", "1.5", "2.1", "2.2", "2.3", "2.4", "2.5", "3.1", "3.2", "3.3", "3.4", "3.5"];
const questOf = (r) => (r.quest.match(/Quest (\d\.\d)/) || [])[1] || null;
// r1 (lag-1 correlation of SEEN misses), mean, sd, and "tired" (later thirds of sessions worse)
function shape(wk) {
  const seen = wk.map((r) => Math.round((parseFloat(r.shown) - 3) * 1000));
  const m = seen.reduce((a, b) => a + b, 0) / seen.length;
  const sd = Math.sqrt(seen.reduce((a, b) => a + (b - m) ** 2, 0) / seen.length) || 1;
  let c = 0; for (let i = 1; i < seen.length; i++) c += (seen[i - 1] - m) * (seen[i] - m);
  const r1 = c / ((seen.length - 1) * sd * sd);
  let first = [], last = [];
  for (let i = 0; i < wk.length; i += 33) { const s = seen.slice(i, i + 33); if (s.length < 30) continue;
    first.push(...s.slice(0, 11).map(Math.abs)); last.push(...s.slice(22).map(Math.abs)); }
  const avg = (a) => a.reduce((x, y) => x + y, 0) / Math.max(1, a.length);
  return { r1, meanMs: m, sdMs: sd, tired: first.length > 30 && avg(last) > 1.15 * avg(first) };
}

function steps(log) {
  let last = 0; const pts = [];
  log.slice(0, N).forEach((r, i) => { const q = questOf(r); const k = q ? ORDER.indexOf(q) : -1; if (k >= 0) last = k; pts.push(last); });
  return pts;
}
const ys = steps(you), yf = steps(fly);

// ── learning-speed chart (SVG) ──────────────────────────────────────────────
function chart() {
  const W = 328, H = 190, L = 34, R = 8, T = 10, B = 26;
  const x = (i) => L + (W - L - R) * i / (N - 1);
  const y = (k) => T + (H - T - B) * (1 - k / 14);
  const line = (pts) => pts.map((k, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(k).toFixed(1)}`).join("");
  const bands = [[0, 4, "L1 · tenths"], [5, 9, "L2 · hundredths"], [10, 14, "L3 · thousandths"]];
  let g = "";
  bands.forEach(([a, b, lab], j) => {
    g += `<rect x="${L}" y="${y(b + 0.5)}" width="${W - L - R}" height="${y(a - 0.5) - y(b + 0.5)}" fill="${j % 2 ? "#202020" : "#1a1a1a"}"/>`;
    g += `<text x="${L + 4}" y="${y(b + 0.5) + 11}" class="band">${lab}</text>`;
  });
  [0, 4, 9, 14].forEach((k) => { g += `<text x="${L - 4}" y="${y(k) + 3}" class="ax" text-anchor="end">${ORDER[k]}</text>`; });
  for (let i = 0; i <= N; i += 100) g += `<text x="${x(Math.min(i, N - 1))}" y="${H - 8}" class="ax" text-anchor="middle">${i}</text>`;
  g += `<path d="${line(ys)}" fill="none" stroke="#ff551c" stroke-width="2.2"/>`;
  g += `<path d="${line(yf)}" fill="none" stroke="#a78bfa" stroke-width="2.2"/>`;
  g += `<circle cx="${x(N - 1)}" cy="${y(ys[N - 1])}" r="3.5" fill="#ff551c"/><circle cx="${x(N - 1)}" cy="${y(yf[N - 1])}" r="3.5" fill="#a78bfa"/>`;
  return `<svg viewBox="0 0 ${W} ${H}" width="100%">${g}</svg>`;
}

// ── comparison numbers for the last 7 "days" (35 attempts a day) ───────────
function stats(log) {
  const w = log.slice(N - 105, N);
  const err = w.map((r) => r.holdMs - 3000), m = err.reduce((a, b) => a + b, 0) / err.length;
  const exact = w.filter((r) => r.colour === "win" && !r.eased).length;
  const early = w.filter((r) => r.colour === "early").length, late = w.filter((r) => r.colour === "late").length;
  let run = 0, best = 0; w.forEach((r) => { if (r.colour === "win" && !r.eased) { run++; best = Math.max(best, run); } else run = 0; });
  return { exact, oneIn: exact ? Math.round(w.length / exact) : null, spread: Math.round(Math.sqrt(err.reduce((a, b) => a + (b - m) ** 2, 0) / err.length)), early, late, best };
}
const sy = stats(you), sf = stats(fly);
const qy = ORDER[ys[N - 1]], qf = ORDER[yf[N - 1]];
const lvName = (q) => ({ 1: "tenths", 2: "hundredths", 3: "thousandths" })[q[0]];
const lean = (s) => s.early >= 2 * s.late ? "mostly early" : s.late >= 2 * s.early ? "mostly late" : "even";

function row(label, a, b, better) {
  const ca = better === "a" ? "win" : "", cb = better === "b" ? "win" : "";
  return `<tr><td class="lab">${label}</td><td class="${ca}">${a}</td><td class="${cb}">${b}</td></tr>`;
}

// ── weekly reflections from the fly's own run (replayed mind), 15 attempts a day ──
const PER_WEEK = 105;
function reflections() {
  const { weeksFromLog } = require("./weekly_reflection.js");
  const out = []; let arc = null;
  for (const w of weeksFromLog(fly.slice(0, N), PER_WEEK, MicroPlayer)) {
    const e = weeklyEntry(w, arc); arc = e.arc;
    out.push({ week: w.index + 1, text: e.text, issue: mainIssue(w), n: w.attempts, quests: w.quests });
  }
  return out;
}
const refl = reflections();

const CSS = `
*{box-sizing:border-box} body{margin:0;background:#0f0f0f;font-family:'DejaVu Sans','Liberation Sans',Arial,sans-serif;color:#fff}
.screen{width:360px;padding:12px 16px 24px}
.pill{display:flex;justify-content:space-between;background:#1a1a1a;border-radius:18px;padding:6px 10px;font:600 12px 'DejaVu Sans';color:#7f7f7f}
.pill b{color:#ff551c}
.daterow{display:flex;align-items:center;margin:8px 0 10px;height:28px}
.who{display:flex;height:22px;border-radius:11px;background:#1a1a1a;border:1px solid #333;overflow:hidden;font:600 11px 'DejaVu Sans'}
.who span{padding:0 9px 0 9px;display:flex;align-items:center;color:rgba(255,255,255,.45)}
.who .me.on{background:#ff551c;color:#fff}.who .ai.on{background:#7c5cff;color:#fff}
.date{margin-left:auto;color:rgba(255,255,255,.78);font:500 12.5px 'DejaVu Sans'}
.date i{color:#ff551c;font-style:normal;padding:0 10px;font-weight:700}
.about{color:#c4b5fd;font:600 12px 'DejaVu Sans';margin:2px 4px 10px}
.title{display:flex;justify-content:space-between;color:#7f7f7f;font:600 12px 'DejaVu Sans';margin:14px 4px 8px}
.card{background:#1a1a1a;border-radius:12px;padding:14px}
.level{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.level .k{font:600 11px 'DejaVu Sans';color:#7f7f7f;margin-bottom:4px;display:flex;align-items:center;gap:6px}
.dot{width:8px;height:8px;border-radius:4px;display:inline-block}
.level .v{font:700 22px 'DejaVu Sans'}.level .s{font:500 12px 'DejaVu Sans';color:rgba(255,255,255,.7);margin-top:2px}
.legend{display:flex;gap:14px;font:600 11px 'DejaVu Sans';color:rgba(255,255,255,.8);margin:0 2px 6px}
svg .ax{font:10px 'DejaVu Sans';fill:#7f7f7f} svg .band{font:600 9.5px 'DejaVu Sans';fill:#5f5f5f}
table{width:100%;border-collapse:collapse;font:500 13px 'DejaVu Sans'}
th{font:600 11px 'DejaVu Sans';color:#7f7f7f;text-align:right;padding:0 0 8px}
th:first-child{text-align:left}
td{padding:7px 0;border-top:1px solid #262626;text-align:right;color:rgba(255,255,255,.75)}
td.lab{text-align:left;color:#9a9a9a;font-size:12px}
td.win{color:#fff;font-weight:700}
.note{font:11px 'DejaVu Sans';color:#6f6f6f;margin:8px 2px 0}
/* log */
.tree .yr,.tree .mo,.tree .wk,.tree .dy{display:flex;justify-content:space-between;padding:9px 4px;border-top:1px solid #222;font:500 13px 'DejaVu Sans';color:rgba(255,255,255,.88)}
.tree .mo{padding-left:14px}.tree .wk{padding-left:24px}.tree .dy{padding-left:36px;color:rgba(255,255,255,.7);font-size:12.5px}
.tree span.r{color:#7f7f7f;font-size:12px}
.wkdiary{margin:2px 4px 8px 24px;border-left:2px solid #7c5cff;padding:8px 10px 10px 12px;background:#17151f;border-radius:0 10px 10px 0}
.wkdiary .h{display:flex;align-items:center;gap:6px;font:600 11px 'DejaVu Sans';color:#c4b5fd;margin-bottom:6px}
.wkdiary p{margin:0;font:13.5px/1.5 'DejaVu Serif','Liberation Serif',Georgia,serif;color:rgba(255,255,255,.9)}
`;

const flyIcon = `<svg width="12" height="12" viewBox="0 0 20 20"><circle cx="7" cy="7" r="3.2" fill="#a78bfa" opacity=".55"/><path d="M1.3 18c.5-3.6 2.8-5.4 6-5.4s5.5 1.8 6 5.4" fill="#a78bfa" opacity=".55"/><circle cx="13" cy="7" r="3.2" fill="#c4b5fd"/><path d="M6.7 18c.5-3.6 2.8-5.4 6-5.4s5.5 1.8 6 5.4" fill="#c4b5fd"/></svg>`;

const aiScreen = `<div class="screen">
<div class="pill"><span>session</span><span>day</span><b>week</b><span>month</span><span>all time</span><span>?</span></div>
<div class="daterow"><div class="who"><span class="me">${NAME}</span><span class="ai on">AI</span></div><div class="date"><i>‹</i>3 Nov – 9 Nov<i>›</i></div></div>
<div class="about">▸ Who is Doppelgänger?</div>
<div class="title"><span>Level now</span></div>
<div class="card level">
 <div><div class="k"><span class="dot" style="background:#ff551c"></span>${NAME}</div><div class="v">Level ${qy[0]}</div><div class="s">quest ${qy} · ${lvName(qy)}</div></div>
 <div><div class="k"><span class="dot" style="background:#a78bfa"></span>Doppelgänger</div><div class="v">Level ${qf[0]}</div><div class="s">quest ${qf} · ${lvName(qf)}</div></div>
</div>
<div class="title"><span>Speed of learning</span><span>quest reached · by attempts</span></div>
<div class="card"><div class="legend"><span style="color:#ff551c">● ${NAME}</span><span style="color:#a78bfa">● Doppelgänger</span></div>${chart()}
<div class="note">Same number of attempts for both: he makes one for each of yours. The higher line got further for the same practice.</div></div>
<div class="title"><span>${NAME} vs Doppelgänger</span><span>/ week</span></div>
<div class="card"><table><tr><th></th><th>${NAME}</th><th>Doppelgänger</th></tr>
${row("Exact threes", `${sy.exact} · 1 in ${sy.oneIn}`, `${sf.exact} · 1 in ${sf.oneIn}`, sy.exact >= sf.exact ? "a" : "b")}
${row("Spread", `${sy.spread} ms`, `${sf.spread} ms`, sy.spread <= sf.spread ? "a" : "b")}
${row("Lean", lean(sy), lean(sf), "")}
${row("Longest run of threes", sy.best, sf.best, sy.best >= sf.best ? "a" : "b")}
</table><div class="note">Bold — whoever is ahead.</div></div>
</div>`;

const w = refl[3] || refl[refl.length - 1], w2 = refl[1] || refl[0];
const logScreen = `<div class="screen">
<div class="pill"><span>session</span><span>day</span><b>week</b><span>month</span><span>all time</span><span>?</span></div>
<div class="daterow"><div class="who"><span class="me on">${NAME}</span><span class="ai">AI</span></div><div class="date"><i>‹</i>3 Nov – 9 Nov<i>›</i></div></div>
<div class="title"><span>Sessions log</span><span>/ week</span></div>
<div class="card tree" style="padding:4px 10px">
 <div class="yr" style="border-top:0"><span>▾ 2026</span><span class="r">1 400 attempts</span></div>
 <div class="mo"><span>▾ November</span><span class="r">700</span></div>
 <div class="wk"><span>▾ 3 – 9 Nov</span><span class="r">105</span></div>
 <div class="wkdiary"><div class="h">${flyIcon} Doppelgänger’s week</div><p>${w.text}</p></div>
 <div class="dy"><span>▸ Sun, 9 Nov</span><span class="r">15</span></div>
 <div class="dy"><span>▸ Sat, 8 Nov</span><span class="r">15</span></div>
 <div class="wk"><span>▾ 27 Oct – 2 Nov</span><span class="r">105</span></div>
 <div class="wkdiary"><div class="h">${flyIcon} Doppelgänger’s week</div><p>${w2.text}</p></div>
 <div class="dy"><span>▸ Sun, 2 Nov</span><span class="r">15</span></div>
</div></div>`;

(async () => {
  fs.writeFileSync(path.join(outDir, "ai_compare.html"), `<!doctype html><meta charset="utf-8"><style>${CSS}</style><div style="display:flex;gap:24px;align-items:flex-start">${aiScreen}${logScreen}</div>`);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 760, height: 900 }, deviceScaleFactor: 2 });
  await p.goto("file://" + path.join(outDir, "ai_compare.html"));
  await p.screenshot({ path: path.join(outDir, "ai_compare.png"), fullPage: true });
  await b.close();
  console.log("quests: you", qy, "fly", qf, "| refl:"); refl.forEach((r) => console.log(`  week ${r.week} [${r.issue}] ${r.text}`));
})();
