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
section.myself { display: block; padding-block: 18px clamp(40px, 6vw, 60px); }
section.myself h2 { position: static; display: block; margin: 0 0 14px; font-family: var(--sans); font-size: clamp(22px, 3vw, 28px); line-height: 1.12; font-weight: 700; letter-spacing: -0.02em; }
/* step figure and name sit in the left column, where the years used to be */
section.interest { display: grid; grid-template-columns: 10em minmax(0, 1fr); column-gap: 32px; }
section.interest .stephead { grid-column: 1; grid-row: 1 / span 2; flex-direction: column; align-items: flex-start; gap: 10px; margin: 0; position: sticky; top: 16px; align-self: start; }
section.interest .stephead > span { display: grid; gap: 2px; }
section.interest > h2, section.interest > .col { grid-column: 2; }
@media (max-width: 680px) {
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

STEPS = [
  dict(id='arc', fig='arc', term='Reflex arc', q='Do people know what they actually do?',
    body=f'''      <p>In a reflex arc a signal goes in, an action comes out, and nothing comes back. Much of what we do runs this way: we act, and we do not see what we did.</p>
      <p>My first research measured that gap: how different kinds of users picture their own work at a computer, set against what they actually do. Later, at the lab, we interrupted people editing a file in Word with another task, such as finding a number in a phone book, and watched how they got back to the original task. We could see their strategies; the people using them mostly could not.</p>
'''),
  dict(id='ring', fig='ring', term='Reflex ring', q='Can you correct yourself while you act?',
    body='''      <p>Nikolai Bernstein replaced the arc with a ring: an action is corrected as it goes, by what the senses report back. A hand reaching for a cup is steered all the way by the eye and by the feel of the arm.</p>
      <p>At the Laboratory of Work Psychology at Moscow State University, led by <a href="https://msupsyj.ru/en/author/people/705" target="_blank" rel="noopener">Anna B. Leonova</a>, this was the subject: what operator work at computers demands of a person, how interruptions affect it and how to reduce the damage, how to organise work so that it goes well, methods of self-regulation, and ways to assess and correct a person’s functional state, such as fatigue, stress or monotony.</p>
'''),
  dict(id='co', fig='co', term='Co-regulation', q='Who sees what you can’t?',
    body='''      <p>Some things a person cannot see from the inside, however closely they listen to themselves. Then the loop needs another person, included on purpose: someone who watches from the outside and reports back.</p>
      <p>For twenty years I was that person for companies. At <a href="https://en.usethics.ru/" target="_blank" rel="noopener">Usethics</a>, which I founded in 2001 and ran until it ceased operations in 2023, we watched real people use a product and showed the people who built it what we saw: around 2,000 projects for Russian and international clients. Now I do the same as a feedback methodologist.</p>
'''),
  dict(id='auto', fig='auto', term='Automated co-regulation', q='Can a device be the other?',
    body='''      <p>A person who watches you cannot be there all the time. Technology can: software, AI, wearable sensors, connected devices. The work is to make what they report back useful, which means instant and exact enough to act on. Two of my tools already do this.</p>
      <p><a href="https://playoftime.online/" target="_blank" rel="noopener"><b>Play of Time</b></a> is a stopwatch you try to stop at exactly three seconds. Every attempt tells you how precise you were, whether you are rushing or holding back, and your Lucky Index: how often you hit the target, divided by how often pure chance would.</p>
''' + pot + '''      <p><a href="https://playoftime.online/aloud/" target="_blank" rel="noopener"><b>Read Aloud</b></a> listens while you read an English book aloud. Try it on the paragraph below: press the button, allow microphone access, and read it aloud.</p>
''' + aloud),
]

def steps_html():
    out = []
    for k, st in enumerate(STEPS, 1):
        out.append(f'''  <section class="interest" id="{st["id"]}">
    <div class="stephead">{svg(st["fig"], st["term"])}<span><span class="num">Step {k} of 4</span><span class="term">{st["term"]}</span></span></div>
    <h2>{st["q"]}</h2>
    <div class="col">
{st["body"]}    </div>
  </section>

''')
    return ''.join(out)

MAP = '  <nav class="steps" aria-label="Four steps">' + '<span class="arr" aria-hidden="true">→</span>'.join(
    f'<a href="#{st["id"]}">{svg(st["fig"], "")}<span>{st["term"]}</span></a>' for st in STEPS) + '</nav>\n\n'

INTRO = '''  <h1 class="intro">People don’t see what they actually do without a useful feedback loop. Since 1997, I have been learning to build such loops and helping others build them.</h1>
  <p class="lede">A loop like that grows in four steps.</p>

'''

MYSELF = '''  <section class="myself" id="myself">
    <h2>Tried on myself</h2>
    <div class="col">
      <p>Between clients, from 2023 to 2026, I tried feedback on myself. I read about 52 books a year and keep what I take from them in <a href="https://t.me/baaqod" target="_blank" rel="noopener">aka mulator</a>, a Telegram channel of quotes. The questions they raise go to <a href="https://t.me/q_of_t_d" target="_blank" rel="noopener">Question of the Day</a>, one new question every day. Philosophical texts in plain English go to the subreddit <a href="https://www.reddit.com/r/English_but_Simple/" target="_blank" rel="noopener">English but Simple</a>.</p>
      <p>I recorded <a href="revizor/">Gogol’s <i>The Government Inspector</i></a> as a radio play, reading it myself to hear my own voice, and <a href="https://dreamsmoker.playoftime.online/" target="_blank" rel="noopener">a photo project</a> captured how I looked and what I was thinking, as in a dream. In 2026 I took a new job as a methodologist.</p>
    </div>
  </section>

'''

TAIL = f'''  <section class="you">
    <div class="col">
      <p class="ask">What feedback loop do you need to build?</p>
      <p>Write to me and let’s discuss it: {MAIL}</p>
    </div>
  </section>

  <section class="bio">
    <h2>Where and when</h2>
    <ul>
      <li>1997–2001 · Laboratory of Work Psychology, Faculty of Psychology, Moscow State University</li>
      <li>2001–2023 · Usethics, founder and director</li>
      <li>2026 · methodologist</li>
    </ul>
  </section>

''' + pubwrap + '\n'

body = '<main class="wrap">\n' + who + INTRO + MAP + steps_html() + MYSELF + TAIL + footer + '</main>\n' + scripts
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
