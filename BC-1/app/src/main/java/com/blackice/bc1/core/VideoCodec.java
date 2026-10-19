package com.blackice.bc1.core;

/** Export video codecs. Stored by name; append only. */
public enum VideoCodec {
    H264("video/avc", "H.264", 1.0),
    /** H.265 needs about 60% of the H.264 bitrate for the same quality; phones capture 4K120 in it. */
    HEVC("video/hevc", "HEVC (H.265)", 0.6);

    public final String mime, label;
    public final double bitrateFactor;

    VideoCodec(String mime, String label, double bitrateFactor) { this.mime = mime; this.label = label; this.bitrateFactor = bitrateFactor; }

    /** Export codec preference: automatic (H.264 when the device can, HEVC when it must), or fixed. */
    public enum Choice {
        AUTO("Auto (H.264, HEVC when needed)"), H264_ONLY("H.264 (most compatible)"), HEVC_ONLY("HEVC (smaller files)");
        public static final String PREF_KEY = "codec";
        public final String label;
        Choice(String label) { this.label = label; }
        public VideoCodec[] order() {
            return this == AUTO ? new VideoCodec[]{H264, HEVC} : this == H264_ONLY ? new VideoCodec[]{H264} : new VideoCodec[]{HEVC};
        }
        public static Choice parse(String name) {
            if (name != null) for (Choice c : values()) if (c.name().equals(name)) return c;
            return AUTO;
        }
        public static String[] labels() {
            Choice[] all = values(); String[] l = new String[all.length];
            for (int i = 0; i < all.length; i++) l[i] = all[i].label;
            return l;
        }
    }
}
