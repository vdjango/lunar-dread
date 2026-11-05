"""Add consistent captions to real footage, encode GIFs and package forum assets."""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg

MOD = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--recording', type=Path, default=MOD/'test-build/moon-gallery-latest.json')
options = parser.parse_args()
recording = json.loads(options.recording.read_text(encoding='utf-8'))
source = Path(recording['instance'])/'screenshots'
destination = MOD/'docs/images/moons'
destination.mkdir(parents=True,exist_ok=True)
moons = [
    ('normal','普通月','#C5CCD9'),('harvest','丰收月','#E3B960'),('blue','蓝月','#79B6FF'),
    ('wind','旅风月','#8FE0DC'),('green','翠息月','#7BCB90'),('star','星砂月','#B8D5FF'),
    ('blood','血月','#F26B7E'),('purple','紫狱月','#C09AFF'),('pale','苍白月','#DCE4CB'),
    ('mirror','黑镜月','#9CAFC7'),('gravity','倒悬月','#A59CFF'),('amber','琥珀月','#FFBE60'),
    ('tide','青潮月','#6FD0CE'),('rust','锈雨月','#D68C64'),('paper','纸幕月','#E2ADC7'),
    ('ash','灰烬月','#F19C69'),('giant','血月 · 巨月形态','#F26B7E'),
]
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',26)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',13)
tiny = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',15)
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
thumbnails = []
files = []
for number,(phase,name,color) in enumerate(moons,1):
    if phase not in recording['phases']:continue
    filename = f'{number:02d}-{phase}.gif'
    frames = sorted(source.glob(phase+'_[0-9][0-9][0-9].png'))
    assert len(frames)==recording['frames'],(phase,len(frames))
    with tempfile.TemporaryDirectory(prefix='moon-gif-',dir=MOD/'test-build') as temporary:
        folder = Path(temporary)
        for index,path in enumerate(frames):
            frame = Image.new('RGB',(960,600),'#101016')
            draw = ImageDraw.Draw(frame)
            draw.rectangle((0,0,5,59),fill=color)
            draw.text((24,9),name,font=font,fill=color)
            draw.text((25,42),'月魇 · Lunar Dread  |  月色实机预览',font=small,fill='#A3A3B3')
            draw.text((930,15),f'{number:02d} / 16' if number<=16 else 'BONUS',anchor='ra',font=tiny,fill='#CBCAD7')
            draw.text((930,39),'0.12.0  ·  MC 1.21.1 / NeoForge',anchor='ra',font=small,fill='#888899')
            with Image.open(path) as gameplay:
                frame.paste(gameplay.convert('RGB').resize((960,540),Image.Resampling.LANCZOS),(0,60))
            frame.save(folder/f'{index:03d}.png')
            if index==len(frames)//2:
                thumbnails.append(frame.copy())
        result = subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-framerate','10','-i',str(folder/'%03d.png'),
            '-filter_complex','[0:v]split[a][b];[a]palettegen=max_colors=192:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle',
            '-loop','0',str(destination/filename)],capture_output=True,text=True)
        if result.returncode:raise RuntimeError(result.stderr)
    with Image.open(destination/filename) as gif:
        assert gif.n_frames>=30 and gif.info.get('loop')==0,(filename,gif.n_frames)
        assert sum(gif.seek(i) or gif.info.get('duration',0) for i in range(gif.n_frames))>=5500
    files.append((filename,name))
    print(filename,round((destination/filename).stat().st_size/1024),'KiB',flush=True)

# A lightweight, static overview makes the full collection easy to choose from.
sheet = Image.new('RGB',(1280,880),'#101016')
draw = ImageDraw.Draw(sheet)
draw.text((20,12),'月魇 · 十六月相',font=font,fill='#EEE8EF')
draw.text((20,49),'0.12.0 实机录制  /  每种月相一张 GIF  /  另附巨月形态',font=tiny,fill='#ABA4B5')
for index,thumbnail in enumerate(thumbnails[:16]):
    sheet.paste(thumbnail.resize((304,190),Image.Resampling.LANCZOS),(20+(index%4)*312,88+(index//4)*198))
sheet.save(destination/'overview.jpg',quality=92)

rows = ['# 月魇 · 十六月相 GIF','',
    'Minecraft 1.21.1 / NeoForge，月魇 0.12.0 独立实例实机录制。每张 960 × 600、约 6 秒、循环播放、无声。',
    '展示月面颜色与夜空，镜头有轻微移动；不代表该月相的全部事件。普通月沿用游戏当前的原版月面阶段。',
    '', '另附血月的巨月形态，共 17 张。可直接用于模组介绍、论坛帖子和宣传。','',
    '![十六月相总览](overview.jpg)','','| 月相 | 月相 |','| --- | --- |']
for index in range(0,16,2):
    cells = [f'**{files[i][1]}**<br>![{files[i][1]}]({files[i][0]})' for i in (index,index+1)]
    rows.append('| '+' | '.join(cells)+' |')
rows += ['', '## 巨月形态','',f'![血月的巨月形态]({files[16][0]})','',
    '## 文件对照','', '| 文件 | 月相 |','| --- | --- |']
rows += [f'| `{filename}` | {name} |' for filename,name in files]
(destination/'README.md').write_text('\n'.join(rows)+'\n',encoding='utf-8')
archive_path = MOD/'dist/lunar-dread-0.12.0-moon-gifs.zip'
with zipfile.ZipFile(archive_path,'w',zipfile.ZIP_DEFLATED) as archive:
    for filename,_ in files:archive.write(destination/filename,filename)
    for filename in ('README.md','overview.jpg'):archive.write(destination/filename,filename)
print('PACKAGE '+str(archive_path),flush=True)
