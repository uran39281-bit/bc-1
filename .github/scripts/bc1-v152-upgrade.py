"""Install the retained-key release over 1.5.1 and verify a real saved project survives.
Runs only on the disposable CI emulator. Never clears application data or uninstalls either build.
"""
import atexit,hashlib,json,pathlib,re,shlex,struct,subprocess,time,urllib.request,xml.etree.ElementTree as ET,zlib
PKG='com.blackice.bc1.preview'
OUT=pathlib.Path('publish')
OLD=OUT/'previous-v151.apk'
NEW=OUT/'Black-Ice-Editor-v1.5.2-Preview-build46.apk'
NAME='Upgrade fixture'
ID='v152-upgrade-fixture'
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
        adb('shell','rm','-f','/sdcard/v152-ui.xml')
        dump=adb('shell','uiautomator','dump','--compressed','/sdcard/v152-ui.xml')
        raw=adb('exec-out','cat','/sdcard/v152-ui.xml')
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
if not OLD.exists():urllib.request.urlretrieve('https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.1/Black-Ice-Editor-v1.5.1-Preview-build45.apk',OLD)
assert hashlib.sha256(OLD.read_bytes()).hexdigest()=='2c1b69156276c45e723eea98deb01100d8be3904ab1697d980067a49d7a4f707'
print(adb('install','-r',str(OLD)).decode(),flush=True)
adb('shell','wm','size','1920x1080');adb('shell','wm','density','160')
adb('shell','run-as',PKG,'mkdir','-p','files/media','files/projects-v1','shared_prefs')
# A genuinely paused clock is both a migration fixture and a quiet UI for system hierarchy reads.
clock_path='shared_prefs/project-work-clocks.xml'
private(clock_path,('<map><long name="'+ID+'" value="123456"/><boolean name="'+ID+'-paused" value="true"/></map>').encode())
def verify_clock():
    clock=ET.fromstring(private(clock_path))
    values={n.get('name'):n.get('value') for n in clock}
    assert values[ID]=='123456' and values[ID+'-paused']=='true','Saved paused work clock changed'

# Small deterministic PNG: private source media retained byte-for-byte across the APK upgrade.
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([24,85,140,255])*64)*32))+chunk(b'IEND',b'')
private('files/media/upgrade-source.png',png)
# Rotation animation covering every interpolation representation the new Easing Curve Editor must leave alone
# until a person edits that interval: a custom cubic, a legacy analytic preset (Ease Out) and a final key.
rotation=[[0,0,.2,.6,.7,.1,'curve-a',-1],[1000,90,0,0,.58,1,'curve-b',2],[2000,180,0,0,1,1,'curve-c',0]]
motion=[[] for _ in range(12)];motion[3]=rotation
clip=dict(id='upgrade-image',uri=f'file:///data/user/0/{PKG}/files/media/upgrade-source.png',name='Preserved image',image=True,sourceDurationMs=2000,inMs=100,outMs=1600,effect=12,intensity=.5,opacity=.8,volume=1,transition=-1,transitionMs=0,easing=0,width=64,height=32,rotation=17,x=.12,y=-.1,scale=.7,motion=motion)
shape=dict(id='upgrade-shape',text='Rectangle',shape=14,startMs=0,endMs=1500,x=-.3,y=.1,scale=1.2,opacity=.65,rotation=29,color=-16731137)
shape["fill"]=dict(gradient=True,solid=dict(base=-1,keys=[]),angle=dict(base=0,keys=[[0,0],[1500,90]]),stops=[dict(id="left",position=dict(base=0,keys=[]),color=dict(base=-65536,keys=[[0,-65536],[1500,-16776961]])),dict(id="right",position=dict(base=1,keys=[]),color=dict(base=-16776961,keys=[]))],active=["left","right"],structures=[])
project=dict(schema=11,id=ID,name=NAME,portrait=False,previewHeight=540,playheadMs=500,frameRate=30,clips=[clip],overlays=[shape])
path='files/projects-v1/'+ID+'.json'
private(path,json.dumps(project,indent=2).encode())
# Both versions have the live work clock; use the real registered Save shortcut
# rather than requiring the editor's accessibility hierarchy to become idle.
open_project(verify_title=False)
adb('shell','input','keycombination','113','47')
for _ in range(44):
    before_bytes=private(path)
    before=json.loads(before_bytes)
    # The fixture was written pretty-printed; 1.5.1 rewrites it compactly, which proves its save completed.
    if before.get('schema')==11 and b'\n  ' not in before_bytes:break
    time.sleep(.25)
else:raise AssertionError('1.5.1 Ctrl+S did not rewrite the fixture')
subset(project,before)
verify_clock()
print('1.5.1 saved fixture verified; upgrading in place',flush=True)
adb('shell','am','force-stop',PKG)
print(adb('install','-r',str(NEW)).decode(),flush=True)
package=adb('shell','dumpsys','package',PKG).decode();assert 'versionCode=46' in package and 'versionName=1.5.2' in package
# 1.5.2 stores projects in the same compact schema-11 format, so a save of unchanged content is
# otherwise indistinguishable. Re-indent the same content so this build's own rewrite is observable.
private(path,json.dumps(before,indent=2).encode())
open_project(verify_title=False)
print('Opened upgraded editor; saving through Ctrl+S',flush=True)
adb('shell','input','keycombination','113','47')
for _ in range(44):
    after_bytes=private(path)
    after=json.loads(after_bytes)
    if after.get('schema')==11 and b'\n  ' not in after_bytes:break
    time.sleep(.25)
else:raise AssertionError('1.5.2 Ctrl+S did not rewrite the project')
subset(project,after)
verify_clock()
assert after['clips'][0]['effect']==12,'Saved effect must survive the upgrade'
# Opening and saving must not rewrite any interval: custom cubic and legacy preset keep their stored data.
assert [list(k) for k in after['clips'][0]['motion'][3]]==[list(k) for k in before['clips'][0]['motion'][3]],'Easing intervals changed during upgrade'
assert after['clips'][0]['motion'][3][0][7]==-1 and after['clips'][0]['motion'][3][1][7]==2 and after['clips'][0]['motion'][3][2][7]==0
assert private('files/media/upgrade-source.png')==png
(OUT/'upgrade-after.png').write_bytes(adb('exec-out','screencap','-p'))
report=dict(result='PASS',from_version='1.5.1 (45)',to_version='1.5.2 (46)',package=PKG,project_id=ID,source_sha256=hashlib.sha256(png).hexdigest(),verified=['Install -r accepted original Preview signing lineage','Project saved by 1.5.1 opens in 1.5.2 and saves again as schema 11','Source URI and original image bytes retained','Trim, opacity, rotation, scale, position and rotation keyframes retained','Custom cubic handles and the legacy Ease Out preset keep their stored ids, discriminators and coordinates','Shape, timing, opacity and legacy linear gradient/color keys retained','Canvas and frame rate unchanged','Saved clip effect retained','Paused project work clock retains total 123456 ms and paused state'])
adb('shell','wm','size','720x1600');adb('shell','wm','density','320');time.sleep(2)
(OUT/'upgrade-phone-base.png').write_bytes(adb('exec-out','screencap','-p'))
click('Add content');time.sleep(.5)
labels={n.get('content-desc','') for n in tree().iter('node')}
assert all('Filter '+kind in labels for kind in ['All','Video','Audio','Image','Shapes','Favorites']),labels
(OUT/'upgrade-phone-add.png').write_bytes(adb('exec-out','screencap','-p'))
adb('shell','input','keyevent','4');time.sleep(.5)
subset(after,json.loads(private(path)))
assert private('files/media/upgrade-source.png')==png
report['verified'].append('Portrait Add Media opens with all six real filters; Back retains the saved graph and original source bytes')
(OUT/'upgrade-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
OLD.unlink()
(OUT/'upgrade-ui-last.txt').unlink(missing_ok=True)
