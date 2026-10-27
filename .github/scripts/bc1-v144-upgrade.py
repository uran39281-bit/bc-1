"""Install the retained-key release over 1.4.3 and verify a real saved project survives.
Runs only on the disposable CI emulator. Never clears application data or uninstalls either build.
"""
import hashlib,json,pathlib,re,shlex,struct,subprocess,time,urllib.request,xml.etree.ElementTree as ET,zlib
PKG='com.blackice.bc1.preview'
OUT=pathlib.Path('publish')
OLD=OUT/'previous-v143.apk'
NEW=OUT/'Black-Ice-Editor-v1.4.4-Preview-build38.apk'
NAME='Upgrade fixture'
ID='v144-upgrade-fixture'
def run(*args,data=None):
    return subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout

def adb(*args,data=None):return run('adb',*args,data=data)
def private(path,data=None):
    if data is None:return adb('exec-out','run-as',PKG,'cat',path)
    adb('shell','run-as',PKG,'sh','-c',shlex.quote('cat > '+path),data=data)
def tree():
    adb('shell','uiautomator','dump','/sdcard/v144-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/v144-ui.xml'))
def click(label):
    for _ in range(20):
        for n in tree().iter('node'):
            if n.get('text')==label or n.get('content-desc')==label:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
                if x2>x1 and y2>y1:
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
        time.sleep(.5)
    raise AssertionError('Control not available: '+label)
def open_project():
    adb('shell','am','start','-n',PKG+'/com.blackice.bc1.HomeActivity')
    click(NAME)
    for _ in range(30):
        resumed=adb('shell','dumpsys','activity','activities').decode()
        if any('EditorActivity' in line and ('Resumed' in line or 'topResumed' in line) for line in resumed.splitlines()):
            time.sleep(5)
            xml=adb('shell','uiautomator','dump','/sdcard/v144-ui.xml')
            text=adb('exec-out','cat','/sdcard/v144-ui.xml').decode()
            assert 'Could not reopen' not in text and NAME in text,text[-2000:]
            return
        time.sleep(.5)
    raise AssertionError('Project did not open in editor')
def subset(expected,actual):
    if isinstance(expected,dict):
        for k,v in expected.items():subset(v,actual[k])
    elif isinstance(expected,list):
        assert len(expected)==len(actual)
        for a,b in zip(expected,actual):subset(a,b)
    elif isinstance(expected,float):assert abs(expected-actual)<1e-5,(expected,actual)
    else:assert expected==actual,(expected,actual)

OUT.mkdir(exist_ok=True)
if not OLD.exists():urllib.request.urlretrieve('https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.3/Black-Ice-Editor-v1.4.3-Preview-build37.apk',OLD)
assert hashlib.sha256(OLD.read_bytes()).hexdigest()=='0943e83f7bf52cf1434ee96ceb5f18a2d0ab9d84d63b3f9643fa2384e5659d31'
print(adb('install','-r',str(OLD)).decode(),flush=True)
adb('shell','wm','size','1920x1080');adb('shell','wm','density','160')
adb('shell','run-as',PKG,'mkdir','-p','files/media','files/projects-v1')
# Small deterministic PNG: private source media retained byte-for-byte across the APK upgrade.
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([24,85,140,255])*64)*32))+chunk(b'IEND',b'')
private('files/media/upgrade-source.png',png)
motion=[[[0,-.2,.25,.1,.25,1],[700,.3,.25,.1,.25,1]]]+[[] for _ in range(11)]
clip=dict(id='upgrade-image',uri=f'file:///data/user/0/{PKG}/files/media/upgrade-source.png',name='Preserved image',image=True,sourceDurationMs=2000,inMs=100,outMs=1600,effect=0,intensity=.5,opacity=.8,volume=1,transition=-1,transitionMs=0,easing=0,width=64,height=32,rotation=17,x=.12,y=-.1,scale=.7,motion=motion)
shape=dict(id='upgrade-shape',text='Rectangle',shape=14,startMs=0,endMs=1500,x=-.3,y=.1,scale=1.2,opacity=.65,rotation=29,color=-16731137)
project=dict(schema=10,id=ID,name=NAME,portrait=False,previewHeight=540,playheadMs=500,frameRate=30,clips=[clip],overlays=[shape])
path='files/projects-v1/'+ID+'.json'
private(path,json.dumps(project).encode())
open_project();click('Save project');time.sleep(1)
before=json.loads(private(path));subset(project,before)
adb('shell','am','force-stop',PKG)
print(adb('install','-r',str(NEW)).decode(),flush=True)
package=adb('shell','dumpsys','package',PKG).decode();assert 'versionCode=38' in package and 'versionName=1.4.4' in package
open_project();click('Save project');time.sleep(1)
after=json.loads(private(path));subset(project,after)
assert private('files/media/upgrade-source.png')==png
(OUT/'upgrade-after.png').write_bytes(adb('exec-out','screencap','-p'))
report=dict(result='PASS',from_version='1.4.3 (37)',to_version='1.4.4 (38)',package=PKG,project_id=ID,source_sha256=hashlib.sha256(png).hexdigest(),verified=['Install -r accepted original Preview signing lineage','Project opens in the editor before and after upgrade','Source URI and original image bytes retained','Trim, opacity, rotation, scale, position and motion keyframes retained','Shape, timing and opacity retained','Canvas and frame rate unchanged'])
(OUT/'upgrade-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
OLD.unlink()
