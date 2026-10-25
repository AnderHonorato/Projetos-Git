"""Arte original em pixels; Pillow, sem interpolação ou imagens externas."""
from pathlib import Path
from PIL import Image,ImageDraw
import math
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/stitch';DOC=ROOT/'docs'
STATES=[('idle',6),('andar',8),('correr',8),('pular',5),('acenar',6),('apontar',4),('sentar',4),('dormir',4),('celebrar',6),('duvida',4),('toque',4)]
S=80
C={'edge':'#173d65','blue':'#478dc7','shade':'#3069a8','light':'#75bfe0','belly':'#a1d6e8','ear':'#a58bb8','pink':'#d0aacb','black':'#12243c','white':'#f9f5e9','nose':'#203958'}
def sprite(state,frame,total):
 im=Image.new('RGBA',(S,S));d=ImageDraw.Draw(im);p=2*math.pi*frame/total
 walk=state in ('andar','correr')
 lift=round(math.sin(p)*2) if walk else ([-1,-3,-6,-3,-1][frame] if state=='pular' else 0)
 if state=='celebrar':lift=round(-abs(math.sin(p))*4)
 breathe=1 if frame in (1,2) and state=='idle' else 0
 seated=state in ('sentar','dormir');base=4 if seated else 0;hy=lift+base-breathe
 def ell(box,col,outline=None,w=1):d.ellipse(box,fill=C.get(col,col),outline=C.get(outline,outline),width=w)
 def poly(points,col,outline=None):d.polygon(points,fill=C.get(col,col),outline=C.get(outline,outline))
 def line(points,col,w=1):d.line(points,fill=C.get(col,col),width=w)
 ell((24+(2 if state=='pular' else 0),74,57-(2 if state=='pular' else 0),77),'#24415b28')
 ell((49,56+lift,61,65+lift),'shade','edge');ell((51,55+lift,59,61+lift),'blue')
 ell((28-breathe,43+hy,53+breathe,68+lift),'blue','edge',2);ell((33,49+hy,48,65+lift),'belly')
 stride=round(math.sin(p)*(5 if state=='correr' else 3)) if walk else 0
 for side,x in [(-1,29),(1,46)]:
  fx=x+side*stride;fy=65+lift+(round(math.cos(p)*3)*side if walk else 0)
  if seated:fx=x+side*4;fy=66+lift
  if state=='pular':fx=x-side*2;fy=64+lift
  ell((fx-3,fy,fx+10,fy+9),'shade','edge');ell((fx-2,fy,fx+8,fy+5),'blue')
  for n in (0,4,8):line([(fx-1+n,fy+7),(fx+n,fy+9)],'black')
 for side in (-1,1):
  shoulder=(29 if side==-1 else 52,49+hy);x=24 if side==-1 else 57;y=59+hy
  if walk:x+=side*round(math.cos(p)*3);y-=side*round(math.sin(p)*5)
  if state in ('acenar','celebrar') and (side==1 or state=='celebrar'):
   x=60+round(math.sin(p)*4) if side==1 else 19-round(math.sin(p)*4);y=10+hy+round(math.cos(p)*3)
  if state=='apontar' and side==1:x=69;y=46+hy+(frame%2)
  if state=='duvida' and side==1:x=59-frame%2;y=34+hy
  if state=='toque':x+=side*(3 if frame%2 else 0);y-=frame%2*8
  if state=='dormir':x=35 if side==-1 else 47;y=58+hy
  line([shoulder,(x,y)],'edge',9);line([shoulder,(x,y)],'blue',6);ell((x-4,y-4,x+4,y+4),'blue','edge')
  for n in (-2,1,3):line([(x+n,y-2),(x+n,y-4)],'black')
  if state=='apontar' and side==1:line([(x,y-1),(x+5,y-2)],'edge',3);line([(x,y-1),(x+4,y-2)],'blue')
 ear=round(math.sin(p)*2)
 if state=='dormir':ear=-4+frame%2
 if state=='duvida':ear=frame%3
 left=[(28,27+hy),(15,17+hy-ear),(3,14+hy-ear),(6,29+hy),(12,36+hy),(18,38+hy),(25,35+hy)]
 right=[(52,27+hy),(65,17+hy+ear),(77,14+hy+ear),(74,29+hy),(68,36+hy),(62,38+hy),(55,35+hy)]
 poly(left,'blue','edge');poly(right,'blue','edge')
 poly([(24,29+hy),(14,21+hy-ear),(7,19+hy-ear),(10,29+hy),(17,34+hy),(22,34+hy)],'ear')
 poly([(56,29+hy),(66,21+hy+ear),(73,19+hy+ear),(70,29+hy),(63,34+hy),(58,34+hy)],'ear')
 line([(8,19+hy-ear),(13,28+hy),(18,31+hy)],'pink',2);line([(72,19+hy+ear),(67,28+hy),(62,31+hy)],'pink',2)
 poly([(6,27+hy),(10,28+hy),(7,31+hy)],'#00000000');poly([(74,27+hy),(70,28+hy),(73,31+hy)],'#00000000')
 ell((20,20+hy,60,51+hy),'blue','edge',2)
 poly([(23,38+hy),(19,41+hy),(23,42+hy),(20,44+hy),(26,47+hy),(31,46+hy)],'blue','edge')
 poly([(57,38+hy),(61,41+hy),(57,42+hy),(60,44+hy),(54,47+hy),(49,46+hy)],'blue','edge')
 poly([(32,23+hy),(31,19+hy),(36,21+hy),(37,16+hy),(41,21+hy),(45,18+hy),(46,24+hy)],'blue','edge')
 ell((24,28+hy,37,43+hy),'light');ell((43,28+hy,56,43+hy),'light')
 blink=(state=='idle' and frame in (3,4)) or (state in ('sentar','apontar') and frame==2) or (state=='toque' and frame==1);sleep=state=='dormir'
 for x in (27,46):
  if blink or sleep:line([(x,36+hy),(x+2,38+hy),(x+6,36+hy)],'black',2)
  elif state=='celebrar':line([(x,36+hy),(x+3,32+hy),(x+6,36+hy)],'black',2)
  else:
   ey=30+hy+(1 if (state=='duvida' and x==46) or (state=='idle' and frame==2) else 0)
   ell((x,ey,x+7,ey+11),'black');ell((x+1,ey+1,x+2,ey+3),'white')
   if state=='duvida' and x==46:line([(x-1,ey-3),(x+7,ey-4)],'edge',2)
 ell((32,37+hy,49,48+hy),'belly');ell((34,36+hy,47,43+hy),'nose','edge');line([(37,37+hy),(43,37+hy)],'shade');line([(40,43+hy),(40,45+hy)],'edge')
 if state in ('celebrar','toque') and frame%2==0:ell((36,44+hy,45,49+hy),'black');line([(39,48+hy),(43,48+hy)],'pink',2)
 elif state=='duvida':line([(37,46+hy),(43,45+hy)],'edge')
 elif sleep:ell((39,44+hy,42,46+hy),'edge')
 else:line([(34,44+hy),(37,46+hy),(43,46+hy),(47,44+hy)],'edge')
 if state=='toque' and frame in (1,2):line([(24,42+hy),(27,43+hy)],'pink',2);line([(53,43+hy),(56,42+hy)],'pink',2)
 if sleep:
  sy=18-frame*2;line([(61,sy),(67,sy),(61,sy+6),(67,sy+6)],'shade')
 return im

def main():
 OUT.mkdir(parents=True,exist_ok=True);DOC.mkdir(exist_ok=True)
 atlas=Image.new('RGBA',(8*S,len(STATES)*S))
 for row,(name,count) in enumerate(STATES):
  seen=set()
  for f in range(count):
   im=sprite(name,f,count);assert im.tobytes() not in seen,(name,f,'duplicado');seen.add(im.tobytes());atlas.alpha_composite(im,(f*S,row*S))
 atlas.save(OUT/'stitch_atlas.png')
 sheet=Image.new('RGB',(8*S*2+150,len(STATES)*S*2),(247,241,232));dr=ImageDraw.Draw(sheet)
 for row,(name,count) in enumerate(STATES):
  dr.text((10,row*S*2+64),f'{name} / {count}',fill='#6a2939')
  for f in range(count):
   im=atlas.crop((f*S,row*S,(f+1)*S,(row+1)*S)).resize((S*2,S*2),Image.Resampling.NEAREST);sheet.paste(im,(150+f*S*2,row*S*2),im)
 sheet.save(DOC/'stitch_sprite_sheet.png');sprite('idle',0,6).resize((480,480),Image.Resampling.NEAREST).save(DOC/'stitch_preview.png')
 print(f'{sum(n for _,n in STATES)} quadros únicos; atlas {atlas.size}')
if __name__=='__main__':main()
