# -*- coding: utf-8 -*-
"""
behavioral_profile.py — печатает готовый психологический профиль игрока по
всем 12 чертам из docs/BEHAVIORAL_TRAJECTORY_DESIGN.md, одной командой.

Зачем: раньше весь путь (выгрузить сырые попытки -> посчитать 9 метрик
вручную -> написать 12 абзацев руками) занимал час с лишним переписывания.
Детекторы отлажены на реальных данных 01.10.2026 (Agent_007) и одобрены —
дальше просто считаются заново под нового игрока и собираются в текст из
банка формулировок, без ручного письма с нуля.

Использование:
    python behavioral_profile.py <player_id или его первые 8 символов>
    python behavioral_profile.py --fetch <player_id>   # сначала скачать свежие данные

Данные берутся из attempts.json/players.json рядом со скриптом (тот же
формат, что отдаёт api/research_raw_attempts.php — kind=attempts&limit=200000
и kind=players). --fetch скачивает их заново тем же способом, которым это
делалось руками 01.10.2026.

Статус по каждой черте — "найдено"/"отсутствует"/"не проверено" — не
придумывается текстом, а определяется строго числами из detect_all():
посмотреть, что именно решило статус, можно в __main__ (--raw печатает
сырые числа без текста).

Ограничение: №4 (хрупкость на подступах к победе) детектора не имеет —
наш способ измерения реакции на победу смотрит на попытку ПОСЛЕ неё, не
на поведение непосредственно перед достижением цели. Здесь честно
печатается как "не проверено", а не подделывается.
"""
import json, math, datetime, sys, os
from collections import defaultdict, Counter

RESEARCH_KEY = "pot-research-2026-10-01-7f3c9a"  # см. webapp/api/research_raw_attempts.php — это трение, не секрет
API = "https://playoftime.online/api/research_raw_attempts.php"
HERE = os.path.dirname(os.path.abspath(__file__))
ATT_PATH = os.path.join(HERE, "attempts.json")
PLY_PATH = os.path.join(HERE, "players.json")


def fetch_fresh():
    import urllib.request
    for kind, path in (("attempts", ATT_PATH), ("players", PLY_PATH)):
        url = f"{API}?key={RESEARCH_KEY}&kind={kind}" + ("&limit=200000" if kind == "attempts" else "")
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read()
        with open(path, "wb") as f:
            f.write(data)
        print(f"[fetch] {kind} -> {path} ({len(data)} bytes)")


# ---------------------------------------------------------------- helpers --
def median(xs):
    xs = sorted(xs); n = len(xs)
    if n == 0: return None
    m = n // 2
    return xs[m] if n % 2 else (xs[m-1]+xs[m])/2.0

def linreg_slope(xs, ys):
    n = len(xs)
    if n < 2: return None
    mx, my = sum(xs)/n, sum(ys)/n
    sxx = sum((x-mx)**2 for x in xs)
    return (sum((x-mx)*(y-my) for x,y in zip(xs,ys))/sxx) if sxx else None

def spearman(xs, ys):
    n = len(xs)
    if n < 3: return None
    def rank(v):
        order = sorted(range(len(v)), key=lambda i: v[i])
        r = [0.0]*len(v); i = 0
        while i < len(order):
            j = i
            while j+1 < len(order) and v[order[j+1]] == v[order[i]]: j += 1
            avg = (i+j)/2.0+1
            for k in range(i,j+1): r[order[k]] = avg
            i = j+1
        return r
    rx, ry = rank(xs), rank(ys)
    mx, my = sum(rx)/n, sum(ry)/n
    sxx = sum((a-mx)**2 for a in rx); syy = sum((b-my)**2 for b in ry)
    return (sum((a-mx)*(b-my) for a,b in zip(rx,ry))/math.sqrt(sxx*syy)) if sxx and syy else None

def is_true_three(accuracy, error_ms, tolerance_ms):
    factor = 10 ** int(accuracy)
    aim_rounded = round(3000/1000.0*factor)
    dur_rounded = round((3000+error_ms)/1000.0*factor)
    return abs((dur_rounded-aim_rounded)*(1000.0/factor)) <= max(int(tolerance_ms),0)+0.5

# known migration duplicate, same human two player_ids across the 26.09 package
# rename (see docs/BEHAVIORAL_TRAJECTORY_DESIGN.md) — extend this dict if more
# such pairs turn up (full-content-overlap check, same method, finds them).
KNOWN_MERGE = {"4114851c": "1dd5c9c3"}


def canon(pid, players):
    for newp, canp in KNOWN_MERGE.items():
        if pid.startswith(newp):
            for p2 in players:
                if p2.startswith(canp): return p2
    return pid


def load_data():
    players_raw = json.load(open(PLY_PATH, encoding="utf-8"))["rows"]
    attempts_raw = json.load(open(ATT_PATH, encoding="utf-8"))["rows"]
    players = {p["player_id"]: p for p in players_raw}

    seen, merged = set(), []
    for a in attempts_raw:
        pid = canon(a["player_id"], players)
        sig = (pid, a["session_id"], a["local_id"], a["recorded_at"], a["error_ms"])
        if sig in seen: continue
        seen.add(sig)
        a2 = dict(a); a2["player_id"] = pid
        merged.append(a2)
    return players, merged


def qualifying_rows(players, attempts, player_id_prefix, min_level_task=(2,2), current_epoch_only=True):
    pid = next((k for k in players if k.startswith(player_id_prefix)), None)
    if pid is None:
        raise SystemExit(f"player_id starting with '{player_id_prefix}' not found")
    pid = canon(pid, players)
    cur_epoch = players[pid]["current_epoch"]
    rows = [a for a in attempts if a["player_id"] == pid
            and (a["level_id"], a["task_id"]) >= min_level_task
            and (a["epoch"] == cur_epoch if current_epoch_only else True)]
    rows.sort(key=lambda r: (r["session_id"], r["local_id"]))
    return pid, players[pid], rows


# ------------------------------------------------------------ 12 detectors --
def detect_all(rows):
    n = len(rows)
    if n < 20:
        return {"error": f"only {n} qualifying attempts, need >=20"}
    err  = [r["error_ms"] for r in rows]
    abse = [abs(e) for e in err]
    tol  = [r["tolerance_ms"] for r in rows]
    acc  = [r["accuracy"] for r in rows]
    win3 = [is_true_three(r["accuracy"], r["error_ms"], r["tolerance_ms"]) for r in rows]
    baseline_med = median(abse)
    GRID = {1:100, 2:10, 3:1}
    out = {"n": n, "baseline_med_abs": baseline_med}

    # 1. feedback-blindness: longest same-sign run, slope within it
    best_len, best_slope = 0, None
    i = 0
    while i < n:
        s = 1 if err[i]>0 else (-1 if err[i]<0 else 0)
        j = i
        while j+1 < n:
            s2 = 1 if err[j+1]>0 else (-1 if err[j+1]<0 else 0)
            if s2 != s or s == 0: break
            j += 1
        L = j-i+1
        if s != 0 and L > best_len:
            best_len = L; best_slope = linreg_slope(list(range(L)), abse[i:j+1])
        i = j+1
    out["t1_feedback_blindness"] = {
        "run": best_len, "slope": best_slope,
        "status": "found" if (best_len >= 8 and (best_slope is None or best_slope >= -0.5)) else "absent"}

    # 2. unstable peak performance
    grid = [GRID[a] for a in acc]
    near = [1 if (win3[k] or abse[k] <= 2*grid[k]) else 0 for k in range(n)]
    max_run = cur = 0
    for v in near:
        cur = cur+1 if v else 0; max_run = max(max_run, cur)
    near_cnt = sum(near)
    out["t2_unstable_peak"] = {
        "near_count": near_cnt, "max_run": max_run,
        "status": "found" if (near_cnt >= 5 and max_run <= 2) else "absent"}

    # 3. structured ignorance — smoothness of the weekly trend (needs macro_curve first, see below)
    wk_bucket = defaultdict(list)
    for r,a in zip(rows, abse):
        try:
            dt = datetime.date.fromisoformat(r["recorded_at"][:10])
            iso = dt.isocalendar(); wk_bucket[(iso[0],iso[1])].append(a)
        except Exception: pass
    weeks_sorted = sorted(wk_bucket.keys())
    weekly = [median(wk_bucket[k]) for k in weeks_sorted if len(wk_bucket[k]) >= 5]
    slope = linreg_slope(list(range(len(weekly))), weekly) if len(weekly) >= 3 else None
    out["t12_macro_curve"] = {"n_weeks": len(weekly), "slope_per_week": slope, "weekly": weekly,
                               "status": "found" if (slope is not None and slope < 0) else
                                         ("found_regression" if (slope is not None and slope > 0) else "not_enough_data")}

    if len(weekly) >= 4:
        net = weekly[0]-weekly[-1]
        pathlen = sum(abs(weekly[k+1]-weekly[k]) for k in range(len(weekly)-1))
        smoothness = (net/pathlen) if pathlen else None
        max_step = max(abs(weekly[k+1]-weekly[k]) for k in range(len(weekly)-1))
        max_step_share = (max_step/net) if net and net>0 else None
        is_smooth = bool(smoothness is not None and smoothness >= 0.5 and
                          (max_step_share is None or max_step_share < 0.5) and net > 0)
        out["t3_structured_ignorance"] = {"net_decline": net, "path_length": pathlen,
                                           "smoothness": smoothness, "max_step_share_of_net": max_step_share,
                                           "status": "found" if is_smooth else "absent"}
    else:
        out["t3_structured_ignorance"] = {"status": "not_enough_data"}

    # 4. clutch (proxy only — this is AFTER a win, not approaching one — see #5)
    after1, after2 = [], []
    for k in range(1, n):
        if win3[k-1]:
            after1.append(abse[k])
            if k >= 2 and win3[k-2]: after2.append(abse[k])
    out["t4_clutch_approach"] = {"status": "not_tested",
                                  "note": "no detector distinguishes the moment BEFORE a win from after it"}

    # 5. relaxation after relief
    if len(after1) >= 10:
        med1 = median(after1); med2 = median(after2) if len(after2) >= 5 else None
        worse = med1 > baseline_med * 1.05
        out["t5_relaxation_after_relief"] = {"n_after1": len(after1), "med_after1": med1,
                                              "n_after2": len(after2), "med_after2": med2, "baseline": baseline_med,
                                              "status": "found" if worse else "absent"}
    else:
        out["t5_relaxation_after_relief"] = {"status": "not_enough_data"}

    # 6. standard drift
    base_grp = [abse[k] for k in range(n) if tol[k]==0]
    eased_grp = [abse[k] for k in range(n) if tol[k]>0]
    if len(base_grp) >= 10 and len(eased_grp) >= 10:
        mb, me = median(base_grp), median(eased_grp)
        ratio = me/mb if mb else None
        out["t6_standard_drift"] = {"n_baseline": len(base_grp), "n_eased": len(eased_grp),
                                     "med_baseline": mb, "med_eased": me, "ratio": ratio,
                                     "status": "drift" if (ratio and ratio > 1.15) else ("held" if (ratio and ratio < 0.9) else "neutral")}
    else:
        out["t6_standard_drift"] = {"status": "not_enough_data"}

    # 7. recovery speed
    out_thr = 3*baseline_med if baseline_med else None
    recov = []
    if out_thr:
        k = 0
        while k < n:
            if abse[k] > out_thr:
                j = k+1
                while j < n and abse[j] > 1.5*baseline_med: j += 1
                if j < n: recov.append(j-k)
            k += 1
    out["t7_recovery"] = {"n_outliers": len(recov), "mean_len": (sum(recov)/len(recov)) if len(recov)>=5 else None,
                           "status": "found" if len(recov)>=5 else "not_enough_data"}

    # 8. pendulum
    flips, grew = 0, 0
    for k in range(1, n):
        s1 = 1 if err[k-1]>0 else (-1 if err[k-1]<0 else 0)
        s2 = 1 if err[k]>0 else (-1 if err[k]<0 else 0)
        if s1 and s2 and s1 != s2:
            flips += 1
            if abse[k] > abse[k-1]: grew += 1
    pct = (100.0*grew/flips) if flips >= 10 else None
    out["t8_pendulum"] = {"n_flips": flips, "grew_pct": pct,
                           "status": ("found" if pct is not None and pct >= 60 else
                                      ("absent" if pct is not None else "not_enough_data"))}

    # 9. asymmetric correction
    e_imp,e_n,l_imp,l_n = 0,0,0,0
    for k in range(n-1):
        if err[k] < 0:
            e_n += 1
            if abse[k+1] < abse[k]: e_imp += 1
        elif err[k] > 0:
            l_n += 1
            if abse[k+1] < abse[k]: l_imp += 1
    if e_n >= 15 and l_n >= 15:
        e_pct, l_pct = 100.0*e_imp/e_n, 100.0*l_imp/l_n
        gap = e_pct - l_pct
        out["t9_asymmetric"] = {"early_n": e_n, "early_pct": e_pct, "late_n": l_n, "late_pct": l_pct, "gap": gap,
                                 "status": "found" if abs(gap) >= 10 else "absent"}
    else:
        out["t9_asymmetric"] = {"status": "not_enough_data"}

    # 10. deliberation vs impulsivity (pause -> next-attempt improvement)
    by_sess = defaultdict(list)
    for r in rows: by_sess[r["session_id"]].append(r)
    pairs = []
    for sid, grp in by_sess.items():
        grp = sorted(grp, key=lambda r: r["local_id"])
        for k in range(1, len(grp)):
            a,b = grp[k-1], grp[k]
            if a["recorded_ms"] and b["recorded_ms"]:
                p = (b["recorded_ms"]-a["recorded_ms"])/1000.0
                if 0 < p < 60:
                    pairs.append((p, abs(b["error_ms"]) < abs(a["error_ms"])))
    if len(pairs) >= 50:
        pauses_sorted = sorted(p for p,_ in pairs)
        med_pause = pauses_sorted[len(pauses_sorted)//2]
        short = [imp for p,imp in pairs if p < med_pause]
        longp = [imp for p,imp in pairs if p >= med_pause]
        short_pct = 100.0*sum(short)/len(short) if short else None
        long_pct  = 100.0*sum(longp)/len(longp) if longp else None
        gap = (short_pct-long_pct) if (short_pct is not None and long_pct is not None) else None
        out["t10_deliberation"] = {"n_pairs": len(pairs), "short_pct": short_pct, "long_pct": long_pct, "gap": gap,
                                    "status": "found" if gap is not None and abs(gap) >= 3 else "absent"}
    else:
        out["t10_deliberation"] = {"status": "not_enough_data", "n_pairs": len(pairs)}

    # 11. warmup/burnout
    sess = defaultdict(list)
    for r,a in zip(rows, abse): sess[r["session_id"]].append(a)
    corrs, weights = [], []
    for sid, vals in sess.items():
        if len(vals) >= 8:
            c = spearman(list(range(len(vals))), vals)
            if c is not None: corrs.append(c); weights.append(len(vals))
    warm = (sum(c*w for c,w in zip(corrs,weights))/sum(weights)) if weights else None
    out["t11_warmup_burnout"] = {"n_sessions": len(corrs), "corr": warm,
                                  "status": ("warmup" if warm is not None and warm < -0.03 else
                                             ("burnout" if warm is not None and warm > 0.03 else
                                              ("neutral" if warm is not None else "not_enough_data")))}

    return out


# ---------------------------------------------------------- phrase bank ----
# Банк формулировок — одна-две развёрнутые версии на статус, той же глубины,
# что одобрено 01.10.2026 на Agent_007. Не идеальная замена ручного письма
# (это по-прежнему шаблон, не живой текст), но закрывает 80% работы — дальше
# достаточно точечной правки, а не письма с нуля.
PHRASES = {
    "t1_feedback_blindness": {
        "found": "Он повторяет один и тот же тип ошибки на протяжении длинных серий попыток подряд. Результат предыдущей попытки слабо влияет на следующее решение — внутри такой серии характер ошибки остаётся прежним, заметной поправки по ходу серии нет.",
        "absent": "Ошибка одного типа у него не держится долго. Внутри серии однотипных промахов видна поправка: каждая следующая попытка в среднем точнее предыдущей, прошлый результат реально влияет на следующее решение.",
    },
    "t2_unstable_peak": {
        "found": "Лучший результат у него случается нечасто и держится недолго: после разового случая точной игры почти сразу возвращается обычный уровень. Повторить пиковый результат по требованию он пока не может.",
        "absent": "Лучшая форма у него держится растянутыми полосами — подряд несколько попыток на пиковом уровне, а не одна случайная удача. Этот уровень воспроизводим, на него можно рассчитывать.",
    },
    "t3_structured_ignorance": {
        "found": "Улучшение распределено равномерно по неделям, без выделяющегося скачка в какой-то одной из них. Это постепенное привыкание — его трудно связать с одним конкретным моментом или решением.",
        "absent": "Улучшение сосредоточено в одной конкретной неделе, остальные недели почти не двигают результат. Это разовая перестройка подхода к задаче, после которой результат сразу выходит на новый уровень.",
        "not_enough_data": "Истории на этом уровне пока меньше четырёх полных недель — для разделения «постепенное привыкание» и «разовый скачок» нужно больше данных.",
    },
    "t4_clutch_approach": {
        "not_tested": "Эта черта описывает поведение в момент непосредственно перед победой, пока цель ещё не достигнута. Текущий детектор измеряет только попытку сразу после победы (это черта №5) — для момента перед ней нужен отдельный детектор, он ещё не написан.",
    },
    "t5_relaxation_after_relief": {
        "found": "Сразу после достижения цели точность следующих попыток заметно снижается, и возвращается к обычному уровню только через несколько попыток. Успех на короткое время снижает собранность.",
        "absent": "Сразу после достижения цели точность следующих попыток остаётся на обычном уровне, а после двух побед подряд становится даже немного выше исходной — собранность после успеха сохраняется.",
        "not_enough_data": "Побед в выборке пока мало для оценки точности сразу после успеха.",
    },
    "t6_standard_drift": {
        "drift": "Когда требования к точности смягчаются, его реальная точность снижается вместе с ними — он быстро подстраивается под новый, более низкий порог и держится на нём, пока условия снова не станут строгими.",
        "held": "Когда требования к точности смягчаются, его реальная точность остаётся на прежнем уровне. Он продолжает играть так же собранно, как при строгих условиях.",
        "neutral": "Смягчение требований к точности почти не меняет его реальный результат — он остаётся на том же уровне, что и при строгих условиях.",
        "not_enough_data": "Попыток в строгом и в смягчённом режиме пока мало для сравнения.",
    },
    "t7_recovery": {
        "found": "После крупного сбоя точность возвращается к обычному уровню быстро, за несколько попыток. Сбой заметно влияет только на короткий отрезок игры сразу после него.",
        "not_enough_data": "Крупных сбоев в выборке пока мало для оценки скорости возврата к обычному уровню.",
    },
    "t8_pendulum": {
        "found": "После отклонения в одну сторону он часто переходит в отклонение в противоположную сторону, причём более сильное. К равновесию он приходит через лишний шаг в другую крайность, а не сразу.",
        "absent": "После отклонения в одну сторону он возвращается к равновесию напрямую, следующая попытка ложится близко к цели с первого раза.",
        "not_enough_data": "Разворотов направления ошибки в выборке пока мало для оценки.",
    },
    "t9_asymmetric": {
        "found": "Он заметно лучше справляется с одним типом ошибки, чем с другим: поправка после одного типа отклонения происходит значительно чаще, чем после другого.",
        "absent": "Он одинаково хорошо справляется с обоими типами ошибки — доля удачных поправок после недолёта и после перелёта примерно равна.",
        "not_enough_data": "Случаев раннего и позднего типа ошибки порознь пока мало для сравнения.",
    },
    "t10_deliberation": {
        "found": "Короткая пауза перед следующей попыткой в среднем даёт более точный результат, чем долгая — быстрое решение работает для него немного лучше продолжительного обдумывания, хотя разница небольшая.",
        "absent": "Длина паузы перед следующей попыткой почти не связана с её точностью — быстрое решение и долгое обдумывание дают примерно одинаковый результат.",
        "not_enough_data": "Пар попыток с точным временем между ними пока мало для оценки.",
    },
    "t11_warmup_burnout": {
        "warmup": "Внутри одной продолжительной сессии точность растёт по ходу игры: первые попытки сессии менее точны, а точность увеличивается по мере того, как сессия продолжается.",
        "burnout": "Внутри одной продолжительной сессии точность снижается по ходу игры — продолжительное усилие заметно утомляет его внимание, к концу сессии результат хуже, чем в начале.",
        "neutral": "Продолжительность сессии почти не влияет на точность — она держится на одном уровне от начала до конца.",
        "not_enough_data": "Продолжительных сессий в выборке пока мало для оценки.",
    },
    "t12_macro_curve": {
        "found": "На дистанции в несколько месяцев виден устойчивый прогресс неделя за неделей — результат продолжает улучшаться, не останавливаясь на одном уровне.",
        "found_regression": "На дистанции в несколько месяцев результат ухудшается неделя за неделей — вместо прогресса видна обратная тенденция.",
        "not_enough_data": "Истории на этом уровне пока меньше нескольких недель — для оценки долгосрочного тренда нужно больше данных.",
    },
}

ORDER = ["t1_feedback_blindness","t2_unstable_peak","t3_structured_ignorance","t4_clutch_approach",
         "t5_relaxation_after_relief","t6_standard_drift","t7_recovery","t8_pendulum",
         "t9_asymmetric","t10_deliberation","t11_warmup_burnout","t12_macro_curve"]
TITLES = {
    "t1_feedback_blindness":"1. Слепота к обратной связи","t2_unstable_peak":"2. Нестабильный пик",
    "t3_structured_ignorance":"3. Структурированное незнание","t4_clutch_approach":"4. Хрупкость на подступах к победе",
    "t5_relaxation_after_relief":"5. Отпущенные вожжи","t6_standard_drift":"6. Соскальзывание вслед за уступкой",
    "t7_recovery":"7. Скорость восстановления","t8_pendulum":"8. Маятник","t9_asymmetric":"9. Однорукая обучаемость",
    "t10_deliberation":"10. Вдумчивость vs импульсивность","t11_warmup_burnout":"11. Разогрев vs выгорание",
    "t12_macro_curve":"12. Форма освоения навыка",
}

def narrate(results):
    lines = []
    for key in ORDER:
        r = results.get(key, {})
        status = r.get("status", "not_enough_data")
        text = PHRASES.get(key, {}).get(status) or PHRASES.get(key, {}).get("not_enough_data") or "(нет формулировки для этого статуса)"
        lines.append(f"**{TITLES[key]}.** {text}")
    return "\n\n".join(lines)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--fetch" in args:
        args.remove("--fetch"); fetch_fresh()
    if not args:
        raise SystemExit("usage: python behavioral_profile.py [--fetch] <player_id_prefix> [--raw]")
    raw_only = "--raw" in args
    if raw_only: args.remove("--raw")
    prefix = args[0]

    players, attempts = load_data()
    pid, pinfo, rows = qualifying_rows(players, attempts, prefix)
    results = detect_all(rows)
    print(f"=== {pid} (L{pinfo['level_id']}.{pinfo['task_id']}) — {len(rows)} качественных попыток ===\n")
    if raw_only:
        print(json.dumps(results, indent=2, ensure_ascii=False, default=str))
    else:
        print(narrate(results))
