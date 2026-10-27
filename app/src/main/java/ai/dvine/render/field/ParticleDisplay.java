package ai.dvine.render.field;

import ai.dvine.render.StageRenderer;

/**
 * Everything on screen besides the pod, made of particles in the 3D stage: a drawing or a thing built in 3D
 * floating above the pod (the user turns, pinches and moves it), the thought and the download bar above it, and
 * the captions along the bottom of the screen. Each is a {@link ParticleField}, so a new picture forms out of the
 * old one. Pure Java.
 */
public final class ParticleDisplay {
    public static final int PANEL_POINTS = 9000, CAPTION_POINTS = 12000, THOUGHT_POINTS = 6000, DRAWING_POINTS = 12000;

    private final ParticleField panel = new ParticleField(PANEL_POINTS), caption = new ParticleField(CAPTION_POINTS),
            thought = new ParticleField(THOUGHT_POINTS), drawing = new ParticleField(DRAWING_POINTS);
    private final float[] buffer = new float[Math.max(CAPTION_POINTS, Math.max(PANEL_POINTS, DRAWING_POINTS)) * ParticleField.STRIDE];

    public ParticleDisplay() {
        // words hold still and steady so they read crisply
        panel.setCalm(true);
        caption.setCalm(true);
        thought.setCalm(true);
    }
    private String panelKey, captionKey, thoughtKey, drawingKey;
    private float drawingUntil = -1f, time, step;

    /** The download bar (or any panel); null clears it. {@code key} skips re-forming an unchanged picture. */
    public synchronized void panel(String key, Shape s) {
        if (!same(key, panelKey)) {
            panelKey = key;
            panel.show(s);
        }
    }

    public synchronized void caption(String key, Shape s) {
        if (!same(key, captionKey)) {
            captionKey = key;
            caption.show(s);
        }
    }

    public synchronized void thought(String key, Shape s) {
        if (!same(key, thoughtKey)) {
            thoughtKey = key;
            thought.show(s);
        }
    }

    /** A drawing that stays for {@code seconds} (0: until replaced); null clears it. */
    public synchronized void drawing(String key, Shape s, float seconds) {
        drawing(key, s, seconds, false);
    }

    /** As {@link #drawing(String, Shape, float)}; {@code solid} things turn slowly so they read as 3D. */
    public synchronized void drawing(String key, Shape s, float seconds, boolean solid) {
        drawingKey = key;
        original = s;
        shown = s;
        drawing.show(s);
        stay = seconds;
        drawingUntil = s == null || seconds <= 0f ? -1f : time + seconds;
        size = 1f;
        yaw = 0f;
        pitch = 0f;
        turnTo = Float.NaN;
        tiltTo = Float.NaN;
        spinPitch = 0f;
        spin = solid ? 0.45f : 0f;
        dx = dy = 0f;
        scattered = false;
    }

    // what the user did to the thing on show: its size, turn, spin, place, colour, scattered or not
    private Shape original, shown;
    private float stay, size = 1f, yaw, pitch, turnTo = Float.NaN, tiltTo = Float.NaN, spin, spinPitch, dx, dy;
    private boolean scattered;

    /** Carries out a change to the thing on show; returns false when nothing is shown. */
    public synchronized boolean apply(ShapeCommand c) {
        if (original == null || drawing.used() == 0) {
            return false;
        }
        switch (c.op) {
            case BIGGER: size = Math.min(2.4f, size * 1.35f); break;
            case SMALLER: size = Math.max(0.35f, size / 1.35f); break;
            case SPIN: {
                float dir = c.a == 0f ? 1f : Math.signum(c.a);
                spin = dir * (spin != 0f && Math.signum(spin) == dir ? Math.min(2.5f, Math.abs(spin) * 1.5f) : 0.8f);
                spinPitch = 0f;
                turnTo = tiltTo = Float.NaN;
                break;
            }
            case SPIN_VERTICAL: {
                float dir = c.a == 0f ? 1f : Math.signum(c.a);
                spinPitch = dir * (spinPitch != 0f && Math.signum(spinPitch) == dir ? Math.min(2.5f, Math.abs(spinPitch) * 1.5f) : 0.8f);
                spin = 0f;
                turnTo = tiltTo = Float.NaN;
                break;
            }
            case FASTER:
                if (spin == 0f && spinPitch == 0f) {
                    spin = 0.8f;
                }
                spin = Math.signum(spin) * Math.min(3f, Math.abs(spin) * 1.6f);
                spinPitch = Math.signum(spinPitch) * Math.min(3f, Math.abs(spinPitch) * 1.6f);
                break;
            case SLOWER:
                spin /= 1.6f;
                spinPitch /= 1.6f;
                break;
            case STOP: spin = 0f; spinPitch = 0f; turnTo = Float.NaN; tiltTo = Float.NaN; break;
            case TURN: spin = 0f; turnTo = yaw + (float) Math.PI; break;
            case TURN_BY: spin = 0f; turnTo = (Float.isNaN(turnTo) ? yaw : turnTo) + (float) Math.toRadians(c.a); break;
            case TILT_BY: spinPitch = 0f; tiltTo = (Float.isNaN(tiltTo) ? pitch : tiltTo) + (float) Math.toRadians(c.a); break;
            case VIEW:
                spin = 0f;
                spinPitch = 0f;
                // the nearest way round to the asked-for angle
                float want = (float) Math.toRadians(c.a), full = (float) (Math.PI * 2);
                turnTo = want + full * Math.round((yaw - want) / full);
                tiltTo = (float) Math.toRadians(c.b);
                if (c.a == 0f && c.b == 0f) {
                    dx = dy = 0f;
                }
                break;
            case LEFT: dx -= 0.25f; break;
            case RIGHT: dx += 0.25f; break;
            case UP: dy += 0.2f; break;
            case DOWN: dy -= 0.2f; break;
            case COLOUR:
                shown = ShapeCommand.tint(original, c.colour);
                drawing.show(scattered ? ShapeCommand.scatter(shown, 5) : shown);
                break;
            case SCATTER:
                scattered = true;
                drawing.show(ShapeCommand.scatter(shown, 5));
                break;
            case GATHER:
                scattered = false;
                drawing.show(shown);
                break;
            case RESET:
                size = 1f;
                dx = dy = 0f;
                scattered = false;
                shown = original;
                drawing.show(shown);
                break;
            case CLEAR:
                drawing(null, null, 0f);
                return true;
            default:
                break;
        }
        if (stay > 0f) {
            drawingUntil = time + Math.max(stay, 60f); // being worked on: it stays
        }
        return true;
    }

    /**
     * The thing on show changed (a part taken out, recoloured, added): it re-forms in place, keeping how the user has
     * turned, zoomed and moved it. False when nothing is shown.
     */
    public synchronized boolean reshape(Shape s) {
        if (original == null || s == null || s.count == 0) {
            return false;
        }
        original = s;
        shown = s;
        drawing.show(scattered ? ShapeCommand.scatter(s, 5) : s);
        if (stay > 0f) {
            drawingUntil = time + Math.max(stay, 60f);
        }
        return true;
    }

    // ------------------------------------------------------------ the user's fingers on the thing on show

    /** A drag: turns it round (radians across) and tips it toward or away (radians down); its own spin stops. */
    public synchronized void rotateBy(float yawRad, float pitchRad) {
        spin = 0f;
        spinPitch = 0f;
        turnTo = Float.NaN;
        tiltTo = Float.NaN;
        // after "upside down" or a vertical spin the tilt can be anywhere: bring it back into range before the drag
        float full = (float) (Math.PI * 2);
        pitch -= full * Math.round(pitch / full);
        yaw += yawRad;
        pitch = Math.max(-1.45f, Math.min(1.45f, pitch + pitchRad));
    }

    /** A pinch: bigger (factor above 1) or smaller, from a quarter of its size to six times, for the details. */
    public synchronized void zoomBy(float factor) {
        size = Math.max(0.25f, Math.min(6f, size * factor));
    }

    /** Two fingers moving together: moves it (stage units). */
    public synchronized void panBy(float x, float y) {
        dx = Math.max(-1.5f, Math.min(1.5f, dx + x));
        dy = Math.max(-1.5f, Math.min(1.5f, dy + y));
    }

    public synchronized float drawingSize() {
        return size;
    }

    public synchronized boolean drawingShown() {
        return drawing.used() > 0;
    }

    /** What is on show ("lighthouse"), or null. */
    public synchronized String drawingName() {
        return drawing.used() > 0 ? drawingKey : null;
    }

    public synchronized boolean panelShown() {
        return panel.used() > 0;
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * One frame of the display over the pod (stage units: the stage's centre at the origin, the pod below): a drawing
     * or a thing built in 3D floating above the pod, turned, tipped, sized and moved as the user's fingers or words
     * set it; the thought above it while thinking; the download bar above that; the captions along the bottom.
     */
    public synchronized void frame(float dt, StageRenderer r, ai.dvine.render.pod.PodLayout l, float aspect) {
        step = dt;
        time += dt;
        if (drawingUntil > 0f && time > drawingUntil) {
            drawing(null, null, 0f);
        }
        if (!Float.isNaN(turnTo)) {
            yaw += (turnTo - yaw) * Math.min(1f, dt * 3f);
            if (Math.abs(turnTo - yaw) < 0.01f) {
                yaw = turnTo;
                turnTo = Float.NaN;
            }
        } else {
            yaw += spin * dt;
        }
        if (!Float.isNaN(tiltTo)) {
            pitch += (tiltTo - pitch) * Math.min(1f, dt * 3f);
            if (Math.abs(tiltTo - pitch) < 0.01f) {
                pitch = tiltTo;
                tiltTo = Float.NaN;
            }
        } else {
            pitch += spinPitch * dt;
        }
        submit(r, StageRenderer.FIELD_PANEL, panel, false, 0f, 0.62f, 0.1f, 0.6f, 1.9f);
        submit(r, StageRenderer.FIELD_THOUGHT, thought, false, 0f, 0.8f, 0.1f, 0.6f, 1.6f);
        step(drawing);
        int n = drawing.pack(buffer);
        if (yaw != 0f || pitch != 0f) {
            float c = (float) Math.cos(yaw), sn = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            for (int i = 0; i < n; i++) {
                int o = i * ParticleField.STRIDE;
                float x = buffer[o], y = buffer[o + 1], z = buffer[o + 2];
                float x1 = c * x + sn * z, z1 = -sn * x + c * z;
                buffer[o] = x1;
                buffer[o + 1] = cp * y - sp * z1;
                buffer[o + 2] = sp * y + cp * z1;
            }
        }
        float unit = Math.min(0.62f, 0.62f * aspect / 0.5f);
        r.setField(StageRenderer.FIELD_DRAWING, buffer, n, false, dx, 0.05f + dy, 0f, unit * size, 2.6f);
        // captions: screen units, y up from -1 (bottom) to 1; the picture fits 92% of the width
        submit(r, StageRenderer.FIELD_CAPTION, caption, true, 0f, -0.8f, 0f, 0.92f * aspect, 1.45f);
    }

    private void step(ParticleField f) {
        f.step(step);
    }

    private void submit(StageRenderer r, int layer, ParticleField f, boolean screen, float x, float y, float z, float scale, float size) {
        f.step(step);
        int n = f.pack(buffer);
        r.setField(layer, buffer, n, screen, x, y, z, scale, size);
    }
}
