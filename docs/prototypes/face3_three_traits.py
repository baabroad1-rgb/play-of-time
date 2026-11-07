# -*- coding: utf-8 -*-
"""Макет «три характеристики в одном лице» (Алекс 07.11.2026).

Запуск: python3 face3_three_traits.py  ->  рядом face3_three_traits.html.
Формулы и пороги — docs/FACE_V3_THREE_TRAITS.md. Это эскиз для обсуждения,
не реализация: в report.html ничего не менялось."""
import math, itertools
_id=itertools.count()
def clamp(x,a,b): return max(a,min(b,x))
def face(sigma=28, B=0.0, L=1.0, third=False, gate=False, size=170):
    """sigma ms (кучность), B in [-1,1] (баланс, - = рано), L luck ratio."""
    i=next(_id)
    t=clamp(math.log(max(sigma,1)/28)/math.log(200/28),0,1)        # 0 резко .. 1 туман
    blur=6.5*t
    stage = 1 if sigma>50 else (2 if abs(B)>0.2 else 3)
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
   cell(face(sigma=48,B=-0.30,L=1.06,gate=True)[0],'L2, спешит','σ 48 мс · B −0.30 · этап 2'),
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
<p class="lead">Сначала резкость (точность), потом прямота (баланс), потом красота (везение). Числа — реальные, из 40 тыс. попыток.</p>''']
for t,d,cells in rows:
    html.append(f'<div class="row"><h2>{t}</h2><div class="d">{d}</div><div class="cells">{"".join(cells)}</div></div>')
open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'face3_three_traits.html'),'w',encoding='utf-8').write(''.join(html))
