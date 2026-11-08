// mock_rival_screen.js — макет экрана аналитики «You / AI» (Алекс 08.11.2026).
// «Игрок» (агент careful) делает 60 попыток в настоящем вебе, Doppelgänger —
// те же 60 попыток в тех же условиях; снимаются оба режима экрана, в режиме AI —
// дневник. Это МАКЕТ: переключатель и дневник впрыскиваются в страницу, в
// app.*.js их нет.
//   (cd webapp && python3 -m http.server 8123) &
//   node tools/micro_player/mock_rival_screen.js /tmp/out     -> you.png, ai.png
const { chromium } = require('playwright');
const { MicroPlayer } = require('./micro_player.js');
const OUT = (process.argv[2] || '.') + '/';
const KEY = 'play-of-time.web.v1';
const CELL = { 0: 1000, 1: 100, 2: 10, 3: 1 };
const cells = (ms, a) => Math.floor((ms + CELL[a] / 2) / CELL[a]);
function scm(prev, curr, next) {
  const d = (x) => x.endTime - x.startTime, A = 3000;
  const tp = Math.abs(d(prev) - A), tc = Math.abs(d(curr) - A), tn = Math.abs(d(next) - A);
  const den = 3 * tc + tp + tn + Math.abs(d(curr) - d(next)) + Math.abs(d(curr) - d(prev)) - Math.abs(d(next) - d(prev)) / 3 + 1;
  return 99 / den * Math.pow(10, curr.accuracy - 3);
}
function stats(atts) {
  const win = (a) => Math.abs((cells(a.endTime - a.startTime, a.accuracy) - cells(3000, a.accuracy)) * CELL[a.accuracy]) <= (a.toleranceMs || 0) + 0.5;
  const w = atts.filter(win).length;
  const e = atts.map((a) => a.endTime - a.startTime - 3000).sort((x, y) => x - y);
  const med = e[e.length >> 1];
  const mad = [...e.map((x) => Math.abs(x - med))].sort((x, y) => x - y)[e.length >> 1] * 1.4826;
  return { hit: 100 * w / atts.length, wins: w, sigma: mad };
}
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 360, height: 740 }, deviceScaleFactor: 2 });
  await p.addInitScript(() => { window.__clock = 1000; const e0 = Date.now() - 3600000; performance.now = () => window.__clock; Date.now = () => e0 + window.__clock; HTMLMediaElement.prototype.play = function () { return Promise.resolve(); }; });
  await p.goto('http://localhost:8123/index.html');
  await p.evaluate((K) => { localStorage.clear(); localStorage.setItem(K, JSON.stringify({ attempts: [], sessions: [], ids: {}, settings: {} })); }, KEY);
  await p.reload(); await p.waitForTimeout(300);
  await p.evaluate(() => { const n = document.querySelector('.nav-play'); if (n) n.click(); }); await p.waitForTimeout(200);
  await p.evaluate(() => { const w = document.getElementById('welcomeClose'); if (w) w.click(); });
  const press = (d) => p.evaluate((d) => { window.__clock += d; document.getElementById('runButton').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true })); }, d);
  const human = new MicroPlayer({ preset: 'careful', seed: 5 });     // stands in for the player
  for (let i = 0; i < 60; i++) {
    for (let k = 0; k < 8; k++) {
      const has = await p.evaluate(() => !!document.getElementById('runButton'));
      if (!has) { await p.evaluate(() => { const x = [...document.querySelectorAll('button')].find((x) => x.offsetParent && !/nav-tab|help-button|gameLauncher/.test(x.className + ' ' + x.id)); if (x) x.click(); }); await p.waitForTimeout(120); continue; }
      const lbl = await p.evaluate(() => document.querySelector('#runButton .timer-action').innerText);
      if (/RUN/.test(lbl)) break; await press(400); await p.waitForTimeout(50);
    }
    await press(900); await p.waitForTimeout(15); await press(human.nextDurationMs()); await p.waitForTimeout(35);
    const s = await p.evaluate(() => { const t = document.getElementById('timeDisplay'); return { shown: t.innerText.trim(), c: t.className }; });
    human.feedback(s.shown, /is-win/.test(s.c) ? 'win' : /is-early/.test(s.c) ? 'early' : 'late');
  }
  const db = await p.evaluate((K) => JSON.parse(localStorage.getItem(K)), KEY);
  const mine = [...db.attempts].sort((a, b) => a.startTime - b.startTime);
  // Doppelgänger: same attempts, same conditions, learns only from what the screen would show.
  const twin = new MicroPlayer({ preset: 'kalman', seed: 21, lang: 'en' });
  const ai = mine.map((a) => {
    const dur = twin.nextDurationMs();
    const errCells = cells(dur, a.accuracy) - cells(3000, a.accuracy);
    const shown = (cells(dur, a.accuracy) / Math.pow(10, a.accuracy)).toFixed(a.accuracy);
    const win = Math.abs(errCells * CELL[a.accuracy]) <= (a.toleranceMs || 0) + 0.5;
    twin.feedback(shown, win ? 'win' : errCells < 0 ? 'early' : 'late');
    return { ...a, endTime: a.startTime + dur };
  });
  ai.forEach((a, i) => { a.scm = scm(ai[i - 1] || a, a, ai[i + 1] || a); });
  const sm = stats(mine), sa = stats(ai);
  const lead = sm.hit >= sa.hit;
  const race = `${mine.length} attempts each · ${lead ? 'You lead' : 'Doppelgänger leads'}: wins ${sm.wins} vs ${sa.wins}, spread ${Math.round(sm.sigma)} vs ${Math.round(sa.sigma)} ms`;
  const css = `
    .period-context{justify-content:flex-end;gap:2px}
    .who{margin-right:auto;display:flex;border-radius:16px;background:#1a1a1a;border:1px solid #333;overflow:hidden}
    .who button{display:flex;align-items:center;gap:5px;height:30px;padding:0 12px 0 8px;border:0;background:transparent;color:rgba(255,255,255,.45);font:600 13px Roboto,Arial,sans-serif}
    .who button svg{width:20px;height:20px;opacity:.45}
    .who button.on{color:#fff}.who button.on svg{opacity:1}
    .who button.me.on{background:#ff551c}
    .who button.ai.on{background:#7c5cff}
    body.ai-mode .sh-left-card{box-shadow:inset 0 0 0 1.5px #7c5cff}
    body.ai-mode .summary-row > :not(.sh-left-card){display:none}
    body.ai-mode .summary-row .sh-left-card{width:100%;flex:1}
    .diary-head{display:flex;justify-content:space-between;margin:14px 4px 6px;color:#c4b5fd;font:500 15px Roboto,Arial,sans-serif}
    .diary-head span{color:rgba(255,255,255,.45)}
    .diary{background:#1c1c1c;border:1px solid #2c2c2c;border-radius:12px;padding:4px 14px}
    .diary p{margin:0;padding:9px 0;border-bottom:1px solid #2a2a2a;color:rgba(255,255,255,.86);font:13px/1.4 Roboto,Arial,sans-serif}
    .diary p:last-child{border-bottom:0}
    .diary p b{display:inline-block;min-width:30px;color:#a78bfa;font-weight:600}
    .diary .more{color:#a78bfa;font:600 12px Roboto,Arial,sans-serif;text-align:right;padding:8px 0}`;
  const meIcon = (c) => `<svg viewBox="0 0 20 20"><circle cx="10" cy="6.8" r="3.6" fill="${c}"/><path d="M3.5 18c.6-4 3.2-6 6.5-6s5.9 2 6.5 6" fill="${c}"/></svg>`;
  const twinIcon = (c1, c2) => `<svg viewBox="0 0 20 20"><circle cx="7.3" cy="7" r="3.2" fill="${c1}"/><path d="M1.3 18c.5-3.6 2.8-5.4 6-5.4s5.5 1.8 6 5.4" fill="${c1}"/><circle cx="12.7" cy="7" r="3.2" fill="${c2}"/><path d="M6.7 18c.5-3.6 2.8-5.4 6-5.4s5.5 1.8 6 5.4" fill="${c2}"/></svg>`;
  const diaryHtml = '<div class="diary-head">Doppelgänger’s diary<span>/ day</span></div><div class="diary">' +
    twin.diary.slice(-6).reverse().map((d) => `<p><b>#${d.n}</b>${d.text}</p>`).join('') + `<div class="more">All ${twin.diary.length} entries ›</div></div>`;
  async function shot(name, aiMode) {
    await p.evaluate(() => { const n = document.querySelector('.nav-play'); if (n) n.click(); }); await p.waitForTimeout(250);
    await p.evaluate(() => { const w = document.getElementById('welcomeClose'); if (w) w.click(); }); await p.waitForTimeout(100);
    await p.evaluate(() => document.querySelector('[data-view="analytics"]').click()); await p.waitForTimeout(300);
    await p.evaluate(() => document.querySelector('[data-period="TODAY"]').click()); await p.waitForTimeout(300);
    await p.evaluate(([css, aiMode, meOn, meOff, twOn, twOff, diaryHtml]) => {
      const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
      document.body.classList.toggle('ai-mode', aiMode);
      const row = document.querySelector('.period-context');
      const who = document.createElement('div'); who.className = 'who';
      who.innerHTML = `<button class="me${aiMode ? '' : ' on'}">${aiMode ? meOff : meOn}You</button><button class="ai${aiMode ? ' on' : ''}">${aiMode ? twOn : twOff}AI</button>`;
      row.prepend(who);
      if (aiMode) { const d = document.createElement('div'); d.innerHTML = diaryHtml; document.querySelector('.summary-row').after(d); }
    }, [css, aiMode, meIcon('#fff'), meIcon('#9a9a9a'), twinIcon('#d9ceff', '#ffffff'), twinIcon('#7a7a7a', '#9a9a9a'), diaryHtml]);
    await p.waitForTimeout(150);
    await p.screenshot({ path: OUT + name, clip: { x: 0, y: 0, width: 360, height: aiMode ? 740 : 560 } });
  }
  await shot('you.png', false);
  await p.evaluate(([K, ai]) => { const d = JSON.parse(localStorage.getItem(K)); d.attempts = ai; localStorage.setItem(K, JSON.stringify(d)); }, [KEY, ai]);
  await p.reload(); await p.waitForTimeout(400);
  await shot('ai.png', true);
  console.log(race);
  console.log('Doppelgänger diary:'); for (const d of twin.diary.slice(0, 6)) console.log('  #' + d.n, d.text);
  await b.close();
})();
