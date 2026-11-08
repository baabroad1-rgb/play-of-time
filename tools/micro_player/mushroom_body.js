// mushroom_body.js — «муха»: учится учиться, устроена как центр обучения
// дрозофилы — грибовидное тело (Алекс 08.11.2026: «а насколько сложно
// реализовать это в виде какой-то минимальной нейронной сеточки? … выгрузили же
// структуру мозга дрозофилы — может, на её основе?»).
//
// Это НЕ эмуляция мозга мухи (та не учится и не умеет отсчитывать 3 секунды).
// Это та же схема, по которой муха учится связывать запах с наградой:
//
//   входы (проекционные нейроны) — что он видел на счётчике в последних попытках:
//     величина промаха в клетках уровня, сторона, серия с одной стороны, удивление
//     («больше обычного»), уровень точности. Частоты 0…1, как у нейронов.
//   ~2000 клеток Кеньона — каждая слушает 6 случайных входов, и активны только
//     5 % самых возбуждённых (у мухи это делает тормозной нейрон APL). Так любая
//     ситуация кодируется своим редким набором клеток, а похожие — похожими.
//   выходные нейроны — «голоса» за то, на какую долю увиденного промаха
//     поправиться: 0 %, 10 %, 20 %, 35 %, 50 %, 75 %, 100 %. Это и есть «учиться
//     учиться»: он выбирает не время нажатия, а шаг своего обучения.
//   дофамин — после следующей попытки: насколько промах вышел меньше обычного
//     (+ бонус за зелёную). Ошибка предсказания награды меняет связи активных
//     клеток Кеньона с выбранным выходом — и только их, как у мухи.
//
// Направление поправки он знает (держал дольше — число больше), как и игрок.
// Учится только связь «клетки Кеньона → выходы»; остальное фиксировано и случайно.
"use strict";

const STEPS = [0, 0.10, 0.20, 0.35, 0.50, 0.75, 1.00];

class MushroomBody {
  constructor(rand, opts = {}) {
    this.r = rand;
    this.nKC = opts.nKC || 2000;
    this.fanIn = opts.fanIn || 6;
    this.nActive = Math.round(this.nKC * (opts.sparsity || 0.05));
    this.eta = opts.eta || 0.05;           // скорость пластичности синапса
    this.temp = opts.temp || 0.03;         // «любопытство»: насколько случайно выбирает
    this.counterfactual = opts.counterfactual !== false;
    this.nPN = 26;
    // Проводка вход → клетка Кеньона: случайная и неизменная, как у мухи.
    this.kcIn = new Int16Array(this.nKC * this.fanIn);
    this.kcW = new Float32Array(this.nKC * this.fanIn);
    for (let i = 0; i < this.nKC * this.fanIn; i++) {
      this.kcIn[i] = Math.floor(rand.next() * this.nPN);
      this.kcW[i] = 0.5 + rand.next();
    }
    this.w = new Float32Array(this.nKC * STEPS.length);   // клетка Кеньона → выход (учится)
    this.pending = null;                   // что выбрал в прошлый раз и в какой ситуации
    this.avgAbs = null;                    // обычный размер промаха (для «меньше обычного»)
    this.recent = [];                      // последние выборы — для дневника
  }

  // Входы — частоты 0…1. hist: [{err, cell, side}], последняя — текущая.
  features(hist) {
    const pn = new Float32Array(this.nPN);
    const h = hist[hist.length - 1], cell = h.cell;
    const cells = Math.abs(h.err) / cell;
    // величина промаха в клетках — популяция из 6 нейронов, настроенных на 0, 1, 2, 4, 8, 16+ клеток
    const lc = Math.log2(1 + cells);
    for (let k = 0; k < 6; k++) pn[k] = Math.exp(-((lc - k) ** 2) / 0.5);
    // сторона этой, прошлой и позапрошлой попыток: рано / ровно / поздно
    for (let j = 0; j < 3; j++) {
      const x = hist[hist.length - 1 - j];
      if (x) pn[6 + 3 * j + (x.side + 1)] = 1;
    }
    // серия с одной стороны: 1, 2, 3+
    let run = 0;
    for (let j = hist.length - 1; j >= 0 && hist[j].side === h.side && h.side !== 0; j--) run++;
    if (run > 0) pn[15 + Math.min(run, 3) - 1] = 1;
    // удивление: промах относительно обычного — меньше, как обычно, больше, сильно больше
    const rel = this.avgAbs ? Math.abs(h.err) / Math.max(this.avgAbs, 0.5) : 1;
    const rl = Math.log2(rel + 1e-3);
    [-1.5, 0, 1, 2].forEach((c, k) => { pn[18 + k] = Math.exp(-((rl - c) ** 2) / 0.6); });
    // уровень точности
    pn[22 + (cell >= 100 ? 0 : cell >= 10 ? 1 : 2)] = 1;
    pn[25] = 0.5;                          // фон
    return pn;
  }

  // Редкий код: 5 % самых возбуждённых клеток Кеньона.
  encode(pn) {
    const a = new Float32Array(this.nKC);
    for (let i = 0; i < this.nKC; i++) {
      let s = 0;
      for (let f = 0; f < this.fanIn; f++) s += pn[this.kcIn[i * this.fanIn + f]] * this.kcW[i * this.fanIn + f];
      a[i] = s + this.r.next() * 1e-3;     // разбить ничьи
    }
    const idx = Array.from(a.keys()).sort((x, y) => a[y] - a[x]);
    return idx.slice(0, this.nActive);
  }

  values(active) {
    const v = new Float64Array(STEPS.length);
    for (const i of active) for (let j = 0; j < STEPS.length; j++) v[j] += this.w[i * STEPS.length + j];
    for (let j = 0; j < STEPS.length; j++) v[j] /= active.length;
    return v;
  }

  // Увидел промах err (мс) → дофамин за прошлый выбор → выбор шага на этот раз.
  // Возвращает поправку к своему сдвигу (мс) и выбранный шаг.
  step(hist, win) {
    const h = hist[hist.length - 1];
    const abs = Math.abs(h.err);
    if (this.pending) {
      const norm = Math.max(this.avgAbs || abs || 1, h.cell / 2);
      const { active, j, v, prevErr, all } = this.pending;
      if (this.counterfactual) {
        // «А если бы поправился иначе?» — следующий промах при другом шаге
        // предсказуем: e' = e + (k_выбранный − k_другой)·e_прошлый. Дофамин
        // получают все выходы, каждый за свой вариант.
        for (let q = 0; q < STEPS.length; q++) {
          const e2 = h.err + (STEPS[j] - STEPS[q]) * prevErr;
          const rq = -Math.abs(e2) / norm + (Math.abs(e2) <= h.cell / 2 ? 0.5 : 0);
          const dq = rq - all[q];
          for (const i of active) this.w[i * STEPS.length + q] += this.eta * dq;
        }
      } else {
        const reward = -abs / norm + (win ? 0.5 : 0);
        const delta = reward - v;          // ошибка предсказания награды — дофамин
        for (const i of active) this.w[i * STEPS.length + j] += this.eta * delta;
      }
    }
    this.avgAbs = this.avgAbs == null ? Math.max(abs, h.cell / 2) : 0.97 * this.avgAbs + 0.03 * Math.max(abs, h.cell / 2);

    const active = this.encode(this.features(hist));
    const v = this.values(active);
    // выбор: softmax по «голосам» выходных нейронов
    const mx = Math.max(...v);
    const p = Array.from(v, (x) => Math.exp((x - mx) / this.temp));
    const sum = p.reduce((a, b) => a + b, 0);
    let u = this.r.next() * sum, j = 0;
    while (j < STEPS.length - 1 && (u -= p[j]) > 0) j++;
    this.pending = { active, j, v: v[j], prevErr: h.err, all: v };
    const k = STEPS[j];
    // для дневника — не случайно выбранный шаг, а ожидаемый по нынешним
    // «голосам» (иначе в привычки попадает шум выбора)
    const ek = p.reduce((a, pj, q) => a + pj * STEPS[q], 0) / sum;
    this.recent.push({ k: ek, side: h.side, prevSide: hist.length > 1 ? hist[hist.length - 2].side : 0, cells: abs / h.cell });
    if (this.recent.length > 100) this.recent.shift();
    return { correction: k * h.err, k };
  }

  // Средний шаг в трёх ситуациях за последние 100 попыток — для дневника.
  habits() {
    const avg = (f) => { const x = this.recent.filter(f); return x.length >= 8 ? x.reduce((a, b) => a + b.k, 0) / x.length : null; };
    return {
      repeat: avg((x) => x.side !== 0 && x.side === x.prevSide),
      switched: avg((x) => x.side !== 0 && x.prevSide !== 0 && x.side !== x.prevSide),
      near: avg((x) => x.cells <= 1),
    };
  }
}

module.exports = { MushroomBody, STEPS };
