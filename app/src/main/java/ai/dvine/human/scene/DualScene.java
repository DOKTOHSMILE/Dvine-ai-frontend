package ai.dvine.human.scene;

/**
 * Where Obi and Ivie stand in one shared world, and how they get there. Each
 * persona has its own root position (x across, z toward the user, body units,
 * the floor is the rig's) and facing (degrees, 0 faces the user, positive
 * turns toward +x). Arrangements:
 *
 * <ul>
 *   <li>{@link #SINGLE}: the persona on screen stands in the middle; the other is away.</li>
 *   <li>{@link #SIDE}: side by side, both facing the user.</li>
 *   <li>{@link #MEET}: facing each other at a talking distance.</li>
 *   <li>{@link #WALK}: both walk side by side across the stage and back, then stand side by side.</li>
 * </ul>
 *
 * A persona who isn't on stage enters from its own side, off screen, and
 * walks in (never appears in place). Walking eases in and slows near the
 * target, turns toward where it goes, then turns to its final facing. Bodies
 * are kept apart: a step that would bring the two closer than {@link #MIN_GAP}
 * is not taken. Pure Java, no Android; the stage reads it every frame.
 */
public final class DualScene {
    public static final String SINGLE = "single", SIDE = "side", MEET = "meet", WALK = "walk";
    /** Walking speed, body units per second (the walk cycle's stride). */
    public static final float SPEED = 2.2f;
    /** Turning speed, degrees per second. */
    public static final float TURN = 200f;
    /** The two body centres never come closer than this (shoulder to shoulder with a little air). */
    public static final float MIN_GAP = 1.7f;
    /** Spacing side by side and the talking distance (about 1.2 m at 1 unit = 0.33 m). */
    public static final float SIDE_GAP = 2.3f, TALK_GAP = 3.4f;
    /**
     * Relative heights when they share the stage (scaled from the floor): both bodies are
     * built to one standard height, so side by side Ivie is drawn at 0.93 of Obi (about 1.65 m to 1.77 m).
     */
    public static final float[] HEIGHT = {1f, 0.93f};
    /** Where an absent persona waits, off screen on its own side. */
    public static final float ENTRANCE_X = 7.5f;

    /** One persona's place. */
    public static final class Actor {
        public float x, z, yaw;
        public float tx, tz, tyaw;
        /** Current speed (eases up and down). */
        public float speed;
        public boolean present;
        /** True while it is walking (the stage plays the walk cycle). */
        public boolean walking;

        void place(float x, float z, float yaw) {
            this.x = tx = x;
            this.z = tz = z;
            this.yaw = tyaw = yaw;
            speed = 0f;
            walking = false;
        }
    }

    /** 0 Obi, 1 Ivie. Obi's side of the stage is -x (screen left), Ivie's +x. */
    public final Actor[] actor = {new Actor(), new Actor()};
    private String arrangement = SINGLE;
    private boolean focusIvie;
    /** The remaining legs of a walk-together (target x offsets), then side by side. */
    private final float[] legs = new float[4];
    private int leg = -1, legCount;
    private float legPause;

    public DualScene() {
        actor[0].place(0f, 0f, 0f);
        actor[0].present = true;
        actor[1].place(ENTRANCE_X, 0f, 0f);
    }

    public String arrangement() {
        return arrangement;
    }

    /** True when both are (or are becoming) on stage. */
    public boolean both() {
        return !arrangement.equals(SINGLE);
    }

    /** Is this persona on stage (visible)? */
    public boolean onStage(boolean ivie) {
        return actor[ivie ? 1 : 0].present;
    }

    /** The persona in front: in single, the only one on stage. */
    public void setFocus(boolean ivie) {
        focusIvie = ivie;
        if (!both()) {
            single(ivie);
        }
    }

    /** Changes the arrangement; returns false for an unknown name. */
    public boolean arrange(String name) {
        switch (name) {
            case SINGLE:
                arrangement = SINGLE;
                single(focusIvie);
                return true;
            case SIDE:
                arrangement = SIDE;
                enterOther();
                target(0, -SIDE_GAP * 0.5f, 0f, 0f);
                target(1, SIDE_GAP * 0.5f, 0f, 0f);
                leg = -1;
                return true;
            case MEET:
                arrangement = MEET;
                enterOther();
                // turned toward each other, a little open to the user so both faces read
                target(0, -TALK_GAP * 0.5f, 0f, 70f);
                target(1, TALK_GAP * 0.5f, 0f, -70f);
                leg = -1;
                return true;
            case WALK:
                arrangement = WALK;
                enterOther();
                // first gather side by side, then walk across and back together
                target(0, -SIDE_GAP * 0.5f, 0f, 0f);
                target(1, SIDE_GAP * 0.5f, 0f, 0f);
                legCount = 0;
                legs[legCount++] = -2.6f;
                legs[legCount++] = 2.6f;
                legs[legCount++] = 0f;
                leg = 0;
                legPause = 0f;
                return true;
            default:
                return false;
        }
    }

    private void single(boolean ivie) {
        int on = ivie ? 1 : 0, off = 1 - on;
        actor[on].present = true;
        target(on, 0f, 0f, 0f);
        if (actor[on].walking || Math.abs(actor[on].x) > ENTRANCE_X - 1f) {
            // it was away: it simply stands in the middle (the face close-up shows only it)
            actor[on].place(0f, 0f, 0f);
        }
        // the other leaves the stage at once (single view shows one persona)
        actor[off].present = false;
        actor[off].place(off == 0 ? -ENTRANCE_X : ENTRANCE_X, 0f, 0f);
    }

    /** The persona not on stage comes in from its own side. */
    private void enterOther() {
        for (int i = 0; i < 2; i++) {
            Actor a = actor[i];
            if (!a.present) {
                a.place(i == 0 ? -ENTRANCE_X : ENTRANCE_X, 0f, i == 0 ? 90f : -90f);
                a.present = true;
            }
        }
    }

    private void target(int i, float x, float z, float yaw) {
        Actor a = actor[i];
        a.tx = x;
        a.tz = z;
        a.tyaw = yaw;
    }

    /** True when neither is still walking or turning. */
    public boolean settled() {
        for (Actor a : actor) {
            if (a.present && (a.walking || Math.abs(angle(a.tyaw - a.yaw)) > 1f)) {
                return false;
            }
        }
        return leg < 0;
    }

    /** Advances both by {@code dt} seconds. */
    public void step(float dt) {
        if (dt <= 0f) {
            return;
        }
        dt = Math.min(dt, 0.1f);
        if (leg >= 0 && arrived(0) && arrived(1)) {
            legPause -= dt;
            if (legPause <= 0f) {
                if (leg < legCount) {
                    float cx = legs[leg++];
                    target(0, cx - SIDE_GAP * 0.5f, 0f, 0f);
                    target(1, cx + SIDE_GAP * 0.5f, 0f, 0f);
                    legPause = 0.4f;
                } else {
                    leg = -1;
                    arrangement = SIDE;
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            move(i, dt);
        }
    }

    private boolean arrived(int i) {
        Actor a = actor[i];
        return !a.present || !a.walking && Math.hypot(a.tx - a.x, a.tz - a.z) < 0.05f;
    }

    private void move(int i, float dt) {
        Actor a = actor[i];
        if (!a.present) {
            return;
        }
        float dx = a.tx - a.x, dz = a.tz - a.z;
        float dist = (float) Math.hypot(dx, dz);
        if (dist < 0.03f) {
            a.x = a.tx;
            a.z = a.tz;
            a.speed = 0f;
            a.walking = false;
            a.yaw = approach(a.yaw, a.tyaw, TURN * dt);
            return;
        }
        // face the way it walks first (a short turn on the spot), then go
        float heading = (float) Math.toDegrees(Math.atan2(dx, dz));
        a.yaw = approach(a.yaw, heading, TURN * dt);
        a.walking = true;
        if (Math.abs(angle(heading - a.yaw)) > 35f) {
            a.speed = Math.max(0f, a.speed - SPEED * 3f * dt);
            return;
        }
        // ease up to speed, slow down over the last stretch
        float want = SPEED * Math.min(1f, dist / 1.2f + 0.25f);
        a.speed += Math.max(-SPEED * 3f * dt, Math.min(SPEED * 2f * dt, want - a.speed));
        float s = Math.min(dist, a.speed * dt);
        float nx = a.x + dx / dist * s, nz = a.z + dz / dist * s;
        Actor o = actor[1 - i];
        if (o.present) {
            float before = (float) Math.hypot(o.x - a.x, o.z - a.z), after = (float) Math.hypot(o.x - nx, o.z - nz);
            if (after < MIN_GAP && after < before) {
                a.speed = 0f; // never walk into the other
                return;
            }
        }
        a.x = nx;
        a.z = nz;
    }

    /** Distance between the two body centres. */
    public float gap() {
        return (float) Math.hypot(actor[0].x - actor[1].x, actor[0].z - actor[1].z);
    }

    /**
     * The middle (x) and half width of where everyone on stage is going, body units,
     * for the camera: it frames the meeting place, and an entering persona walks into the frame.
     */
    public float[] span() {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Actor a : actor) {
            if (a.present) {
                lo = Math.min(lo, a.tx);
                hi = Math.max(hi, a.tx);
            }
        }
        if (lo > hi) {
            return new float[]{0f, 0f};
        }
        if (leg >= 0) {
            // walking together: frame the whole walk
            for (int k = 0; k < legCount; k++) {
                lo = Math.min(lo, legs[k] - SIDE_GAP * 0.5f);
                hi = Math.max(hi, legs[k] + SIDE_GAP * 0.5f);
            }
        }
        return new float[]{(lo + hi) * 0.5f, (hi - lo) * 0.5f};
    }

    static float angle(float d) {
        while (d > 180f) {
            d -= 360f;
        }
        while (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private static float approach(float from, float to, float max) {
        float d = angle(to - from);
        return Math.abs(d) <= max ? to : from + Math.signum(d) * max;
    }
}
