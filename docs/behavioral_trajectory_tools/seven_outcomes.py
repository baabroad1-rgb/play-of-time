# seven_outcomes.py — 7 outcomes of an attempt relative to the previous one
# (Alex 2026-10-29). Run: python seven_outcomes.py (data: attempts.json /
# players.json next to it, same as behavioral_profile.py; --fetch there).
#  1 SC same colour, closer   2 OC other colour, closer
#  3 SE same colour, equal    4 OE other colour, equal
#  5 SF same colour, farther  6 OF other colour, farther
#  7 G  three (green)
# "colour" = side of the miss (early red / late blue); closer/equal/farther =
# |error| rounded to the level's cell, as the player sees it. Pairs inside one
# sitting (gap <= 10 min), same accuracy; last attempt of a sitting dropped
# (abandon artifact, as in the trait study). Pairs where the previous attempt
# was a three have no colour to compare with -> counted apart as "P".
import sys, math, random, statistics as st
from collections import defaultdict, Counter
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import behavioral_profile as bp

random.seed(7)
CATS = ['SC', 'OC', 'SE', 'OE', 'SF', 'OF', 'G']
NAMES = {'SC': '1 тот же цвет, ближе', 'OC': '2 другой цвет, ближе', 'SE': '3 тот же цвет, равно',
         'OE': '4 другой цвет, равно', 'SF': '5 тот же цвет, дальше', 'OF': '6 другой цвет, дальше', 'G': '7 тройка'}

def disp_err(row):
    cell = 10 ** (3 - row['accuracy'])
    dur = row['duration_ms']
    return math.floor(dur / cell + 0.5) * cell - 3000

def classify(prev, cur):
    """prev/cur = (err, green). Returns category or 'P' (previous was a three)."""
    if cur[1]: return 'G'
    if prev[1]: return 'P'
    same = (prev[0] < 0) == (cur[0] < 0)
    a, b = abs(prev[0]), abs(cur[0])
    d = 'C' if b < a else 'E' if b == a else 'F'
    return ('S' if same else 'O') + d

def runs_of(rows, sit):
    """Contiguous same-accuracy runs inside each sitting, last attempt of the sitting dropped."""
    by = defaultdict(list)
    for i in range(len(rows)): by[sit[i]].append(i)
    out = []
    for g in by.values():
        g = g[:-1]
        cur = []
        for i in g:
            if cur and rows[i]['accuracy'] != rows[cur[-1]]['accuracy']:
                out.append(cur); cur = []
            cur.append(i)
        if len(cur) >= 2: out.append(cur)
    return out

def counts(seqs):
    c = Counter()
    for s in seqs:
        for a, b in zip(s, s[1:]): c[classify(a, b)] += 1
    return c

def analyse(rows, sit, keep=lambda s: True, shuffles=60):
    runs = [r for r in runs_of(rows, sit) if keep(sit[r[0]])]
    seqs = [[(disp_err(rows[i]), rows[i]['is_win'] == 1) for i in r] for r in runs]
    obs = counts(seqs)
    exp = Counter()
    for _ in range(shuffles):
        sh = []
        for s in seqs:
            s2 = s[:]; random.shuffle(s2); sh.append(s2)
        for k, v in counts(sh).items(): exp[k] += v / shuffles
    return obs, exp

def shares(c):
    n = sum(c[k] for k in CATS)
    return {k: (c[k] / n if n else float('nan')) for k in CATS}, n

def spearman(a, b):
    def rk(x):
        o = sorted(range(len(x)), key=lambda k: x[k]); z = [0] * len(x)
        for j, k in enumerate(o): z[k] = j
        return z
    a, b = rk(a), rk(b); ma, mb = st.mean(a), st.mean(b)
    den = math.sqrt(sum((x - ma) ** 2 for x in a) * sum((y - mb) ** 2 for y in b))
    return sum((x - ma) * (y - mb) for x, y in zip(a, b)) / den if den else float('nan')

players, attempts = bp.load_data()
by = defaultdict(list)
for a in attempts: by[a['player_id']].append(a)
data = {}
for pid, rows in by.items():
    if len(rows) < bp.MIN_ATTEMPTS: continue
    rows, r, pause, sit, t = bp.prep(rows)
    if sit[-1] + 1 < bp.MIN_SITTINGS: continue
    data[pid[:8]] = (rows, sit)

res = {}
for p, (rows, sit) in sorted(data.items(), key=lambda kv: -len(kv[1][0])):
    obs, exp = analyse(rows, sit)
    so, n = shares(obs); se, _ = shares(exp)
    halves = [analyse(rows, sit, keep=lambda s, h=h: s % 2 == h, shuffles=30) for h in (0, 1)]
    nq = max(sit) + 1
    quarters = [analyse(rows, sit, keep=lambda s, q=q: s * 4 // nq == q, shuffles=30) for q in range(4)]
    res[p] = dict(n=n, P=obs['P'], obs=so, exp=se,
                  halves=[(shares(o)[0], shares(e)[0]) for o, e in halves],
                  quarters=[(shares(o)[0], shares(e)[0]) for o, e in quarters])
    lv = Counter(rr['accuracy'] for rr in rows)
    print(f"{p} попыток {len(rows)}, пар {n} (+{obs['P']} после тройки), уровни {dict(sorted(lv.items()))}")

print('\n## Доли исходов, % (в скобках — во сколько раз чаще случайного порядка)')
print('| Игрок | пар | ' + ' | '.join(NAMES[k] for k in CATS) + ' |')
print('|---|---|' + '---|' * len(CATS))
for p, d in res.items():
    cells = [f"{100*d['obs'][k]:.1f} ({d['obs'][k]/d['exp'][k]:.2f})" if d['exp'][k] > 0 else f"{100*d['obs'][k]:.1f} (—)" for k in CATS]
    print(f"| {p} | {d['n']} | " + ' | '.join(cells) + ' |')

print('\n## Надёжность: совпадение на чётных и нечётных заходах (ρ по игрокам)')
for k in CATS:
    hs = [(d['halves'][0][0][k], d['halves'][1][0][k]) for d in res.values()]
    hr = [(d['halves'][0][0][k] / d['halves'][0][1][k], d['halves'][1][0][k] / d['halves'][1][1][k])
          for d in res.values() if d['halves'][0][1][k] > 0 and d['halves'][1][1][k] > 0]
    rs = spearman([a for a, _ in hs], [b for _, b in hs])
    rr = spearman([a for a, _ in hr], [b for _, b in hr]) if len(hr) >= 6 else float('nan')
    print(f"{NAMES[k]:28s} доля ρ={rs:.2f}   ×случай ρ={rr:.2f} (игроков {len(hr)})")

print('\n## Четверти истории у больших игроков: доля, % / ×случай')
big = [p for p in res if res[p]['n'] >= 900][:5]
for k in CATS:
    line = []
    groups = []
    for p in big:
        q = res[p]['quarters']
        v = [qq[0][k] / qq[1][k] for qq in q if qq[1][k] > 0]
        line.append(p[:4] + ' ' + ' '.join(f"{100*qq[0][k]:.0f}/{qq[0][k]/qq[1][k]:.2f}" if qq[1][k] > 0 else '—' for qq in q))
        if len(v) >= 3: groups.append(v)
    F = float('nan')
    if len(groups) >= 2:
        allv = [x for g in groups for x in g]; gm = st.mean(allv)
        sb = sum(len(g) * (st.mean(g) - gm) ** 2 for g in groups) / (len(groups) - 1)
        sw = sum((x - st.mean(g)) ** 2 for g in groups for x in g) / (len(allv) - len(groups))
        F = sb / sw if sw else float('inf')
    print(f"{NAMES[k]:28s} F={F:5.1f} | " + ' || '.join(line))

print('\n## Ведущая стратегия игрока: самый частый исход и самый «нарочный» (max ×случай) — вся история / чётные / нечётные / четверти')
for p, d in res.items():
    def top(s, e=None, excl=('G',)):
        ks = [k for k in CATS if k not in excl]
        if e is None: return max(ks, key=lambda k: s[k])
        return max([k for k in ks if e[k] > 0], key=lambda k: s[k] / e[k])
    mod = [top(d['obs'])] + [top(h[0]) for h in d['halves']] + [top(q[0]) for q in d['quarters']]
    over = [top(d['obs'], d['exp'])] + [top(h[0], h[1]) for h in d['halves']] + [top(q[0], q[1]) for q in d['quarters']]
    print(f"{p}: частый {mod[0]} | половины {mod[1]},{mod[2]} | четверти {','.join(mod[3:])}   ||   нарочный {over[0]} | половины {over[1]},{over[2]} | четверти {','.join(over[3:])}")

def analyse_lvl(rows, sit, acc, shuffles=80):
    runs = [r for r in runs_of(rows, sit) if rows[r[0]]['accuracy'] == acc]
    seqs = [[(disp_err(rows[i]), rows[i]['is_win'] == 1) for i in r] for r in runs]
    obs = counts(seqs); exp = Counter()
    for _ in range(shuffles):
        sh = [random.sample(s, len(s)) for s in seqs]
        for k, v in counts(sh).items(): exp[k] += v / shuffles
    return obs, exp
print('## По уровням: доля % (×случай), z = (набл − ожид)/√ожид')
for p, (rows, sit) in sorted(data.items(), key=lambda kv: -len(kv[1][0])):
    for acc in (2, 3):
        obs, exp = analyse_lvl(rows, sit, acc)
        n = sum(obs[k] for k in CATS)
        if n < 150: continue
        cells = []
        for k in CATS[:6]:
            z = (obs[k] - exp[k]) / math.sqrt(exp[k]) if exp[k] > 0 else float('nan')
            cells.append(f"{k} {100*obs[k]/n:4.1f}% ×{obs[k]/exp[k]:.2f} z{z:+.1f}" if exp[k] > 0 else f"{k} —")
        print(f"{p} L{acc} пар {n:5d} | " + ' | '.join(cells))
