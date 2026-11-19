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
pubwrap = re.sub(r'<summary>.*?</summary>', '<summary><span>Publications, 1997–2011</span><svg class="chev" viewBox="0 0 12 12" aria-hidden="true">'
    '<path d="M2.5 4.5 6 8l3.5-3.5"/></svg></summary>', pubwrap, count=1, flags=re.S)
href = lambda part: re.search(r'href="([^"]*' + re.escape(part) + r'[^"]*)"', src).group(1)
U_INT = href('770336b7d596'); MAIL = re.search(r'(<a href="mailto:[^"]+">[^<]+</a>)', src).group(1)

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
def svg(k, label): return f'<svg viewBox="0 0 64 40" role="img" aria-label="{label}">{FIG[k]}</svg>'

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
  dict(id='arc', fig='arc', term='Reflex arc', q='Do people know what they actually do?',
    body=loop_fig('arc') + f'''      <p>In a reflex arc a signal goes in, an action comes out, and nothing comes back. Much of what we do runs this way: we act, and we do not see what we did.</p>
      <p class="me">My first research measured that gap: how different kinds of users picture their own work at a computer, set against what they actually do. Later, at the lab, we interrupted people editing a file in Word with another task, such as finding a number in a phone book, and watched how they got back to the original task. We could see their strategies; the people using them mostly could not.</p>
'''),
  dict(id='ring', fig='ring', term='Reflex ring', q='Can you correct yourself while you act?',
    body='''''' + ring_fig() + '''      <p>Almost nothing we do is done in one go. You start, see how it is going and adjust, again and again, mostly without noticing. Pouring tea, you watch the level and slow down near the top. On a bike you are never still: every wobble is noticed and corrected. Physiologists of the last century, Nikolai Bernstein among them, called this a reflex ring: unlike the arc, the result comes back and steers the next move. Today we would call it a feedback loop.</p>
      <p>The ring works as long as the senses report what matters. They are good with a teapot or a bike, and poor with time, the sound of your own voice or the way you spend a day. For these, the ring needs help from outside. That is what steps 3 and 4 are about.</p>
      <p class="me">On myself, from 2023 to 2026, I ran the same ring more slowly: I record what I do, come back to the record later as an observer, compare it with what I meant to do, and change the next round. I read about 52 books a year and keep what I take from them in <a href="https://t.me/baaqod" target="_blank" rel="noopener">aka mulator</a>, a Telegram channel of quotes; reading it, I watch myself reading. The questions the books raise go to <a href="https://t.me/q_of_t_d" target="_blank" rel="noopener">Question of the Day</a>, one new question every day. Many have a hidden part, an answer or a further question, that opens with a tap. Rereading them later, I try to guess what is hidden before I tap, then check: the gap shows how much I have changed since. When my new guess is quite different, I post it as a new question that links to the old one, and the rings join into chains. I recorded <a href="revizor/">Gogol’s <i>The Government Inspector</i></a> as a radio play, reading it myself: the same ring, with my voice.</p>
'''),
  dict(id='co', fig='co', term='Co-regulation', q='Who sees what you can’t?',
    body=loop_fig('co') + '''      <p>Some things a person cannot see from the inside, however closely they listen to themselves. Then the loop needs another person, included on purpose: someone who watches from the outside and reports back. I call this co&#8209;regulation: two people keep one loop going, one acts, the other watches and tells.</p>
      <p class="me">For twenty years I was that person for companies. At <a href="https://en.usethics.ru/" target="_blank" rel="noopener">Usethics</a>, which I founded in 2001 and ran until it ceased operations in 2023, we watched real people use a product and showed the people who built it what we saw: around 2,000 projects for Russian and international clients.</p>
      <p class="me">On myself: in <a href="https://dreamsmoker.playoftime.online/" target="_blank" rel="noopener">a photo project</a>, a photographer shot a dream of mine as a series of pictures; they show me from the outside how I looked and what I was thinking in it. I also retell philosophical texts in plain English in the subreddit <a href="https://www.reddit.com/r/English_but_Simple/" target="_blank" rel="noopener">English but Simple</a>, and the readers show me where I am still unclear.</p>
'''),
  dict(id='auto', fig='auto', term='Automated co-regulation', q='Can a device be the other?',
    body=loop_fig('auto') + '''      <p>A person who watches you cannot be there all the time. Technology can: software, AI, wearable sensors, connected devices. The work is to make what they report back useful, which means instant and exact enough to act on. Two of my tools already do this, for two things from step 2 that the senses report poorly: time and your own voice.</p>
      <p class="me"><a href="https://playoftime.online/" target="_blank" rel="noopener"><b>Play of Time</b></a> is a stopwatch you try to stop at exactly three seconds. By feel you cannot tell how far off you were. Every attempt tells you how precise you were, whether you are rushing or holding back, and your Lucky Index: how often you hit the target, divided by how often pure chance would.</p>
''' + pot + '''      <p class="me"><a href="https://playoftime.online/aloud/" target="_blank" rel="noopener"><b>Read Aloud</b></a> listens while you read an English book aloud and shows, word by word, which words came out right. Try it on the paragraph below: press the button, allow microphone access, and read it aloud.</p>
''' + aloud),
]

def steps_html():
    out = []
    for k, st in enumerate(STEPS, 1):
        out.append(f'''  <section class="interest" id="{st["id"]}">
    <div class="stephead">{svg(st["fig"], st["term"])}<span><span class="num">Step {k} of 4</span><span class="term">{re.sub(r"(\S+-\S+)", r'<span class="nw">\1</span>', st["term"])}</span></span></div>
    <h2>{st["q"]}</h2>
    <div class="col">
{st["body"]}    </div>
  </section>

''')
    return ''.join(out)

MAP = '  <nav class="steps" aria-label="Four steps">' + '<span class="arr" aria-hidden="true">→</span>'.join(
    f'<a href="#{st["id"]}">{svg(st["fig"], "")}<span>{st["term"]}</span></a>' for st in STEPS) + '</nav>\n\n'

INTRO = '''  <h1 class="intro">People don’t see what they actually do unless a useful feedback loop shows them.</h1>
  <p class="lede">Four steps: no feedback at all, then your own senses, then another person, then a device.</p>

'''

TAIL = f'''  <section class="you">
    <div class="col">
      <p class="ask">What feedback loop do you need to build?</p>
      <p>Tell me who needs to see what, and what should change once they see it: your team, your users or you yourself. I will reply with a sketch of the loop: what to record, who or what reports it back, and how soon.</p>
      <p>{MAIL}</p>
    </div>
  </section>

  <section class="bio">
    <h2>Where and when</h2>
    <ul>
      <li>1997–2001 · Laboratory of Work Psychology, Faculty of Psychology, Moscow State University</li>
      <li>2001–2023 · Usethics, founder and director</li>
      <li>2026 · feedback methodologist</li>
    </ul>
  </section>

''' + pubwrap + '\n'

body = '<main class="wrap">\n' + who + INTRO + MAP + steps_html() + TAIL + footer + '</main>\n' + scripts
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
