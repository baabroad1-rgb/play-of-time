# Main page of baa.playoftime.online, built around four steps of a feedback loop
# (reflex arc → reflex ring → co-regulation → automated co-regulation); main since 18.11.2026.
# Takes the shared parts (portrait header, stopwatch, Read Aloud frame, footer, scripts,
# head, styles, publications) from the current baa/index.html and rewrites it.
# Stable on rebuild.  Run from anywhere:
#     python3 tools/baa_page/build.py                    -> baa/index.html
#     python3 tools/baa_page/build.py draft-xxxxxxxxxx   -> hidden, not indexed baa/<draft>/index.html
import re, sys, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'baa')
DRAFT = sys.argv[1] if len(sys.argv) > 1 else None
src = open(f'{ROOT}/index.html', encoding='utf-8').read()
E = ' target="_blank" rel="noopener"'

def grab(a, b, s=src):
    i = s.index(a); j = s.index(b, i) + len(b); return s[i:j]

head = src[src.index('<!--head:start-->') + len('<!--head:start-->\n'):src.index('<style>')]
style = src[src.index('<style>') + len('<style>\n'):src.index('</style>')]
style = style.split('\n/* ---- four steps ---- */')[0]
style = style.replace('\n  border-top: 1px solid var(--fg);', '').replace(' border-top: 1px solid var(--fg);', '')
who = grab('  <header class="who">', '</header>\n')
pot = grab('      <div class="pot" id="pot">', '        </div>\n      </div>\n')
aloud = grab('      <iframe class="aloud"', '</iframe>\n')
footer = grab('  <footer>', '</footer>\n')
scripts = src[src.index('</main>\n') + len('</main>\n'):src.index('<!--body:end-->')]
pubwrap = grab('  <details class="pubwrap">', '</details>\n')
# the toggle: title and a chevron that turns when open
pubwrap = re.sub(r'<summary>.*?</summary>', '<summary><span>Artifacts</span><svg class="chev" viewBox="0 0 12 12" aria-hidden="true">'
    '<path d="M2.5 4.5 6 8l3.5-3.5"/></svg></summary>', pubwrap, count=1, flags=re.S)
href = lambda part: re.search(r'href="([^"]*' + re.escape(part) + r'[^"]*)"', src).group(1)
U_INT = href('770336b7d596'); MAIL = re.search(r'(<a href="mailto:[^"]+">.*?</a>)', src, re.S).group(1)

if DRAFT:
    head = re.sub(r'<link rel="canonical"[^>]*>\n', '', head)
    head = re.sub(r'<script type="application/ld\+json">.*?</script>\n', '', head, flags=re.S)
    head = re.sub(r'<meta name="robots" content="[^"]*">', '<meta name="robots" content="noindex, nofollow, noarchive">', head)

EXTRA = '''
/* ---- four steps ---- */
details.pubwrap summary { display: inline-flex; align-items: center; gap: 8px; list-style: none; user-select: none; -webkit-user-select: none; }
details.pubwrap summary::-webkit-details-marker { display: none; }
details.pubwrap summary .chev { width: 14px; height: 14px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; transition: transform .2s; }
details.pubwrap[open] summary .chev { transform: rotate(180deg); }
@media (hover: hover) { details.pubwrap summary:hover span { text-decoration: underline; text-underline-offset: 3px; } }
details.pubwrap summary:focus-visible { outline: 2px solid var(--fg); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) { details.pubwrap summary .chev { transition: none; } }
.steps { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; margin: 0 0 clamp(40px, 7vw, 72px); font-family: var(--sans); font-size: 15px; }
.steps a { display: inline-flex; align-items: center; gap: 8px; text-decoration: none; }
.steps a:hover span { text-decoration: underline; }
.steps svg { width: 44px; height: 28px; flex: none; }
.steps .arr { font-size: 15px; }
.stephead { display: flex; align-items: center; gap: 14px; margin-bottom: 8px; }
.stephead svg { width: 64px; height: 40px; flex: none; }
.stephead .num { margin: 0; }
.stephead .term { font-family: var(--sans); font-size: 15px; font-weight: 700; }
.fig { fill: none; stroke: #000; stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; }
.fig-dot { fill: #000; }
.fig-dev { fill: #fff; stroke: #000; stroke-width: 1.6; }
/* step figure and name sit in the left column, where the years used to be */
section.interest { display: grid; grid-template-columns: 104px minmax(0, 1fr); column-gap: 24px; }
section.interest .stephead { grid-column: 1; grid-row: 1 / span 2; flex-direction: column; align-items: flex-start; gap: 10px; margin: 0; position: sticky; top: 16px; align-self: start; }
section.interest .stephead > span { display: grid; gap: 2px; }
section.interest > h2, section.interest > .col { grid-column: 2; }
.stephead .nw { white-space: nowrap; }
.ringfig { float: right; width: 260px; margin: 2px 0 12px 24px; }
.ringfig svg { display: block; width: 100%; height: auto; }
.ringfig .lbl { font-family: var(--sans); font-size: 15px; fill: var(--fg); }
.ringfig .nm { font-family: var(--sans); font-size: 12px; font-weight: 700; fill: var(--fg); }
.ringfig .ctr { font-family: var(--sans); font-size: 16px; font-weight: 700; fill: var(--fg); }
section.interest .col { display: block; }
section.interest .col > p, section.interest .col > .pot { margin: 0 0 16px; }
section.interest .col > :last-child { margin-bottom: 0; }
section.interest .col::after { content: ""; display: block; clear: both; }
section.interest .col > .pot, section.interest .col > .aloud { clear: both; }
/* sources still being looked for: keep the entry, hide the note */
.pubs .todo { display: none; }
/* paragraphs about my own work read as notes to the idea */
.col p.me { font-family: var(--sans); font-size: 16px; line-height: 1.5; }
/* Telegram mark before links to the channels */
.potlogo { position: relative; perspective: 300px; }
img.ico.rider { border: 1px solid var(--fg); border-radius: 50%; box-sizing: border-box; }
.potlogo img { position: absolute; inset: 0; width: 100%; height: 100%; }
.potlogo .pl-ring { animation: pl-ring 5s cubic-bezier(.34,1.56,.64,1) infinite; }
.potlogo .pl-tri { transform-origin: 50% 50%; animation: pl-tri 3s linear infinite; }
.potlogo .pl-letter { transform-origin: 48.5% 50%; animation: pl-letter 3s linear infinite; }
@keyframes pl-ring { 0%, 8% { transform: rotate(0deg); } 22% { transform: rotate(372deg); } 30% { transform: rotate(355deg); } 36%, 100% { transform: rotate(360deg); } }
@keyframes pl-tri { to { transform: rotateY(360deg); } }
@keyframes pl-letter { to { transform: rotateY(-360deg); } }
@media (prefers-reduced-motion: reduce) { .potlogo img { animation: none; } }
/* marks sit closer to their own word than to the neighbouring one */
.ext { display: inline-block; width: 0.78em; height: 0.78em; margin: 0 0 0 0.04em; vertical-align: -0.04em; }
/* three random quotes and questions from the channels (api/samples.php). The page has three
   levels: the idea (serif), my own notes to it (p.me, smaller sans), and these samples, which are
   details of the notes: one step quieter still, smaller and grey, indented under their paragraph. */
.samples { list-style: none; margin: -6px 0 18px 32px; padding: 0; display: grid; gap: 8px;
  font-family: var(--sans); font-size: 14px; line-height: 1.45; color: #4D4D4D; }
.samples li { min-width: 0; }
.samples q { quotes: none; white-space: pre-line; }
.samples .src { display: block; margin-top: 2px; font-size: 12px; color: #8A8A8A; }
/* a spoiler: blurred until tapped, as in the channel */
/* a long quote is folded to three lines; "more" opens the rest (an accordion) */
.samples li.fold q { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }
.samples .more { font: inherit; color: inherit; background: none; border: 0; padding: 0; cursor: pointer; text-decoration: underline; text-underline-offset: 2px; }
.samples .more::after { content: "▾"; text-decoration: none; display: inline-block; margin-left: 0.25em; }
.samples li.open-all .more::after { content: "▴"; }
.samples .sp { filter: blur(4px); background: #EDEDED; border-radius: 3px; cursor: pointer; transition: filter .25s; }
.samples .sp.open { filter: none; background: none; cursor: auto; }
/* every mark before a word: one size, one baseline, one gap to the word; the space before it is the ordinary word space */
.ico, .ico.potlogo, .ico.ralogo, img.ico { display: inline-block; width: 1.15em; height: 1.15em; vertical-align: -0.22em; margin: 0 0.08em 0 0; }
/* ---- v2: plain names, first-screen test ---- */
.lede.lede-try { margin-bottom: 20px; }
/* the first-screen stopwatch: the same button as Play of Time below, but the time is hidden while it runs */
.pot.pot-top { margin: 0 0 clamp(48px, 8vw, 96px); max-width: 40em; font-size: 16px; }
.pot.pot-top .pot-btn { align-self: flex-start; }   /* the button keeps its size whatever the text beside it */
.pot-time.counting { font-size: 26px; line-height: 46px; letter-spacing: 0.02em; }
.pot-time.counting i { font-style: normal; opacity: 0; animation: dot 1.2s infinite; }
.pot-time.counting i:nth-child(2) { animation-delay: 0.3s; }
.pot-time.counting i:nth-child(3) { animation-delay: 0.6s; }
@keyframes dot { 0% { opacity: 0; } 20%, 60% { opacity: 1; } 80%, 100% { opacity: 0; } }
@media (prefers-reduced-motion: reduce) { .pot-time.counting i { animation: none; opacity: 1; } }
.blind { display: flex; flex-wrap: wrap; align-items: center; gap: 14px 22px; margin: 0 0 clamp(48px, 8vw, 96px); font-family: var(--sans); font-size: 16px; line-height: 1.4; }
.blind-btn { font: inherit; font-size: 18px; font-weight: 700; min-width: 7.5em; min-height: 52px; padding: 0 22px; background: var(--fg); color: var(--bg); border: 1px solid var(--fg); border-radius: 10px; cursor: pointer; touch-action: manipulation; -webkit-user-select: none; user-select: none; }
.blind-btn.on { background: var(--bg); color: var(--fg); }
.blind-btn:focus-visible { outline: 2px solid var(--fg); outline-offset: 3px; }
.blind-res { margin: 0; flex: 1 1 16em; min-width: 0; }
.blind-res b { font-size: 22px; font-variant-numeric: tabular-nums; }
.steps { display: block; }
.steps-h { margin: 0 0 12px; font-weight: 700; }
.steps ol { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px 20px; counter-reset: st; }
.steps li { counter-increment: st; min-width: 0; }
.steps li a { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: 4px 10px; min-height: 44px; }
.steps li a span { display: grid; gap: 1px; }
.steps li a span b::before { content: counter(st) ". "; }
.steps li a small { font-size: 13px; }
.steps li a:hover b { text-decoration: underline; }
.stephead .plain { font-family: var(--sans); font-size: 15px; font-weight: 700; }
.stephead .term { font-weight: 400 !important; font-size: 13px !important; }
.bio .about { margin: 0 0 12px; max-width: 34em; font-family: var(--sans); font-size: 16px; line-height: 1.5; }
.you .mail { font-size: 20px; }
details.pubwrap summary { min-height: 44px; }
@media (max-width: 760px) { .steps ol { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 680px) {
  .ringfig { float: none; margin: 4px auto 18px; }
  section.interest { display: block; }
  section.interest .stephead { position: static; flex-direction: row; align-items: center; gap: 14px; margin-bottom: 8px; }
}
'''

# The four figures: one shape that grows from a one-way line into a loop through another
# person, and then through a device. Drawn in a 64×40 box.
ARROW = '<path class="fig" d="M{x1},{y1} l-5,-3 m5,3 l-5,3"/>'
FIG = {
  'arc': '<path class="fig" d="M6,30 Q32,2 58,30"/><path class="fig" d="M58,30 l-6,-1.5 m6,1.5 l-1.5,-6"/><circle class="fig-dot" cx="6" cy="30" r="2.4"/>',
  'ring': '<circle class="fig" cx="32" cy="20" r="15"/><path class="fig" d="M47,20 l-3,-5 m3,5 l3,-5"/><circle class="fig-dot" cx="17" cy="20" r="2.4"/>',
  'co': '<circle class="fig" cx="20" cy="20" r="13"/><circle class="fig" cx="44" cy="20" r="13"/><circle class="fig-dot" cx="7" cy="20" r="2.4"/><circle class="fig-dot" cx="57" cy="20" r="2.4"/>',
  'auto': '<circle class="fig" cx="20" cy="20" r="13"/><circle class="fig" cx="44" cy="20" r="13"/><circle class="fig-dot" cx="7" cy="20" r="2.4"/><rect class="fig-dev" x="52.5" y="15.5" width="9" height="9" rx="1.5"/>',
}
def svg(k, label):
    a = f'role="img" aria-label="{label}"' if label else 'aria-hidden="true" focusable="false"'
    return f'<svg viewBox="0 0 64 40" {a}>{FIG[k]}</svg>'

def ring_fig():
    # act → see what happened → compare with the aim → correct → act …, clockwise around the aim
    import math
    W, H, cx, cy, r = 320, 234, 160, 114, 60
    nodes = [('act',), ('see what', 'happened'), ('compare', 'with the aim'), ('correct',)]
    parts = [f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="The reflex ring: act, see what happened, compare with the aim, correct, and act again">',
             '<defs><marker id="ah" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker></defs>']
    n = len(nodes)
    for k, lines in enumerate(nodes):
        a = -math.pi / 2 + k * 2 * math.pi / n
        a1, a2 = a + 0.3, a + 2 * math.pi / n - 0.3
        p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1)); p2 = (cx + r * math.cos(a2), cy + r * math.sin(a2))
        parts.append(f'<path d="M{p1[0]:.1f},{p1[1]:.1f} A{r},{r} 0 0 1 {p2[0]:.1f},{p2[1]:.1f}" fill="none" stroke="#000" stroke-width="1.5" marker-end="url(#ah)"/>')
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="11" fill="#fff" stroke="#000" stroke-width="1.5"/>'
                     f'<text class="nm" x="{x:.1f}" y="{y + 4:.1f}" text-anchor="middle">{k + 1}</text>')
        c, sn = math.cos(a), math.sin(a)
        lx, ly = cx + (r + 18) * c, cy + (r + 18) * sn
        anchor = 'middle' if abs(c) < 0.2 else ('start' if c > 0 else 'end')
        h = 17 * (len(lines) - 1)
        y0 = ly - 4 - h if sn < -0.9 else (ly + 14 if sn > 0.9 else ly + 5 - h / 2)
        tsp = ''.join(f'<tspan x="{lx:.1f}" dy="{0 if i == 0 else 17}">{t}</tspan>' for i, t in enumerate(lines))
        parts.append(f'<text class="lbl" x="{lx:.1f}" y="{y0:.1f}" text-anchor="{anchor}">{tsp}</text>')
    parts.append(f'<text class="ctr" x="{cx}" y="{cy + 6}" text-anchor="middle">the aim</text></svg>')
    return '      <figure class="ringfig">' + ''.join(parts) + '</figure>\n'

def loop_fig(kind):
    # Small figures for steps 1, 3 and 4, in the style of the ring in step 2.
    # arc: a signal goes in, an action comes out, nothing comes back;
    # co / auto: what you do goes to another person or a device, and what they see comes back.
    mk = f'ah-{kind}'
    defs = (f'<defs><marker id="{mk}" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
            '<path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker></defs>')
    line = f'fill="none" stroke="#000" stroke-width="1.5" marker-end="url(#{mk})"'
    if kind == 'arc':
        label = 'The reflex arc: a signal goes in, an action comes out, nothing comes back'
        body = (f'<path d="M48,96 Q160,-4 268,92" {line}/>'
                '<circle cx="48" cy="96" r="4" fill="#000"/>'
                '<text class="lbl" x="48" y="122" text-anchor="middle">signal</text>'
                '<text class="lbl" x="272" y="122" text-anchor="middle">action</text>'
                '<text class="ctr" x="160" y="74" text-anchor="middle">you</text>'
                '<path d="M250,140 L70,140" fill="none" stroke="#8C8C8C" stroke-width="1.5" stroke-dasharray="5 5"/>'
                '<text class="lbl" x="160" y="164" text-anchor="middle" style="fill:#6B6B6B">nothing comes back</text>')
        H = 172
    else:
        other = ('<circle cx="246" cy="86" r="36" fill="#fff" stroke="#000" stroke-width="1.5"/>'
                 '<text class="lbl" x="246" y="83" text-anchor="middle">another</text><text class="lbl" x="246" y="100" text-anchor="middle">person</text>'
                 ) if kind == 'co' else (
                 '<rect x="214" y="58" width="64" height="56" rx="8" fill="#fff" stroke="#000" stroke-width="1.5"/>'
                 '<text class="lbl" x="246" y="91" text-anchor="middle">device</text>')
        label = ('Co-regulation: what you do goes to another person, and what they see comes back' if kind == 'co'
                 else 'Automated co-regulation: what you do goes to a device, and what it measures comes back')
        back = 'what they see' if kind == 'co' else 'what it measures'
        body = ('<circle cx="74" cy="86" r="36" fill="#fff" stroke="#000" stroke-width="1.5"/>'
                '<text class="ctr" x="74" y="92" text-anchor="middle">you</text>' + other +
                f'<path d="M104,64 Q160,28 212,64" {line}/>'
                f'<path d="M212,108 Q160,144 104,108" {line}/>'
                '<text class="lbl" x="160" y="26" text-anchor="middle">what you do</text>'
                f'<text class="lbl" x="160" y="160" text-anchor="middle">{back}</text>')
        H = 172
    return (f'      <figure class="ringfig loopfig"><svg viewBox="0 0 320 {H}" role="img" aria-label="{label}">'
            + defs + body + '</svg></figure>\n')

STEPS = [
  dict(id='arc', fig='arc', plain='Nothing comes back', term='Reflex arc', q='Do people know what they actually do?',
    body=loop_fig('arc') + f'''      <p>In a reflex arc a signal goes in and an action comes out. Nothing comes back.</p>
      <p>Much of what we do works this way: we act and never see what we did.</p>
      <p class="me">My first research measured that gap: how different kinds of users picture their work at a computer, compared with what they actually do.</p>
      <p class="me">Later, at the lab, we interrupted people editing a Word document with another task, such as finding a number in a phone book, and watched them find their way back. We could see their strategies. They mostly could not.</p>
'''),
  dict(id='ring', fig='ring', plain='Your own senses', term='Reflex ring', q='Can you correct yourself while you act?',
    body='''''' + ring_fig() + '''      <p>Almost nothing is done in one go. You start, see how it is going and adjust, mostly without noticing. Pouring tea, you slow down near the top. On a bike, you correct every wobble.</p>
      <p>Physiologists of the last century, Nikolai Bernstein among them, called this a reflex ring: the result comes back and steers the next move. Today we would say feedback loop.</p>
      <p>The ring works when the senses report what matters. They do with a teapot or a bike, but not with time, your own voice or how you spend a day. There the ring needs help from outside: steps 3 and 4.</p>
      <p class="me">Since 2023 I have run this ring on myself, only slower. I record what I do, come back to it later, compare it with what I meant, and change the next round.</p>
      <p class="me"><b>Books.</b> I read about 52 a year and quote the ones that widen my context most in <a href="https://t.me/baaqod" target="_blank" rel="noopener"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="12" fill="#26A5E4"/><path fill="#fff" d="M5.4 11.7 17.2 7.1c.6-.2 1 .1.9.8l-2 9.4c-.1.6-.6.8-1.1.5l-3-2.2-1.5 1.4c-.2.2-.3.3-.6.3l.2-3.1 5.6-5.1c.2-.2 0-.3-.3-.1l-6.9 4.4-3-.9c-.6-.2-.7-.6.1-.9z"/></svg>aka mulator</a>, a Telegram channel: over 800 quotes from some 65 books since 2022. Most are about how the mind works and where it came from: Vygotsky, Csikszentmihalyi, Jaynes, Dunbar, Luria, Uznadze, Lakoff, Pinker, Deutsch. Others are about time, language, money and technology. Rereading the channel, I watch myself read.</p>
      <ul class="samples" id="samples-quotes" hidden></ul>
      <p class="me"><b>Questions.</b> The questions these books raise go to <a href="https://t.me/q_of_t_d" target="_blank" rel="noopener"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="12" fill="#26A5E4"/><path fill="#fff" d="M5.4 11.7 17.2 7.1c.6-.2 1 .1.9.8l-2 9.4c-.1.6-.6.8-1.1.5l-3-2.2-1.5 1.4c-.2.2-.3.3-.6.3l.2-3.1 5.6-5.1c.2-.2 0-.3-.3-.1l-6.9 4.4-3-.9c-.6-.2-.7-.6.1-.9z"/></svg>Question of the Day</a>, one a day since December 2023, over 850 so far: about time, meaning, context, inner speech, how we think and where we go wrong. Many hide an answer or a follow-up under a tap.</p>
      <ul class="samples" id="samples-questions" hidden></ul>
      <p class="me">Later I reread them and guess what is hidden before I tap. The gap shows how much I have changed. A guess that turns out very different becomes a new question linked to the old one, so the rings form chains.</p>
'''),
  dict(id='co', fig='co', plain='Another person', term='Co-regulation', q='Who sees what you can’t?',
    body=loop_fig('co') + '''      <p>Some things you cannot see from the inside, however closely you listen to yourself. Then the loop needs another person: someone who watches from outside and tells you.</p>
      <p>I call this co&#8209;regulation: two people run one loop. One acts, the other watches and tells.</p>
      <p class="me">For twenty years I was that person for companies. At <a href="https://en.usethics.ru/" target="_blank" rel="noopener">Usethics</a>, which I founded in 2001 and ran until 2023, we watched real people use products and showed the makers what we saw: about 2,000 projects for Russian and international clients.</p>
      <p class="me"><b>Readers.</b> In the subreddit <a href="https://www.reddit.com/r/English_but_Simple/" target="_blank" rel="noopener">English but Simple</a> I retell philosophical texts in plain English, and readers show me where I am still unclear.</p>
'''),
  dict(id='auto', fig='auto', plain='A device', term='Automated co-regulation', q='Can a device be the other?',
    body=loop_fig('auto') + '''      <p>Someone watching you cannot be there all the time. A device can: software, AI, wearable sensors. The work is to make what it reports useful: immediate, and exact enough to act on.</p>
      <p>Two of my tools do this for two things the senses report poorly: time and your own voice.</p>
      <p class="me"><a href="https://playoftime.online/" target="_blank" rel="noopener"><b>Play of Time</b></a> is a stopwatch you try to stop at exactly three seconds. By feel you cannot tell how far off you are. After each attempt it shows how precise you were, whether you rush or hold back, and your Lucky Index: how often you hit the target compared with pure chance.</p>
''' + pot + '''      <p class="me"><a href="https://playoftime.online/aloud/" target="_blank" rel="noopener"><b>Read Aloud</b></a> listens as you read an English book aloud and shows which words came out right. Try it below: press the button, allow the microphone and read the paragraph.</p>
''' + aloud),
]

def steps_html():
    out = []
    for k, st in enumerate(STEPS, 1):
        out.append(f'''  <section class="interest" id="{st["id"]}">
    <div class="stephead">{svg(st["fig"], st["term"])}<span><span class="num">Step {k} of 4</span><span class="plain">{st["plain"]}</span><span class="term">{re.sub(r"(\S+-\S+)", r'<span class="nw">\1</span>', st["term"])}</span></span></div>
    <h2>{st["q"]}</h2>
    <div class="col">
{st["body"]}    </div>
  </section>

''')
    return ''.join(out)

MAP = ('  <nav class="steps" aria-labelledby="steps-h"><p class="steps-h" id="steps-h">Four ways what you do comes back to you</p><ol>'
    + ''.join(f'<li><a href="#{st["id"]}">{svg(st["fig"], "")}<span><b>{st["plain"]}</b><small>{re.sub(r"(\S+-\S+)", r'<span class="nw">\1</span>', st["term"])}</small></span></a></li>' for st in STEPS)
    + '</ol></nav>\n\n')

INTRO = '''  <h1 class="intro">People don’t see much of what they actually do until a useful feedback loop shows them.</h1>
  <p class="lede lede-try">Try it: press RUN, count three seconds in your head, press STOP.</p>
  <div class="pot pot-top" id="top">
    <button type="button" class="pot-btn" id="top-btn" aria-describedby="top-hint">
      <span class="pot-time" id="top-time">0.00</span>
      <span class="pot-act"><svg viewBox="0 0 24 24" aria-hidden="true" id="top-icon"></svg><span id="top-label">RUN</span></span>
    </button>
    <div class="pot-side">
      <p class="pot-hint" id="top-hint">The timer stays hidden while it runs. How far off will you be?</p>
      <p class="pot-res" id="top-res" aria-live="polite"></p>
      <div class="pot-last"><span class="pot-cap">Last three</span><ol id="top-last"></ol></div>
    </div>
  </div>

'''


TAIL = f'''  <section class="you">
    <div class="col">
      <p class="ask">What feedback loop do you need to build?</p>
      <p>Write to me with one concrete case: who needs to see what, and what should change once they do. It can be your team, your users or you. I will reply with a sketch of the loop: what to record, who or what reports back, and how soon.</p>
      <p>If you think about the same things, write too: what have you noticed in yourself, and how did you check it?</p>
      <p class="mail">{MAIL}</p>
    </div>
  </section>

  <section class="bio">
    <h2>Where and when</h2>
    <p class="about">A feedback methodologist works out how people can get an exact, timely picture of what they actually do: from their own records, another person or a device.</p>
    <ul>
      <li>1997–2001 · Laboratory of Work Psychology, Faculty of Psychology, Moscow State University</li>
      <li>2001–2023 · Usethics, founder and director</li>
      <li>2026 · feedback methodologist</li>
    </ul>
  </section>

''' + pubwrap + '\n'

# Service marks before links to well-known services (glyphs from simple-icons, CC0).
# Telegram links carry their mark in the text above; Reddit and Medium get theirs here,
# also inside the publications block, which is taken from the built page, hence the guard.
MARKS = {'reddit.com': 'M12 0C5.373 0 0 5.373 0 12c0 3.314 1.343 6.314 3.515 8.485l-2.286 2.286C.775 23.225 1.097 24 1.738 24H12c6.627 0 12-5.373 12-12S18.627 0 12 0Zm4.388 3.199c1.104 0 1.999.895 1.999 1.999 0 1.105-.895 2-1.999 2-.946 0-1.739-.657-1.947-1.539v.002c-1.147.162-2.032 1.15-2.032 2.341v.007c1.776.067 3.4.567 4.686 1.363.473-.363 1.064-.58 1.707-.58 1.547 0 2.802 1.254 2.802 2.802 0 1.117-.655 2.081-1.601 2.531-.088 3.256-3.637 5.876-7.997 5.876-4.361 0-7.905-2.617-7.998-5.87-.954-.447-1.614-1.415-1.614-2.538 0-1.548 1.255-2.802 2.803-2.802.645 0 1.239.218 1.712.585 1.275-.79 2.881-1.291 4.64-1.365v-.01c0-1.663 1.263-3.034 2.88-3.207.188-.911.993-1.595 1.959-1.595Zm-8.085 8.376c-.784 0-1.459.78-1.506 1.797-.047 1.016.64 1.429 1.426 1.429.786 0 1.371-.369 1.418-1.385.047-1.017-.553-1.841-1.338-1.841Zm7.406 0c-.786 0-1.385.824-1.338 1.841.047 1.017.634 1.385 1.418 1.385.785 0 1.473-.413 1.426-1.429-.046-1.017-.721-1.797-1.506-1.797Zm-3.703 4.013c-.974 0-1.907.048-2.77.135-.147.015-.241.168-.183.305.483 1.154 1.622 1.964 2.953 1.964 1.33 0 2.47-.81 2.953-1.964.057-.137-.037-.29-.184-.305-.863-.087-1.795-.135-2.769-.135Z', 'medium.com': 'M4.21 0A4.201 4.201 0 0 0 0 4.21v15.58A4.201 4.201 0 0 0 4.21 24h15.58A4.201 4.201 0 0 0 24 19.79v-1.093c-.137.013-.278.02-.422.02-2.577 0-4.027-2.146-4.09-4.832a7.592 7.592 0 0 1 .022-.708c.093-1.186.475-2.241 1.105-3.022a3.885 3.885 0 0 1 1.395-1.1c.468-.237 1.127-.367 1.664-.367h.023c.101 0 .202.004.303.01V4.211A4.201 4.201 0 0 0 19.79 0Zm.198 5.583h4.165l3.588 8.435 3.59-8.435h3.864v.146l-.019.004c-.705.16-1.063.397-1.063 1.254h-.003l.003 10.274c.06.676.424.885 1.063 1.03l.02.004v.145h-4.923v-.145l.019-.005c.639-.144.994-.353 1.054-1.03V7.267l-4.745 11.15h-.261L6.15 7.569v9.445c0 .857.358 1.094 1.063 1.253l.02.004v.147H4.405v-.147l.019-.004c.705-.16 1.065-.397 1.065-1.253V6.987c0-.857-.358-1.094-1.064-1.254l-.018-.004zm19.25 3.668c-1.086.023-1.733 1.323-1.813 3.124H24V9.298a1.378 1.378 0 0 0-.342-.047Zm-1.862 3.632c-.1 1.756.86 3.239 2.204 3.634v-3.634z'}
HOUSE = 'M12 3 2.5 11.2h2.8V21h5.2v-5.6h3V21h5.2v-9.8h2.8z'
ENVELOPE = 'M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm1.6 2 7.4 5.6L19.4 7z'
OWN = ('playoftime.online', 'dreamsmoker.playoftime.online')
def ico(d, fill='currentColor'): return f'<svg class="ico" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill="{fill}" d="{d}"/></svg>'
BRAND = {'reddit.com': '#FF4500', 'medium.com': '#000000'}
# Play of Time: the animated brand mark of the game (webapp .pot-logo: ring, triangle, letter)
POTLOGO = ('<span class="ico potlogo" aria-hidden="true"><img src="assets/pot-logo-ring.png" alt="" class="pl-ring">'
           '<img src="assets/pot-logo-triangle.png" alt="" class="pl-tri"><img src="assets/pot-logo-letter.png" alt="" class="pl-letter"></span>')
def mark_links(h):
    # Four kinds of links look different: a page of this site — plain; another site of mine —
    # a house before; someone else's site — the service mark if there is one and ↗ after;
    # e-mail — an envelope before. Guards keep it stable, since parts come from the built page.
    def one(m):
        a, inner = m.group(1), m.group(2)
        href = re.search(r'href="([^"]*)"', a).group(1)
        if href.startswith('#') or 'class="ext"' in inner or 'class="ico own"' in inner or 'class="ico mail"' in inner or 'potlogo' in inner or 'ralogo' in inner or 'class="ico img' in inner:
            return m.group(0)
        if href.startswith('mailto:'):
            return a + ico(ENVELOPE).replace('class="ico"', 'class="ico mail"') + inner + '</a>'
        host = re.match(r'https?://([^/]+)', href)
        if not host or host.group(1) == 'baa.playoftime.online':
            return m.group(0)
        host = host.group(1)
        if href.rstrip('/') == 'https://playoftime.online/aloud':
            return a + '<img class="ico ralogo" src="assets/read-aloud-logo.svg" alt="" aria-hidden="true">' + inner + '</a>'
        if href.rstrip('/') == 'https://playoftime.online':
            return a + POTLOGO + inner + '</a>'
        if host == 'dreamsmoker.playoftime.online':
            return a + '<img class="ico img rider" src="assets/rider.png?v=3" alt="" aria-hidden="true">' + inner + '</a>'
        if host in OWN:
            return a + ico(HOUSE).replace('class="ico"', 'class="ico own"') + inner + '</a>'
        svc = next((k for k in MARKS if host.endswith(k)), None)
        if host.endswith('usethics.ru') and 'usethics-mark' not in inner:
            inner = '<img class="ico img" src="assets/usethics-mark.png" alt="" aria-hidden="true">' + inner
        if svc and not inner.startswith('<svg class="ico"'):
            inner = ico(MARKS[svc], BRAND.get(svc, 'currentColor')) + inner
        return a + inner + '<svg class="ext" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill="currentColor" d="M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"/></svg></a>'
    return re.sub(r'(<a [^>]*>)(.*?)</a>', one, h, flags=re.S)


body = mark_links('<main class="wrap">\n' + who + INTRO + MAP + steps_html() + TAIL + footer + '</main>\n') + scripts
html = src[:src.index('<!--head:start-->')] + '<!--head:start-->\n' + head + '<style>\n' + style + EXTRA \
    + '</style>\n' + src[src.index('</style>\n') + len('</style>\n'):src.index('<!--body:start-->')] \
    + '<!--body:start-->\n' + body + src[src.index('<!--body:end-->'):]
if not DRAFT:
    open(f'{ROOT}/index.html', 'w', encoding='utf-8').write(html)
    print('built baa/index.html'); sys.exit()
html = re.sub(r'(href|src|srcset)="(?!https?:|mailto:|#|/)([^"]+)"', lambda m: f'{m.group(1)}="../{m.group(2)}"', html)
os.makedirs(f'{ROOT}/{DRAFT}', exist_ok=True)
open(f'{ROOT}/{DRAFT}/index.html', 'w', encoding='utf-8').write(html)
print('built', DRAFT)
