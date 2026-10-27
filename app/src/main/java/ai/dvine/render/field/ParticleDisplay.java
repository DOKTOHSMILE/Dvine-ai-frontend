package ai.dvine.render.field;

import ai.dvine.render.WireRenderer;

/**
 * Everything on screen that isn't a body, made of nano particles in the 3D stage: the download bar floating in
 * front of the persona, a drawing beside its head, a thought above it, and the captions along the bottom of the
 * screen. Each is a {@link ParticleField}, so a new picture forms out of the old one. Placed every frame from the
 * persona's head (crown and chin), so it follows the persona and the camera. Pure Java.
 */
public final class ParticleDisplay {
    public static final int PANEL_POINTS = 9000, CAPTION_POINTS = 12000, THOUGHT_POINTS = 6000, DRAWING_POINTS = 7000;

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
        turnTo = Float.NaN;
        spin = solid ? 0.45f : 0f;
        dx = dy = 0f;
        scattered = false;
    }

    // what the user did to the thing on show: its size, turn, spin, place, colour, scattered or not
    private Shape original, shown;
    private float stay, size = 1f, yaw, turnTo = Float.NaN, spin, dx, dy;
    private boolean scattered;

    /** Carries out a change to the thing on show; returns false when nothing is shown. */
    public synchronized boolean apply(ShapeCommand c) {
        if (original == null || drawing.used() == 0) {
            return false;
        }
        switch (c.op) {
            case BIGGER: size = Math.min(2.4f, size * 1.35f); break;
            case SMALLER: size = Math.max(0.35f, size / 1.35f); break;
            case SPIN: spin = spin > 0f ? Math.min(2.5f, spin * 1.8f) : 0.8f; turnTo = Float.NaN; break;
            case STOP: spin = 0f; turnTo = Float.NaN; break;
            case TURN: spin = 0f; turnTo = yaw + (float) Math.PI; break;
            case LEFT: dx -= 0.35f; break;
            case RIGHT: dx += 0.35f; break;
            case UP: dy += 0.3f; break;
            case DOWN: dy -= 0.3f; break;
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
     * Steps the particles and hands them to the renderer. {@code head} is the persona's crown (x, y, z) and chin
     * height in stage units; {@code floor} the ground; {@code aspect} the screen's width over height.
     */
    public synchronized void frame(float dt, WireRenderer r, float[] head, float floor, boolean bodyView, float aspect) {
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
        float cx = head[0], crown = head[1], cz = head[2], chin = head[3];
        float tall = Math.max(1f, crown - floor), headSize = Math.max(0.2f, crown - chin);
        // the download bar floats at the persona's feet, toward the viewer; in the face close-up, under the chin
        float pw = bodyView ? tall * 0.66f : headSize * 2.1f;
        float py = bodyView ? floor + tall * 0.03f : chin - headSize * 0.45f;
        submit(r, WireRenderer.FIELD_PANEL, panel, false, cx, py, cz + (bodyView ? tall * 0.32f : headSize * 0.9f), pw / 2f, 1.9f);
        // the thought just above the head, and a drawing in the open space above that
        float tw = bodyView ? tall * 0.42f : headSize * 1.8f;
        submit(r, WireRenderer.FIELD_THOUGHT, thought, false, cx, crown + (bodyView ? tall * 0.07f : headSize * 0.35f), cz, tw / 2f, 1.6f);
        float dw = bodyView ? tall * 0.5f : headSize * 2.0f;
        step(drawing);
        int n = drawing.pack(buffer);
        if (yaw != 0f) {
            float c = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
            for (int i = 0; i < n; i++) {
                int o = i * ParticleField.STRIDE;
                float x = buffer[o], z = buffer[o + 2];
                buffer[o] = c * x + sn * z;
                buffer[o + 2] = -sn * x + c * z;
            }
        }
        float unit = dw / 2f;
        r.setField(WireRenderer.FIELD_DRAWING, buffer, n, false, cx + dx * unit * 2f,
                crown + (bodyView ? tall * 0.36f : headSize * 1.5f) + dy * unit * 2f, cz, unit * size, 2.6f);
        // captions: screen units, y up from -1 (bottom) to 1; the picture fits 92% of the width
        submit(r, WireRenderer.FIELD_CAPTION, caption, true, 0f, -0.76f, 0f, 0.92f * aspect, 1.45f);
    }

    private void step(ParticleField f) {
        f.step(step);
    }

    private void submit(WireRenderer r, int layer, ParticleField f, boolean screen, float x, float y, float z, float scale, float size) {
        f.step(step);
        int n = f.pack(buffer);
        r.setField(layer, buffer, n, screen, x, y, z, scale, size);
    }
}
