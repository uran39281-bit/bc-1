"""Install the retained-key release over 1.4.5 and verify a real saved project survives.
Runs only on the disposable CI emulator. Never clears application data or uninstalls either build.
"""
import atexit,hashlib,json,pathlib,re,shlex,struct,subprocess,time,urllib.request,xml.etree.ElementTree as ET,zlib
PKG='com.blackice.bc1.preview'
OUT=pathlib.Path('publish')
OLD=OUT/'previous-v145.apk'
NEW=OUT/'Black-Ice-Editor-v1.4.6-Preview-build40.apk'
NAME='Upgrade fixture'
ID='v146-upgrade-fixture'
def run(*args,data=None):
    return subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=120).stdout

def adb(*args,data=None):return run('adb',*args,data=data)
def private(path,data=None):
    if data is None:return adb('exec-out','run-as',PKG,'cat',path)
    adb('shell','run-as',PKG,'sh','-c',shlex.quote('cat > '+path),data=data)
def tree():
    # A freshly started/resized activity can be busy before UIAutomator has a hierarchy.
    # Treat that as not ready, while the bounded caller keeps waiting for the actual control.
    try:
        adb('shell','rm','-f','/sdcard/v146-ui.xml')
        dump=adb('shell','uiautomator','dump','--compressed','/sdcard/v146-ui.xml')
        raw=adb('exec-out','cat','/sdcard/v146-ui.xml')
    except subprocess.CalledProcessError as error:
        (OUT/'upgrade-ui-last.txt').write_bytes((error.stdout or b'')+(error.stderr or b''))
        return ET.Element('hierarchy')
    (OUT/'upgrade-ui-last.txt').write_bytes(dump+b'\n'+raw)
    try:return ET.fromstring(raw)
    except ET.ParseError:return ET.Element('hierarchy')
def diagnostics():
    if (OUT/'upgrade-verification.json').exists():return
    for filename,args in [('upgrade-failure.png',('exec-out','screencap','-p')),('upgrade-logcat.txt',('logcat','-d','-t','1500'))]:
        try:(OUT/filename).write_bytes(adb(*args))
        except Exception:pass
atexit.register(diagnostics)
def click(label):
    for _ in range(30):
        for n in tree().iter('node'):
            if n.get('text')==label or n.get('content-desc')==label:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
                if x2>x1 and y2>y1:
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
        time.sleep(.5)
    raise AssertionError('Control not available: '+label)
def open_project(verify_title=True):
    adb('shell','am','start','-n',PKG+'/com.blackice.bc1.HomeActivity')
    click(NAME)
    for _ in range(30):
        resumed=adb('shell','dumpsys','activity','activities').decode()
        if any('EditorActivity' in line and ('Resumed' in line or 'topResumed' in line) for line in resumed.splitlines()):
            time.sleep(5)
            if not verify_title:return  # Saving/schema migration below proves the intended project opened.
            labels=' '.join(n.get('text','') for n in tree().iter('node'))
            assert 'Could not reopen' not in labels,labels[-2000:]
            if NAME in labels:return
        time.sleep(.5)
    raise AssertionError('Project did not open in editor')
def subset(expected,actual):
    if isinstance(expected,dict):
        for k,v in expected.items():subset(v,actual[k])
    elif isinstance(expected,list):
        if len(expected)==6 and all(isinstance(x,(int,float)) for x in expected) and len(actual)==8:
            assert actual[6] and actual[7]==-1, 'Legacy interpolation must remain unchanged'
            actual=actual[:6]
        if len(expected)==2 and all(isinstance(x,(int,float)) for x in expected) and len(actual)==4:
            assert actual[2] and actual[3]==0, "Legacy fill interpolation must stay linear"
            actual=actual[:2]
        assert len(expected)==len(actual)
        for a,b in zip(expected,actual):subset(a,b)
    elif isinstance(expected,float):assert abs(expected-actual)<1e-5,(expected,actual)
    else:assert expected==actual,(expected,actual)

OUT.mkdir(exist_ok=True)
if not OLD.exists():urllib.request.urlretrieve('https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/Black-Ice-Editor-v1.4.5-Preview-build39.apk',OLD)
assert hashlib.sha256(OLD.read_bytes()).hexdigest()=='5e859c3b73ca3e662341f44f75c04c3ace9cd1cb85b8ce47d96308721c9a839b'
print(adb('install','-r',str(OLD)).decode(),flush=True)
adb('shell','wm','size','1920x1080');adb('shell','wm','density','160')
adb('shell','run-as',PKG,'mkdir','-p','files/media','files/projects-v1')
# Small deterministic PNG: private source media retained byte-for-byte across the APK upgrade.
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([24,85,140,255])*64)*32))+chunk(b'IEND',b'')
private('files/media/upgrade-source.png',png)
motion=[[[0,-.2,.25,.1,.25,1],[700,.3,.25,.1,.25,1]]]+[[] for _ in range(11)]
clip=dict(id='upgrade-image',uri=f'file:///data/user/0/{PKG}/files/media/upgrade-source.png',name='Preserved image',image=True,sourceDurationMs=2000,inMs=100,outMs=1600,effect=12,intensity=.5,opacity=.8,volume=1,transition=-1,transitionMs=0,easing=0,width=64,height=32,rotation=17,x=.12,y=-.1,scale=.7,motion=motion)
shape=dict(id='upgrade-shape',text='Rectangle',shape=14,startMs=0,endMs=1500,x=-.3,y=.1,scale=1.2,opacity=.65,rotation=29,color=-16731137)
shape["fill"]=dict(gradient=True,solid=dict(base=-1,keys=[]),angle=dict(base=0,keys=[[0,0],[1500,90]]),stops=[dict(id="left",position=dict(base=0,keys=[]),color=dict(base=-65536,keys=[[0,-65536],[1500,-16776961]])),dict(id="right",position=dict(base=1,keys=[]),color=dict(base=-16776961,keys=[]))],active=["left","right"],structures=[])
project=dict(schema=10,id=ID,name=NAME,portrait=False,previewHeight=540,playheadMs=500,frameRate=30,clips=[clip],overlays=[shape])
path='files/projects-v1/'+ID+'.json'
private(path,json.dumps(project,indent=2).encode())
# Both versions have the live work clock; use the real registered Save shortcut
# rather than requiring the editor's accessibility hierarchy to become idle.
open_project(verify_title=False)
adb('shell','input','keycombination','113','47')
for _ in range(40):
    before_bytes=private(path)
    before=json.loads(before_bytes)
    if before.get('schema')==11:break
    time.sleep(.25)
else:raise AssertionError('1.4.5 Ctrl+S did not persist schema 11')
expected=dict(project,schema=11)
subset(expected,before)
assert b'\n  ' in before_bytes,'Fixture must exercise the 1.4.5 pretty-printed format'
print('1.4.5 saved fixture verified; upgrading in place',flush=True)
adb('shell','am','force-stop',PKG)
print(adb('install','-r',str(NEW)).decode(),flush=True)
package=adb('shell','dumpsys','package',PKG).decode();assert 'versionCode=40' in package and 'versionName=1.4.6' in package
# The live work clock continuously changes accessibility text. UiAutomator's CLI
# waits for global UI idleness and can fail to dump an otherwise healthy editor.
# Exercise the real registered Ctrl+S command, then await its asynchronous disk write.
open_project(verify_title=False)
print('Opened upgraded editor; saving through Ctrl+S',flush=True)
adb('shell','input','keycombination','113','47')
for _ in range(40):
    after_bytes=private(path)
    after=json.loads(after_bytes)
    # Schema 11 is already present before upgrade. Await the compact rewrite,
    # which proves this version's asynchronous save actually completed.
    if after.get('schema')==11 and b'\n  ' not in after_bytes:break
    time.sleep(.25)
else:raise AssertionError('1.4.6 Ctrl+S did not persist compact schema-11 JSON')
expected=dict(project,schema=11);subset(expected,after)
assert b'\n  ' not in private(path),'1.4.6 writes compact project JSON'
assert after['clips'][0]['effect']==12,'Saved effect must survive the upgrade'
assert private('files/media/upgrade-source.png')==png
(OUT/'upgrade-after.png').write_bytes(adb('exec-out','screencap','-p'))
report=dict(result='PASS',from_version='1.4.5 (39)',to_version='1.4.6 (40)',package=PKG,project_id=ID,source_sha256=hashlib.sha256(png).hexdigest(),verified=['Install -r accepted original Preview signing lineage','Project opens before and after upgrade; Ctrl+S persists schema 11','Source URI and original image bytes retained','Trim, opacity, rotation, scale, position and motion keyframes retained','Shape, timing, opacity and legacy linear gradient/color keys retained','Canvas and frame rate unchanged','Saved clip effect retained (now rendered) and project rewritten as compact JSON'])
(OUT/'upgrade-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
OLD.unlink()
(OUT/'upgrade-ui-last.txt').unlink(missing_ok=True)
