from pathlib import Path
import re, json
root=Path(__file__).resolve().parents[2]
source=(root/"android/source/app/src/main/java/dev/mietze/piimager/workbench/WorkbenchCatalog.kt").read_text(encoding="utf-8")
catalog={}
for m in re.finditer(r'add\(WbKind\("([^"]+)","([^"]+)","([^"]+)"',source):
    id,title,category=m.groups()
    catalog[id]={"id":id,"title":title,"category":category}
for m in re.finditer(r'listOf\((.*?)\)\.forEach\{\(id,title\)->add\(WbKind\(id,title,"([^"]+)"',source,re.S):
    entries,category=m.groups()
    for id,title in re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"',entries):
        catalog[id]={"id":id,"title":title,"category":category}
order=["Operating systems","Boot","Storage","Drivers","Network","Runtimes","Minecraft","Services","Notes"]
items=sorted(catalog.values(),key=lambda v:(order.index(v["category"]) if v["category"] in order else 999,v["title"]))
assert len(items)>40,len(items)
out=Path(__file__).resolve().parents[1]/"src/catalog.json"
out.write_text(json.dumps(items,ensure_ascii=False,indent=2),encoding="utf-8")
print("CATEGORIES",len(items))
from collections import Counter
print(Counter(x["category"] for x in items))
