from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
import math,shutil
root=Path(__file__).resolve().parents[2]
assets=root/"windows/assets";assets.mkdir(exist_ok=True)
web=root/"docs/assets";web.mkdir(parents=True,exist_ok=True)
S=1024
img=Image.new("RGBA",(S,S),(0,0,0,0))
d=ImageDraw.Draw(img)
for y in range(65,960):
    mix=(y-65)/900
    r=int(244-35*mix);g=int(52-37*mix);b=int(123-18*mix)
    d.line((70,y,954,y),fill=(r,g,b,255),width=2)
mask=Image.new("L",(S,S),0);ImageDraw.Draw(mask).rounded_rectangle((65,65,959,959),radius=245,fill=255)
img.putalpha(mask)
d=ImageDraw.Draw(img)
# soft tonal shading around the raspberry cluster
d.ellipse((215,180,818,870),fill=(135,12,65,32))
# leaf sprays
for points in [
  [(503,308),(372,206),(285,204),(305,252),(411,311),(507,334)],
  [(520,304),(601,197),(723,205),(695,268),(590,321),(524,340)],
  [(515,310),(489,242),(527,190),(559,227),(547,284)]
]:
  d.polygon(points,fill=(135,201,110,255))
d.line([(410,243),(506,327),(607,244)],fill=(75,147,78,255),width=15,joint="curve")
berries=[(395,365,112),(555,364,109),(324,492,108),(474,478,119),(632,491,104),
         (392,612,117),(552,616,114),(477,735,105)]
for x,y,r in berries:
    d.ellipse((x-r//2+7,y-r//2+13,x+r//2+7,y+r//2+13),fill=(128,7,52,150))
    d.ellipse((x-r//2,y-r//2,x+r//2,y+r//2),fill=(157,14,63,255),outline=(247,93,138,200),width=5)
    d.ellipse((x-r//2+18,y-r//2+16,x-r//2+51,y-r//2+45),fill=(246,111,156,170))
img.resize((512,512),Image.Resampling.LANCZOS).save(assets/"app.png")
img.save(assets/"app.ico",sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)],format="ICO")
img.resize((256,256),Image.Resampling.LANCZOS).save(web/"logo.png")
board='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 610" role="img" aria-label="Stylized Raspberry Pi hardware illustration">
<defs>
 <linearGradient id="pcb" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#245e4c"/><stop offset=".5" stop-color="#194c41"/><stop offset="1" stop-color="#0a2c29"/></linearGradient>
 <linearGradient id="metal" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#e3e6df"/><stop offset=".5" stop-color="#929ca3"/><stop offset="1" stop-color="#464a57"/></linearGradient>
 <linearGradient id="chip" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#2d3338"/><stop offset="1" stop-color="#0a0e13"/></linearGradient>
 <filter id="shadow" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="24"/></filter>
 <pattern id="dots" width="34" height="34" patternUnits="userSpaceOnUse"><circle cx="17" cy="17" r="1.2" fill="#e84a84" opacity=".2"/></pattern>
</defs>
<rect width="760" height="610" fill="transparent"/>
<ellipse cx="397" cy="482" rx="260" ry="60" fill="#e9457a" opacity=".17" filter="url(#shadow)"/>
<g transform="translate(112 112) rotate(-10 290 190)">
<rect x="12" y="20" width="545" height="350" rx="21" fill="#030609" opacity=".47" filter="url(#shadow)"/>
<rect x="0" y="0" width="555" height="353" rx="17" fill="url(#pcb)" stroke="#54b28b" stroke-width="6"/>
<g stroke="#5bb58e" opacity=".33" stroke-width="2" fill="none">
 <path d="M50 60 H235 V109 H333"/><path d="M66 286 H198 V255 H289"/><path d="M480 276 H370 V238 H292"/>
 <path d="M181 26 V125 H120 V194 H40"/><path d="M510 128 H454 V195 H337"/><path d="M500 78 H370 V142"/>
 <path d="M40 250 H140 V320 H380"/><path d="M180 340 V290 H258"/><path d="M300 12 V72 H420"/>
</g>
<g fill="#d6aa61" stroke="#dbcf96" stroke-width="5"><circle cx="29" cy="26" r="15"/><circle cx="526" cy="26" r="15"/><circle cx="29" cy="326" r="15"/><circle cx="526" cy="326" r="15"/></g>
<g fill="#0e3530"><circle cx="29" cy="26" r="9"/><circle cx="526" cy="26" r="9"/><circle cx="29" cy="326" r="9"/><circle cx="526" cy="326" r="9"/></g>
<rect x="166" y="111" width="143" height="129" rx="8" fill="url(#chip)" stroke="#8d9496" stroke-width="2"/>
<rect x="197" y="142" width="82" height="65" rx="3" fill="#20272b" stroke="#586067"/>
<text x="239" y="173" font-size="11" text-anchor="middle" fill="#d5dada" font-family="Arial" font-weight="bold">ARM</text>
<text x="239" y="190" font-size="10" text-anchor="middle" fill="#b2bbb9" font-family="Arial">Cortex</text>
<rect x="331" y="94" width="96" height="81" rx="6" fill="url(#chip)" stroke="#727e7b"/><text x="380" y="135" text-anchor="middle" fill="#c6cec7" font-size="19" font-weight="bold">RP1</text>
<rect x="334" y="196" width="106" height="89" rx="5" fill="url(#chip)" stroke="#727e7b"/><text x="385" y="245" text-anchor="middle" fill="#9baea3" font-size="12">LPDDR</text>
<g transform="translate(47 56)"><rect width="91" height="99" rx="8" fill="url(#metal)" stroke="#737b7c" stroke-width="3"/><rect x="16" y="15" width="55" height="52" fill="#182a30" stroke="#53646a" stroke-width="4"/><path d="M16 79 H70" stroke="#333a42" stroke-width="6"/></g>
<g transform="translate(469 71)"><rect width="93" height="98" rx="8" fill="url(#metal)" stroke="#929d9c" stroke-width="3"/><rect x="18" y="14" width="55" height="45" fill="#151d26"/><rect x="18" y="69" width="55" height="20" fill="#162027"/></g>
<g transform="translate(472 198)"><rect width="90" height="55" rx="4" fill="url(#metal)"/><rect x="13" y="11" width="65" height="33" fill="#11191f"/></g>
<g transform="translate(470 263)"><rect width="93" height="59" rx="4" fill="url(#metal)"/><rect x="10" y="8" width="72" height="43" fill="#121b20"/></g>
<g fill="#d0a767">'''
for i in range(24):
    x=70+i*15
    board+=f'<rect x="{x}" y="6" width="6" height="22" rx="2"/><rect x="{x}" y="324" width="6" height="20" rx="2"/>'
board+='''</g>
<text x="164" y="77" fill="#e4e7d5" font-family="Arial" font-size="28" font-weight="bold">Raspberry Pi 5</text>
<text x="165" y="95" fill="#aed6c0" font-family="Arial" font-size="11" letter-spacing="2">ILLUSTRATED HARDWARE</text>
<g fill="#aab69c" opacity=".65"><circle cx="120" cy="264" r="11"/><circle cx="145" cy="264" r="11"/><rect x="292" y="291" width="90" height="17" rx="5"/></g>
</g></svg>'''
(web/"pi5-illustration.svg").write_text(board,encoding="utf-8")
print("Generated",assets/"app.ico",assets/"app.png",web/"logo.png",web/"pi5-illustration.svg")
