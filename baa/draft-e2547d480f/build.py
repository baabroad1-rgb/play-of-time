# Builds two draft versions of baa.playoftime.online (variant B: interests as questions;
# variant C: interests with a loop map on top) from the live index.html, so they share its
# header, stopwatch, Read Aloud widget, scripts and footer.
import re, sys, os

ROOT = '/home/user/play-of-time/baa'
DRAFT = os.path.basename(os.path.dirname(os.path.abspath(__file__)))
src = open(f'{ROOT}/index.html', encoding='utf-8').read()
E = ' target="_blank" rel="noopener"'

def grab(a, b, s=src):
    i = s.index(a); j = s.index(b, i) + len(b); return s[i:j]

head = src[src.index('<!--head:start-->') + len('<!--head:start-->\n'):src.index('<style>')]
style = src[src.index('<style>') + len('<style>\n'):src.index('</style>')]
who = grab('  <header class="who">', '</header>\n')
pot = grab('      <div class="pot" id="pot">', '        </div>\n      </div>\n')
aloud = grab('      <iframe class="aloud"', '</iframe>\n')
footer = grab('  <footer>', '</footer>\n')
scripts = src[src.index('</main>\n') + len('</main>\n'):src.index('<!--body:end-->')]
pubs = grab('      <ol class="pubs">', '      </ol>\n')
blog = grab('      <h4>On the Usethics blog</h4>', '      </ol>\n')
href = lambda part: re.search(r'href="([^"]*' + re.escape(part) + r'[^"]*)"', src).group(1)
U_INT = href('770336b7d596'); MAIL = re.search(r'(<a href="mailto:[^"]+">[^<]+</a>)', src).group(1)

# Draft pages: not indexed, no canonical, no structured data.
head = re.sub(r'<link rel="canonical"[^>]*>\n', '', head)
head = re.sub(r'<script type="application/ld\+json">.*?</script>\n', '', head, flags=re.S)
head = re.sub(r'<meta name="robots" content="[^"]*">', '<meta name="robots" content="noindex, nofollow, noarchive">', head)

EXTRA = '''
/* ---- drafts: one centred reading column; interests carry the page, years are small notes ---- */
.wrap { max-width: 760px; }
.intro { max-width: none; }
.lede { max-width: 32em; }
section.interest { display: block; padding-block: 18px clamp(48px, 7vw, 72px); border-top: 1px solid var(--fg); }
.interest .num { display: block; font-family: var(--sans); font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; margin-bottom: 8px; }
.interest h2 { display: block; position: static; margin: 0 0 18px; font-family: var(--sans); font-size: clamp(28px, 4.2vw, 40px); line-height: 1.08; letter-spacing: -0.025em; font-weight: 700; text-wrap: balance; }
.interest .col { max-width: none; }
.where { margin-top: 6px; padding-top: 10px; border-top: 1px dotted var(--fg); font-family: var(--sans); font-size: 14px; line-height: 1.45; display: grid; gap: 2px; }
.where b { font-weight: 700; }
.back { font-family: var(--sans); font-size: 15px; }
section.bio { display: block; padding-block: 18px 40px; border-top: 1px solid var(--fg); font-family: var(--sans); font-size: 15px; }
section.bio h2, details.pubwrap summary { position: static; display: block; font-family: var(--sans); font-size: 15px; font-weight: 700; margin: 0 0 10px; }
section.bio ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; font-variant-numeric: tabular-nums; }
details.pubwrap { padding-block: 18px 40px; border-top: 1px solid var(--fg); }
details.pubwrap summary { cursor: pointer; margin: 0; }
details.pubwrap[open] summary { margin-bottom: 16px; }
details.pubwrap .col { max-width: none; }
section.you { display: block; padding-block: 18px 56px; border-top: 1px solid var(--fg); }
/* variant C: the loop map */
.map { margin: 0 0 clamp(40px, 7vw, 72px); }
.map svg { display: block; width: 100%; height: auto; }
.map a { cursor: pointer; }
.map a:hover text { text-decoration: underline; }
.map .lbl { font-family: var(--sans); font-size: 15px; fill: var(--fg); }
.map .nm { font-family: var(--sans); font-size: 13px; font-weight: 700; fill: var(--fg); }
.map .ctr { font-family: var(--sans); font-size: 16px; font-weight: 700; fill: var(--fg); }
.map ol { display: none; margin: 0; padding-left: 1.4em; font-family: var(--sans); font-size: 16px; line-height: 1.6; }
@media (max-width: 620px) { .map svg { display: none; } .map ol { display: block; } }
'''

# The six interests. q = the question (variant B), name = the plain title (variant C).
INTERESTS = [
  dict(id='gap', q='Do people know what they actually do?', name='What people do, and what they think they do',
    body='''      <p>Mostly not. From the inside you see what you meant to do. What you actually did shows only from the outside, in a record of it. Closing that gap is the thread through everything on this page.</p>
      <p>I first measured it as research: how different kinds of users picture their own work at a computer, set against what they do. Then I spent twenty years showing it to the people who make products, because they can act on it.</p>
''',
    where=[('1997', f'graduate research at Moscow State University, <a href="http://usability.ru/Articles/osobennosti.htm"{E}>How different kinds of users picture their work with a computer</a>'),
           ('2001–2023', f'founded and ran <a href="https://usethics.ru/"{E}>Usethics</a>, the first Russian company doing only usability research: about 2,000 projects'),
           ('2011', f'<a href="https://link.springer.com/chapter/10.1007/978-0-85729-304-6_17"{E}>Usability in Russia</a>, a chapter in <i>Global Usability</i> (Springer)')]),
  dict(id='interrupt', q='What happens when work is interrupted?', name='Interruption, and the way back',
    body=f'''      <p>Someone edits a file in Word. We interrupt: look up a word in a paper dictionary, find a number in a phone book. Then we watch how they get back to the task.</p>
      <p>Each person returns in their own way, and the ways repeat. We could see the strategies; the people using them mostly could not.</p>
''',
    where=[('1997–2001', 'work psychology laboratory, Faculty of Psychology, Moscow State University'),
           ('', f'<a href="{U_INT}"{E}>How interruptions affect people working at a PC</a>, with Ivan Burmistrov (in Russian)'),
           ('2001', f'<a href="https://link.springer.com/chapter/10.1007/978-94-010-0784-9_8"{E}>Psychological Support of Work Safety and Labor Protection</a>, a chapter in a Kluwer volume')]),
  dict(id='time', q='Can you feel three seconds?', name='Time, felt from the inside',
    body='''      <p>Too much excitement and you press early. Too much restraint and you press late. Between them is a narrow working state that can be found again on purpose, and three seconds are enough to train it.</p>
      <p>Luck turns out not to be random either. How often you hit, divided by how often pure chance would, is a stable trait of each person. Try it:</p>
''' + pot,
    where=[('2025–2026', '<a href="https://playoftime.online/" target="_blank" rel="noopener">Play of Time</a>, a stopwatch trainer built on S. G. Gellerstein’s timing drills for athletes')]),
  dict(id='self', q='Do you sound the way you think you do?', name='Hearing and seeing yourself',
    body='''      <p>Your own voice reaches you through the bones of your head; everyone else hears it through the air. So reading aloud needs an outside listener. Read Aloud is one: it scores every word as you say it. Try it on the paragraph below.</p>
''' + aloud + '''      <p>The same idea without software: <a href="revizor/">Gogol’s <i>The Government Inspector</i></a>, which I recorded as a radio play to hear my own voice, and <a href="https://dreamsmoker.playoftime.online/" target="_blank" rel="noopener">a photo project</a> that records how I look and what I think, in a dream.</p>
''',
    where=[('2023–2026', '<a href="https://playoftime.online/aloud/" target="_blank" rel="noopener">Read Aloud</a>, an e-book reader that listens; the audiobook; the photo project')]),
  dict(id='questions', q='What is today’s question?', name='The question as a way of thinking',
    body='''      <p>Every day I write one new question. A question changes what you notice more than a statement does, so I have started to treat it as the basic unit of thinking. I call the idea Qology.</p>
      <p>The material comes from reading, about 52 books a year. What I take from them goes to one channel, the questions they leave to another.</p>
''',
    where=[('Now', '<a href="https://t.me/q_of_t_d" target="_blank" rel="noopener">Question of the Day</a>, a Telegram channel · <a href="https://t.me/baaqod" target="_blank" rel="noopener">aka mulator</a>, quotes from the books')]),
  dict(id='plain', q='Can a hard thing be said simply?', name='Saying it plainly',
    body='''      <p>A checklist that anyone can run on an interface without training. Philosophical texts retold in plain English. Both test whether an idea survives without its jargon.</p>
      <p>Plain words are feedback too: if I cannot say something simply, I see what I have not understood yet. Which brings it back to the first question.</p>
''',
    where=[('2011', '<a href="texts/user-interface-checklist/">A user interface checklist</a>, with Vlad Golovach, translated here'),
           ('Now', '<a href="https://www.reddit.com/r/English_but_Simple/" target="_blank" rel="noopener">English but Simple</a>, a subreddit')]),
]

def where_html(rows):
    return '      <div class="where">' + ''.join(f'<span>{f"<b>{y}</b> · " if y else ""}{t}</span>' for y, t in rows) + '</div>\n'

def interests(title_key):
    out = []
    n = len(INTERESTS)
    for k, it in enumerate(INTERESTS, 1):
        back = f'      <p class="back"><a href="#{INTERESTS[0]["id"]}">↺ Back to the first question</a></p>\n' if k == n else ''
        out.append(f'''  <section class="interest" id="{it["id"]}">
    <span class="num">{k} / {n}</span>
    <h2>{it[title_key]}</h2>
    <div class="col">
{it["body"]}{back}{where_html(it["where"])}    </div>
  </section>

''')
    return ''.join(out)

TAIL = f'''  <section class="you">
    <div class="col">
      <p class="ask">What feedback loop do you need to build?</p>
      <p>Write to me and let’s discuss it: {MAIL}</p>
    </div>
  </section>

  <section class="bio">
    <h2>Where and when</h2>
    <ul>
      <li>1997–2001 · Faculty of Psychology, Moscow State University: work psychology laboratory</li>
      <li>2001–2023 · Usethics, founder and director</li>
      <li>2026 · methodologist</li>
    </ul>
  </section>

  <details class="pubwrap">
    <summary>Publications, 1997–2011</summary>
    <div class="col">
{pubs}{blog}    </div>
  </details>

'''

INTRO_B = '''  <h1 class="intro">People don’t see what they actually do without a useful feedback loop. Since 1997 I have been learning to build such loops, and helping others build them.</h1>
  <p class="lede">Six questions I keep coming back to. Years and places are there only to show where each one came up.</p>

'''
INTRO_C = INTRO_B.replace('Six questions I keep coming back to.', 'Six interests, each a kind of feedback loop.')

def loop_map():
    import math
    W, H, cx, cy, r = 680, 400, 340, 200, 128
    parts = [f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="Six interests around one feedback loop">',
             '<defs><marker id="ah" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker></defs>',
             f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="#000" stroke-width="1.5"/>']
    n = len(INTERESTS)
    for k, it in enumerate(INTERESTS):
        a = -math.pi / 2 + k * 2 * math.pi / n
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        # an arrow along the circle, between this node and the next: the loop runs clockwise
        a1, a2 = a + 0.2, a + 2 * math.pi / n - 0.2
        p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1)); p2 = (cx + r * math.cos(a2), cy + r * math.sin(a2))
        parts.append(f'<path d="M{p1[0]:.1f},{p1[1]:.1f} A{r},{r} 0 0 1 {p2[0]:.1f},{p2[1]:.1f}" fill="none" stroke="#000" stroke-width="1.5" marker-end="url(#ah)"/>')
        lx, ly = cx + (r + 22) * math.cos(a), cy + (r + 22) * math.sin(a)
        anchor = 'middle' if abs(math.cos(a)) < 0.2 else ('start' if math.cos(a) > 0 else 'end')
        dy = -8 if math.sin(a) < -0.9 else (18 if math.sin(a) > 0.9 else 5)
        words = it['name'].split(' ')
        # two lines at most
        mid = (len(words) + 1) // 2
        lines = [' '.join(words[:mid]), ' '.join(words[mid:])] if len(it['name']) > 22 else [it['name']]
        if dy < 0 and len(lines) == 2: dy -= 18
        tspans = ''.join(f'<tspan x="{lx:.1f}" dy="{0 if i == 0 else 18}">{t}</tspan>' for i, t in enumerate(lines))
        parts.append(f'<a href="#{it["id"]}"><circle cx="{x:.1f}" cy="{y:.1f}" r="13" fill="#fff" stroke="#000" stroke-width="1.5"/>'
                     f'<text class="nm" x="{x:.1f}" y="{y + 4.5:.1f}" text-anchor="middle">{k + 1}</text>'
                     f'<text class="lbl" x="{lx:.1f}" y="{ly + dy:.1f}" text-anchor="{anchor}">{tspans}</text></a>')
    parts.append(f'<text class="ctr" x="{cx}" y="{cy - 4}" text-anchor="middle">a useful</text><text class="ctr" x="{cx}" y="{cy + 16}" text-anchor="middle">feedback loop</text>')
    parts.append('</svg>')
    ol = '<ol>' + ''.join(f'<li><a href="#{it["id"]}">{it["name"]}</a></li>' for it in INTERESTS) + '</ol>'
    return '  <nav class="map" aria-label="The six interests">\n    ' + ''.join(parts) + '\n    ' + ol + '\n  </nav>\n\n'

def page(intro, extra_top, title_key):
    body = '<main class="wrap">\n' + who + intro + extra_top + interests(title_key) + TAIL + footer + '</main>\n' + scripts
    html = '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n' \
        + head + '<style>\n' + style + EXTRA + '</style>\n</head>\n<body>\n' + body + '</body>\n</html>\n'
    # the drafts sit two folders down: point relative links back to the site root
    html = re.sub(r'(href|src|srcset)="(?!https?:|mailto:|#|/)([^"]+)"', lambda m: f'{m.group(1)}="../../{m.group(2)}"', html)
    return html

os.makedirs(f'{ROOT}/{DRAFT}/b', exist_ok=True); os.makedirs(f'{ROOT}/{DRAFT}/c', exist_ok=True)
open(f'{ROOT}/{DRAFT}/b/index.html', 'w', encoding='utf-8').write(page(INTRO_B, '', 'q'))
open(f'{ROOT}/{DRAFT}/c/index.html', 'w', encoding='utf-8').write(page(INTRO_C, loop_map(), 'name'))
print('built', DRAFT)
