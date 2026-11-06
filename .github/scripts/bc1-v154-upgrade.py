"""Install the retained-key release over 1.5.3 and verify a real saved project survives.
Runs only on the disposable CI emulator. Never clears application data or uninstalls either build.
"""
import atexit,hashlib,json,pathlib,re,shlex,struct,subprocess,time,urllib.request,xml.etree.ElementTree as ET,zlib
PKG='com.blackice.bc1.preview'
OUT=pathlib.Path('publish')
OLD=OUT/'previous-v153.apk'
NEW=OUT/'Black-Ice-Editor-v1.5.4-Preview-build48.apk'
NAME='Upgrade fixture'
ID='v154-upgrade-fixture'
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
        adb('shell','rm','-f','/sdcard/v154-ui.xml')
        dump=adb('shell','uiautomator','dump','--compressed','/sdcard/v154-ui.xml')
        raw=adb('exec-out','cat','/sdcard/v154-ui.xml')
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
        if len(expected) in (2,4) and all(isinstance(x,(int,float)) for x in expected[:2]) and len(actual)==8:
            assert actual[4:]==[0,0,1,1], 'Legacy fill handles must retain linear defaults'
            if len(expected)==2:assert actual[2] and actual[3]==0, 'Legacy fill interpolation changed'
            actual=actual[:len(expected)]
        if len(expected)==2 and all(isinstance(x,(int,float)) for x in expected) and len(actual)==4:
            assert actual[2] and actual[3]==0, "Legacy fill interpolation must stay linear"
            actual=actual[:2]
        assert len(expected)==len(actual)
        for a,b in zip(expected,actual):subset(a,b)
    elif isinstance(expected,float):assert abs(expected-actual)<1e-5,(expected,actual)
    else:assert expected==actual,(expected,actual)

OUT.mkdir(exist_ok=True)
if not OLD.exists():urllib.request.urlretrieve('https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.3/Black-Ice-Editor-v1.5.3-Preview-build47.apk',OLD)
assert hashlib.sha256(OLD.read_bytes()).hexdigest()=='ca20a3b02afb04970e763d36ae3a4586230f13f888cb7e8d16ee9df71acf7d59'
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

# Preserve the old wide/landscape layout choice during this real APK upgrade.
space_path='shared_prefs/bc1-editor.xml'
private(space_path,b'<map><int name="workspace-focus" value="1"/></map>')
def verify_space():
    values={n.get('name'):n.get('value') for n in ET.fromstring(private(space_path))}
    assert values['workspace-focus']=='1','Saved wide/landscape layout choice changed'
    assert 'portrait-workspace-focus' not in values,'First portrait launch must use its Balanced default without overwriting preferences'

# Small deterministic PNG: private source media retained byte-for-byte across the APK upgrade.
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',64,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([24,85,140,255])*64)*32))+chunk(b'IEND',b'')
private('files/media/upgrade-source.png',png)
# Rotation animation covering every interpolation representation the new Easing Curve Editor must leave alone
# until a person edits that interval: a custom cubic, a legacy analytic preset (Ease Out) and a final key.
rotation=[[0,0,.2,.6,.7,.1,'curve-a',-1],[1000,90,0,0,.58,1,'curve-b',2],[2000,180,0,0,1,1,'curve-c',0]]
motion=[[] for _ in range(12)];motion[3]=rotation
motion[5]=[[100,.2,0,0,1,1,'strength-start',0],[1500,.8,.25,.1,.75,.9,'strength-end',-1]]
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
    # The fixture was written pretty-printed; 1.5.3 rewrites it compactly, which proves its save completed.
    if before.get('schema')==11 and b'\n  ' not in before_bytes:break
    time.sleep(.25)
else:raise AssertionError('1.5.3 Ctrl+S did not rewrite the fixture')
subset(project,before)
verify_clock()
verify_space()
print('1.5.3 saved fixture verified; upgrading in place',flush=True)
adb('shell','am','force-stop',PKG)
print(adb('install','-r',str(NEW)).decode(),flush=True)
package=adb('shell','dumpsys','package',PKG).decode();assert 'versionCode=48' in package and 'versionName=1.5.4' in package
# Re-indent the old fixture so the new build's own compact schema-12 save is observable.
private(path,json.dumps(before,indent=2).encode())
open_project(verify_title=False)
print('Opened upgraded editor; saving through Ctrl+S',flush=True)
adb('shell','input','keycombination','113','47')
for _ in range(44):
    after_bytes=private(path)
    after=json.loads(after_bytes)
    if after.get('schema')==12 and b'\n  ' not in after_bytes:break
    time.sleep(.25)
else:raise AssertionError('1.5.4 Ctrl+S did not rewrite the project')
expected=dict(project);expected['schema']=12;subset(expected,after)
verify_clock()
verify_space()
assert after['clips'][0]['effect']==12,'Saved effect must survive the upgrade'
stack=after['clips'][0]['effects']
assert len(stack)==1 and stack[0]['type']==12 and stack[0]['id']=='legacy-upgrade-image','Legacy effect must migrate once with deterministic identity'
assert stack[0]['intensity']==after['clips'][0]['intensity'] and stack[0]['motion'][5]==after['clips'][0]['motion'][5],'Effect intensity and its own curve must be retained'

# Opening and saving must not rewrite any interval: custom cubic and legacy preset keep their stored data.
assert [list(k) for k in after['clips'][0]['motion'][3]]==[list(k) for k in before['clips'][0]['motion'][3]],'Easing intervals changed during upgrade'
assert after['clips'][0]['motion'][3][0][7]==-1 and after['clips'][0]['motion'][3][1][7]==2 and after['clips'][0]['motion'][3][2][7]==0
assert private('files/media/upgrade-source.png')==png
(OUT/'upgrade-after.png').write_bytes(adb('exec-out','screencap','-p'))
report=dict(result='PASS',from_version='1.5.3 (47)',to_version='1.5.4 (48)',package=PKG,project_id=ID,source_sha256=hashlib.sha256(png).hexdigest(),verified=['Install -r accepted original Preview signing lineage','Project saved by 1.5.3 opens in 1.5.4 and saves again as schema 12','Source URI and original image bytes retained','Trim, opacity, rotation, scale, position and rotation keyframes retained','Custom cubic handles and the legacy Ease Out preset keep their stored ids, discriminators and coordinates','Shape, timing, opacity and legacy linear gradient/color keys retained','Canvas and frame rate unchanged','Saved clip effect retained','Paused project work clock retains total 123456 ms and paused state'])
adb('shell','wm','size','824x1784');adb('shell','wm','density','320');time.sleep(2)
verify_space()
fullscreen=[n for n in tree().iter('node') if n.get('content-desc')=='Fullscreen preview']
assert len(fullscreen)==1,'Portrait preview fullscreen control must be available'
preview_bounds=list(map(int,re.findall(r'\d+',fullscreen[0].get('bounds'))))
assert preview_bounds[3]>824*.7,('Retained Expand timeline shrank the new portrait preview',preview_bounds)
report['verified'].append('Saved wide/landscape Expand timeline choice survives; Samsung-shaped portrait opens at full Balanced reference height')
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
