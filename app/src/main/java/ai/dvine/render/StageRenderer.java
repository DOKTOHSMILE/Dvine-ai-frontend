package ai.dvine.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Everything the stage draws with OpenGL ES 3: the pod in space ({@link ai.dvine.render.pod.PodRenderer}) and,
 * over it, the particle display (captions, the thought, the download bar,
 * drawings and things built in 3D). A new GL context rebuilds every GL object; {@link #dispose} frees them.
 */
public final class StageRenderer {
    private final GlApi gl;
    private final Object lock = new Object();
    public final ai.dvine.render.pod.PodRenderer pod;
    public final FrameStats stats = new FrameStats();
    private int width = 1, height = 1;
    private int fieldProg;
    /** Count of live GL objects of the particle display (for leak checks). */
    public int liveObjects;
    public int glErrors;
    /** What went wrong first, in words (null while nothing has). */
    public volatile String fault;
    /** Set by the view: pixels per dp, so dots are the same size on every screen. */
    public volatile float displayDensity = 1f;
    private final float[] fieldMvp = new float[16];
    private final float[] pendingMvp = new float[16];

    public StageRenderer(GlApi gl) {
        this.gl = gl;
        pod = new ai.dvine.render.pod.PodRenderer(gl);
        pendingMvp[0] = pendingMvp[5] = pendingMvp[10] = pendingMvp[15] = 1f;
    }

    // ------------------------------------------------------------ the particle display

    /** Layers of the particle display: the download bar, captions on the screen, the thought above the head, a drawing. */
    public static final int FIELD_PANEL = 0, FIELD_CAPTION = 1, FIELD_THOUGHT = 2, FIELD_DRAWING = 3;

    private static final class Field {
        int vbo, count, shown, uploaded = -1;
        boolean screen, dirty;
        float[] data = new float[0];
        final float[] place = {0f, 0f, 0f, 1f};
        float size = 3f;
        /** Words: a crisp dot with a clean edge instead of a soft glow. */
        boolean crisp;
    }

    private final Field[] fields = {new Field(), new Field(), new Field(), new Field()};

    /**
     * This frame's particles for a layer ({@link ai.dvine.render.field.ParticleField#pack} format, {@code count} of
     * them), placed at (x, y, z) and scaled: in the scene's stage units, or in screen units (-1..1, y up) when
     * {@code screen}. {@code size} is the dot size (pixels on screen; in the scene it shrinks with distance).
     */
    public void setField(int layer, float[] packed, int count, boolean screen, float x, float y, float z, float scale, float size) {
        Field f = fields[layer];
        f.crisp = layer != FIELD_DRAWING;
        synchronized (lock) {
            int n = count * ai.dvine.render.field.ParticleField.STRIDE;
            if (f.data.length < n) {
                f.data = new float[n];
            }
            System.arraycopy(packed, 0, f.data, 0, n);
            f.count = count;
            f.screen = screen;
            f.place[0] = x;
            f.place[1] = y;
            f.place[2] = z;
            f.place[3] = scale;
            f.size = size;
            f.dirty = true;
        }
    }

    /** The scene's projection for the particle layers placed in it (stage units, from the pod's layout). */
    public void setProjection(float[] mvp) {
        synchronized (lock) {
            System.arraycopy(mvp, 0, pendingMvp, 0, 16);
        }
    }

    /** The screen's width over its height. */
    public float aspect() {
        return width / (float) Math.max(1, height);
    }

    // ------------------------------------------------------------ GL thread

    /** A (new) GL context: everything is created from scratch (old handles died with the old context). */
    public void onCreated() {
        liveObjects = 0;
        fieldProg = gl.program(FIELD_VS, FIELD_FS);
        if (fieldProg != 0) {
            liveObjects++;
        } else {
            glErrors++;
            if (fault == null) {
                fault = "a shader that wouldn't compile";
            }
        }
        for (Field fl : fields) {
            fl.vbo = gl.buffer();
            liveObjects++;
            fl.uploaded = -1;
            fl.dirty = fl.count > 0;
        }
        pod.onCreated();
    }

    public void onSize(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        gl.viewport(width, height);
    }

    public void onDraw(long nanos) {
        stats.frame(nanos);
        synchronized (lock) {
            System.arraycopy(pendingMvp, 0, fieldMvp, 0, 16);
        }
        pod.onDraw(nanos, width, height);
        drawFields();
        int err = gl.error();
        if (err != GlApi.NO_ERROR) {
            glErrors++;
            if (fault == null) {
                fault = String.format(java.util.Locale.US, "graphics error 0x%x while drawing", err);
            }
        }
    }

    private void drawFields() {
        if (fieldProg == 0) {
            return;
        }
        boolean any = false;
        for (Field f : fields) {
            any |= f.count > 0;
        }
        if (!any) {
            return;
        }
        int p = fieldProg;
        gl.useProgram(p);
        gl.uniformMatrix4(gl.uniform(p, "uMvp"), fieldMvp);
        gl.uniform4f(gl.uniform(p, "uAspect"), height / (float) Math.max(1, width), 1f, displayDensity,
                ai.dvine.render.pod.PodLayout.EYE);
        gl.disable(GlApi.DEPTH_TEST);
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.ONE, GlApi.ONE);
        gl.depthMask(false);
        for (Field f : fields) {
            int n;
            synchronized (lock) {
                n = f.count;
                if (n > 0 && f.dirty) {
                    gl.bindBuffer(GlApi.ARRAY_BUFFER, f.vbo);
                    int floats = n * ai.dvine.render.field.ParticleField.STRIDE;
                    FloatBuffer data = floats(java.util.Arrays.copyOf(f.data, floats));
                    if (f.uploaded < n) {
                        gl.bufferData(GlApi.ARRAY_BUFFER, floats * 4, data, GlApi.DYNAMIC_DRAW);
                        f.uploaded = n;
                    } else {
                        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, floats * 4, data);
                    }
                    f.dirty = false;
                    f.shown = n;
                }
            }
            if (n <= 0 || f.shown <= 0) {
                continue;
            }
            gl.uniform4f(gl.uniform(p, "uPlace"), f.place[0], f.place[1], f.place[2], f.place[3]);
            gl.uniform4f(gl.uniform(p, "uMode"), f.screen ? 1f : 0f, f.size, f.crisp ? 1f : 0f, 0f);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, f.vbo);
            gl.vertexAttrib(0, 3, 28, 0);
            gl.vertexAttrib(1, 4, 28, 12);
            gl.drawArrays(GlApi.POINTS, 0, f.shown);
            gl.disableAttrib(0);
            gl.disableAttrib(1);
        }
        gl.depthMask(true);
        gl.disable(GlApi.BLEND);
    }

    /** Frees every GL object (view detached). Safe to call twice. */
    public void dispose() {
        pod.dispose();
        if (fieldProg != 0) {
            gl.deleteProgram(fieldProg);
            liveObjects--;
            fieldProg = 0;
        }
        for (Field fl : fields) {
            if (fl.vbo != 0) {
                gl.deleteBuffer(fl.vbo);
                liveObjects--;
                fl.vbo = 0;
            }
        }
    }

    private static FloatBuffer floats(float[] a) {
        FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(a).flip();
        return b;
    }

    static final String FIELD_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec4 aCol;\n"
            + "uniform mat4 uMvp; uniform vec4 uPlace; uniform vec4 uMode; uniform vec4 uAspect; out vec4 vCol;\n"
            + "void main(){ vec3 p = aPos * uPlace.w;\n"
            + "  if (uMode.x > 0.5) { gl_Position = vec4((p.x + uPlace.x) * uAspect.x, p.y + uPlace.y, 0.0, 1.0); gl_PointSize = uMode.y * uAspect.z; }\n"
            // in the scene the dots shrink with distance, like everything else there
            + "  else { gl_Position = uMvp * vec4(p + uPlace.xyz, 1.0); gl_PointSize = clamp(uMode.y * uAspect.z * uAspect.w / max(gl_Position.w, 0.1), 1.0, 18.0); }\n"
            + "  vCol = aCol; }\n";
    static final String FIELD_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec4 vCol; uniform highp vec4 uMode; out vec4 o;\n"
            // a soft glowing dot with a bright core; for words a crisp round dot with a clean edge
            + "void main(){ vec2 c = gl_PointCoord * 2.0 - 1.0; float d = dot(c, c); if (d > 1.0) discard;\n"
            + "  float a = (uMode.z > 0.5 ? (1.0 - smoothstep(0.45, 1.0, d)) * 0.95 : exp(-d * 3.0) * 0.8 + exp(-d * 12.0) * 0.6) * vCol.a;\n"
            + "  o = vec4(vCol.rgb * a, a); }\n";
}
