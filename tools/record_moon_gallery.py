"""Record real Minecraft moon footage in an isolated, disposable mod instance.

Requires the local build classpath and an installed NeoForge 1.21.1 instance.
Never opens or edits a player's world. Raw screenshots stay in test-build/.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile
import time
import zipfile

import minecraft_launcher_lib.command

MOD = Path(__file__).resolve().parents[1]
ROOT = MOD.parent
parser = argparse.ArgumentParser()
parser.add_argument('--phases', default='normal,harvest,blue,wind,green,star,blood,purple,pale,mirror,gravity,amber,tide,rust,paper,ash,giant')
parser.add_argument('--minecraft', type=Path, default=Path('E:/bugg/.minecraft'))
parser.add_argument('--height', type=int, default=108)
parser.add_argument('--clock', type=int, default=13800)
parser.add_argument('--fov', type=int, default=48)
parser.add_argument('--pitch', type=float, default=9)
parser.add_argument('--frames', type=int, default=60)
options = parser.parse_args()
phases = options.phases.split(',')
jdk = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Microsoft/jdk-21.0.8.9-hotspot'))
game = Path(tempfile.mkdtemp(prefix='moon-gallery-', dir=MOD/'test-build'))
artifact = MOD/'dist/lunar-dread-0.12.0.jar'
classes = game/'probe-classes'
classes.mkdir()
args = [json.loads(s) for s in (MOD/'build/javac.args').read_text(encoding='utf-8').splitlines()]
args = args[:args.index('-d')]+['-d',str(classes)]
args[args.index('-classpath')+1] += os.pathsep+str(artifact)
args += [str(p) for p in (MOD/'test/standalone').rglob('*.java')]
argfile = game/'probe.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
subprocess.run([str(jdk/'bin/javac.exe'),'@'+str(argfile)],check=True)
(game/'mods').mkdir()
shutil.copy2(artifact,game/'mods'/artifact.name)
with zipfile.ZipFile(game/'mods/gallery-probe.jar','w',zipfile.ZIP_DEFLATED) as archive:
    for path in classes.rglob('*.class'):
        archive.write(path,path.relative_to(classes).as_posix())
    archive.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="lunar_standalone_validation"\nversion="0.1.0"\ndisplayName="Moon gallery camera"\n')
fixture = Path(json.loads((ROOT/'research/standalone-lunar-result.json').read_text(encoding='utf-8'))['instance'])
shutil.copytree(fixture/'standalone-world',game/'saves/standalone-world',ignore=shutil.ignore_patterns('session.lock'))
(game/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\nrenderDistance:8\nsimulationDistance:3\nmaxFps:60\nenableVsync:false\nlang:zh_cn\nclouds:true\ngamma:0.65\n',encoding='utf-8')
control,visual,log = (game/name for name in ('control.json','visual.json','client.log'))
control.write_text('{"id":"","commands":[]}',encoding='utf-8')
visual.write_text('{"label":""}',encoding='utf-8')
version = next(p.name for p in (options.minecraft/'versions').iterdir() if p.name.endswith('-0.14.7-preview'))
opts = {'username':'MoonGallery','uuid':'18c6f57b531b3d24ac2e5f8851d37056','token':'0','gameDirectory':str(game),
    'executablePath':str(jdk/'bin/java.exe'),'customResolution':True,'resolutionWidth':'1152','resolutionHeight':'648',
    'jvmArguments':['-Xms512M','-Xmx3G','-Dfile.encoding=UTF-8','-Dlunar_dread.standalone.world=standalone-world',
        '-Dlunar_dread.standalone.control='+str(control),'-Dlunar_dread.standalone.visual='+str(visual),
        '-Dlunar_dread.standalone.output='+str(game)],'launcherName':'lunar-gallery'}
command = minecraft_launcher_lib.command.get_minecraft_command(version,str(options.minecraft),opts)
meta = json.loads((options.minecraft/'versions'/version/(version+'.json')).read_text(encoding='utf-8'))
command = [s.replace(meta['id'],version) for s in command]
result = {'instance':str(game),'phases':[], 'frames':options.frames, 'fps':10, 'resolution':[1152,648]}

def read():
    return log.read_text(encoding='utf-8',errors='replace') if log.exists() else ''

def waitfor(predicate,timeout=60):
    deadline = time.monotonic()+timeout
    while time.monotonic()<deadline:
        if predicate():return
        if process.poll() is not None:raise RuntimeError(read()[-6000:])
        time.sleep(.15)
    raise TimeoutError(read()[-6000:])

def write(path,data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data),encoding='utf-8')
    temporary.replace(path)

def cmd(label,commands):
    write(control,{'id':label,'commands':commands.splitlines()})
    waitfor(lambda:'STANDALONE_BARRIER '+label in read())

print('GALLERY_INSTANCE '+str(game),flush=True)
with log.open('w',encoding='utf-8') as output:
    process = subprocess.Popen(command,cwd=game,stdout=output,stderr=subprocess.STDOUT)
    try:
        waitfor(lambda:'STANDALONE_CLIENT_READY' in read(),150)
        cmd('setup',f'gamerule doDaylightCycle false\ngamerule doMobSpawning false\nweather clear\ntime set {4800000+options.clock}\ngamemode spectator @a\ntp @a 0 {options.height} 0\nlunardread mode probability\nscoreboard players set #moon_attacks lunar_dread.sys 0\nlunardread moon set normal')
        time.sleep(4)
        for phase in phases:
            commands = 'lunardread event cancel\nlunardread moon set '+('blood' if phase=='giant' else phase)
            if phase in ('blood','giant'):commands+='\nlunardread moon form '+('giant' if phase=='giant' else 'normal')
            cmd('phase-'+phase,commands)
            time.sleep(2)
            write(visual,{'label':phase,'frames':options.frames,'interval':2,'fov':options.fov,'pitchOffset':options.pitch if phase!='giant' else 0,'pan':1.8})
            waitfor(lambda:f'STANDALONE_SAVED {phase}_{options.frames-1:03d}' in read(),90)
            result['phases'].append(phase)
            (MOD/'test-build/moon-gallery-latest.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
            print('RECORDED '+phase,flush=True)
        cmd('stop','__halt')
        waitfor(lambda:'All dimensions are saved' in read() or 'All chunks are saved' in read(),30)
    finally:
        if process.poll() is None:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        (game/'recording.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
        print(json.dumps(result),flush=True)
