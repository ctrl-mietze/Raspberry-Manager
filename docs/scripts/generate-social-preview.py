"""Generate a social-preview image from real Raspberry Manager screenshots.
Requires Pillow for this optional developer asset-generation step.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

root=Path(__file__).resolve().parents[2]
assets=root/"docs/assets"
W,H=1280,640
im=Image.new("RGB",(W,H),(10,9,15))
pixels=im.load()
for y in range(H):
    for x in range(W):
        dx=(x-965)/540
        dy=(y-320)/400
        pink=max(0,1-math.sqrt(dx*dx+dy*dy))
        pixels[x,y]=(min(255,int(13+38*pink)),int(10+9*pink),int(19+24*pink))
d=ImageDraw.Draw(im)
for gx in range(590,W,29):
    for gy in range(75,H,29):
        d.ellipse((gx,gy,gx+1,gy+1),fill=(75,32,56))
# rounded brand strip
d.rounded_rectangle((44,50,594,575),radius=25,fill=(24,16,28),outline=(92,41,66),width=2)
logo=Image.open(assets/"logo.png").convert("RGBA").resize((84,84),Image.Resampling.LANCZOS)
im.paste(logo,(85,92),logo)
fontCandidates=[Path(r"C:\Windows\Fonts\segoeuib.ttf"),Path(r"C:\Windows\Fonts\arialbd.ttf")]
regularCandidates=[Path(r"C:\Windows\Fonts\segoeui.ttf"),Path(r"C:\Windows\Fonts\arial.ttf")]
bold=next((p for p in fontCandidates if p.exists()),None)
regular=next((p for p in regularCandidates if p.exists()),None)
def font(size,weight=False):
    path=bold if weight else regular
    return ImageFont.truetype(str(path),size) if path else ImageFont.load_default()
d.text((187,102),"Raspberry Manager",fill=(250,222,235),font=font(30,True))
d.text((188,150),"MEGA MANAGER X  ·  VX",fill=(244,141,182),font=font(14,True))
d.text((84,220),"Build. Flash.",fill=(251,234,242),font=font(51,True))
d.text((84,282),"Connect. Manage.",fill=(238,83,140),font=font(44,True))
d.text((85,370),"Your Raspberry Pi workspace",fill=(217,194,209),font=font(24))
d.text((85,405),"for Android and Windows.",fill=(217,194,209),font=font(24))
d.rounded_rectangle((81,471,296,518),radius=14,fill=(234,63,123))
d.text((115,484),"WINDOWS 10 / 11",fill=(23,9,17),font=font(15,True))
d.rounded_rectangle((313,471,493,518),radius=14,fill=(58,35,57),outline=(128,70,103),width=2)
d.text((358,484),"ANDROID 9+",fill=(249,216,230),font=font(15,True))
# screenshot frame with soft glow and border
shot=Image.open(assets/"windows-workbench.png").convert("RGB")
# crop important canvas center
shot=shot.crop((230,55,shot.width-5,shot.height-20))
shot.thumbnail((627,425),Image.Resampling.LANCZOS)
gx=610;gy=134
glow=Image.new("RGBA",(W,H),(0,0,0,0))
gd=ImageDraw.Draw(glow)
gd.rounded_rectangle((gx-26,gy-30,gx+638,gy+445),radius=34,fill=(203,37,100,105))
glow=glow.filter(ImageFilter.GaussianBlur(38))
im=Image.alpha_composite(im.convert("RGBA"),glow)
d=ImageDraw.Draw(im)
d.rounded_rectangle((gx-9,gy-32,gx+636,gy+442),radius=22,fill=(26,14,28),outline=(151,60,97),width=3)
d.rounded_rectangle((gx+1,gy-23,gx+629,gy+5),radius=10,fill=(61,23,46))
d.text((gx+18,gy-17),"RASPBERRY MANAGER  /  PI MAGIC WORKBENCH",fill=(242,161,195),font=font(13,True))
# image crop constrained inside bezel
frame=shot.resize((627,413),Image.Resampling.LANCZOS)
im.paste(frame,(gx+1,gy+7))
d=ImageDraw.Draw(im)
d.text((620,585),"REAL WINDOWS APP SCREENSHOT  ·  V10.3.0",fill=(174,132,155),font=font(13,True))
im.convert("RGB").save(assets/"social-preview.png",optimize=True)
print("Created",assets/"social-preview.png")
