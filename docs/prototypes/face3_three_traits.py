# -*- coding: utf-8 -*-
"""Макеты «Морда 3.0» (Алекс 07.11.2026): три характеристики в одном лице и уши.

Запуск: python3 face3_three_traits.py -> рядом face3_three_traits.html и face3_earrings.html.
Формулы, пороги и данные — docs/FACE_V3_THREE_TRAITS.md. Это эскиз для
обсуждения, не реализация: в report.html ничего не менялось."""
import os
HERE=os.path.dirname(os.path.abspath(__file__))
import math, itertools
_id=itertools.count()
def clamp(x,a,b): return max(a,min(b,x))
def _face_base(sigma=28, B=0.0, L=1.0, third=False, gate=False, size=170):
    """sigma ms (кучность), B in [-1,1] (баланс, - = рано), L luck ratio."""
    i=next(_id)
    t=clamp(math.log(max(sigma,1)/28)/math.log(200/28),0,1)        # 0 резко .. 1 туман
    blur=6.5*t
    stage = 1 if sigma>45 else (2 if abs(B)>0.2 else 3)
    b=clamp((L-1)/0.5,0,1); u=clamp((1-L)/0.4,0,1)
    if gate and stage<3: b=0; u=0; third=False
    Bc=clamp(B,-1,1); a=abs(Bc); left = Bc<0
    def drop(side_left, k):  # how much a feature on this side droops
        return k*a if (side_left==left and a>0) else -0.25*k*a if a>0 else 0
    cx,cy=100,112
    s=[]
    s.append(f'<svg viewBox="0 0 200 220" width="{size}" height="{size*1.1:.0f}" xmlns="http://www.w3.org/2000/svg">')
    s.append(f'<defs><filter id="bl{i}" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="{blur:.2f}"/></filter>'
             f'<radialGradient id="gl{i}"><stop offset="60%" stop-color="#ffd76a" stop-opacity="{0.55*b:.2f}"/><stop offset="100%" stop-color="#ffd76a" stop-opacity="0"/></radialGradient></defs>')
    if b>0: s.append(f'<ellipse cx="{cx}" cy="{cy}" rx="98" ry="108" fill="url(#gl{i})"/>')
    s.append(f'<g filter="url(#bl{i})" stroke="#2a2420" stroke-width="2.6" stroke-linecap="round" fill="none">')
    s.append(f'<ellipse cx="{cx}" cy="{cy}" rx="70" ry="88" fill="#f3dcc0"/>')
    # ears
    for sl in (True,False):
        x=cx-70 if sl else cx+70; dy=drop(sl,8)
        s.append(f'<ellipse cx="{x:.1f}" cy="{108+dy:.1f}" rx="9" ry="15" fill="#efd2b2"/>')
    # blush
    if b>0:
        for sl in (True,False):
            x=cx-40 if sl else cx+40; dy=drop(sl,9)
            s.append(f'<ellipse cx="{x}" cy="{132+dy:.1f}" rx="12" ry="7" fill="#ff8a8a" fill-opacity="{0.45*b:.2f}" stroke="none"/>')
    # eyes + brows
    for sl in (True,False):
        x=cx-27 if sl else cx+27; dy=drop(sl,9)
        ry=7.5-2.2*u
        s.append(f'<ellipse cx="{x}" cy="{98+dy:.1f}" rx="12" ry="{ry:.1f}" fill="#fff"/>')
        s.append(f'<circle cx="{x}" cy="{98+dy:.1f}" r="4.4" fill="#2a2420" stroke="none"/>')
        if b>0: s.append(f'<circle cx="{x+1.8}" cy="{96.2+dy:.1f}" r="{1.2+0.9*b:.1f}" fill="#fff" stroke="none"/>')
        # brow: outer end droops more on heavy side
        inner=(x+10 if sl else x-10); outer=(x-12 if sl else x+12)
        by_in=80+drop(sl,8); by_out=80+drop(sl,14)
        s.append(f'<path d="M{outer} {by_out:.1f} L{inner} {by_in:.1f}"/>')
    # nose tilts toward heavy side
    ang=math.radians(18*Bc)  # B<0 -> nose bottom goes left
    nx,ny=cx,104; L_=24
    bx=nx+L_*math.sin(ang); byy=ny+L_*math.cos(ang)
    s.append(f'<path d="M{nx} {ny} L{bx:.1f} {byy:.1f} L{bx+7*math.cos(ang)*(-1 if Bc<0 else 1):.1f} {byy-1:.1f}"/>')
    # mouth
    ly=152+drop(True,12); ry_=152+drop(False,12)
    mx=cx+6*Bc
    smile=5+11*b-5*u
    s.append(f'<path d="M{cx-24+3*Bc:.1f} {ly:.1f} Q{mx:.1f} {(ly+ry_)/2+smile:.1f} {cx+24+3*Bc:.1f} {ry_:.1f}"/>')
    if third:
        s.append(f'<ellipse cx="{cx}" cy="60" rx="10" ry="6" fill="#fff6d6" stroke="#b8860b"/>'
                 f'<circle cx="{cx}" cy="60" r="3.6" fill="#7a4cc2" stroke="none"/><circle cx="{cx+1.2}" cy="58.8" r="1.1" fill="#fff" stroke="none"/>')
    s.append('</g></svg>')
    return ''.join(s), stage

def ear(side, cy, flare, size, beads=None):
    """side -1 left / +1 right; flare 0..1 (how much it sticks out); size ~0.8..1.3"""
    h=46*size; w=(13+30*flare)*size
    ax=100+side*65           # attachment line x (inside head edge, head covers it)
    tilt=math.radians(side*(4+22*flare))
    def P(x,y):  # local: x outward, y down from attach centre
        X=x*math.cos(tilt)-y*math.sin(tilt); Y=x*math.sin(tilt)+y*math.cos(tilt)
        return ax+side*X, cy+Y
    pts_out=[(0,-h*0.5),(w*0.55,-h*0.62),(w*1.0,-h*0.38),(w*1.02,h*0.02),(w*0.78,h*0.36),(w*0.42,h*0.52),(0,h*0.45)]
    o=[P(*p) for p in pts_out]
    d='M%.1f %.1f C%.1f %.1f %.1f %.1f %.1f %.1f C%.1f %.1f %.1f %.1f %.1f %.1f Z'%(o[0]+o[1]+o[2]+o[3]+o[4]+o[5]+o[6]) 
    pi=[P(w*0.25,-h*0.30),P(w*0.70,-h*0.30),P(w*0.72,h*0.05),P(w*0.40,h*0.18)]
    inner='M%.1f %.1f Q%.1f %.1f %.1f %.1f Q%.1f %.1f %.1f %.1f'%(pi[0]+pi[1]+pi[2]+pi[2]+pi[3])
    s=[f'<path d="{d}" fill="#efd2b2"/>', f'<path d="{inner}" stroke-width="1.8"/>']
    if beads:
        # beads along the outer rim: list of colours
        n=len(beads)
        for i,c in enumerate(beads):
            t=0.15+0.7*(i/(max(n-1,1)))
            ang=-math.pi*0.45+math.pi*0.95*t
            x=w*0.5+w*0.62*math.cos(ang); y=h*0.45*math.sin(ang)-h*0.05
            X,Y=P(x,y)
            s.append(f'<circle cx="{X:.1f}" cy="{Y:.1f}" r="3.4" fill="{c}" stroke="#2a2420" stroke-width="0.9"/>')
    return ''.join(s)
TWIN_COLOR = '#8EFF51'   # близнецы — зелёный овал в логе (resultGreenColor)
NB_COLOR   = '#E6A440'   # соседи — жёлтый овал в логе (resultYellowColor натива)
RINGS_CAP  = 16          # серёг на оба уха; больше — прореживание с той же пропорцией

def ring_colors(tw, nb, cap=RINGS_CAP):
    """Цвета колец: одно кольцо — одна пара; сверх потолка — с той же пропорцией.
    Сначала близнецы, потом соседи: при раздаче на два уха по очереди в каждой
    серьге сверху зелёные, снизу жёлтые — так проще посчитать."""
    n = tw + nb
    if n > cap:
        tw = round(cap * tw / n); nb = cap - tw
    return [TWIN_COLOR] * tw + [NB_COLOR] * nb

EAR_CAP = 8   # колец на одно ухо

def ear_items(c, cap=EAR_CAP):
    """Кольца одного уха из счётчиков {(вид, зеркальный): n}: сначала близнецы
    (одинаковые, потом зеркальные), потом соседи. Больше cap — пропорционально."""
    order = [('tw', False), ('tw', True), ('nb', False), ('nb', True)]
    n = [c.get(k, 0) for k in order]; tot = sum(n)
    if tot > cap:                                   # прореживание с той же пропорцией
        raw = [cap * x / tot for x in n]; m = [int(x) for x in raw]
        for k in sorted(range(4), key=lambda k: raw[k] - m[k], reverse=True)[:cap - sum(m)]: m[k] += 1
        n = m
    out = []
    for (kind, mirror), k in zip(order, n):
        out += [(TWIN_COLOR if kind == 'tw' else NB_COLOR, mirror)] * k
    return out

def earrings(side, cy, flare, size, items, style='open'):
    """Серьга-цепочка из мочки вниз. Одинаковая пара — целое кольцо; зеркальная —
    style 'open': кольцо с разрывом в сторону лица (пришла с той стороны),
    style 'bead': сплошная бусина."""
    h=46*size; w=(13+30*flare)*size
    ax=100+side*65
    tilt=math.radians(side*(4+22*flare))
    lx,ly=w*0.40,h*0.50
    X0=ax+side*(lx*math.cos(tilt)-ly*math.sin(tilt)); Y0=cy+lx*math.sin(tilt)+ly*math.cos(tilt)
    r=5.0; step=8.6
    out=[]
    for k,(c,mirror) in enumerate(items):
        X,Y=X0, Y0+r*0.7+k*step
        if not mirror:
            out.append(f'<circle cx="{X:.1f}" cy="{Y:.1f}" r="{r}" fill="none" stroke="#2a2420" stroke-width="3.8"/>'
                       f'<circle cx="{X:.1f}" cy="{Y:.1f}" r="{r}" fill="none" stroke="{c}" stroke-width="2.4"/>')
        elif style=='bead':
            out.append(f'<circle cx="{X:.1f}" cy="{Y:.1f}" r="{r+0.9}" fill="{c}" stroke="#2a2420" stroke-width="1.3"/>')
        else:
            gap=math.radians(150); mid=0.0 if side<0 else math.pi    # разрыв смотрит на лицо
            a0=mid+gap/2; a1=mid+2*math.pi-gap/2
            x0,y0=X+r*math.cos(a0),Y+r*math.sin(a0); x1,y1=X+r*math.cos(a1),Y+r*math.sin(a1)
            d=f'M{x0:.2f} {y0:.2f} A{r} {r} 0 1 1 {x1:.2f} {y1:.2f}'
            out.append(f'<path d="{d}" fill="none" stroke="#2a2420" stroke-width="3.8" stroke-linecap="round"/>'
                       f'<path d="{d}" fill="none" stroke="{c}" stroke-width="2.4" stroke-linecap="round"/>')
    return ''.join(out)

def face_eared(sigma=28,B=0.0,L=1.0,third=False,gate=False,size=170,flare=0.5,esize=1.0,beads=None,pairs=None,sides=None,style='open'):
    svg,stage=_face_base(sigma,B,L,third,gate,size)
    # remove old glued ears (ellipses with fill #efd2b2 rx=9)
    import re
    svg=svg.replace('viewBox="0 0 200 220"','viewBox="-22 0 244 220"',1)
    svg=re.sub(r'<ellipse cx="[0-9.]+" cy="[0-9.]+" rx="9" ry="15" fill="#efd2b2"/>','',svg)
    Bc=clamp(B,-1,1); a=abs(Bc); left=Bc<0
    def drop(sl,k): return k*a if (sl==left and a>0) else (-0.25*k*a if a>0 else 0)
    ears=''.join(ear(-1 if sl else 1, 104+drop(sl,8), flare, esize, (beads or {}).get('L' if sl else 'R')) for sl in (True,False))
    # insert ears right after the filter group opens, before head ellipse
    svg=svg.replace('<ellipse cx="100" cy="112" rx="70" ry="88" fill="#f3dcc0"/>', ears+'<ellipse cx="100" cy="112" rx="70" ry="88" fill="#f3dcc0"/>',1)
    if pairs is not None and sides is None:   # старый вид: поровну на оба уха, без зеркальных
        cols=ring_colors(*pairs)
        sides={'L':{('tw',False):cols[0::2].count(TWIN_COLOR),('nb',False):cols[0::2].count(NB_COLOR)},
              'R':{('tw',False):cols[1::2].count(TWIN_COLOR),('nb',False):cols[1::2].count(NB_COLOR)}}
    if sides is not None:                     # левое ухо — ранние, правое — поздние (Алекс 07.11)
        rings=''.join(earrings(-1 if sl else 1, 104+drop(sl,8), flare, esize, ear_items(sides['L' if sl else 'R']), style) for sl in (True,False))
        svg=svg.replace('</g></svg>', rings+'</g></svg>',1)
    return svg,stage

# ---- страница 1: три характеристики ----
def face(**kw):
    kw.setdefault('flare',0.3); kw.setdefault('esize',1.1)
    return face_eared(**kw)
def cell(svg,cap,sub=''):
    return f'<div class="cell">{svg}<div class="cap">{cap}</div><div class="sub">{sub}</div></div>'
rows=[]
rows.append(('1. Точность → резкость', 'кучность: типичный разброс вокруг своего центра, мс',
  [cell(face(sigma=s)[0], f'{s} мс', d) for s,d in ((28,'предел человека'),(40,'хорошо, L3'),(60,'середина L2'),(100,'новичок L1'),(200,'туман'))]))
rows.append(('2. Баланс → перекос', 'B = сумма ошибок ÷ сумма |ошибок|; влево — спешишь, вправо — запаздываешь',
  [cell(face(B=b)[0], f'B = {b:+.1f}', d) for b,d in ((-0.6,'сильно спешит'),(-0.3,'спешит'),(0.0,'ровно'),(0.3,'запаздывает'),(0.6,'сильно запаздывает'))]))
rows.append(('3. Везение → красота', 'чистые тройки ÷ сколько их положено по кучности и балансу',
  [cell(face(L=l,third=t)[0], c, d) for l,t,c,d in ((0.7,False,'× 0.7','не везёт'),(1.0,False,'× 1.0','как положено'),(1.25,False,'× 1.25','везёт'),(1.5,False,'× 1.5','очень везёт'),(1.5,True,'шанс < 5%','третий глаз'))]))
rows.append(('Вместе: типичные дни из данных (порядок этапов включён)', 'везение показывается только на 3-м этапе',
  [cell(face(sigma=93,B=-0.39,L=0.95,gate=True)[0],'новичок, L1','σ 93 мс · B −0.39 · этап 1'),
   cell(face(sigma=42,B=-0.30,L=1.06,gate=True)[0],'L2, спешит','σ 42 мс · B −0.30 · этап 2'),
   cell(face(sigma=31,B=0.03,L=1.07,gate=True)[0],'L3, обычный день','σ 31 мс · B +0.03 · этап 3'),
   cell(face(sigma=30,B=0.02,L=1.45,gate=True)[0],'L3, удачный день','× 1.45 · этап 3'),
   cell(face(sigma=29,B=-0.05,L=1.6,third=True,gate=True)[0],'L3, редкая удача','шанс < 5% · третий глаз')]))
html=['''<!doctype html><meta charset=utf-8><style>
body{margin:0;background:#f4f3ef;color:#151515;font-family:Roboto,Arial,sans-serif;padding:22px;width:1000px}
h1{font-size:22px;margin:0 0 4px}.lead{color:#555;margin:0 0 16px;font-size:14px}
.row{background:#fff;border:1px solid #e2dfd6;border-radius:12px;padding:12px 16px 8px;margin-bottom:14px}
.row h2{font-size:17px;margin:0}.row .d{color:#666;font-size:13px;margin:2px 0 6px}
.cells{display:flex;justify-content:space-between}.cell{width:186px;text-align:center}
.cap{font-weight:600;font-size:14px;margin-top:-6px}.sub{color:#777;font-size:12px;min-height:16px}
</style><h1>Три характеристики игрока — в одном лице</h1>
<p class="lead">Сначала резкость (точность), потом прямота (баланс), потом красота (везение). Пороги — по пятёрке лучших игроков.</p>''']
for t,d,cells in rows:
    html.append(f'<div class="row"><h2>{t}</h2><div class="d">{d}</div><div class="cells">{"".join(cells)}</div></div>')
open(os.path.join(HERE,'face3_three_traits.html'),'w',encoding='utf-8').write(''.join(html))

# ---- страница 2: серьги — близнецы и соседи, ранние и поздние ----
EAR=dict(flare=0.3, esize=1.1)      # уши всегда одинаковые (Алекс 07.11)
def E(to=0,tm=0,no=0,nm=0):          # счётчики одного уха: близнецы одинак./зерк., соседи одинак./зерк.
    return {('tw',False):to,('tw',True):tm,('nb',False):no,('nb',True):nm}
def ecell(svg,cap,sub=''): return f'<div class="cell">{svg}<div class="cap">{cap}</div><div class="sub">{sub}</div></div>'
def desc(e):
    t=lambda k: e.get(k,0)
    parts=[]
    if t(('tw',False)) or t(('tw',True)): parts.append(f'<span class="g">●</span> {t(("tw",False))}+{t(("tw",True))}')
    if t(('nb',False)) or t(('nb',True)): parts.append(f'<span class="y">●</span> {t(("nb",False))}+{t(("nb",True))}')
    return ' '.join(parts) or '—'
def ears_lbl(L,R): return f'ранние {desc(L)} · поздние {desc(R)}'
EX_L3 = dict(L=E(4,1,1,2), R=E(3,1,1,1))
erows=[]
erows.append(('Левое ухо — ранние, правое — поздние', 'пара висит на ухе той стороны, куда попала вторая попытка; зелёное — близнец, жёлтое — сосед; целое кольцо — одинаковые (−0.05 и −0.05), кольцо с разрывом к лицу — зеркальные (−0.05 и +0.05)',
 [ecell(face_eared(sides=dict(L=E(1),R=E()),**EAR)[0],'одинаковый близнец','−0.05 → −0.05'),
  ecell(face_eared(sides=dict(L=E(),R=E(tm=1)),**EAR)[0],'зеркальный близнец','−0.05 → +0.05'),
  ecell(face_eared(sides=dict(L=E(),R=E(no=1)),**EAR)[0],'одинаковый сосед','+0.05 → +0.06'),
  ecell(face_eared(sides=dict(L=E(nm=1),R=E()),**EAR)[0],'зеркальный сосед','+0.05 → −0.06'),
  ecell(face_eared(sides=dict(L=E(2,0,0,1),R=E(1,1,1,1)),**EAR)[0],'L3, ~100 попыток',ears_lbl(E(2,0,0,1),E(1,1,1,1)))]))
erows.append(('Больше пар, перекос по сторонам', 'до 8 колец на ухо, дальше прореживание с той же пропорцией; подпись — одинаковые+зеркальные',
 [ecell(face_eared(sides=EX_L3,**EAR)[0],'L3, ~200 попыток',ears_lbl(EX_L3['L'],EX_L3['R'])),
  ecell(face_eared(sides=dict(L=E(5,4,9,8),R=E(5,4,7,8)),**EAR)[0],'L2, день лучших','по ~26 на ухо → 8'),
  ecell(face_eared(B=-0.4,sides=dict(L=E(4,1,2,1),R=E(1,1,0,1)),**EAR)[0],'спешит','ранних пар больше'),
  ecell(face_eared(B=0.4,sides=dict(L=E(1,1,0,1),R=E(4,1,2,1)),**EAR)[0],'запаздывает','поздних пар больше'),
  ecell(face_eared(sides=dict(L=E(0,3,0,3),R=E(0,2,0,3)),**EAR)[0],'одни зеркальные','перескакивает через 3')]))
erows.append(('Как ещё можно показать зеркальные', 'то же, что «L3, ~200 попыток»',
 [ecell(face_eared(sides=EX_L3,style='open',**EAR)[0],'А. разрыв к лицу','предлагаю'),
  ecell(face_eared(sides=EX_L3,style='bead',**EAR)[0],'Б. сплошная бусина','проще, но без смысла «оттуда»'),
  '<div class="cell"></div>','<div class="cell"></div>','<div class="cell"></div>']))
html=['''<!doctype html><meta charset=utf-8><style>
body{margin:0;background:#f4f3ef;color:#151515;font-family:Roboto,Arial,sans-serif;padding:22px;width:1000px}
h1{font-size:22px;margin:0 0 4px}.lead{color:#555;margin:0 0 16px;font-size:14px}
.row{background:#fff;border:1px solid #e2dfd6;border-radius:12px;padding:12px 16px 8px;margin-bottom:14px}
.row h2{font-size:17px;margin:0}.row .d{color:#666;font-size:13px;margin:2px 0 6px}
.cells{display:flex;justify-content:space-between}.cell{width:186px;text-align:center}
.cap{font-weight:600;font-size:13px;margin-top:-6px}.sub{color:#777;font-size:11.5px;min-height:16px}
.g{color:#3d9a1e}.y{color:#c98516}
</style><h1>Серьги: ранние слева, поздние справа</h1>
<p class="lead">Уши всегда одинаковые. Одно кольцо — одна пара повторов. Цвет — близнец или сосед, форма — одинаковые или зеркальные.</p>''']
for t,d,cells in erows: html.append(f'<div class="row"><h2>{t}</h2><div class="d">{d}</div><div class="cells">{"".join(cells)}</div></div>')
open(os.path.join(HERE,'face3_earrings.html'),'w',encoding='utf-8').write(''.join(html))
