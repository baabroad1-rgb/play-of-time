// micro_player.js — «микро-ИИ»: искусственный игрок Play of Time (Алекс 08.11.2026:
// «эмулятор, который будет стараться попасть в три секунды независимо от игрока,
// но не через управление счётчиком, а на основании обратной связи»).
//
// Агент НЕ знает своей длительности. У него, как у человека:
//   • внутренние часы с шумом, пропорциональным интервалу (закон Вебера):
//     нажатие = (намерение + свой_сдвиг) · (1 + N(0, cv)) + моторный шум;
//   • свой сдвиг, о котором он не знает (спешит или тянет), может дрейфовать;
//   • практика: шум падает от cv0 к cvBest по мере попыток.
// После попытки он видит только то, что видит игрок: число на счётчике (с
// точностью уровня) и цвет. По этому он и подстраивает намерение.
//
// Стратегии обучения:
//   kalman    — оценивает свой сдвиг как фильтр Калмана: большой шаг, пока не
//               уверен, потом маленький; не дёргается на шум;
//   delta     — поправка на долю увиденной ошибки (α), как многие люди: при
//               большом α перескакивает туда-сюда;
//   colour    — видит только цвет (рано/поздно/победа): лесенка — шаг в
//               сторону, при смене стороны шаг вдвое меньше;
//   none      — не учится вовсе (контроль: чистая случайность при сдвиге).
//
// Модуль общий: Node (симуляция, игра через браузер) и браузер.

(function (root) {
  "use strict";

  // Детерминированный генератор (mulberry32) — один и тот же seed даёт тот же прогон.
  function rng(seed) {
    let a = seed >>> 0;
    const next = () => {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    const normal = () => {
      let u = 0, v = 0;
      while (u === 0) u = next();
      while (v === 0) v = next();
      return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    };
    return { next, normal };
  }

  const PRESETS = {
    // Числа подобраны по реальным игрокам (docs/FACE_V3_THREE_TRAITS.md):
    // новички на L1 разброс ~90 мс и спешат, лучшие на L3 ~28–31 мс.
    novice:   { label: "новичок, спешит, дёргается", bias: -180, cv0: 0.035, cvBest: 0.020, practiceTau: 600, motorMs: 6, drift: 1.5, strategy: "delta",  alpha: 0.8 },
    careful:  { label: "осторожный, тянет, учится медленно", bias: 140, cv0: 0.025, cvBest: 0.014, practiceTau: 800, motorMs: 5, drift: 1.0, strategy: "delta", alpha: 0.15 },
    kalman:   { label: "внимательный (Калман)", bias: -120, cv0: 0.025, cvBest: 0.009, practiceTau: 700, motorMs: 3, drift: 1.0, strategy: "kalman" },
    colour:   { label: "смотрит только на цвет", bias: 160, cv0: 0.025, cvBest: 0.010, practiceTau: 700, motorMs: 3, drift: 1.0, strategy: "colour", step: 80 },
    robot:    { label: "не учится (контроль)", bias: -60, cv0: 0.012, cvBest: 0.012, practiceTau: 1, motorMs: 3, drift: 0, strategy: "none" },
  };

  class MicroPlayer {
    constructor(opts = {}) {
      const p = Object.assign({}, PRESETS.kalman, opts.preset ? PRESETS[opts.preset] : {}, opts);
      this.p = p;
      this.r = rng(p.seed == null ? 7 : p.seed);
      this.trueBias = p.bias;        // мс: насколько его «3 секунды» на самом деле длиннее/короче
      this.biasHat = 0;              // что он думает о своём сдвиге
      this.P = 200 * 200;            // неуверенность в этом (Калман), мс²
      this.step = p.step || 80;      // шаг лесенки (colour)
      this.lastSide = 0;
      this.n = 0;                    // сколько попыток сделано
      // Дневник (Алекс 08.11: «сопернику можно вменить ведение дневника — как он
      // рефлексирует над своими попытками»): записи от первого лица в заметные
      // моменты. Без языковой модели — правила над тем, что агент и так знает.
      this.diary = [];
      this.hist = [];                // {err, cell, colour, step}
      this.digits = null;
      this.sinceClean = 0;
      this.cleanTotal = 0;
    }

    write(text) { this.diary.push({ n: this.n, text }); return text; }

    reflect(seenErr, cell, colour, stepMs) {
      const h = this.hist, n = this.n, lv = { 100: "десятые", 10: "сотые", 1: "тысячные" }[cell] || "";
      const fmt = (ms) => `${Math.round(Math.abs(ms))} мс`;
      const side = seenErr < 0 ? -1 : seenErr > 0 ? 1 : 0;
      const prev = h.length > 1 ? h[h.length - 2] : null;
      const out = [];
      // Не повторяться: одна и та же мысль — не чаще, чем раз в gap попыток.
      this.said = this.said || {};
      const once = (kind, gap, text) => { if (n - (this.said[kind] ?? -1e9) >= gap) { this.said[kind] = n; out.push(text); } };
      if (this.digits !== cell) {
        if (this.digits != null) out.push(`Новая точность — ${lv}. Теперь «попал» значит уложиться в ±${cell / 2} мс; посмотрим, чего стоит моя рука.`);
        else out.push(`Начинаю. Считаю до трёх как умею и смотрю, что покажет счётчик.`);
        this.digits = cell;
      }
      if (side === 0) {
        this.cleanTotal++;
        if (this.cleanTotal === 1) out.push(`Ровно три! Первая чистая тройка — с ${n}-й попытки.`);
        else if (this.sinceClean >= 25) out.push(`Наконец ровно три — после ${this.sinceClean} попыток мимо. Ничего не меняю.`);
        this.sinceClean = 0;
      } else this.sinceClean++;
      // серия с одной стороны
      const last3 = h.slice(-3);
      if (last3.length === 3 && last3.every((x) => x.side === side && side !== 0) && !(h.length > 3 && h[h.length - 4].side === side)) {
        const mean = last3.reduce((a, x) => a + x.err, 0) / 3;
        once("streak", 15, `Третий раз подряд ${side < 0 ? "рано" : "поздно"}, в среднем на ${fmt(mean)}. Похоже, я ${side < 0 ? "спешу" : "тяну"} — ${side < 0 ? "держу дольше" : "отпускаю раньше"}.`);
      }
      // перескок: поправился и улетел на другую сторону дальше, чем был
      // (поправка, сделанная ПОСЛЕ прошлой попытки, должна быть заметной долей той ошибки —
      // иначе это не перестарался, а просто шум)
      if (prev && prev.side !== 0 && side !== 0 && side !== prev.side && Math.abs(seenErr) > Math.abs(prev.err) * 1.3 &&
          Math.abs(prev.step) >= Math.max(cell, 0.4 * Math.abs(prev.err))) {
        once("overshoot", 10, `Перестарался: после ${prev.err < 0 ? "раннего" : "позднего"} промаха поправился на ${fmt(prev.step)} и улетел на другую сторону (${seenErr > 0 ? "+" : "−"}${fmt(seenErr)}). Надо мельче.`);
      }
      // близнец: та же ошибка по модулю
      if (prev && side !== 0 && prev.side !== 0 && Math.abs(Math.abs(seenErr) - Math.abs(prev.err)) < 0.5) {
        once("twin", 40, `Повторил прошлый промах в точности (${fmt(seenErr)}). Рука помнит — знать бы ещё, что именно.`);
      }
      // уверенность (только Калман)
      if (this.p.strategy === "kalman" && !this.confident && Math.sqrt(this.P) < 15) {
        this.confident = true;
        out.push(`Кажется, я понял свой сдвиг: от природы ${this.biasHat < 0 ? "спешу" : "тяну"} примерно на ${fmt(this.biasHat)}, плюс-минус ${Math.round(Math.sqrt(this.P))} мс. Дальше поправляюсь понемногу — остальное уже не сдвиг, а разброс.`);
      }
      // каждые 25 попыток — итог
      if (n % 25 === 0) {
        const w = h.slice(-25), hit = w.filter((x) => x.side === 0).length, e = w.filter((x) => x.side < 0).length, l = w.filter((x) => x.side > 0).length;
        const errs = w.map((x) => x.err), m = errs.reduce((a, x) => a + x, 0) / w.length;
        const sd = Math.sqrt(errs.reduce((a, x) => a + (x - m) * (x - m), 0) / Math.max(1, w.length - 1));
        const what = Math.abs(m) > sd / 2 ? `главное сейчас — сдвиг (${m < 0 ? "рано" : "поздно"} на ${fmt(m)})` : `сдвига почти нет, мешает разброс ±${fmt(sd)}`;
        out.push(`Итог последних 25: ровно ${hit}, рано ${e}, поздно ${l}. ${what[0].toUpperCase() + what.slice(1)}.`);
      }
      return out.map((t) => this.write(t));
    }

    cv() {
      const p = this.p;
      return p.cvBest + (p.cv0 - p.cvBest) * Math.exp(-this.n / Math.max(1, p.practiceTau));
    }

    // Сколько мс он продержит кнопку — до того, как увидит результат.
    nextDurationMs() {
      const intention = 3000 - this.biasHat;
      const clock = (intention + this.trueBias) * (1 + this.r.normal() * this.cv());
      const motor = this.r.normal() * this.p.motorMs;
      this.trueBias += this.r.normal() * this.p.drift;  // медленный дрейф (усталость, настроение)
      return Math.max(1500, Math.round(clock + motor));
    }

    // Обратная связь — ровно то, что видно игроку: текст счётчика и цвет.
    // shown: "2.97" / "3.0" / "3.001"; colour: "early" | "late" | "win".
    feedback(shown, colour) {
      this.n++;
      const digits = (String(shown).split(".")[1] || "").length;
      const cell = Math.pow(10, 3 - digits);                 // 100 / 10 / 1 мс
      const seenErr = (parseFloat(shown) - 3) * 1000;        // ошибка с точностью уровня
      const p = this.p;
      const before = this.biasHat;
      if (p.strategy === "kalman") {
        // Наблюдение своего сдвига: z = seenErr + biasHat. Шум наблюдения — шум
        // часов плюс округление счётчика (клетка²/12); процесс — дрейф.
        const R = Math.pow(3000 * this.cv(), 2) + Math.pow(p.motorMs, 2) + cell * cell / 12;
        const Q = Math.pow(p.drift, 2) + 1;
        this.P += Q;
        const K = this.P / (this.P + R);
        this.biasHat += K * seenErr;
        this.P *= (1 - K);
      } else if (p.strategy === "delta") {
        this.biasHat += p.alpha * seenErr;
      } else if (p.strategy === "colour") {
        const side = colour === "early" ? -1 : colour === "late" ? 1 : 0;
        if (side !== 0) {
          if (this.lastSide !== 0 && side !== this.lastSide) this.step = Math.max(cell / 2, this.step / 2);
          this.biasHat += side * this.step;
          this.lastSide = side;
        }
      }
      const step = this.biasHat - before;
      this.hist.push({ err: seenErr, cell, colour, step, side: seenErr < 0 ? -1 : seenErr > 0 ? 1 : 0 });
      const notes = this.reflect(seenErr, cell, colour, step);
      return { seenErr, cell, notes };
    }
  }

  const api = { MicroPlayer, PRESETS, rng };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.MicroPlayerLib = api;
})(typeof self !== "undefined" ? self : this);
