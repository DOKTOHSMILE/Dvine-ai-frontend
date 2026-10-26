package ai.dvine.human.touch;

import ai.dvine.human.Regions;

/**
 * One touch on the 3D body, after classification: which part (stable id,
 * key and spoken name), what kind of touch, where (world and bone-local
 * contact points, body units), when and for how long. A phone screen gives
 * no real pressure, so none is claimed.
 */
public final class BodyTouch {
    public enum Kind {
        /** A single short tap. */
        TAP,
        /** Another tap on the same part soon after the last one. */
        REPEAT,
        /** A finger held on the part. */
        HOLD
    }

    public final int region;
    public final Kind kind;
    /** Taps in this run of repeats (1 for the first). */
    public final int count;
    public final long timestampMs;
    public final long durationMs;
    public final float[] world;
    public final int bone;
    public final float[] local;

    public BodyTouch(int region, Kind kind, int count, long timestampMs, long durationMs, float[] world, int bone,
                     float[] local) {
        this.region = region;
        this.kind = kind;
        this.count = count;
        this.timestampMs = timestampMs;
        this.durationMs = durationMs;
        this.world = world;
        this.bone = bone;
        this.local = local;
    }

    public String key() {
        return Regions.key(region);
    }

    public String spoken() {
        return Regions.spoken(region);
    }
}
