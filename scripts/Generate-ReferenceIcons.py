"""Convert the approved HTML SVG geometry to native VectorDrawable resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
html=(ROOT/"reference/sbros_v15_full_list.html").read_text(encoding="utf-8")
entries=re.findall(r"(\w+):'([^']*)'",re.search(r"const paths=\{(.*?)\};",html,re.S).group(1))
out=ROOT/"app/src/main/res/drawable"

def geometry(node):
    a=node.attrib
    if node.tag=="path":
        # SVG accepts adjacent arc flags; Android's path parser reads 0014 as one float.
        return a["d"].replace("a7 7 0 0014 0","a7,7 0,0,0 14,0")
    if node.tag=="circle":
        x,y,r=(float(a[k]) for k in ("cx","cy","r"))
        return f"M{x-r},{y} A{r},{r} 0,1,0 {x+r},{y} A{r},{r} 0,1,0 {x-r},{y} Z"
    x,y,w,h=(float(a.get(k,0)) for k in ("x","y","width","height"))
    rx=min(float(a.get("rx",a.get("ry",0))),w/2)
    ry=min(float(a.get("ry",a.get("rx",0))),h/2)
    if not rx or not ry:return f"M{x},{y} H{x+w} V{y+h} H{x} Z"
    return (f"M{x+rx},{y} H{x+w-rx} A{rx},{ry} 0,0,1 {x+w},{y+ry} "
            f"V{y+h-ry} A{rx},{ry} 0,0,1 {x+w-rx},{y+h} H{x+rx} "
            f"A{rx},{ry} 0,0,1 {x},{y+h-ry} V{y+ry} A{rx},{ry} 0,0,1 {x+rx},{y} Z")

def write(name,nodes,stroke):
    paths="\n".join(f'<path android:pathData="{geometry(n)}" android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="{stroke}" android:strokeLineCap="round" android:strokeLineJoin="round"/>' for n in nodes)
    (out/f"ic_{name}.xml").write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'+paths+'\n</vector>\n',encoding="utf-8")

for name,svg in entries:
    nodes=list(ET.fromstring("<svg>"+svg+"</svg>"))
    write(name,nodes,1.85)
    if name in ("bolt","feather","mic","calendar","review"):write(name+"_nav",nodes,1.8)
    if name=="mic":write("mic_hero",nodes,1.55)
print(f"Converted {len(entries)} SVG icons; exact native arcs and contextual stroke widths")
