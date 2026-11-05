"""Render the player introduction as UTF-8 BBCode for forum source mode."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
def inline(value):
    value=re.sub(r'!\[([^]]*)\]\((docs/images/[^)]+)\)',
        lambda m:'[img]https://raw.githubusercontent.com/vdjango/lunar-dread/v0.12.0/'+m[2]+'[/img]',value)
    value=re.sub(r'\[([^]]+)\]\((https?://[^)]+)\)',r'[url=\2][color=#6862A1]\1[/color][/url]',value)
    value=re.sub(r'\*\*(.*?)\*\*',r'[b]\1[/b]',value)
    return re.sub(r'`([^`]+)`',r'[font=Consolas]\1[/font]',value)

lines=(ROOT/'README-MCBBS.md').read_text(encoding='utf-8').splitlines()
output=['[font=\u5fae\u8f6f\u96c5\u9ed1][size=3]'];i=0;center=False;listing=False
while i<len(lines):
    line=lines[i]
    if line.startswith('|'):
        if listing:output.append('[/list]');listing=False
        output.append('[table=98%]');row=0
        while i<len(lines) and lines[i].startswith('|'):
            cells=[s.strip() for s in lines[i].strip('|').split('|')]
            if not all(re.fullmatch(r'[: -]+',s) for s in cells):
                output.append('[tr]'+''.join('[td]'+('[color=#A5783F][b]'+inline(s)+'[/b][/color]' if row==0 else inline(s))+'[/td]' for s in cells)+'[/tr]')
                row+=1
            i+=1
        output.append('[/table]');continue
    item=re.match(r'^(?:\d+\.|-) (.*)$',line)
    if item:
        if not listing:output.append('[list=1]' if re.match(r'\d',line) else '[list]');listing=True
        output.append('[*]'+inline(item[1]));i+=1;continue
    if listing:output.append('[/list]');listing=False
    if line.startswith('# '):
        output.append('[align=center][size=5][color=#923E51][b]'+inline(line[2:])+'[/b][/color][/size]');center=True
    elif line.startswith('## '):
        if center:output.append('[/align]');center=False
        output.append('\n[hr][size=4][color=#923E51][b]'+inline(line[3:])+'[/b][/color][/size]\n')
    elif line.startswith('### '):output.append('[color=#A5783F][b]'+inline(line[4:])+'[/b][/color]')
    elif line.startswith('Minecraft '):output.append('[color=#777777]'+inline(line)+'[/color]')
    else:output.append(inline(line))
    i+=1
if listing:output.append('[/list]')
if center:output.append('[/align]')
output.append('[/size][/font]')
text='\n'.join(output)+'\n'
assert '\u6708\u9b47' in text and '\ufffd' not in text and '?' not in text
stack=[]
for closing,tag in re.findall(r'\[(/?)(align|size|font|color|b|url|img|table|tr|td|list)(?:=[^]]*)?\]',text):
    if closing:assert stack.pop()==tag,tag
    else:stack.append(tag)
assert not stack,stack
(ROOT/'README-MCBBS.txt').write_text(text,encoding='utf-8-sig')
print('Updated README-MCBBS.txt (UTF-8 with BOM, balanced BBCode)')
