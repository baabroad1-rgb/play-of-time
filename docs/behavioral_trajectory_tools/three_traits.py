# -*- coding: utf-8 -*-
"""
three_traits.py — три характеристики игрока для лица (Алекс 07.11.2026):
точность (кучность), баланс, везение. Формулы и смысл — docs/FACE_V3_THREE_TRAITS.md.

    python three_traits.py                # сводка по дням игроков: распределения, устойчивость, этапы
    python three_traits.py --fetch        # сначала скачать свежие attempts.json/players.json

Данные — attempts.json рядом со скриптом (формат api/research_raw_attempts.php,
тот же, что у behavioral_profile.py; --fetch берётся оттуда же).
"""
import json, math, os, sys, collections, statistics as st

HERE = os.path.dirname(os.path.abspath(__file__))
ATT_PATH = os.path.join(HERE, "attempts.json")
CELL = {1: 100, 2: 10, 3: 1}      # ячейка уровня, мс: десятые / сотые / тысячные
TRIM_MS = 1500                    # промах дальше полутора секунд — случайное нажатие, не считаем
MIN_N, MIN_MISSES, MIN_E = 20, 12, 3.0
LUCK_PRIOR = 3                    # сглаживание везения: (G + 3) / (E + 3)
STAGE_SIGMA, STAGE_B = 50.0, 0.20 # этап 1 → 2 при σ ≤ 50 мс; этап 2 → 3 при |B| ≤ 0.20


def Phi(x): return 0.5 * (1 + math.erf(x / math.sqrt(2)))

def rounded_error(r):
    f = 10 ** r["accuracy"]
    return (round(r["duration_ms"] / 1000 * f) - round(3 * f)) * (1000 / f)

def is_clean(r): return abs(rounded_error(r)) < 1e-9   # ровно 3 на точности уровня, без допуска

def robust(es):
    m = st.median(es)
    return m, 1.4826 * st.median([abs(e - m) for e in es])

def pois_tail(k, mu):
    """P(X >= k) для пуассоновского X со средним mu."""
    k = int(round(k))
    if k <= 0: return 1.0
    s, t = 0.0, math.exp(-mu)
    for i in range(k): s += t; t *= mu / (i + 1)
    return max(0.0, 1 - s)

def traits(window):
    """Три характеристики по набору попыток (может смешивать уровни). None — мало попыток."""
    rows = [r for r in window if abs(r["error_ms"]) <= TRIM_MS]
    if len(rows) < MIN_N: return None
    med, sigma = robust([r["error_ms"] for r in rows])          # 1. кучность, мс
    misses = [r["error_ms"] for r in rows if not is_clean(r)]
    s_abs = sum(abs(e) for e in misses)
    B = sum(misses) / s_abs if len(misses) >= MIN_MISSES and s_abs else None   # 2. баланс, −1..+1
    G = E = 0.0                                                    # 3. везение
    for a, c in CELL.items():
        lv = [r for r in rows if r["accuracy"] == a]
        if len(lv) < MIN_N: continue
        m, s = robust([r["error_ms"] for r in lv]); s = max(s, 1.0)
        E += len(lv) * (Phi((c / 2 - m) / s) - Phi((-c / 2 - m) / s))
        G += sum(1 for r in lv if is_clean(r))
    luck = (G + LUCK_PRIOR) / (E + LUCK_PRIOR) if E >= MIN_E else None
    rare = pois_tail(G, E) < 0.05 if E >= MIN_E else False
    stage = 1 if sigma > STAGE_SIGMA else (2 if B is None or abs(B) > STAGE_B else 3)
    return dict(n=len(rows), sigma=sigma, median=med, B=B, G=G, E=E, luck=luck, third_eye=rare, stage=stage)


def icc(groups):
    groups = [g for g in groups if len(g) >= 3]
    if len(groups) < 2: return None
    allv = [x for g in groups for x in g]; gm = st.mean(allv); k, N = len(groups), len(allv)
    n0 = (N - sum(len(g) ** 2 for g in groups) / N) / (k - 1)
    msb = sum(len(g) * (st.mean(g) - gm) ** 2 for g in groups) / (k - 1)
    msw = sum((x - st.mean(g)) ** 2 for g in groups for x in g) / (N - k)
    return (msb - msw) / (msb + (n0 - 1) * msw)

def pct(xs, ps=(5, 25, 50, 75, 95)):
    xs = sorted(xs); return "  ".join("P%d %.2f" % (p, xs[min(len(xs) - 1, int(len(xs) * p / 100))]) for p in ps)

def main():
    if "--fetch" in sys.argv:
        sys.path.insert(0, HERE); import behavioral_profile; behavioral_profile.fetch_fresh()
    rows = json.load(open(ATT_PATH, encoding="utf-8"))["rows"]
    days = collections.defaultdict(list)
    for r in rows: days[(r["player_id"], r["epoch"], r["recorded_at"][:10])].append(r)
    res = []
    for (pid, _, day), w in days.items():
        if len(w) < 30: continue
        t = traits(w)
        if not t or t["B"] is None: continue
        t.update(pid=pid[:8], day=day, lvl=collections.Counter(r["accuracy"] for r in w).most_common(1)[0][0])
        res.append(t)
    print(f"попыток {len(rows)}, игровых дней (от 30 попыток) {len(res)}\n")
    for lv in (1, 2, 3):
        R = [t for t in res if t["lvl"] == lv]
        if not R: continue
        L = [t for t in R if t["luck"] is not None]
        print(f"L{lv}: дней {len(R)}")
        print("  кучность σ, мс   ", pct([t["sigma"] for t in R]))
        print("  баланс B         ", pct([t["B"] for t in R]))
        if L: print("  везение ×        ", pct([t["luck"] for t in L]), f"(дней с E≥{MIN_E:.0f}: {len(L)})")
        print("  чистых троек / положено по модели = %.2f" % (sum(t["G"] for t in R) / max(1e-9, sum(t["E"] for t in R))))
        by = collections.defaultdict(list)
        for t in R: by[t["pid"]].append(t)
        for name, f in (("log σ", lambda t: math.log(t["sigma"])), ("B", lambda t: t["B"]),
                        ("везение", lambda t: t["luck"] if t["luck"] is not None else 1.0)):
            v = icc([[f(t) for t in g] for g in by.values()])
            if v is not None: print(f"  ICC {name:<8} {v:.2f}  (доля различий между людьми, а не между днями)")
        D = [t for t in R if t["E"] >= MIN_E]
        if D: print("  разброс везения против чистого случая: %.2f (1 = случай)" % (sum((t["G"] - t["E"]) ** 2 / t["E"] for t in D) / len(D)))
        print("  этапы 1/2/3: %s\n" % "/".join(str(sum(1 for t in R if t["stage"] == s)) for s in (1, 2, 3)))
    D = [t for t in res if t["E"] >= MIN_E]
    print("третий глаз (шанс < 5%%): %d из %d дней" % (sum(1 for t in D if t["third_eye"]), len(D)))
    print("везение ≥ ×2: %d, ≥ ×3: %d" % (sum(1 for t in D if t["luck"] >= 2), sum(1 for t in D if t["luck"] >= 3)))

if __name__ == "__main__":
    main()
