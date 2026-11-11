// weekly_reflection.js — недельная запись Doppelgänger'а: размышление, а не
// пересказ цифр (Алекс 11.11.2026: «дневник сейчас нечитаемый… пересказ
// численных показателей без всякой рефлексии. Сделай его еженедельным и чтобы
// это напоминало размышления человека в концепции ВПФ — какие инструменты мне
// придумать, чтобы эффективнее собой управлять»).
//
// ВПФ (Выготский): произвольное поведение строится через орудия-знаки — счёт,
// ритм, правило, ритуал, внешнюю метку; сначала орудие снаружи и требует
// усилия, потом уходит внутрь и становится своим. Поэтому записи идут линией
// из недели в неделю:
//   неделя 1 — заметил трудность, придумал орудие;
//   неделя 2 — пробую, оно пока костыль, требует усилия, иногда забываю;
//   неделя 3+ — либо «вросло» (трудность заметно меньше) → беру следующую,
//               либо не помогло → бросаю, пробую другое орудие.
// Плюс — настоящая перемена в его привычке поправляться («муха» сама её
// выучила): это орудие, которое он сделал себе, не заметив.
// Цифр — одна-две на запись. Насчёт причины — честно: «практика это или
// приём — пока не скажу».
//
//   node tools/micro_player/weekly_reflection.js <play_fly.json> [attempts_per_day]
"use strict";

const TOOLS = {
  rush: {
    noticed: [
      (w) => `I keep stopping early — my “three” runs short; ${w.earlyShare} of my misses this week were early.`,
      () => "My three is still shorter than the clock’s.",
    ],
    tool: "I can’t trust the feeling alone yet, so I need something outside it to lean on: a counted rhythm, one-and, two-and, three — until the count turns into my own sense of time.",
    alt: "Counting didn’t take. Instead, a picture: three steps across a room, and the stop lands on the third step, not before it.",
    short: "the counted rhythm", altShort: "the three steps",
    metric: (w) => Math.abs(w.meanMs), phrase: (a, b) => `on average I’m early by ${b} ms now, it was ${a}`,
  },
  drag: {
    noticed: [
      (w) => `I hold on too long, as if waiting for permission — ${w.lateShare} of my misses were late.`,
      () => "I still wait too long before letting go.",
    ],
    tool: "A tool for that: decide the moment of release before I start, and give it a word — “now” — that I say to myself on three.",
    alt: "The word didn’t help. A rule instead: if I notice I’m waiting, I’m already late — let go at once.",
    short: "the word “now”", altShort: "the waiting rule",
    metric: (w) => Math.abs(w.meanMs), phrase: (a, b) => `on average I’m late by ${b} ms now, it was ${a}`,
  },
  overshoot: {
    noticed: [
      () => "I chase every miss: one late, and the next one flies early, further than I was.",
      () => "I still jump from side to side after a miss.",
    ],
    tool: "What I need is a rule rather than a feeling: never correct by the whole miss, and never after a single result — a small step, then wait and see.",
    alt: "A rule about size didn’t hold. A rule about time, then: change nothing until two results agree.",
    short: "the rule of small steps", altShort: "waiting for two results",
    metric: (w) => Math.round(-w.r1 * 100), phrase: (a, b) => b < a ? "I jump across less often" : "I jump across as often as before",
  },
  stuck: {
    noticed: [
      () => "I get stuck on one side: three, four misses the same way before I move.",
      () => "I still sit on one side for too long.",
    ],
    tool: "I need a sign that interrupts me — after the second miss on the same side, change something on purpose, like a knot tied on a handkerchief.",
    alt: "The knot didn’t work — I kept forgetting it. Decide before the attempt instead: if the last one was early, this one I hold a little longer, whatever I feel.",
    short: "the knot after the second miss", altShort: "deciding before the attempt",
    metric: (w) => Math.round(w.r1 * 100), phrase: (a, b) => b < a ? "the runs on one side are shorter" : "the runs on one side are as long as before",
  },
  tire: {
    noticed: [
      () => "My last attempts in a session are worse than my first — the hand goes soft before I notice.",
      () => "The end of a session still costs me.",
    ],
    tool: "A tool against that is a stop rule decided in advance: end the session while it still goes well, not after it stops going.",
    alt: "The stop rule I kept breaking. Shorter sessions with a real pause between them, then.",
    short: "the stop rule", altShort: "short sessions",
    metric: (w) => Math.round(w.fatigue * 100), phrase: (a, b) => b < a ? "the end of a session is closer to its start" : "the end of a session is still worse",
  },
  spread: {
    noticed: [
      () => "There is hardly any lean left; what holds me back is how differently each three comes out.",
      () => "No lean to speak of — it’s the spread again.",
    ],
    tool: "I need the beginning to be the same every time — one breath, the same posture, the same first beat — a small ritual, so that if the start is identical, the end can be too.",
    alt: "The start wasn’t it. Maybe the middle: I lose the beat around the second second — count that one out loud inside.",
    short: "the ritual for the start", altShort: "the counted middle",
    metric: (w) => w.sdMs, phrase: (a, b) => `the spread is ${b} ms, it was ${a}`,
  },
};

const WEEK2 = [
  "It still takes effort — I have to remind myself every time, and I forget exactly when it matters.",
  "It’s still a crutch I lean on on purpose. Fine — that’s how a tool starts, outside of me.",
  "Some days it works, some days I forget it altogether.",
];

const CALM = [
  "No new tool this week. I watch how it goes and try not to fiddle with what works.",
  "I left myself alone this week — sometimes the best tool is not to reach for one.",
  "Nothing new to try. The hand is learning on its own; my job is not to get in its way.",
];

function pct(x) { return `${Math.round(x * 100)}%`; }

/**
 * Difficulties of the week in priority order (spread is always last — there
 * is always some). Overshooting and getting stuck come from the lag-1
 * correlation of his seen misses (r1): noise gives ≈ 0, chasing every miss
 * pushes it below zero, slow correction above.
 */
function issues(w) {
  const misses = w.early + w.late;
  const out = [];
  if (misses >= 20 && (w.early >= 2 * w.late || w.meanMs < -0.5 * w.sdMs)) out.push("rush");
  if (misses >= 20 && (w.late >= 2 * w.early || w.meanMs > 0.5 * w.sdMs)) out.push("drag");
  if (w.r1 < -0.2) out.push("overshoot");
  if (w.r1 > 0.25) out.push("stuck");
  if (w.fatigue > 0.15) out.push("tire");
  out.push("spread");
  return out;
}
const mainIssue = (w) => issues(w)[0];

/**
 * w — the week: {attempts, exact, early, late, meanMs, sdMs, r1, fatigue,
 *   questsDone[], windowsFailed, levelUp (name|null), habitStart{repeat,switched,near}, habitEnd{…}}
 * arc — carried from last week: {issue, alt, weeks, start} | null
 * Returns {text, arc}.
 */
function weeklyEntry(w, arc) {
  if (w.attempts < 20) return { text: "I hardly played this week — too little to learn anything about myself from.", arc };
  const s = [];
  const tools = (k, alt) => (alt ? TOOLS[k].alt : TOOLS[k].tool);
  const short = (a) => (a.alt ? TOOLS[a.issue].altShort : TOOLS[a.issue].short);
  const ctx = { earlyShare: pct(w.early / Math.max(1, w.early + w.late)), lateShare: pct(w.late / Math.max(1, w.early + w.late)) };

  // 1. the week, in one line
  if (w.levelUp) s.push(`The week the counter switched to ${w.levelUp} — everything I thought I knew about my three got ten times finer.`);
  else if (w.questsDone.length >= 3) s.push(`A good week: quests ${w.questsDone[0]} to ${w.questsDone[w.questsDone.length - 1]} are behind me.`);
  else if (w.questsDone.length === 2) s.push(`A good week: quests ${w.questsDone.join(" and ")} are behind me.`);
  else if (w.questsDone.length === 1) s.push(`Quest ${w.questsDone[0]} is behind me.`);
  else if (w.windowsFailed >= 2) s.push(`A hard week — ${w.windowsFailed} times the quest window closed on me before I made it.`);
  else if (w.exact === 0) s.push(`${w.attempts} attempts and not one exact three.`);
  else s.push(`About one exact three in ${Math.round(w.attempts / w.exact)} this week.`);

  // 2. a rule he made himself without noticing — a real change in how he corrects
  const names = { repeat: "after a second miss on the same side", switched: "after jumping to the other side", near: "when I’m within a step of three" };
  const ch = Object.keys(names)
    .filter((k) => w.habitStart && w.habitEnd && w.habitStart[k] != null && w.habitEnd[k] != null)
    .map((k) => ({ k, a: Math.round(w.habitStart[k] * 100), b: Math.round(w.habitEnd[k] * 100) }))
    .filter((c) => Math.abs(c.b - c.a) >= 12)
    .sort((x, y) => Math.abs(y.b - y.a) - Math.abs(x.b - x.a))[0];
  if (ch) s.push(ch.b < ch.a
    ? `Without quite deciding to, I made myself a brake: ${names[ch.k]} I now correct by about ${ch.b}% of the miss, not ${ch.a}%.`
    : `I’ve grown bolder ${names[ch.k]}: about ${ch.b}% of the miss now, where it was ${ch.a}%.`);

  // 3–4. the line of the tool
  const list = issues(w);
  const main = list[0];
  const fresh = (k, alt = 0, lead = "") => {
    s.push(lead + TOOLS[k].noticed[0](ctx));
    s.push(tools(k, alt));
    return { issue: k, alt, weeks: 0, start: TOOLS[k].metric(w) };
  };
  if (!arc || (arc.issue !== main && list.indexOf(arc.issue) < 0)) {
    // nothing carried, or the old trouble is gone from the list
    if (arc) s.push(`I hardly notice my old trouble anymore — ${short(arc)} did its job, or practice did.`);
    arc = fresh(main);
  } else if (arc.exhausted && arc.issue === main) {
    // both tools tried; don't loop — watch, or turn to something else if there is something
    const next = list.find((x) => x !== arc.issue && x !== "spread");
    if (next) arc = fresh(next, 0, "I’ve stopped fighting the old trouble head-on. ");
    else s.push(CALM[(w.index || 0) % CALM.length]);
  } else if (arc.settled && arc.issue === main) {
    // the old tool became his own, but this is still the main trouble — a new one on top
    const k = arc.issue;
    if (arc.alt) {
      s.push(`${cap(short(arc))} stays with me. The ${k === "spread" ? "spread" : "trouble"} is still there, but I’m out of ideas for now; maybe this part only gives way to time.`);
      arc = { ...arc, weeks: 0, start: TOOLS[k].metric(w) };
    } else {
      s.push(`${cap(short(arc))} is mine now and stays.`);
      const rest = TOOLS[k].alt.replace(/^[^.]*(didn’t take|didn’t help|didn’t hold|didn’t work[^.]*|wasn’t it|I kept breaking)\.\s*/, "");
      s.push("On top of it, " + rest.charAt(0).toLowerCase() + rest.slice(1));
      arc = { issue: k, alt: 1, weeks: 0, start: TOOLS[k].metric(w) };
    }
  } else {
    const k = arc.issue;
    const now = TOOLS[k].metric(w);
    const better = now < arc.start * 0.85;
    const weeks = arc.weeks + 1;
    if (weeks === 1) {
      s.push(`Second week with ${short(arc)}: ${TOOLS[k].phrase(arc.start, now)}${better ? "" : " — nothing to show yet"}. ${WEEK2[(w.index || 0) % WEEK2.length]}`);
      arc = { ...arc, weeks };
    } else if (better) {
      s.push(`${cap(short(arc))} is turning from a rule I remind myself of into something I simply do — ${TOOLS[k].phrase(arc.start, now)}.`);
      const next = list.find((x) => x !== k);
      if (next && next !== "spread" || (next === "spread" && k !== "spread")) arc = fresh(next, 0, "Time for the next thing. ");
      else { s.push("Nothing else stands out; I’ll keep it and keep going."); arc = { issue: k, alt: arc.alt, weeks: 0, start: now, settled: true }; }
    } else if (!arc.alt) {
      s.push(`${weeks + 1} weeks of ${short(arc)}, and ${TOOLS[k].phrase(arc.start, now)}. I’ll drop it.`);
      s.push(tools(k, 1));
      arc = { issue: k, alt: 1, weeks: 0, start: now };
    } else {
      s.push(`${cap(short(arc))} hasn’t moved anything — ${TOOLS[k].phrase(arc.start, now)}. Maybe this one only gives way to time.`);
      arc = { issue: k, alt: 1, weeks: 0, start: now, exhausted: true };
    }
  }
  return { text: s.join(" "), arc };
}

function cap(x) { return x.charAt(0).toUpperCase() + x.slice(1); }

/** The week's shape from SEEN misses: mean, sd, r1, fatigue (later thirds of sessions vs first thirds). */
function shape(seen, sessionLen = 33) {
  const m = seen.reduce((a, b) => a + b, 0) / seen.length;
  const sd = Math.sqrt(seen.reduce((a, b) => a + (b - m) ** 2, 0) / seen.length) || 1;
  let c = 0;
  for (let i = 1; i < seen.length; i++) c += (seen[i - 1] - m) * (seen[i] - m);
  const r1 = c / ((seen.length - 1) * sd * sd);
  const first = [], last = [];
  for (let i = 0; i < seen.length; i += sessionLen) {
    const x = seen.slice(i, i + sessionLen);
    if (x.length < 30) continue;
    first.push(...x.slice(0, 11).map(Math.abs));
    last.push(...x.slice(22).map(Math.abs));
  }
  const avg = (a) => a.reduce((p, q) => p + q, 0) / Math.max(1, a.length);
  const fatigue = first.length > 30 ? avg(last) / Math.max(1, avg(first)) - 1 : 0;
  return { meanMs: Math.round(m), sdMs: Math.round(sd), r1, fatigue };
}

/** Weeks from a play_web.js log, replaying the fly's mind to read its habits. */
function weeksFromLog(log, perWeek, MicroPlayer) {
  const bot = new MicroPlayer({ preset: "fly", seed: 11, lang: "en" });
  const rows = log.map((r) => {
    bot.nextDurationMs();
    bot.feedback(r.shown, r.colour);
    const seen = Math.round((parseFloat(r.shown) - 3) * 1000);
    return { ...r, seen, side: Math.sign(seen), digits: (r.shown.split(".")[1] || "").length, habits: bot.mb.habits() };
  });
  const out = [];
  for (let i = 0, wi = 0; i < rows.length; i += perWeek, wi++) {
    const wk = rows.slice(i, i + perWeek);
    const quests = [...new Set(wk.map((r) => (r.quest.match(/Quest (\d\.\d)/) || [])[1]).filter(Boolean))];
    const lv = [...new Set(wk.map((r) => r.digits))];
    const prev = i ? rows[i - 1] : null;
    out.push({
      index: wi, attempts: wk.length, exact: wk.filter((r) => r.side === 0).length,
      early: wk.filter((r) => r.side < 0).length, late: wk.filter((r) => r.side > 0).length,
      ...shape(wk.map((r) => r.seen)),
      questsDone: quests.slice(0, -1), windowsFailed: 0,
      levelUp: lv.length > 1 ? (lv[lv.length - 1] === 2 ? "hundredths" : "thousandths") : null,
      habitStart: prev ? prev.habits : null, habitEnd: wk[wk.length - 1].habits, quests,
    });
  }
  return out;
}

module.exports = { weeklyEntry, mainIssue, issues, shape, weeksFromLog, TOOLS };

if (require.main === module) {
  const fs = require("fs");
  const { MicroPlayer } = require("./micro_player.js");
  const log = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
  const perWeek = (Number(process.argv[3]) || 35) * 7;
  let arc = null;
  for (const w of weeksFromLog(log, perWeek, MicroPlayer)) {
    const e = weeklyEntry(w, arc);
    console.log(`\n── Week ${w.index + 1} (${w.attempts} attempts, quests ${w.quests.join(" → ")}; r1 ${w.r1.toFixed(2)}, sd ${w.sdMs}, mean ${w.meanMs}, fatigue ${w.fatigue.toFixed(2)})`);
    console.log(e.text);
    arc = e.arc;
  }
}
