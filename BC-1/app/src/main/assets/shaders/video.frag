#version 300 es
// BC_EFFECT, when defined (inserted after this line), compiles only that effect's code: the full set is
// large and some GPU compilers (software GL on emulators) take many seconds for it.
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uInput;
uniform vec2 uTexel;
uniform float uAspect,uIntensity,uOpacity,uProgress;
uniform vec4 uTransform,uChroma;
// x: signed width scale sign, y: signed height factor (aspect, sign = flip), z/w: tan(skew x/y).
uniform vec4 uShape;
uniform vec2 uAnchor;
uniform vec3 uKeyColor;
uniform int uEffect,uTransition,uRole,uTop;
// Clip source time in seconds, for animated effects (ripple, wave, grain, glitch, light leak).
uniform float uTime;
// Effect controls 0..1 (see EffectCatalog); the defaults reproduce the original single-slider look.
uniform vec4 uParams;
float rnd(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float mask(vec2 uv,float p) {
    if(p<=0.0) return 0.0;
    if(p>=1.0) return 1.0;
    if(uTransition==1) return 1.0-smoothstep(p-.012,p+.012,uv.x);
    if(uTransition==2) { float t=rnd(floor(uv*vec2(12.0,7.0))); return smoothstep(t-.035,t+.035,p); }
    if(uTransition==3) {
        float radius=length(vec2(uAspect,1.0))*.5;
        return 1.0-smoothstep(p*radius-.012,p*radius+.012,length((uv-.5)*vec2(uAspect,1.0)));
    }
    if(uTransition==4) return step(1.0-p,uv.x);
    return p;
}
// ---- Black Ice effects 12-47: written for BC-1 (ICE Lite and the Media3 preview share this shader) ----
const float BC_PI=3.14159265;
vec3 bcRgb2Hsv(vec3 c){vec4 K=vec4(0.0,-1.0/3.0,2.0/3.0,-1.0);vec4 p=mix(vec4(c.bg,K.wz),vec4(c.gb,K.xy),step(c.b,c.g));
  vec4 q=mix(vec4(p.xyw,c.r),vec4(c.r,p.yzx),step(p.x,c.r));float d=q.x-min(q.w,q.y);float e=1.0e-10;
  return vec3(abs(q.z+(q.w-q.y)/(6.0*d+e)),d/(q.x+e),q.x);}
vec3 bcHsv2Rgb(vec3 c){vec3 p=abs(fract(c.xxx+vec3(1.0,2.0/3.0,1.0/3.0))*6.0-3.0);return c.z*mix(vec3(1.0),clamp(p-1.0,0.0,1.0),c.y);}
float bcLuma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}
// HSL band: hue shift (+/-30 deg), saturation scale and lightness push, weighted by distance to the band centre.
vec3 bcHslBand(vec3 c,vec4 p,float centre,float width){vec3 h=bcRgb2Hsv(clamp(c,0.0,1.0));float d=abs(h.x-centre);d=min(d,1.0-d);
  float w=(1.0-smoothstep(0.0,width,d))*smoothstep(.04,.2,h.y);h.x=fract(h.x+(p.x-.5)*(1.0/6.0)*w);h.y=clamp(h.y*(1.0+(p.y-.5)*2.0*w),0.0,1.0);
  vec3 o=bcHsv2Rgb(h);float l=(p.z-.5)*2.0*w;return mix(o,l>0.0?vec3(1.0):vec3(0.0),abs(l)*.6);}
// Colour wheel: tints one tonal range (weight w) toward a hue and lifts or lowers it.
vec3 bcWheel(vec3 c,vec4 p,float w){vec3 t=bcHsv2Rgb(vec3(p.x,1.0,1.0));t-=vec3(bcLuma(t));return c+(t*p.y*.6+(p.z-.5)*.5)*w;}
// Distortions move the sampling point inside the layer (uv in 0..1, aspect-correct around the centre).
vec2 bcDistort(int e,vec2 uv,float k){
    vec2 ctr=(e==32||e==33||e==35||e==37)?vec2(.5):clamp(uParams.yz,0.0,1.0);
    vec2 d=(uv-ctr)*vec2(uAspect,1.0);float r=length(d);
#if !defined(BC_EFFECT) || BC_EFFECT == 29
    if(e==29){float R=mix(.1,.9,uParams.x),t=clamp(1.0-r/R,0.0,1.0);float ang=k*6.0*t*t;float cs=cos(ang),sn=sin(ang);d=mat2(cs,-sn,sn,cs)*d;}           // Swirl
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 30
    if(e==30){float R=.9*uParams.x;if(r<R){d*=mix(1.0,pow(r/R,.65),k);}}                                                           // Bulge
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 31
    if(e==31){float R=.9*uParams.x;if(r>0.0&&r<R){d*=mix(1.0,pow(r/R,-.35),k);}}                                                 // Pinch
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 32
    if(e==32){if(r>0.0)d+=d/r*sin(r*140.0*uParams.x-uTime*14.0*uParams.y)*k*.012;}                                                          // Ripple
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 33
    if(e==33){d.x+=sin(d.y*44.0*uParams.x+uTime*8.0*uParams.y)*k*.035;}                                                                     // Wave
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 34
    if(e==34){float f=1.0+k*1.6*exp2((uParams.x-.5)*2.0)*r*r;d/=f;}                                                                              // Fisheye
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 35
    if(e==35){float sx=(uParams.x-.5)*uAspect;if(k>0.0&&d.x>sx)d.x=2.0*sx-d.x;}                                                                               // Mirror
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 36
    if(e==36){float n=floor(mix(3.0,12.0,k));float a=atan(d.y,d.x)+uParams.x*2.0*BC_PI,seg=2.0*BC_PI/n;a=mod(a,seg);a=abs(a-seg*.5);d=vec2(cos(a),sin(a))*r;} // Kaleidoscope
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 37
    if(e==37){float t=floor(uTime*48.0*uParams.x);d+=(vec2(rnd(vec2(t,1.7)),rnd(vec2(3.1,t)))-.5)*k*.05;}                         // Shake
#endif
    return d/vec2(uAspect,1.0)+ctr;
}
vec4 bcEffect(int e,vec2 uv,vec4 a,float k){
    vec2 px=uTexel;
#if !defined(BC_EFFECT) || BC_EFFECT == 12
    if(e==12){vec3 s=vec3(0.0);float r=mix(1.0,10.0,k)*exp2((uParams.x-.5)*2.0);for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)s+=texture(uInput,uv+vec2(float(x),float(y))*px*r).rgb;a.rgb=s/25.0; return a;} // Box blur
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 13
    if(e==13){vec3 s=vec3(0.0);float an=uParams.x*2.0*BC_PI;vec2 dir=vec2(cos(an),sin(an)*uAspect)*k*.04*exp2((uParams.y-.5)*2.0);for(int i=-7;i<=7;i++)s+=texture(uInput,uv+dir*(float(i)/7.0)).rgb;a.rgb=s/15.0; return a;} // Motion blur
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 14
    if(e==14){vec3 s=vec3(0.0);vec2 c=(uv-uParams.xy)*vec2(uAspect,1.0);for(int i=-7;i<=7;i++){float ang=float(i)/7.0*k*.12;float cs=cos(ang),sn=sin(ang);
        s+=texture(uInput,(mat2(cs,-sn,sn,cs)*c)/vec2(uAspect,1.0)+uParams.xy).rgb;}a.rgb=s/15.0; return a;} // Spin blur
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 15
    if(e==15){vec3 s=vec3(0.0);for(int i=0;i<15;i++)s+=texture(uInput,(uv-uParams.xy)*(1.0-float(i)/14.0*k*.18)+uParams.xy).rgb;a.rgb=s/15.0; return a;} // Zoom blur
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 16
    if(e==16){vec3 b=vec3(0.0);for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)b+=texture(uInput,uv+vec2(float(x),float(y))*px*2.0*exp2((uParams.x-.5)*2.0)).rgb;b/=9.0;a.rgb+=(a.rgb-b)*k*2.5; return a;} // Unsharp mask
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 17
    if(e==17){a.rgb=(a.rgb-.5)*exp2((k-.5)*3.0)+.5; return a;} // Contrast
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 18
    if(e==18){a.rgb+=(k-.5)*.8; return a;} // Brightness
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 19
    if(e==19){a.rgb=pow(max(a.rgb,vec3(0.0)),vec3(exp2((.5-k)*2.0))); return a;} // Gamma
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 20
    if(e==20){a.rgb=(a.rgb-k*.25)/max(1.0-k*.5*exp2((uParams.x-.5)*2.0),.01); return a;} // Levels
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 21
    if(e==21){vec3 h=bcRgb2Hsv(clamp(a.rgb,0.0,1.0));h.x=fract(h.x+k);a.rgb=bcHsv2Rgb(h); return a;} // Hue shift
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 22
    if(e==22){a.rgb=mix(a.rgb,a.rgb*vec3(1.12,1.0,.82)+vec3(.03,.01,0.0),k); return a;} // Warm
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 23
    if(e==23){a.rgb=mix(a.rgb,a.rgb*vec3(.84,1.0,1.14)+vec3(0.0,.01,.03),k); return a;} // Cool
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 24
    if(e==24){float n=floor(mix(16.0,2.0,k));a.rgb=floor(clamp(a.rgb,0.0,1.0)*n+.5)/n; return a;} // Posterize
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 25
    if(e==25){a.rgb=mix(a.rgb,1.0-a.rgb,k); return a;} // Invert
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 26
    if(e==26){vec3 s=vec3(dot(a.rgb,vec3(.393,.769,.189)),dot(a.rgb,vec3(.349,.686,.168)),dot(a.rgb,vec3(.272,.534,.131)));a.rgb=mix(a.rgb,s,k); return a;} // Sepia
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 27
    if(e==27){a.rgb=mix(a.rgb,mix(vec3(.02,.06,.16),vec3(.62,.93,1.0),bcLuma(a.rgb)),k); return a;} // Ice duotone
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 28
    if(e==28){vec2 q=uv+vec2(sin(uTime*.4),cos(uTime*.3))*.08;float g=smoothstep(.9*exp2((uParams.z-.5)*2.0),0.0,length((q-uParams.xy)*vec2(uAspect,1.0)));
        a.rgb+=vec3(1.0,.55,.2)*g*k*.9; return a;} // Light leak
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 38
    if(e==38){float t=mix(.15,.85,k);a.rgb=vec3(smoothstep(t-.02,t+.02,bcLuma(a.rgb))); return a;} // Threshold
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 39
    if(e==39){vec2 ox=px*exp2((uParams.x-.5)*2.0);vec3 l=texture(uInput,uv-vec2(ox.x,0.0)).rgb,r=texture(uInput,uv+vec2(ox.x,0.0)).rgb,u=texture(uInput,uv+vec2(0.0,ox.y)).rgb,d=texture(uInput,uv-vec2(0.0,ox.y)).rgb;
        float g=length(vec2(bcLuma(r)-bcLuma(l),bcLuma(u)-bcLuma(d)))*4.0;a.rgb=mix(a.rgb,vec3(clamp(g,0.0,1.0)),k); return a;} // Edge detect
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 40
    if(e==40){float an=uParams.y*2.0*BC_PI;vec2 off=vec2(cos(an),sin(an))*px*2.12;float g=bcLuma(texture(uInput,uv+off).rgb)-bcLuma(texture(uInput,uv-off).rgb);a.rgb=mix(a.rgb,vec3(.5+g*2.0*exp2((uParams.x-.5)*2.0)),k); return a;} // Emboss
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 41
    if(e==41){a.a*=smoothstep(k-.12*uParams.x,k+.12*uParams.x,bcLuma(a.rgb)); return a;} // Luma key
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 42
    if(e==42){float gs=exp2((uParams.x-.5)*4.0),gt=exp2((uParams.y-.5)*2.0);float n=rnd(floor(uv/px/gs)+vec2(fract(uTime*7.13*gt)*97.0,fract(uTime*3.7*gt)*53.0))-.5;a.rgb+=n*k*.35; return a;} // Film grain
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 43
    if(e==43){float l=sin(uv.y/px.y*BC_PI*.5*exp2((uParams.x-.5)*3.0))*.5+.5;a.rgb*=1.0-k*.6*l; return a;} // Scanlines
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 44
    if(e==44){float t=floor(uTime*24.0*uParams.x),row=floor(uv.y*48.0*uParams.y);float on=step(1.0-k*.6,rnd(vec2(row,t)));float sh=(rnd(vec2(t,row*1.3))-.5)*.15*k*on;
        vec2 g=uv+vec2(sh,0.0);a=vec4(texture(uInput,g+vec2(k*.012,0.0)).r,texture(uInput,g).g,texture(uInput,g-vec2(k*.012,0.0)).b,a.a); return a;} // Glitch
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 45
    if(e==45){vec2 g=uv+vec2(sin(uv.y*140.0+uTime*20.0)*k*.0025*exp2((uParams.x-.5)*2.0),0.0);vec3 c=vec3(texture(uInput,g+vec2(k*.006,0.0)).r,texture(uInput,g).g,texture(uInput,g-vec2(k*.006,0.0)).b);
        c+=(rnd(vec2(floor(uv.y/px.y),floor(uTime*30.0)))-.5)*k*.12*exp2((uParams.y-.5)*2.0);c*=1.0-k*.25*(sin(uv.y/px.y*BC_PI*.5)*.5+.5);a.rgb=mix(a.rgb,c,clamp(k*2.0,0.0,1.0)); return a;} // VHS
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 46
    if(e==46){float o=k*.015,an=uParams.x*2.0*BC_PI;vec2 off=vec2(cos(an),sin(an)*uAspect)*o;a=vec4(texture(uInput,uv+off).r,a.g,texture(uInput,uv-off).b,a.a); return a;} // RGB split
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 47
    if(e==47){float cell=mix(.008,.03,k)*exp2((uParams.y-.5)*2.0);vec2 g=(uv-.5)*vec2(uAspect,1.0)/cell;float ra=uParams.x*2.0*BC_PI;mat2 rot=mat2(cos(ra),-sin(ra),sin(ra),cos(ra));vec2 q=rot*g;
        vec2 c=(floor(q)+.5);float lum=bcLuma(texture(uInput,(transpose(rot)*c)*cell/vec2(uAspect,1.0)+.5).rgb);
        float dot_=smoothstep(.05,-.05,length(q-c)-sqrt(1.0-lum)*.62);a.rgb=mix(a.rgb,vec3(1.0-dot_),clamp(k*1.5,0.0,1.0)); return a;} // Halftone
#endif
// ---- 48-65: grading, detail and look effects (written for BC-1; controls arrive in uParams) ----
#if !defined(BC_EFFECT) || BC_EFFECT == 48
    if(e==48){float l=bcLuma(a.rgb);float e0=mix(.3,.7,uParams.x);float w=smoothstep(e0,1.0,l);a.rgb*=1.0+(k-.5)*2.0*w; return a;} // Highlights
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 49
    if(e==49){float l=bcLuma(a.rgb);float e1=mix(.3,.7,uParams.x);float w=1.0-smoothstep(0.0,e1,l);a.rgb+=(k-.5)*1.2*w; return a;} // Shadows
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 50
    if(e==50){float lift=k*.3*exp2((uParams.x-.5)*2.0);float l=bcLuma(a.rgb);a.rgb=mix(a.rgb,vec3(l),k*.35*uParams.y*2.0);a.rgb=a.rgb*(1.0-lift)+lift; return a;} // Fade
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 51
    if(e==51){float t=(k-.5)*2.0;a.rgb*=vec3(1.0+t*.4,1.0+t*.05,1.0-t*.4); return a;} // Temperature
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 52
    if(e==52){float t=(k-.5)*2.0;a.rgb*=vec3(1.0+t*.16,1.0-t*.35,1.0+t*.16); return a;} // Tint
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 53
    if(e==53){float mx=max(a.r,max(a.g,a.b)),mn=min(a.r,min(a.g,a.b));float l=bcLuma(a.rgb);float boost=(1.0-(mx-mn))*k*1.6;a.rgb=mix(vec3(l),a.rgb,1.0+boost); return a;} // Vibrance
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 54
    if(e==54){float r=8.0*exp2((uParams.x-.5)*2.0);vec3 b=vec3(0.0);for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)b+=texture(uInput,uv+vec2(float(x),float(y))*px*r).rgb;b/=9.0;
        float mid=1.0-abs(bcLuma(a.rgb)*2.0-1.0);a.rgb+=(a.rgb-b)*k*2.2*mid; return a;} // Clarity
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 55
    if(e==55){float m=min(a.r,min(a.g,a.b));float s=clamp(k*.75*exp2((uParams.x-.5)*2.0),0.0,.95);a.rgb=(a.rgb-vec3(m*s))/max(1.0-m*s,.15);
        float l=bcLuma(a.rgb);a.rgb=mix(vec3(l),a.rgb,1.0+k*.25); return a;} // Dehaze
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 56
    if(e==56){float g=mix(2.0,9.0,k);float m=mix(.25,.75,uParams.x);vec3 x=clamp(a.rgb,0.0,1.0);vec3 y=1.0/(1.0+exp(-g*(x-m)));
        float y0=1.0/(1.0+exp(g*m)),y1=1.0/(1.0+exp(-g*(1.0-m)));a.rgb=mix(x,(y-y0)/(y1-y0),min(1.0,k*4.0)); return a;} // S-curve
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 57
    if(e==57){vec3 ts=bcHsv2Rgb(vec3(uParams.x,1.0,1.0)),th=bcHsv2Rgb(vec3(uParams.y,1.0,1.0));float bal=smoothstep(uParams.z-.4,uParams.z+.4,bcLuma(a.rgb));
        vec3 tint=mix(ts,th,bal);a.rgb=mix(a.rgb,a.rgb*(.55+.9*tint),k*.85);a.rgb+=(tint-.5)*k*.06; return a;} // Color balance
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 58
    if(e==58){vec3 h=bcRgb2Hsv(clamp(a.rgb,0.0,1.0));float d=abs(h.x-uParams.x);d=min(d,1.0-d);float range=mix(.03,.25,k);float w=(1.0-smoothstep(range*.5,range,d))*step(.05,h.y);
        h.x=fract(h.x+(uParams.y-.5)*.5*w);h.y=clamp(h.y*mix(1.0,uParams.z*2.0,w),0.0,1.0);h.z*=mix(1.0,uParams.w*2.0,w);a.rgb=bcHsv2Rgb(h); return a;} // Selective colour
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 59
    if(e==59){vec3 x=max(a.rgb,vec3(0.0));a.rgb=pow(x,vec3(1.0/(1.0+k*1.1)));float dark=1.0-smoothstep(0.0,.6,bcLuma(a.rgb));
        a.rgb*=vec3(1.0-dark*k*.22*uParams.x*2.0,1.0,1.0+dark*k*.36*uParams.x*2.0); return a;} // Night boost
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 60
    if(e==60){float p=exp2((uParams.x-.5)*2.0);vec3 x=(a.rgb-.5)*(1.0+.28*k*p)+.5+.04*k;float l=bcLuma(x);float mx=max(x.r,max(x.g,x.b)),mn=min(x.r,min(x.g,x.b));
        a.rgb=mix(vec3(l),x,1.0+(1.0-(mx-mn))*k*.7*p); return a;} // Auto enhance
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 61
    if(e==61){vec3 w=vec3(.2126,.7152,.0722)*exp2((vec3(uParams.x,uParams.y,uParams.z)-.5)*3.0);w/=w.r+w.g+w.b;a.rgb=mix(a.rgb,vec3(dot(a.rgb,w)),k); return a;} // Black & white mix
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 62
    if(e==62){float cells=mix(6.0,26.0,uParams.z);vec2 g=uv*vec2(uAspect,1.0)*cells;vec3 glow=vec3(0.0);
        for(int layer=0;layer<3;layer++){float fl=float(layer);vec2 gg=g*(1.0+fl*.55)+vec2(fl*17.3,fl*5.1);gg.y+=uTime*(.35+.25*fl)*uParams.y*2.0;
            vec2 cell=floor(gg),f=fract(gg)-.5;vec2 off=(vec2(rnd(cell+fl),rnd(cell.yx+31.0+fl))-.5)*.7;float rad=mix(.05,.22,uParams.x)*(.5+rnd(cell+7.0));
            float on=step(.55,rnd(cell+3.0+fl));glow+=vec3(1.0,.95,.85)*smoothstep(rad,rad*.2,length(f-off))*on*(1.0-fl*.25);}
        a.rgb+=glow*k; return a;} // Particles
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 63
    if(e==63){float e0=mix(.05,.45,uParams.y);float v=smoothstep(e0,e0+.7*exp2((uParams.z-.5)*2.0),length((uv-.5)*vec2(uAspect,1.0)));vec3 tint=bcHsv2Rgb(vec3(uParams.x,.85,.9));
        a.rgb=mix(a.rgb,mix(a.rgb*(1.0-.6*v),tint,v*.6),k); return a;} // Tinted vignette
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 64
    if(e==64){vec3 x=max(a.rgb,vec3(0.0))*exp2((uParams.x-.5)*2.0)*1.4;vec3 f=(x*(2.51*x+.03))/(x*(2.43*x+.59)+.14);a.rgb=mix(a.rgb,clamp(f,0.0,1.0),k); return a;} // Filmic tone
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 65
    if(e==65){float tol=mix(.1,.45,k);float soft=.09*uParams.x;a.a*=smoothstep(tol-soft,tol+soft,distance(a.rgb,vec3(.07,.27,.85))); return a;} // Chroma key (blue)
#endif
// ---- 66-69: combined effects (each control centred at 0.5 = unchanged; strength mixes the result) ----
#if !defined(BC_EFFECT) || BC_EFFECT == 66
    if(e==66){vec3 c=a.rgb*exp2((uParams.x-.5)*4.0);c+=(uParams.y-.5)*.8;float l=bcLuma(c);
        c*=1.0+(uParams.z-.5)*2.0*smoothstep(.5,1.0,l);c+=(uParams.w-.5)*1.2*(1.0-smoothstep(0.0,.5,l));a.rgb=mix(a.rgb,c,k); return a;} // Light
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 67
    if(e==67){float t=(uParams.x-.5)*2.0,n=(uParams.y-.5)*2.0;vec3 c=a.rgb*vec3(1.0+t*.4,1.0+t*.05,1.0-t*.4)*vec3(1.0+n*.16,1.0-n*.35,1.0+n*.16);a.rgb=mix(a.rgb,c,k); return a;} // White balance
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 68
    if(e==68){vec3 c=mix(vec3(bcLuma(a.rgb)),a.rgb,uParams.x*2.0);float mx=max(c.r,max(c.g,c.b)),mn=min(c.r,min(c.g,c.b));
        c=mix(vec3(bcLuma(c)),c,1.0+(1.0-clamp(mx-mn,0.0,1.0))*(uParams.y-.5)*3.2);a.rgb=mix(a.rgb,c,k); return a;} // Color intensity
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 69
    if(e==69){vec3 h=bcRgb2Hsv(clamp(a.rgb,0.0,1.0));float d=abs(h.x-uParams.x);d=min(d,1.0-d);float score=d*2.0+max(0.0,.25-h.y)*2.0;
        float tol=mix(.05,.35,uParams.y),soft=mix(.005,.15,uParams.z);float matte=smoothstep(tol-soft,tol+soft,score);
        vec3 key=bcHsv2Rgb(vec3(uParams.x,1.0,1.0));vec3 kc=normalize(key-vec3(bcLuma(key)));float along=max(dot(a.rgb-vec3(bcLuma(a.rgb)),kc),0.0);
        a.rgb-=kc*along*uParams.w*k*(1.0-smoothstep(tol,tol*3.0+soft,score));a.a*=mix(1.0,matte,k); return a;} // Chroma key (any colour)
#endif
// ---- 70-80: HSL bands and colour wheels ----
#if !defined(BC_EFFECT) || BC_EFFECT == 70
    if(e==70){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,0.0/360.0,30.0/360.0),k); return a;} // HSL Reds
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 71
    if(e==71){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,30.0/360.0,30.0/360.0),k); return a;} // HSL Oranges
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 72
    if(e==72){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,60.0/360.0,30.0/360.0),k); return a;} // HSL Yellows
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 73
    if(e==73){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,120.0/360.0,60.0/360.0),k); return a;} // HSL Greens
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 74
    if(e==74){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,180.0/360.0,60.0/360.0),k); return a;} // HSL Aquas
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 75
    if(e==75){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,240.0/360.0,30.0/360.0),k); return a;} // HSL Blues
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 76
    if(e==76){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,270.0/360.0,30.0/360.0),k); return a;} // HSL Purples
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 77
    if(e==77){a.rgb=mix(a.rgb,bcHslBand(a.rgb,uParams,300.0/360.0,30.0/360.0),k); return a;} // HSL Magentas
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 78
    if(e==78){float l=clamp(bcLuma(a.rgb),0.0,1.0);a.rgb=mix(a.rgb,bcWheel(a.rgb,uParams,(1.0-l)*(1.0-l)),k); return a;} // Shadows wheel
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 79
    if(e==79){float l=clamp(bcLuma(a.rgb),0.0,1.0);a.rgb=mix(a.rgb,bcWheel(a.rgb,uParams,4.0*l*(1.0-l)),k); return a;} // Midtones wheel
#endif
#if !defined(BC_EFFECT) || BC_EFFECT == 80
    if(e==80){float l=clamp(bcLuma(a.rgb),0.0,1.0);a.rgb=mix(a.rgb,bcWheel(a.rgb,uParams,l*l),k); return a;} // Highlights wheel
#endif
    return a;
}
void main() {
    float k=uIntensity,p=uProgress;
    // Inverse of s = T + a + R·S·K·(l − a); see TransformMath for the forward mapping.
    vec2 anchor=uAnchor*.5*vec2(uAspect,1.0);
    vec2 q=(vUV-.5-uTransform.xy*.5)*vec2(uAspect,1.0)-anchor;
    float cs=cos(uTransform.w),sn=sin(uTransform.w);
    q=mat2(cs,-sn,sn,cs)*q/(max(.05,uTransform.z)*uShape.xy);
    q=vec2(q.x-uShape.z*q.y,q.y-uShape.w*q.x)/(1.0-uShape.z*uShape.w)+anchor;
    vec2 uv=q/vec2(uAspect,1.0)+.5;
    float inside=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);
    if(uRole>0 && uTransition==4) uv.x+=uRole==2?p:p-1.0;
    if(uRole>0 && uTransition==5) uv=(uv-.5)/(uRole==2?1.0+.35*p:1.35-.35*p)+.5;
    if(uEffect>=29 && uEffect<=37 && k>0.0) {
        uv=bcDistort(uEffect,uv,k);
        // Small wobbles repeat the edge pixels; shape distortions leave areas outside the picture transparent.
        if(uEffect==32||uEffect==33||uEffect==36||uEffect==37) uv=clamp(uv,uTexel*.5,1.0-uTexel*.5);
        else inside*=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);
    }
    vec2 coord=uv;
    if(uEffect==8 && k>0.0) { vec2 cells=(1.0/uTexel)/max(1.0,mix(1.0,48.0,k)*exp2((uParams.x-.5)*2.0)); coord=(floor(uv*cells)+.5)/cells; }
    vec4 a=texture(uInput,coord);
    // A fixed 13-tap Gaussian approximation bounds bandwidth on moving frames.
    if(uEffect==1 || uEffect==2) {
        vec2 d=uTexel*mix(1.0,14.0,k)*exp2((uParams.x-.5)*2.0);
        vec3 b=a.rgb*.20;
        b+=(texture(uInput,uv+vec2(d.x,0)).rgb+texture(uInput,uv-vec2(d.x,0)).rgb
           +texture(uInput,uv+vec2(0,d.y)).rgb+texture(uInput,uv-vec2(0,d.y)).rgb)*.12;
        b+=(texture(uInput,uv+d).rgb+texture(uInput,uv-d).rgb
           +texture(uInput,uv+vec2(d.x,-d.y)).rgb+texture(uInput,uv+vec2(-d.x,d.y)).rgb)*.06;
        b+=(texture(uInput,uv+vec2(2.0*d.x,0)).rgb+texture(uInput,uv-vec2(2.0*d.x,0)).rgb
           +texture(uInput,uv+vec2(0,2.0*d.y)).rgb+texture(uInput,uv-vec2(0,2.0*d.y)).rgb)*.02;
        a.rgb=uEffect==1?mix(a.rgb,b,k):a.rgb+max(b-uParams.y*.5,vec3(0))*k*1.7;
    }
    if(uEffect==3) {float tol=mix(.08,.42,k); float soft=.09*uParams.x;a.a*=smoothstep(tol-soft,tol+soft,distance(a.rgb,vec3(.10,.84,.29)));}
    if(uEffect==4) {vec3 g=vec3(dot(a.rgb,vec3(.2126,.7152,.0722)));g=(g-.5)*(1.0+uParams.x*1.5)+.5;a.rgb=mix(a.rgb,g,k);}
    if(uEffect==5) a.a*=k;
    if(uEffect==6) {float e0=mix(.05,.45,uParams.x);a.rgb*=1.0-k*smoothstep(e0,e0+.7*exp2((uParams.y-.5)*2.0),length((uv-.5)*vec2(uAspect,1)));}
    if(uEffect==7) {
        vec2 sp=uTexel*exp2((uParams.x-.5)*2.0);
        vec3 n=texture(uInput,uv+vec2(sp.x,0)).rgb+texture(uInput,uv-vec2(sp.x,0)).rgb
              +texture(uInput,uv+vec2(0,sp.y)).rgb+texture(uInput,uv-vec2(0,sp.y)).rgb;
        a.rgb+=(a.rgb*4.0-n)*k;
    }
    if(uEffect==9) a.rgb*=exp2((k-.5)*4.0);
    if(uEffect==10) a.rgb=mix(vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),a.rgb,k*2.0);
    if(uEffect==11) a.rgb=mix(a.rgb,(a.rgb-.5)*1.08+.5+vec3(-.06,.015,.09)*exp2((uParams.x-.5)*3.0),k);
    if(uEffect>=12 && !(uEffect>=29 && uEffect<=37)) a=bcEffect(uEffect,uv,a,k);
    if(uChroma.x>.5){
        float chromaDistance=length((a.rgb-dot(a.rgb,vec3(.2126,.7152,.0722)))-(uKeyColor-dot(uKeyColor,vec3(.2126,.7152,.0722))));
        float matte=smoothstep(uChroma.y-uChroma.z,uChroma.y+uChroma.z,chromaDistance);
        a.a*=matte;
        vec3 spill=max(uKeyColor-vec3(dot(uKeyColor,vec3(.333333))),vec3(0));
        a.rgb-=spill*uChroma.w*(1.0-smoothstep(uChroma.y,uChroma.y+uChroma.z*4.0,chromaDistance));
    }
    a.a*=inside;
    if(uRole>0) {
        if(uTransition==6) {
            float fade=uRole==2?max(0.0,1.0-2.0*p):max(0.0,2.0*p-1.0);
            a.rgb*=fade;
            // Hard switch at the black midpoint; no double fade in alpha compositing.
            if(uTop==1) a.a*=uRole==2?(p<.5?1.0:0.0):(p<.5?0.0:1.0);
        } else if(uTop==1) {float m=mask(vUV,p); a.a*=uRole==2?1.0-m:m;}
    }
    fragColor=vec4(clamp(a.rgb,0.0,1.0),a.a*uOpacity);
}
