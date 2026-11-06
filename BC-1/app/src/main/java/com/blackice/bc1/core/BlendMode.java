package com.blackice.bc1.core;

/**
 * Layer blend modes for ICE Lite. Written for BC-1 from the public W3C "Compositing and Blending
 * Level 1" definitions plus the common photo-editing extensions (linear/vivid/pin light, hard mix,
 * subtract, divide, darker/lighter colour). IDs are stored in projects: append only.
 *
 * {@link #blend} is the pure-Java reference used by tests; the GLSL in {@link #GLSL} implements the
 * same formulas for the GPU and is checked against this reference on a device.
 */
public enum BlendMode {
    NORMAL("Normal"), DARKEN("Darken"), MULTIPLY("Multiply"), COLOR_BURN("Color Burn"), LINEAR_BURN("Linear Burn"),
    DARKER_COLOR("Darker Color"), LIGHTEN("Lighten"), SCREEN("Screen"), COLOR_DODGE("Color Dodge"),
    LINEAR_DODGE("Linear Dodge (Add)"), LIGHTER_COLOR("Lighter Color"), OVERLAY("Overlay"), SOFT_LIGHT("Soft Light"),
    HARD_LIGHT("Hard Light"), VIVID_LIGHT("Vivid Light"), LINEAR_LIGHT("Linear Light"), PIN_LIGHT("Pin Light"),
    HARD_MIX("Hard Mix"), DIFFERENCE("Difference"), EXCLUSION("Exclusion"), SUBTRACT("Subtract"), DIVIDE("Divide"),
    HUE("Hue"), SATURATION("Saturation"), COLOR("Color"), LUMINOSITY("Luminosity");

    public final String label;
    BlendMode(String label) { this.label = label; }

    public static BlendMode of(int id) { return id >= 0 && id < values().length ? values()[id] : NORMAL; }
    public static boolean valid(int id) { return id >= 0 && id < values().length; }

    public static String[] labels() {
        BlendMode[] all = values(); String[] l = new String[all.length];
        for (int i = 0; i < all.length; i++) l[i] = all[i].label;
        return l;
    }

    // ---- Reference implementation (colours in 0..1, backdrop b, source s) -------------------------

    /** Blended colour B(b, s) before alpha: the layer's colour as this mode turns it. */
    public float[] blend(float[] b, float[] s) {
        switch (this) {
            case DARKER_COLOR: return lum(s) < lum(b) ? s.clone() : b.clone();
            case LIGHTER_COLOR: return lum(s) > lum(b) ? s.clone() : b.clone();
            case HUE: return setLum(setSat(s, sat(b)), lum(b));
            case SATURATION: return setLum(setSat(b, sat(s)), lum(b));
            case COLOR: return setLum(s, lum(b));
            case LUMINOSITY: return setLum(b, lum(s));
            default:
                float[] r = new float[3];
                for (int i = 0; i < 3; i++) r[i] = channel(b[i], s[i]);
                return r;
        }
    }

    /** Final pixel over an opaque backdrop: the blended colour mixed in by the layer's alpha. */
    public float[] composite(float[] b, float[] s, float alpha) {
        float[] m = blend(b, s), out = new float[3];
        for (int i = 0; i < 3; i++) out[i] = clamp(b[i] + (clamp(m[i]) - b[i]) * alpha);
        return out;
    }

    private float channel(float b, float s) {
        switch (this) {
            case NORMAL: return s;
            case DARKEN: return Math.min(b, s);
            case MULTIPLY: return b * s;
            case COLOR_BURN: return b >= 1 ? 1 : s <= 0 ? 0 : 1 - Math.min(1, (1 - b) / s);
            case LINEAR_BURN: return Math.max(0, b + s - 1);
            case LIGHTEN: return Math.max(b, s);
            case SCREEN: return b + s - b * s;
            case COLOR_DODGE: return b <= 0 ? 0 : s >= 1 ? 1 : Math.min(1, b / (1 - s));
            case LINEAR_DODGE: return Math.min(1, b + s);
            case OVERLAY: return hardLight(s, b);
            case SOFT_LIGHT: {
                if (s <= .5f) return b - (1 - 2 * s) * b * (1 - b);
                float d = b <= .25f ? ((16 * b - 12) * b + 4) * b : (float) Math.sqrt(b);
                return b + (2 * s - 1) * (d - b);
            }
            case HARD_LIGHT: return hardLight(b, s);
            case VIVID_LIGHT: return s <= .5f ? COLOR_BURN.channel(b, 2 * s) : COLOR_DODGE.channel(b, 2 * s - 1);
            case LINEAR_LIGHT: return clamp(b + 2 * s - 1);
            case PIN_LIGHT: return s <= .5f ? Math.min(b, 2 * s) : Math.max(b, 2 * s - 1);
            case HARD_MIX: return b + s >= 1 ? 1 : 0;
            case DIFFERENCE: return Math.abs(b - s);
            case EXCLUSION: return b + s - 2 * b * s;
            case SUBTRACT: return Math.max(0, b - s);
            case DIVIDE: return s <= 0 ? (b > 0 ? 1 : 0) : Math.min(1, b / s);
            default: return s;
        }
    }

    private static float hardLight(float b, float s) { return s <= .5f ? b * 2 * s : SCREEN.channel(b, 2 * s - 1); }
    static float lum(float[] c) { return .3f * c[0] + .59f * c[1] + .11f * c[2]; }
    private static float sat(float[] c) { return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2])); }
    private static float[] clipColor(float[] c) {
        float l = lum(c), n = Math.min(c[0], Math.min(c[1], c[2])), x = Math.max(c[0], Math.max(c[1], c[2]));
        float[] r = c.clone();
        for (int i = 0; i < 3; i++) {
            if (n < 0) r[i] = l + (r[i] - l) * l / (l - n);
            if (x > 1) r[i] = l + (r[i] - l) * (1 - l) / (x - l);
        }
        return r;
    }
    private static float[] setLum(float[] c, float l) {
        float d = l - lum(c);
        return clipColor(new float[]{c[0] + d, c[1] + d, c[2] + d});
    }
    private static float[] setSat(float[] c, float s) {
        int max = 0, min = 0;
        for (int i = 1; i < 3; i++) { if (c[i] > c[max]) max = i; if (c[i] < c[min]) min = i; }
        if (max == min) return new float[]{0, 0, 0};
        int mid = 3 - max - min;
        float[] r = new float[3];
        r[mid] = (c[mid] - c[min]) * s / (c[max] - c[min]);
        r[max] = s; r[min] = 0;
        return r;
    }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    // ---- GPU implementation --------------------------------------------------------------------

    /** GLSL ES 3.0: {@code vec3 bcBlend(int mode, vec3 b, vec3 s)}, identical formulas to {@link #blend}. */
    public static final String GLSL =
            "float bcLum(vec3 c){return dot(c,vec3(.3,.59,.11));}\n" +
            "float bcSat(vec3 c){return max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b));}\n" +
            "vec3 bcClip(vec3 c){float l=bcLum(c),n=min(c.r,min(c.g,c.b)),x=max(c.r,max(c.g,c.b));\n" +
            "  if(n<0.0)c=l+(c-l)*l/(l-n);if(x>1.0)c=l+(c-l)*(1.0-l)/(x-l);return c;}\n" +
            "vec3 bcSetLum(vec3 c,float l){return bcClip(c+(l-bcLum(c)));}\n" +
            "vec3 bcSetSat(vec3 c,float s){float mx=max(c.r,max(c.g,c.b)),mn=min(c.r,min(c.g,c.b));\n" +
            "  if(mx<=mn)return vec3(0.0);return (c-mn)*s/(mx-mn);}\n" +
            "float bcBurn(float b,float s){return b>=1.0?1.0:(s<=0.0?0.0:1.0-min(1.0,(1.0-b)/s));}\n" +
            "float bcDodge(float b,float s){return b<=0.0?0.0:(s>=1.0?1.0:min(1.0,b/(1.0-s)));}\n" +
            "float bcScreen(float b,float s){return b+s-b*s;}\n" +
            "float bcHard(float b,float s){return s<=0.5?b*2.0*s:bcScreen(b,2.0*s-1.0);}\n" +
            "float bcSoft(float b,float s){if(s<=0.5)return b-(1.0-2.0*s)*b*(1.0-b);\n" +
            "  float d=b<=0.25?((16.0*b-12.0)*b+4.0)*b:sqrt(b);return b+(2.0*s-1.0)*(d-b);}\n" +
            "float bcChannel(int m,float b,float s){\n" +
            "  if(m==1)return min(b,s); if(m==2)return b*s; if(m==3)return bcBurn(b,s); if(m==4)return max(0.0,b+s-1.0);\n" +
            "  if(m==6)return max(b,s); if(m==7)return bcScreen(b,s); if(m==8)return bcDodge(b,s); if(m==9)return min(1.0,b+s);\n" +
            "  if(m==11)return bcHard(s,b); if(m==12)return bcSoft(b,s); if(m==13)return bcHard(b,s);\n" +
            "  if(m==14)return s<=0.5?bcBurn(b,2.0*s):bcDodge(b,2.0*s-1.0); if(m==15)return clamp(b+2.0*s-1.0,0.0,1.0);\n" +
            "  if(m==16)return s<=0.5?min(b,2.0*s):max(b,2.0*s-1.0); if(m==17)return b+s>=1.0?1.0:0.0;\n" +
            "  if(m==18)return abs(b-s); if(m==19)return b+s-2.0*b*s; if(m==20)return max(0.0,b-s);\n" +
            "  if(m==21)return s<=0.0?(b>0.0?1.0:0.0):min(1.0,b/s); return s;}\n" +
            "vec3 bcBlend(int m,vec3 b,vec3 s){\n" +
            "  if(m==5)return bcLum(s)<bcLum(b)?s:b; if(m==10)return bcLum(s)>bcLum(b)?s:b;\n" +
            "  if(m==22)return bcSetLum(bcSetSat(s,bcSat(b)),bcLum(b)); if(m==23)return bcSetLum(bcSetSat(b,bcSat(s)),bcLum(b));\n" +
            "  if(m==24)return bcSetLum(s,bcLum(b)); if(m==25)return bcSetLum(b,bcLum(s));\n" +
            "  return vec3(bcChannel(m,b.r,s.r),bcChannel(m,b.g,s.g),bcChannel(m,b.b,s.b));}\n";
}
