package ai.dvine.render.field;

import ai.dvine.render.WireRenderer;

/**
 * Everything on screen that isn't a body, made of nano particles in the 3D stage: the download bar floating in
 * front of the persona, a drawing beside its head, a thought above it, and the captions along the bottom of the
 * screen. Each is a {@link ParticleField}, so a new picture forms out of the old one. Placed every frame from the
 * persona's head (crown and chin), so it follows the persona and the camera. Pure Java.
 */
public final class ParticleDisplay {
    public static final int PANEL_POINTS = 5200, CAPTION_POINTS = 4200, THOUGHT_POINTS = 2600, DRAWING_POINTS = 7000;

    private final ParticleField panel = new ParticleField(PANEL_POINTS), caption = new ParticleField(CAPTION_POINTS),
            thought = new ParticleField(THOUGHT_POINTS), drawing = new ParticleField(DRAWING_POINTS);
    private final float[] buffer = new float[DRAWING_POINTS * ParticleField.STRIDE];
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
        drawingKey = key;
        drawing.show(s);
        drawingUntil = s == null || seconds <= 0f ? -1f : time + seconds;
    }

    public synchronized boolean drawingShown() {
        return drawing.used() > 0;
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
            drawingUntil = -1f;
            drawingKey = null;
            drawing.show(null);
        }
        float cx = head[0], crown = head[1], cz = head[2], chin = head[3];
        float tall = Math.max(1f, crown - floor), headSize = Math.max(0.2f, crown - chin);
        // the download bar floats at the persona's feet, toward the viewer; in the face close-up, under the chin
        float pw = bodyView ? tall * 0.66f : headSize * 2.1f;
        float py = bodyView ? floor + tall * 0.03f : chin - headSize * 0.45f;
        submit(r, WireRenderer.FIELD_PANEL, panel, false, cx, py, cz + (bodyView ? tall * 0.32f : headSize * 0.9f), pw / 2f, 2.3f);
        // the thought just above the head, and a drawing in the open space above that
        float tw = bodyView ? tall * 0.42f : headSize * 1.8f;
        submit(r, WireRenderer.FIELD_THOUGHT, thought, false, cx, crown + (bodyView ? tall * 0.07f : headSize * 0.35f), cz, tw / 2f, 2.1f);
        float dw = bodyView ? tall * 0.5f : headSize * 2.0f;
        submit(r, WireRenderer.FIELD_DRAWING, drawing, false, cx, crown + (bodyView ? tall * 0.36f : headSize * 1.5f), cz,
                dw / 2f, 2.6f);
        // captions: screen units, y up from -1 (bottom) to 1; the picture fits 92% of the width
        submit(r, WireRenderer.FIELD_CAPTION, caption, true, 0f, -0.8f, 0f, 0.92f * aspect, 2.0f);
    }

    private void submit(WireRenderer r, int layer, ParticleField f, boolean screen, float x, float y, float z, float scale, float size) {
        f.step(step);
        int n = f.pack(buffer);
        r.setField(layer, buffer, n, screen, x, y, z, scale, size);
    }
}
