import json, math, random, re

SRC = r"C:\PlayOfTime\webapp\report.html"
OUT = r"C:\Users\Admin\AppData\Local\Temp\claude\C--PlayOfTime-time-mvp-quest-dev\a9781076-5042-4375-9cd1-7166e7764946\scratchpad\report_snapshot.html"

random.seed(42)

AIM = 3.000  # seconds

def gen_attempts():
    attempts = []
    session = 0
    n_in_session = 0
    # 70 days of history at level 3 (milliseconds), improving trend, so
    # "what changed" / "holding steady" / optimal-zone all have real content.
    base_date = __import__("datetime").datetime(2026, 7, 8, 10, 0, 0)
    day_cursor = 0
    while day_cursor < 70:
        # not every day played
        if random.random() < 0.25:
            day_cursor += 1
            continue
        attempts_today = random.randint(15, 45)
        # skill improves over time: sigma shrinks, bias drifts toward 0
        progress = day_cursor / 70.0
        sigma_ms = 220 - 160 * progress  # 220ms -> 60ms
        bias_ms = 40 - 60 * progress     # +40ms early-skew -> slight late
        t0 = base_date + __import__("datetime").timedelta(days=day_cursor, hours=random.uniform(9, 20))
        for i in range(attempts_today):
            if i == 0 or random.random() < 0.06:
                session += 1
                n_in_session = 0
            n_in_session += 1
            err = random.gauss(bias_ms, sigma_ms)
            # occasional real outlier (>500ms) to exercise the outlier-exclusion rule
            if random.random() < 0.01:
                err = random.choice([-1, 1]) * random.uniform(600, 1400)
            ts = t0 + __import__("datetime").timedelta(seconds=i * random.uniform(8, 40))
            attempts.append({
                "e": round(err, 1),
                "s": session,
                "n": n_in_session,
                "t": ts.strftime("%Y-%m-%dT%H:%M:%S"),
            })
        day_cursor += 1

    # Final stretch: a genuinely excellent run (last 3 days) so the optimal-zone
    # green highlight + laughing-eye shapes actually fire on "recent" periods.
    last_date = base_date + __import__("datetime").timedelta(days=70)
    session += 1
    n_in_session = 0
    for d in range(3):
        session += 1
        n_in_session = 0
        t0 = last_date + __import__("datetime").timedelta(days=d, hours=random.uniform(10, 19))
        for i in range(25):
            n_in_session += 1
            err = random.gauss(2, 35)  # tight, near-zero bias
            ts = t0 + __import__("datetime").timedelta(seconds=i * random.uniform(6, 20))
            attempts.append({
                "e": round(err, 1),
                "s": session,
                "n": n_in_session,
                "t": ts.strftime("%Y-%m-%dT%H:%M:%S"),
            })

    attempts.sort(key=lambda a: a["t"])
    return attempts

attempts = gen_attempts()
data_json = json.dumps({"me": {"label": "Test Player", "acc": "ms", "attempts": attempts}})

with open(SRC, "r", encoding="utf-8") as f:
    html = f.read()

marker_start = "async function loadReport(){"
marker_end = "loadReport();\n</script>"
i0 = html.index(marker_start)
i1 = html.index(marker_end, i0) + len(marker_end)

replacement = f"""DATA = {data_json};
document.getElementById("loadingMsg").hidden = true;
document.getElementById("reportBody").hidden = false;
buildLangSeg();
setDefaultRanges();
render();
[60, 250, 600, 1200].forEach(delay => setTimeout(render, delay));
</script>"""

new_html = html[:i0] + replacement + html[i1:]

with open(OUT, "w", encoding="utf-8") as f:
    f.write(new_html)

print("attempts:", len(attempts))
print("wrote", OUT, len(new_html), "bytes")
