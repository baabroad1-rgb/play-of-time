# Read Aloud logo: the Play of Time ring (dark disc, pink→teal arc, green lettering) with READ ALOUD
# on it, and IPA signs in the middle that replace one another; animated with CSS inside the SVG.
import math, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
OUT = sys.argv[1]
S = 512; C = 256
BG = '#101A26'; PINK = (222, 66, 100); TEAL = (12, 168, 204); GREEN = '#8EFF51'; YELLOW = '#FFCF3D'
sans = TTFont('/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf')
serif = TTFont('/usr/share/fonts/truetype/freefont/FreeSerifBoldItalic.ttf')

def glyph_path(font, ch, size, x, y, rot=0.0):
    gs = font.getGlyphSet(); name = font.getBestCmap()[ord(ch)]
    upm = font['head'].unitsPerEm; k = size / upm
    pen = SVGPathPen(gs)
    c, s = math.cos(rot), math.sin(rot)
    # font units: y up; SVG: y down. Scale, flip, rotate, translate.
    tp = TransformPen(pen, (k * c, k * s, k * s * -1 * -1 if False else k * s, -k * c, x, y))
    tp = TransformPen(pen, (k * c, k * s, k * s, -k * c, x, y))
    gs[name].draw(tp)
    return pen.getCommands(), gs[name].width * k

def ring():
    out = [f'<circle cx="{C}" cy="{C}" r="225" fill="{BG}"/>']
    # arc: from about 11 o'clock counter-clockwise round the bottom to 3 o'clock, pink to teal
    r = 205; a0, a1 = math.radians(-112), math.radians(-450 + 360 + 0)  # -112° → 0° going the long way
    start, end = -112, -360   # degrees, clockwise-negative: we go counter-clockwise from -112 to -360
    n = 90
    for i in range(n):
        t0, t1 = i / n, (i + 1) / n
        d0 = math.radians(start + (end - start) * t0 + (0.8 if i else 0)); d1 = math.radians(start + (end - start) * t1)
        col = tuple(round(PINK[j] + (TEAL[j] - PINK[j]) * (t0 + t1) / 2) for j in range(3))
        x0, y0 = C + r * math.cos(d0), C + r * math.sin(d0); x1, y1 = C + r * math.cos(d1), C + r * math.sin(d1)
        out.append(f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 0 0 {x1:.1f},{y1:.1f}" stroke="rgb{col}" stroke-width="9" fill="none" stroke-linecap="{"round" if i in (0, n - 1) else "butt"}"/>')
    # lettering along the gap, clockwise, from about 12 o'clock to 3 o'clock
    text = 'READ ALOUD'; size = 34; rt = 180; track = 8
    widths = [glyph_path(sans, ch, size, 0, 0)[1] + track if ch != ' ' else size * 0.45 for ch in text]
    total = sum(widths) - track
    span = total / rt                        # radians the text takes
    mid = math.radians(-45)                  # centre of the gap
    ang = mid - span / 2
    paths = []
    for ch, w in zip(text, widths):
        if ch != ' ':
            gw = w - track
            a = ang + (gw / 2) / rt
            # glyph centred on its angle, baseline outward
            x = C + rt * math.cos(a); y = C + rt * math.sin(a)
            rot = a + math.pi / 2
            # move back by half width along the tangent
            ox = x - (gw / 2) * math.cos(rot); oy = y - (gw / 2) * math.sin(rot)
            d, _ = glyph_path(sans, ch, size, ox, oy, rot)
            paths.append(d)
        ang += w / rt
    out.append(f'<path d="{" ".join(paths)}" fill="{GREEN}"/>')
    return '\n'.join(out)

IPA = list('əθʃæŋð')
def signs():
    out = []
    for i, ch in enumerate(IPA):
        d, w = glyph_path(serif, ch, 245, 0, 0)
        # centre the glyph: measure its bounds roughly with the bounds pen
        from fontTools.pens.boundsPen import BoundsPen
        gs = serif.getGlyphSet(); bp = BoundsPen(gs); gs[serif.getBestCmap()[ord(ch)]].draw(bp)
        xmin, ymin, xmax, ymax = bp.bounds; k = 245 / serif['head'].unitsPerEm
        cx = (xmin + xmax) / 2 * k; cy = (ymin + ymax) / 2 * k
        d, _ = glyph_path(serif, ch, 245, C - cx, C + cy)
        out.append(f'<path class="s s{i}" d="{d}" fill="{YELLOW}"/>')
    return '\n'.join(out)

n = len(IPA); slot = 3.0 / n   # the six signs share three seconds, like the Play of Time spin
style = f'''<style>
.ring {{ transform-origin: {C}px {C}px; animation: ring 5s cubic-bezier(.34,1.56,.64,1) infinite; }}
@keyframes ring {{ 0%, 8% {{ transform: rotate(0deg); }} 22% {{ transform: rotate(372deg); }} 30% {{ transform: rotate(355deg); }} 36%, 100% {{ transform: rotate(360deg); }} }}
.s {{ transform-box: fill-box; transform-origin: center; transform: scaleX(0); animation: sign 3s linear infinite; }}
{''.join(f'.s{i} {{ animation-delay: {i * slot - 3:.2f}s; }}' for i in range(n))}
@keyframes sign {{ 0% {{ transform: scaleX(0); }} {100 / n * 0.2:.2f}%, {100 / n * 0.8:.2f}% {{ transform: scaleX(1); }} {100 / n:.2f}%, 100% {{ transform: scaleX(0); }} }}
@media (prefers-reduced-motion: reduce) {{ .ring, .s {{ animation: none; }} .s0 {{ transform: none; }} }}
</style>'''
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {S} {S}" role="img" aria-label="Read Aloud">
{style}
<g class="ring">
{ring()}
</g>
{signs()}
</svg>
'''
open(OUT, 'w', encoding='utf-8').write(svg)
print('ok', len(svg))
