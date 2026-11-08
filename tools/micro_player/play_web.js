// play_web.js — микро-игрок играет в НАСТОЯЩИЙ веб Play of Time через браузер.
//
//   (cd webapp && python3 -m http.server 8123) &
//   node tools/micro_player/play_web.js kalman 400            # характер, сколько попыток
//   node tools/micro_player/play_web.js kalman 400 http://localhost:8123/index.html out_dir
//
// Агент жмёт кнопку и видит только экран: число на счётчике и его цвет. Квесты,
// допуски, уровни, сессии — всё настоящее, из app.*.js. Часы браузера
// управляемые (как в наших тестах): «продержать кнопку N мс» = сдвинуть часы на N
// мс между двумя нажатиями — счётчик при этом никто не трогает.
"use strict";
const { chromium } = require("playwright");
const { MicroPlayer, PRESETS } = require("./micro_player.js");

const preset = process.argv[2] || "kalman";
const total = Number(process.argv[3]) || 300;
const url = process.argv[4] || "http://localhost:8123/index.html";
const outDir = process.argv[5] || null;
const KEY = "play-of-time.web.v1";

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 360, height: 740 }, deviceScaleFactor: 1 });
  p.on("pageerror", (e) => console.log("PAGEERROR", e.message));
  await p.addInitScript(() => {
    window.__clock = 1000; const epoch0 = Date.now() - 600000;
    performance.now = () => window.__clock; Date.now = () => epoch0 + window.__clock;
    HTMLMediaElement.prototype.play = function () { return Promise.resolve(); };
  });
  await p.goto(url);
  await p.evaluate((KEY) => { localStorage.clear(); localStorage.setItem(KEY, JSON.stringify({ attempts: [], sessions: [], ids: {}, settings: {} })); }, KEY);
  await p.reload(); await p.waitForTimeout(400);
  await p.evaluate(() => { const n = document.querySelector(".nav-play"); if (n) n.click(); });
  await p.waitForTimeout(200);
  await p.evaluate(() => { const w = document.getElementById("welcomeClose"); if (w) w.click(); });

  // Нажатие пальцем: сдвигаем часы на паузу перед касанием и жмём кнопку.
  const press = (pauseMs) => p.evaluate((d) => {
    window.__clock += d;
    document.getElementById("runButton").dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, cancelable: true }));
  }, pauseMs);
  const screen = () => p.evaluate(() => {
    const t = document.getElementById("timeDisplay"), btn = document.getElementById("runButton");
    const cls = t ? t.className : "";
    const strip = document.querySelector(".quest-strip");
    return {
      shown: t ? t.innerText.trim() : "",
      colour: /is-win/.test(cls) ? "win" : /is-early/.test(cls) ? "early" : /is-late/.test(cls) ? "late" : "",
      eased: /eased/.test(cls),
      label: btn ? (btn.querySelector(".timer-action") || btn).innerText.replace(/\s+/g, " ").trim() : "",
      quest: strip ? strip.innerText.replace(/\s+/g, " ").trim() : "",
    };
  });

  const bot = new MicroPlayer({ preset, seed: 11 });
  const log = [];
  let lastQuest = "";
  for (let i = 0; i < total; i++) {
    // До RUN: закрыть результат, «OK», «HOORAY!», карточку «TAKE NEXT QUEST» —
    // как сделал бы игрок.
    for (let k = 0; k < 8; k++) {
      const hasRun = await p.evaluate(() => !!document.getElementById("runButton"));
      if (!hasRun) {
        const clicked = await p.evaluate(() => {
          const skip = (x) => /nav-tab|help-button|gameLauncher/.test(x.className + " " + x.id);
          const btn = [...document.querySelectorAll("button")].find((x) => x.offsetParent && !skip(x));
          if (btn) { btn.click(); return btn.innerText.replace(/\s+/g, " ").trim(); }
          return "";
        });
        if (clicked && /NEXT|QUEST|LEVEL/i.test(clicked)) console.log(`   [${clicked}]`);
        await p.waitForTimeout(150);
        continue;
      }
      const s = await screen();
      if (/RUN/.test(s.label)) break;
      await press(400); await p.waitForTimeout(60);
    }
    const holdMs = bot.nextDurationMs();
    await press(800);                      // пауза перед RUN, как у человека
    await p.waitForTimeout(20);
    await press(holdMs);                   // STOP через holdMs после RUN
    await p.waitForTimeout(40);
    const s = await screen();
    if (!s.shown || !s.colour) { console.log("не понял экран:", s); break; }
    const fb = bot.feedback(s.shown, s.colour);
    log.push({ i: i + 1, holdMs, ...s, notes: fb.notes });
    const q = s.quest.split(" ").slice(0, 5).join(" ");
    if (q !== lastQuest) { console.log(`#${i + 1}: ${s.quest}`); lastQuest = q; }
  }
  const wins = log.filter((r) => r.colour === "win");
  console.log(`\n${preset} — ${PRESETS[preset].label}: ${log.length} попыток, побед ${wins.length} (по смягчению ${wins.filter((r) => r.eased).length})`);
  console.log("последние 12:", log.slice(-12).map((r) => `${r.shown}${r.colour === "win" ? (r.eased ? "~" : "✓") : r.colour === "early" ? "−" : "+"}`).join(" "));
  console.log(`\nДневник (${bot.diary.length} записей), последние:`);
  for (const d of bot.diary.slice(-8)) console.log(`  #${d.n} ${d.text}`);
  if (outDir) {
    require("fs").writeFileSync(`${outDir}/play_${preset}.json`, JSON.stringify(log));
    require("fs").writeFileSync(`${outDir}/diary_${preset}.txt`, bot.diary.map((d) => `#${d.n}  ${d.text}`).join("\n") + "\n");
    await p.screenshot({ path: `${outDir}/play_${preset}.png` });
  }
  await b.close();
})();
