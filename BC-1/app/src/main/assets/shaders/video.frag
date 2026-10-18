#version 300 es
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
// Distortions move the sampling point inside the layer (uv in 0..1, aspect-correct around the centre).
vec2 bcDistort(int e,vec2 uv,float k){
    vec2 d=(uv-.5)*vec2(uAspect,1.0);float r=length(d);
    if(e==29){float R=.5,t=clamp(1.0-r/R,0.0,1.0);float ang=k*6.0*t*t;float cs=cos(ang),sn=sin(ang);d=mat2(cs,-sn,sn,cs)*d;}           // Swirl
    if(e==30){float R=.45;if(r<R){d*=mix(1.0,pow(r/R,.65),k);}}                                                           // Bulge
    if(e==31){float R=.45;if(r>0.0&&r<R){d*=mix(1.0,pow(r/R,-.35),k);}}                                                 // Pinch
    if(e==32){if(r>0.0)d+=d/r*sin(r*70.0-uTime*7.0)*k*.012;}                                                          // Ripple
    if(e==33){d.x+=sin(d.y*22.0+uTime*4.0)*k*.035;}                                                                     // Wave
    if(e==34){float f=1.0+k*1.6*r*r;d/=f;}                                                                              // Fisheye
    if(e==35){if(k>0.0&&d.x>0.0)d.x=-d.x;}                                                                               // Mirror
    if(e==36){float n=floor(mix(3.0,12.0,k));float a=atan(d.y,d.x),seg=2.0*BC_PI/n;a=mod(a,seg);a=abs(a-seg*.5);d=vec2(cos(a),sin(a))*r;} // Kaleidoscope
    if(e==37){float t=floor(uTime*24.0);d+=(vec2(rnd(vec2(t,1.7)),rnd(vec2(3.1,t)))-.5)*k*.05;}                         // Shake
    return d/vec2(uAspect,1.0)+.5;
}
vec4 bcEffect(int e,vec2 uv,vec4 a,float k){
    vec2 px=uTexel;
    if(e==12){vec3 s=vec3(0.0);float r=mix(1.0,10.0,k);for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)s+=texture(uInput,uv+vec2(float(x),float(y))*px*r).rgb;a.rgb=s/25.0; return a;} // Box blur
    if(e==13){vec3 s=vec3(0.0);for(int i=-7;i<=7;i++)s+=texture(uInput,uv+vec2(float(i)/7.0*k*.04,0.0)).rgb;a.rgb=s/15.0; return a;} // Motion blur
    if(e==14){vec3 s=vec3(0.0);vec2 c=(uv-.5)*vec2(uAspect,1.0);for(int i=-7;i<=7;i++){float ang=float(i)/7.0*k*.12;float cs=cos(ang),sn=sin(ang);
        s+=texture(uInput,(mat2(cs,-sn,sn,cs)*c)/vec2(uAspect,1.0)+.5).rgb;}a.rgb=s/15.0; return a;} // Spin blur
    if(e==15){vec3 s=vec3(0.0);for(int i=0;i<15;i++)s+=texture(uInput,(uv-.5)*(1.0-float(i)/14.0*k*.18)+.5).rgb;a.rgb=s/15.0; return a;} // Zoom blur
    if(e==16){vec3 b=vec3(0.0);for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)b+=texture(uInput,uv+vec2(float(x),float(y))*px*2.0).rgb;b/=9.0;a.rgb+=(a.rgb-b)*k*2.5; return a;} // Unsharp mask
    if(e==17){a.rgb=(a.rgb-.5)*exp2((k-.5)*3.0)+.5; return a;} // Contrast
    if(e==18){a.rgb+=(k-.5)*.8; return a;} // Brightness
    if(e==19){a.rgb=pow(max(a.rgb,vec3(0.0)),vec3(exp2((.5-k)*2.0))); return a;} // Gamma
    if(e==20){a.rgb=(a.rgb-k*.25)/max(1.0-k*.5,.01); return a;} // Levels
    if(e==21){vec3 h=bcRgb2Hsv(clamp(a.rgb,0.0,1.0));h.x=fract(h.x+k);a.rgb=bcHsv2Rgb(h); return a;} // Hue shift
    if(e==22){a.rgb=mix(a.rgb,a.rgb*vec3(1.12,1.0,.82)+vec3(.03,.01,0.0),k); return a;} // Warm
    if(e==23){a.rgb=mix(a.rgb,a.rgb*vec3(.84,1.0,1.14)+vec3(0.0,.01,.03),k); return a;} // Cool
    if(e==24){float n=floor(mix(16.0,2.0,k));a.rgb=floor(clamp(a.rgb,0.0,1.0)*n+.5)/n; return a;} // Posterize
    if(e==25){a.rgb=mix(a.rgb,1.0-a.rgb,k); return a;} // Invert
    if(e==26){vec3 s=vec3(dot(a.rgb,vec3(.393,.769,.189)),dot(a.rgb,vec3(.349,.686,.168)),dot(a.rgb,vec3(.272,.534,.131)));a.rgb=mix(a.rgb,s,k); return a;} // Sepia
    if(e==27){a.rgb=mix(a.rgb,mix(vec3(.02,.06,.16),vec3(.62,.93,1.0),bcLuma(a.rgb)),k); return a;} // Ice duotone
    if(e==28){vec2 q=uv+vec2(sin(uTime*.4),cos(uTime*.3))*.08;float g=smoothstep(.9,0.0,length((q-vec2(0.0,1.0))*vec2(uAspect,1.0)));
        a.rgb+=vec3(1.0,.55,.2)*g*k*.9; return a;} // Light leak
    if(e==38){float t=mix(.15,.85,k);a.rgb=vec3(smoothstep(t-.02,t+.02,bcLuma(a.rgb))); return a;} // Threshold
    if(e==39){vec3 l=texture(uInput,uv-vec2(px.x,0.0)).rgb,r=texture(uInput,uv+vec2(px.x,0.0)).rgb,u=texture(uInput,uv+vec2(0.0,px.y)).rgb,d=texture(uInput,uv-vec2(0.0,px.y)).rgb;
        float g=length(vec2(bcLuma(r)-bcLuma(l),bcLuma(u)-bcLuma(d)))*4.0;a.rgb=mix(a.rgb,vec3(clamp(g,0.0,1.0)),k); return a;} // Edge detect
    if(e==40){float g=bcLuma(texture(uInput,uv+px*1.5).rgb)-bcLuma(texture(uInput,uv-px*1.5).rgb);a.rgb=mix(a.rgb,vec3(.5+g*2.0),k); return a;} // Emboss
    if(e==41){a.a*=smoothstep(k-.06,k+.06,bcLuma(a.rgb)); return a;} // Luma key
    if(e==42){float n=rnd(uv/px+vec2(fract(uTime*7.13)*97.0,fract(uTime*3.7)*53.0))-.5;a.rgb+=n*k*.35; return a;} // Film grain
    if(e==43){float l=sin(uv.y/px.y*BC_PI*.5)*.5+.5;a.rgb*=1.0-k*.6*l; return a;} // Scanlines
    if(e==44){float t=floor(uTime*12.0),row=floor(uv.y*24.0);float on=step(1.0-k*.6,rnd(vec2(row,t)));float sh=(rnd(vec2(t,row*1.3))-.5)*.15*k*on;
        vec2 g=uv+vec2(sh,0.0);a=vec4(texture(uInput,g+vec2(k*.012,0.0)).r,texture(uInput,g).g,texture(uInput,g-vec2(k*.012,0.0)).b,a.a); return a;} // Glitch
    if(e==45){vec2 g=uv+vec2(sin(uv.y*140.0+uTime*20.0)*k*.0025,0.0);vec3 c=vec3(texture(uInput,g+vec2(k*.006,0.0)).r,texture(uInput,g).g,texture(uInput,g-vec2(k*.006,0.0)).b);
        c+=(rnd(vec2(floor(uv.y/px.y),floor(uTime*30.0)))-.5)*k*.12;c*=1.0-k*.25*(sin(uv.y/px.y*BC_PI*.5)*.5+.5);a.rgb=mix(a.rgb,c,clamp(k*2.0,0.0,1.0)); return a;} // VHS
    if(e==46){float o=k*.015;a=vec4(texture(uInput,uv+vec2(o,0.0)).r,a.g,texture(uInput,uv-vec2(o,0.0)).b,a.a); return a;} // RGB split
    if(e==47){float cell=mix(.008,.03,k);vec2 g=(uv-.5)*vec2(uAspect,1.0)/cell;mat2 rot=mat2(.7071,-.7071,.7071,.7071);vec2 q=rot*g;
        vec2 c=(floor(q)+.5);float lum=bcLuma(texture(uInput,(transpose(rot)*c)*cell/vec2(uAspect,1.0)+.5).rgb);
        float dot_=smoothstep(.05,-.05,length(q-c)-sqrt(1.0-lum)*.62);a.rgb=mix(a.rgb,vec3(1.0-dot_),clamp(k*1.5,0.0,1.0)); return a;} // Halftone
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
    if(uEffect==8 && k>0.0) { vec2 cells=(1.0/uTexel)/mix(1.0,48.0,k); coord=(floor(uv*cells)+.5)/cells; }
    vec4 a=texture(uInput,coord);
    // A fixed 13-tap Gaussian approximation bounds bandwidth on moving frames.
    if(uEffect==1 || uEffect==2) {
        vec2 d=uTexel*mix(1.0,14.0,k);
        vec3 b=a.rgb*.20;
        b+=(texture(uInput,uv+vec2(d.x,0)).rgb+texture(uInput,uv-vec2(d.x,0)).rgb
           +texture(uInput,uv+vec2(0,d.y)).rgb+texture(uInput,uv-vec2(0,d.y)).rgb)*.12;
        b+=(texture(uInput,uv+d).rgb+texture(uInput,uv-d).rgb
           +texture(uInput,uv+vec2(d.x,-d.y)).rgb+texture(uInput,uv+vec2(-d.x,d.y)).rgb)*.06;
        b+=(texture(uInput,uv+vec2(2.0*d.x,0)).rgb+texture(uInput,uv-vec2(2.0*d.x,0)).rgb
           +texture(uInput,uv+vec2(0,2.0*d.y)).rgb+texture(uInput,uv-vec2(0,2.0*d.y)).rgb)*.02;
        a.rgb=uEffect==1?mix(a.rgb,b,k):a.rgb+max(b-.16,vec3(0))*k*1.7;
    }
    if(uEffect==3) {float tol=mix(.08,.42,k); a.a*=smoothstep(tol-.045,tol+.045,distance(a.rgb,vec3(.10,.84,.29)));}
    if(uEffect==4) a.rgb=mix(a.rgb,vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),k);
    if(uEffect==5) a.a*=k;
    if(uEffect==6) a.rgb*=1.0-k*smoothstep(.25,.95,length((uv-.5)*vec2(uAspect,1)));
    if(uEffect==7) {
        vec3 n=texture(uInput,uv+vec2(uTexel.x,0)).rgb+texture(uInput,uv-vec2(uTexel.x,0)).rgb
              +texture(uInput,uv+vec2(0,uTexel.y)).rgb+texture(uInput,uv-vec2(0,uTexel.y)).rgb;
        a.rgb+=(a.rgb*4.0-n)*k;
    }
    if(uEffect==9) a.rgb*=exp2((k-.5)*4.0);
    if(uEffect==10) a.rgb=mix(vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),a.rgb,k*2.0);
    if(uEffect==11) a.rgb=mix(a.rgb,(a.rgb-.5)*1.08+.5+vec3(-.06,.015,.09),k);
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
