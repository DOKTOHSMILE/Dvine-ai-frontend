package ai.dvine.anre;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The particle transformation state machine. One set of particles is the
 * medium; this decides, from the clock, where they come from, where they go
 * and how far along they are:
 * <pre>
 * BODY_INTACT → BODY_DISSOLVING → PARTICLES_IN_TRANSIT → OBJECT_ASSEMBLING → OBJECT_COMPLETE
 * OBJECT_COMPLETE ⇄ OBJECT_MODIFYING
 * OBJECT_COMPLETE → OBJECT_DISSOLVING → BODY_RECONSTRUCTING → BODY_RESTORED → BODY_INTACT
 * any transition → TRANSFORMATION_FAILED → BODY_RECONSTRUCTING (the body always comes back)
 * </pre>
 * The canonical body is never touched: only the particles move. An object
 * is only accepted when it validated; BODY_RESTORED is only reported once the
 * body passes its integrity check. Cancelling a transformation in flight
 * reverses it smoothly (the particles' order is symmetric, so reversing the
 * progress sends every particle back the way it came).
 */
public final class TransformationController {
    public enum State {
        BODY_INTACT, BODY_DISSOLVING, PARTICLES_IN_TRANSIT, OBJECT_ASSEMBLING, OBJECT_COMPLETE, OBJECT_MODIFYING,
        OBJECT_DISSOLVING, BODY_RECONSTRUCTING, BODY_RESTORED, TRANSFORMATION_FAILED
    }

    /** Placements, matching the renderer's. */
    public static final int BODY = 0, OBJECT = 1, SCATTER = 2, PREVIOUS = 3;

    /** What the renderer should draw now. */
    public static final class View {
        public int from = BODY, to = BODY;
        public float progress = 1f;
        public boolean byObject;
        public boolean reverse;
        public boolean bodyFill = true;
        public boolean objectShown;
    }

    static final float TO_OBJECT = 4.2f, TO_BODY = 3.6f, MODIFY = 1.4f, ASSEMBLE = 3.2f, RESTORED_HOLD = 0.6f;
    private static final Map<State, Set<State>> NEXT = new EnumMap<State, Set<State>>(State.class);

    static {
        NEXT.put(State.BODY_INTACT, EnumSet.of(State.BODY_DISSOLVING, State.BODY_RECONSTRUCTING));
        NEXT.put(State.BODY_DISSOLVING, EnumSet.of(State.PARTICLES_IN_TRANSIT, State.BODY_RECONSTRUCTING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.PARTICLES_IN_TRANSIT, EnumSet.of(State.OBJECT_ASSEMBLING, State.BODY_RECONSTRUCTING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.OBJECT_ASSEMBLING, EnumSet.of(State.OBJECT_COMPLETE, State.BODY_RECONSTRUCTING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.OBJECT_COMPLETE, EnumSet.of(State.OBJECT_MODIFYING, State.OBJECT_DISSOLVING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.OBJECT_MODIFYING, EnumSet.of(State.OBJECT_COMPLETE, State.OBJECT_DISSOLVING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.OBJECT_DISSOLVING, EnumSet.of(State.BODY_RECONSTRUCTING, State.TRANSFORMATION_FAILED));
        NEXT.put(State.BODY_RECONSTRUCTING, EnumSet.of(State.BODY_RESTORED, State.TRANSFORMATION_FAILED, State.BODY_DISSOLVING));
        NEXT.put(State.BODY_RESTORED, EnumSet.of(State.BODY_INTACT, State.BODY_DISSOLVING));
        NEXT.put(State.TRANSFORMATION_FAILED, EnumSet.of(State.BODY_RECONSTRUCTING));
    }

    private State state = State.BODY_INTACT;
    private float start;
    private float duration = 1f;
    /** The current morph's endpoints and ordering. */
    private int from = BODY, to = BODY;
    private boolean byObject, reverse;
    /** Progress offset when a morph starts part way (a reversal). */
    private float startAt;
    private final BooleanSupplier bodyOk;
    private String failure;
    private boolean hasObject;

    public TransformationController(BooleanSupplier bodyIntegrity) {
        this.bodyOk = bodyIntegrity == null ? () -> true : bodyIntegrity;
    }

    public State state() {
        return state;
    }

    public String failure() {
        return failure;
    }

    public boolean busy() {
        return state != State.BODY_INTACT && state != State.OBJECT_COMPLETE && state != State.BODY_RESTORED;
    }

    /** Whether the particles are (or are becoming) an object. */
    public boolean objectMode() {
        return hasObject && state != State.BODY_INTACT && state != State.BODY_RESTORED && state != State.BODY_RECONSTRUCTING;
    }

    /** Mode C: the body assembles from a cloud of particles, head first (at start-up, or "rebuild yourself"). */
    public boolean assemble(float now) {
        if (state != State.BODY_INTACT && state != State.BODY_RESTORED) {
            return false;
        }
        go(State.BODY_RECONSTRUCTING, now, SCATTER, BODY, ASSEMBLE, false, true, 0f);
        hasObject = false;
        return true;
    }

    /** Body → object. Only for a validated object (the caller's model.valid()). */
    public boolean begin(boolean objectValid, float now) {
        if (!objectValid) {
            failure = "the object did not pass validation";
            return false;
        }
        if (state != State.BODY_INTACT && state != State.BODY_RESTORED && state != State.BODY_RECONSTRUCTING) {
            return false;
        }
        float at = 0f;
        if (state == State.BODY_RECONSTRUCTING && to == BODY && from == OBJECT) {
            at = 1f - progress(now); // turning back toward the object mid-way
        }
        hasObject = true;
        go(State.BODY_DISSOLVING, now, BODY, OBJECT, TO_OBJECT, true, false, at);
        return true;
    }

    /** The object changed (a validated new version): the particles morph from the old version to the new one. */
    public boolean modify(boolean objectValid, float now) {
        if (!objectValid || (state != State.OBJECT_COMPLETE && state != State.OBJECT_MODIFYING)) {
            return false;
        }
        go(State.OBJECT_MODIFYING, now, PREVIOUS, OBJECT, MODIFY, true, false, 0f);
        return true;
    }

    /** Object → body ("return to your normal form"). */
    public boolean restore(float now) {
        if (state == State.OBJECT_COMPLETE || state == State.OBJECT_MODIFYING) {
            go(State.OBJECT_DISSOLVING, now, OBJECT, BODY, TO_BODY, true, true, 0f);
            return true;
        }
        return cancel(now);
    }

    /** Stops a transformation in flight: the particles turn round and the body comes back. */
    public boolean cancel(float now) {
        if (state == State.BODY_DISSOLVING || state == State.PARTICLES_IN_TRANSIT || state == State.OBJECT_ASSEMBLING) {
            float p = progress(now);
            go(State.BODY_RECONSTRUCTING, now, OBJECT, BODY, TO_OBJECT, true, true, 1f - p);
            return true;
        }
        return false;
    }

    /** Something broke (an invalid object, a rendering failure): the body is rebuilt from a cloud. */
    public void fail(String why, float now) {
        failure = why;
        state = State.TRANSFORMATION_FAILED;
        hasObject = false;
        go(State.BODY_RECONSTRUCTING, now, SCATTER, BODY, ASSEMBLE, false, true, 0f);
    }

    /** Advances the state with the clock and says what to draw. */
    public View view(float now) {
        float p = progress(now);
        switch (state) {
            case BODY_DISSOLVING:
            case PARTICLES_IN_TRANSIT:
            case OBJECT_ASSEMBLING:
                if (p >= 1f) {
                    set(State.OBJECT_COMPLETE);
                    from = OBJECT;
                    to = OBJECT;
                } else {
                    State want = p < 0.3f ? State.BODY_DISSOLVING : p < 0.65f ? State.PARTICLES_IN_TRANSIT : State.OBJECT_ASSEMBLING;
                    if (want != state && NEXT.get(state).contains(want)) {
                        set(want);
                    }
                }
                break;
            case OBJECT_MODIFYING:
                if (p >= 1f) {
                    set(State.OBJECT_COMPLETE);
                    from = OBJECT;
                    to = OBJECT;
                }
                break;
            case OBJECT_DISSOLVING:
                if (p >= 0.45f) {
                    set(State.BODY_RECONSTRUCTING);
                }
                break;
            case BODY_RECONSTRUCTING:
                if (p >= 1f) {
                    if (bodyOk.getAsBoolean()) {
                        set(State.BODY_RESTORED);
                        start = now;
                        from = BODY;
                        to = BODY;
                        hasObject = false;
                    } else {
                        // the body did not check out: rebuild it again from a clean cloud
                        failure = "the body failed its integrity check";
                        state = State.TRANSFORMATION_FAILED;
                        go(State.BODY_RECONSTRUCTING, now, SCATTER, BODY, ASSEMBLE, false, true, 0f);
                    }
                }
                break;
            case BODY_RESTORED:
                if (now - start >= RESTORED_HOLD) {
                    set(State.BODY_INTACT);
                }
                break;
            default:
                break;
        }
        View v = new View();
        v.from = from;
        v.to = to;
        v.progress = state == State.OBJECT_COMPLETE || state == State.BODY_INTACT || state == State.BODY_RESTORED ? 1f : progress(now);
        v.byObject = byObject;
        v.reverse = reverse;
        v.bodyFill = state == State.BODY_INTACT || state == State.BODY_RESTORED;
        v.objectShown = state == State.OBJECT_COMPLETE || state == State.OBJECT_MODIFYING;
        return v;
    }

    private float progress(float now) {
        return Math.max(0f, Math.min(1f, startAt + (now - start) / duration));
    }

    private void go(State s, float now, int f, int t, float secs, boolean object, boolean rev, float at) {
        if (state != State.TRANSFORMATION_FAILED && s != state && !NEXT.get(state).contains(s)) {
            throw new IllegalStateException(state + " → " + s);
        }
        state = s;
        start = now;
        from = f;
        to = t;
        duration = secs;
        byObject = object;
        reverse = rev;
        startAt = at;
    }

    private void set(State s) {
        if (!NEXT.get(state).contains(s)) {
            throw new IllegalStateException(state + " → " + s);
        }
        state = s;
    }
}
