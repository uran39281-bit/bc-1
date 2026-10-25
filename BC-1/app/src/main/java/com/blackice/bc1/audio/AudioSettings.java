package com.blackice.bc1.audio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes6.dex */
public final class AudioSettings {
    public static final int[] FREQUENCIES = {80, 250, 1000, 4000, 10000};
    public final float[] eqDb = new float[5];
    public int noise;
    public float normalizationDb;
    public float preampDb;

    public AudioSettings copy() {
        AudioSettings s = new AudioSettings();
        s.noise = this.noise;
        s.preampDb = this.preampDb;
        s.normalizationDb = this.normalizationDb;
        System.arraycopy(this.eqDb, 0, s.eqDb, 0, 5);
        return s;
    }

    public boolean active() {
        if (this.noise != 0 || this.preampDb != 0.0f || this.normalizationDb != 0.0f) {
            return true;
        }
        for (float v : this.eqDb) {
            if (v != 0.0f) {
                return true;
            }
        }
        return false;
    }

    public void validate() {
        if (this.noise < 0 || this.noise > 3) {
            throw new IllegalArgumentException("Invalid noise reduction");
        }
        check(this.preampDb, 12);
        check(this.normalizationDb, 24);
        for (float v : this.eqDb) {
            check(v, 12);
        }
    }

    private static void check(float v, int max) {
        if (!Float.isFinite(v) || Math.abs(v) > max) {
            throw new IllegalArgumentException("Invalid audio gain");
        }
    }

    public JSONObject json() throws JSONException {
        validate();
        JSONArray a = new JSONArray();
        for (float v : this.eqDb) {
            a.put(v);
        }
        return new JSONObject().put("noise", this.noise).put("preamp", this.preampDb).put("normalization", this.normalizationDb).put("eq", a);
    }

    public static AudioSettings from(JSONObject j) throws JSONException {
        AudioSettings s = new AudioSettings();
        if (j != null) {
            s.noise = j.optInt("noise", 0);
            s.preampDb = (float) j.optDouble("preamp", 0.0d);
            s.normalizationDb = (float) j.optDouble("normalization", 0.0d);
            JSONArray a = j.optJSONArray("eq");
            if (a != null) {
                if (a.length() != 5) {
                    throw new IllegalArgumentException("Expected five EQ bands");
                }
                for (int i = 0; i < 5; i++) {
                    s.eqDb[i] = (float) a.getDouble(i);
                }
            }
        }
        s.validate();
        return s;
    }
}
