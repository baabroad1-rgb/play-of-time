# -*- coding: utf-8 -*-
"""
behavioral_profile.py — профиль поведения игрока по 7 чертам одной командой:
числа (значение черты и место среди игроков) и готовый абзац на каждую черту.

С 25.10.2026 (решение Алекса) — 7 черт вместо прежних 12. Черты отобраны
исследованием 24–25.10.2026 из 40 кандидатов: держатся у одного игрока от
четверти к четверти истории (F-отношение) и совпадают на чётных и нечётных
сеансах (ρ половин) — то есть описывают человека, а не случай.

    1 Сторона промахов            доля ранних среди промахов
    2 Смена стороны после промаха 1 − (частота смены знака ÷ случайная)
    3 Ровность попыток            худшие 5% попыток ÷ обычная попытка
    4 Реакция на серию неудач     уход после 4+ промахов подряд ÷ обычный уход
    5 Регулярность                доля дней с игрой
    6 Дробность игры              заходов в игровой день
    7 Темп (предварительно)       типичная пауза между попытками, с

У черты три состояния (А, середина, Б), состояние — по постоянным порогам
из TRAITS. Абзацы берутся из TRAIT_TEXTS.md рядом со скриптом (21 абзац,
утверждены Алексом 25.10.2026): правка текста — там, не здесь.

Использование:
    python behavioral_profile.py <player_id или его первые 8 символов>
    python behavioral_profile.py --fetch <id>   # сначала скачать свежие данные
    python behavioral_profile.py <id> --raw     # только числа, JSON
    python behavioral_profile.py --all          # таблица состояний всех игроков

Данные — attempts.json/players.json рядом со скриптом (формат
api/research_raw_attempts.php: kind=attempts&limit=200000 и kind=players).
"""
import json, math, datetime, sys, os, re
import statistics as st
from collections import defaultdict, Counter

RESEARCH_KEY = "pot-research-2026-10-01-7f3c9a"  # см. webapp/api/research_raw_attempts.php — это трение, не секрет
API = "https://playoftime.online/api/research_raw_attempts.php"
HERE = os.path.dirname(os.path.abspath(__file__))
ATT_PATH = os.path.join(HERE, "attempts.json")
PLY_PATH = os.path.join(HERE, "players.json")
TEXTS_PATH = os.path.join(HERE, "TRAIT_TEXTS.md")

SITTING_GAP_S = 600     # больше 10 минут между попытками — новый заход
MIN_ATTEMPTS = 150
MIN_SITTINGS = 6
MIN_SPAN_DAYS = 7       # черты 5–6
MIN_PAUSES = 150        # черта 7, только паузы с миллисекундами


def fetch_fresh():
    import urllib.request
    for kind, path in (("attempts", ATT_PATH), ("players", PLY_PATH)):
        url = f"{API}?key={RESEARCH_KEY}&kind={kind}" + ("&limit=200000" if kind == "attempts" else "")
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read()
        with open(path, "wb") as f:
            f.write(data)
        print(f"[fetch] {kind} -> {path} ({len(data)} bytes)")


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


# ---------------------------------------------------------------- helpers --
NAN = float("nan")

def ok(v): return v is not None and not math.isnan(v)

def mean(xs):
    xs = [x for x in xs if ok(x)]
    return sum(xs) / len(xs) if xs else NAN

def med(xs):
    xs = [x for x in xs if ok(x)]
    return st.median(xs) if xs else NAN

def ratio(a, b):
    return a / b if ok(a) and ok(b) and b != 0 else NAN

def tsec(a):
    """Время попытки в секундах UTC: recorded_ms (точно), иначе recorded_at (до секунды)."""
    if a["recorded_ms"]: return a["recorded_ms"] / 1000.0
    return datetime.datetime.fromisoformat(a["recorded_at"]).replace(tzinfo=datetime.timezone.utc).timestamp()

def utc_date(ts): return datetime.datetime.fromtimestamp(ts, datetime.timezone.utc).date()


def prep(rows):
    """Попытки по времени; r — ошибка относительно местной нормы игрока; паузы; заходы."""
    rows = sorted(rows, key=lambda a: (tsec(a), a["local_id"]))
    # местная норма |ошибки|: медиана 51 соседней попытки того же уровня точности —
    # так черта 3 не путает провал с переходом на уровень, где ошибки другого масштаба
    idx_by_acc = defaultdict(list)
    for i, a in enumerate(rows): idx_by_acc[a["accuracy"]].append(i)
    r = [None] * len(rows)
    for acc, idxs in idx_by_acc.items():
        errs = [abs(rows[i]["error_ms"]) for i in idxs]
        for k, i in enumerate(idxs):
            m = st.median(errs[max(0, k - 25): k + 26]) or (10 ** (3 - acc)) / 2
            r[i] = abs(rows[i]["error_ms"]) / max(m, 1.0)
    t = [tsec(a) for a in rows]
    sit = [0]
    for i in range(1, len(rows)):
        sit.append(sit[-1] + (1 if t[i] - t[i - 1] > SITTING_GAP_S else 0))
    # пауза ПЕРЕД попыткой i: от STOP предыдущей до RUN этой; только внутри
    # захода и только когда у обеих попыток есть миллисекунды
    pause = [None] * len(rows)
    for i in range(1, len(rows)):
        if sit[i] == sit[i - 1] and rows[i]["recorded_ms"] and rows[i - 1]["recorded_ms"]:
            pause[i] = max(0.0, t[i] - t[i - 1] - rows[i]["duration_ms"] / 1000.0)
    return rows, r, pause, sit, t


# ------------------------------------------------------------- 7 traits ----
def compute(rows_raw):
    """Значения 7 черт; None, если данных на профиль мало."""
    if len(rows_raw) < MIN_ATTEMPTS: return None
    rows, r, pause, sit, t = prep(rows_raw)
    if sit[-1] + 1 < MIN_SITTINGS: return None
    win = [a["is_win"] == 1 for a in rows]
    sign = [(1 if a["error_ms"] > 0 else -1 if a["error_ms"] < 0 else 0) for a in rows]
    sittings = defaultdict(list)
    for i in range(len(rows)): sittings[sit[i]].append(i)
    sittings = list(sittings.values())
    # последняя попытка захода выброшена: часто это брошенная, недожатая попытка
    G = [g[:-1] for g in sittings if len(g) >= 6]
    I = [i for g in G for i in g]

    v = {}
    # 1. доля ранних среди промахов
    early = mean([1.0 if sign[i] < 0 else 0.0 for i in I if not win[i] and sign[i]])
    v[1] = early
    # 2. держит ли сторону: смены знака между соседними промахами против случайной
    #    частоты при той же доле ранних; минус — меняет чаще случая, плюс — держит
    sw = mean([1.0 if sign[a] != sign[b] else 0.0
               for g in G for a, b in zip(g, g[1:])
               if not win[a] and not win[b] and sign[a] and sign[b]])
    v[2] = 1 - ratio(sw, 2 * early * (1 - early))
    # 3. хвост: 95-й процентиль ошибки к медиане (в местных нормах)
    rs = sorted(r[i] for g in sittings for i in g[:-1])
    v[3] = ratio(rs[int(len(rs) * .95)], rs[len(rs) // 2]) if rs else NAN
    # 4. конец захода после 4+ промахов подряд против обычной доли концов
    ends = []
    for g in sittings:
        streak = 0
        for k, i in enumerate(g):
            streak = 0 if win[i] else streak + 1
            ends.append((streak, k == len(g) - 1))
    v[4] = ratio(mean([1.0 if e else 0.0 for s, e in ends if s >= 4]),
                 mean([1.0 if e else 0.0 for _, e in ends]))
    # 5–6. дни с игрой и заходы в игровой день
    days = Counter(utc_date(t[g[0]]) for g in sittings)
    span = (max(days) - min(days)).days + 1
    v[5] = len(days) / span if span >= MIN_SPAN_DAYS else NAN
    v[6] = mean(list(days.values())) if span >= MIN_SPAN_DAYS else NAN
    # 7. типичная пауза перед попыткой (паузы от 10 с — перерыв, не темп)
    ps = [pause[i] for i in I if pause[i] is not None and pause[i] < 10]
    v[7] = med(ps) if len(ps) >= MIN_PAUSES else NAN

    return {"values": v, "attempts": len(rows), "sittings": len(sittings),
            "play_days": len(days), "span_days": span, "pauses": len(ps)}


# Пороги — Таблица 1 в TRAIT_TEXTS.md, посчитаны на 10 игроках 24.10.2026,
# примерно по третям разброса. Пересмотреть, когда игроков станет больше.
#   a_if(v) / b_if(v) — условие состояния А / Б; иначе середина.
#   better — какие состояния «лучше» (Таблица 3), остальные «хуже».
TRAITS = {
    1: dict(title="Сторона промахов", measure="доля ранних среди промахов",
            a="рано", b="поздно", a_if=lambda v: v >= 0.55, b_if=lambda v: v <= 0.47,
            better={"mid"}),
    2: dict(title="Смена стороны после промаха", measure="1 − (смена стороны ÷ случайная)",
            a="меняет часто", b="держит сторону", a_if=lambda v: v <= -0.08, b_if=lambda v: v >= 0.08,
            better={"mid"}),
    3: dict(title="Ровность попыток", measure="худшие 5% попыток ÷ обычная",
            a="ровно", b="провалы", a_if=lambda v: v <= 3.3, b_if=lambda v: v >= 4.0,
            better={"A"}),
    4: dict(title="Реакция на серию неудач", measure="уход после 4+ промахов ÷ обычный уход",
            a="уходит", b="продолжает", a_if=lambda v: v >= 1.3, b_if=lambda v: v <= 0.85,
            better={"mid"}),
    5: dict(title="Регулярность", measure="доля дней с игрой",
            a="регулярно", b="вспышками", a_if=lambda v: v >= 0.6, b_if=lambda v: v <= 0.3,
            better={"A", "mid"}),
    6: dict(title="Дробность игры", measure="заходов в игровой день",
            a="дробно", b="один заход", a_if=lambda v: v >= 2.5, b_if=lambda v: v <= 1.4,
            better={"A", "mid"}),
    7: dict(title="Темп (предварительно)", measure="типичная пауза между попытками, с",
            a="быстрый", b="размеренный", a_if=lambda v: v <= 0.72, b_if=lambda v: v >= 0.85,
            better={"mid"}),
}

def state_of(n, v):
    """'A' / 'mid' / 'B', None — мало данных."""
    if not ok(v): return None
    tr = TRAITS[n]
    return "A" if tr["a_if"](v) else "B" if tr["b_if"](v) else "mid"

def state_name(n, s):
    return {"A": TRAITS[n]["a"], "B": TRAITS[n]["b"], "mid": "середина", None: "мало данных"}[s]


def load_texts():
    """21 абзац из TRAIT_TEXTS.md по заголовкам вида **1А. …**, **1 середина …**, **1Б. …**."""
    texts = {}
    for para in re.split(r"\n\s*\n", open(TEXTS_PATH, encoding="utf-8").read()):
        m = re.match(r"\*\*([1-7])(А|Б| середина)[^*]*\*\*\s*(.*)", para.strip(), re.S)
        if m:
            texts[(int(m[1]), {"А": "A", "Б": "B", " середина": "mid"}[m[2]])] = " ".join(m[3].split())
    missing = [f"{n}{s}" for n in TRAITS for s in ("A", "mid", "B") if (n, s) not in texts]
    if missing: raise SystemExit(f"в {TEXTS_PATH} нет абзацев: {', '.join(missing)}")
    return texts

NO_DATA_TEXT = {
    1: "Промахов пока мало, чтобы увидеть, в какую сторону вы ошибаетесь чаще.",
    2: "Промахов подряд пока мало, чтобы увидеть, как вы поправляетесь после ошибки.",
    3: "Попыток пока мало, чтобы оценить ровность.",
    4: "Серий промахов пока мало, чтобы увидеть, как вы на них отвечаете.",
    5: "История игры пока короче недели — регулярность появится позже.",
    6: "История игры пока короче недели — дробность появится позже.",
    7: "Попыток с точным временем пока мало — темп появится, когда их наберётся больше.",
}


# --------------------------------------------------------------- output ----
def num(v, digits=2):
    return f"{v:.{digits}f}".replace(".", ",").replace("-", "−") if ok(v) else "—"

def plural(n, one, few, many):
    k = n % 100
    w = many if 11 <= k <= 14 else one if k % 10 == 1 else few if 2 <= k % 10 <= 4 else many
    return f"{n} {w}"

def pool_of(attempts):
    by = defaultdict(list)
    for a in attempts: by[a["player_id"]].append(a)
    return {pid: res for pid, rows in by.items() if (res := compute(rows))}

def profile(pid, pool, texts):
    me = pool[pid]
    out = [f"# Профиль поведения: {pid[:8]}",
           "",
           f"{plural(me['attempts'], 'попытка', 'попытки', 'попыток')}, "
           f"{plural(me['sittings'], 'заход', 'захода', 'заходов')}, "
           f"{plural(me['play_days'], 'игровой день', 'игровых дня', 'игровых дней')} за "
           f"{plural(me['span_days'], 'день', 'дня', 'дней')} истории. Сравнение — с остальными "
           f"игроками, у кого от {MIN_ATTEMPTS} попыток и {MIN_SITTINGS} заходов "
           f"(сейчас их {len(pool) - 1}).",
           "",
           "| № | Черта | Что считается | Значение | Медиана всех игроков | У скольких других меньше | Состояние |",
           "|---|---|---|---|---|---|---|"]
    for n, tr in TRAITS.items():
        mine = me["values"][n]
        vals = [p["values"][n] for p in pool.values() if ok(p["values"][n])]
        others = [p["values"][n] for q, p in pool.items() if q != pid and ok(p["values"][n])]
        s = state_of(n, mine)
        verdict = "" if s is None else (" (лучше)" if s in tr["better"] else " (хуже)")
        below = f"{sum(1 for x in others if x < mine)} из {len(others)}" if ok(mine) else "—"
        out.append(f"| {n} | {tr['title']} | {tr['measure']} | {num(mine)} | {num(med(vals))} | "
                   f"{below} | {state_name(n, s)}{verdict} |")
    out.append("")
    for n, tr in TRAITS.items():
        s = state_of(n, me["values"][n])
        text = NO_DATA_TEXT[n] if s is None else texts[(n, s)]
        out += [f"**{n}. {tr['title']} — {state_name(n, s)}.** {text}", ""]
    return "\n".join(out)

def all_table(pool):
    out = ["| Игрок | Попыток | " + " | ".join(f"{n} {tr['title']}" for n, tr in TRAITS.items()) + " |",
           "|---|---|" + "---|" * len(TRAITS)]
    for pid, p in sorted(pool.items(), key=lambda kv: -kv[1]["attempts"]):
        cells = [f"{state_name(n, state_of(n, p['values'][n]))} ({num(p['values'][n])})"
                 if ok(p["values"][n]) else "—" for n in TRAITS]
        out.append(f"| {pid[:8]} | {p['attempts']} | " + " | ".join(cells) + " |")
    return "\n".join(out)


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--fetch" in args:
        args.remove("--fetch"); fetch_fresh()
    raw = "--raw" in args
    if raw: args.remove("--raw")
    show_all = "--all" in args
    if show_all: args.remove("--all")
    if not args and not show_all:
        raise SystemExit("usage: python behavioral_profile.py [--fetch] <player_id_prefix> [--raw] | --all")

    players, attempts = load_data()
    pool = pool_of(attempts)
    if show_all:
        print(all_table(pool)); raise SystemExit
    pid = next((k for k in players if k.startswith(args[0])), None)
    if pid is None:
        raise SystemExit(f"player_id starting with '{args[0]}' not found")
    pid = canon(pid, players)
    if pid not in pool:
        n = sum(1 for a in attempts if a["player_id"] == pid)
        raise SystemExit(f"{pid[:8]}: {n} попыток — для профиля нужно от {MIN_ATTEMPTS} попыток и {MIN_SITTINGS} заходов")
    if raw:
        print(json.dumps(pool[pid], indent=2, ensure_ascii=False, default=str))
    else:
        print(profile(pid, pool, load_texts()))
