package ai.dvine.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import ai.dvine.human.HumanAsset;

/**
 * ANRE, the anatomical nanoparticle renderer. The digital humans are drawn
 * only as luminous particles on black:
 * <ol>
 *   <li>a dark fill of the body that writes depth (pushed slightly back), so
 *   particles on the far side are hidden;</li>
 *   <li>the particles, one point each: the GPU places every particle on its
 *   triangle (u·A + v·B + w·C of the skinned corners, fetched from a float
 *   texture), so they follow the skin as it moves, and lights it brighter
 *   where the surface turns away (a glowing silhouette).</li>
 * </ol>
 * The same particles are also the medium for transformations: each frame
 * they morph between two placements (on the body, scattered around it, or
 * on an object's surface) with a staggered, arcing transit, which is how the
 * body assembles, dissolves into an object, and comes back.
 * Plus a dotted floor. All data is batched per persona; per frame only the
 * skinned positions and normals are uploaded.
 * Pure Java over {@link GlApi}; call everything on the GL thread except
 * {@link #setAsset} and {@link #submit}.
 */
public final class WireRenderer {
    public static final int SLOTS = 2;
    /** Inner anatomy parts the shader can filter (one float each). */
    public static final int MAX_PARTS = 80;
    static final int TEX_W = 1024;

    /** What to draw for one persona this frame (filled on the UI thread). */
    public static final class Frame {
        public float[] pos;
        public float[] nor;
        public float r, g, b;
        public float alpha = 1f;
        /** Particle size factor (1 close-up; smaller far away). */
        public float lod = 1f;
        public boolean visible;
        /** Transition: where particles come from and go to, and how far along (0..1). */
        public int from = PLACE_BODY, to = PLACE_BODY;
        public float progress = 1f;
        /**
         * Which order sequences the transit: each particle's body height, or its
         * build order on the object (matched by rank to its body height, so the
         * body leaves feet first while the object builds in order); reversed
         * runs it backwards (the head comes back first).
         */
        public boolean orderByObject;
        public boolean headFirst;
        /** The scattered cloud: an offset from the body and how far particles stray from their place (body units). */
        public final float[] scatter = {0f, 0.2f, 0f, 0.9f};
        /** Object placement (object space → body space), and the component to highlight (-1 none). */
        public final float[] object = identity();
        public float selected = -1f;
        /** Body fill on (hides particles behind the body); off while the body is particles in flight. */
        public boolean bodyFill = true;
        /** Mode B: the anatomical view (skin as a faint shell, the chosen inner parts shown). */
        public boolean anatomy;
        /** Lifelike skin (lit, tone below) instead of the hologram, while the body is whole. */
        public boolean skin;
        /** Skin tone, linear RGB. */
        public final float[] tone = {0.25f, 0.12f, 0.07f};
        public final float[] showLayer = new float[4];
        public final float[] showPart = new float[MAX_PARTS];
    }

    /** Where particles can be: on the body, on the object, scattered in a cloud, on the object's previous version. */
    public static final int PLACE_BODY = 0, PLACE_OBJECT = 1, PLACE_SCATTER = 2, PLACE_PREVIOUS = 3;

    private final GlApi gl;
    private final Slot[] slots = new Slot[SLOTS];
    private final Camera camera = new Camera();
    private final Object lock = new Object();
    private final Camera pendingCamera = new Camera();
    private float density = 2f;
    private float floorY = -4.22f;
    private int width = 1, height = 1;
    public final FrameStats stats = new FrameStats();

    // programs and shared objects
    private int fillProg, particleProg, innerProg, gridProg, skinProg, studioProg, fieldProg;
    private int gridVbo, gridCount, studioVbo;
    /** Count of live GL objects (for leak checks). */
    public int liveObjects;
    public int glErrors;
    /** What went wrong first, in words (null while nothing has). */
    public volatile String fault;

    private static final class Slot {
        HumanAsset asset;
        boolean dirtyStatic;
        int vertices, triIndices, particles, texH, innerVertices, innerParticles;
        FloatBuffer innerCorner, innerBary;
        IntBuffer tri;
        FloatBuffer corner, bary, aux;
        /** Per vertex: 0 off the eyes; on an eyeball 2 + how far it faces forward at rest (pupil 3, sides 2). */
        FloatBuffer eye;
        /** Per vertex for the lifelike skin: material, warmth, feature mask (see SkinMaterial); and the rest positions. */
        FloatBuffer mat, rest;
        /** The head frame (origin, scales) for drawing brows, hairline and lips in head units. */
        final float[] headFrame = new float[7];
        /** The hairstyle's number (Look.HAIR_STYLES) for the scalp painting. */
        int hairStyle = 1;
        /** Where the feet meet the floor this frame (x, z) and whether any are near it, for the contact shadow. */
        final float[] feet = new float[3];
        /** Clothes for the lifelike look: each garment's triangles and its index buffer. */
        ai.dvine.human.Clothing.Garment[] garments = new ai.dvine.human.Clothing.Garment[0];
        int[] garmentIbo = new int[0];
        // the object the particles can become (targets aligned one-to-one with the particles)
        FloatBuffer target, previous;
        boolean dirtyTarget;
        IntBuffer objTri;
        FloatBuffer objPos, objNor;
        int objIndices, objVertices;
        // double-buffered dynamic data
        FloatBuffer pos, nor, texPos, texNor;
        final Frame pending = new Frame();
        final Frame shown = new Frame();
        boolean hasPending;
        // GL handles
        int vboInCorner, vboInBary;
        int vboPos, vboNor, vboEye, vboMat, vboRest, ibo, vboCorner, vboBary, vboAux, vboTarget, vboPrev, objVbo, objNbo, objIbo, tPos, tNor;
        boolean created;
    }

    public WireRenderer(GlApi gl) {
        this.gl = gl;
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new Slot();
        }
    }

    public void setDensity(float d) {
        density = d;
    }

    // ------------------------------------------------------------ UI thread

    /** Sets (or replaces) the asset and its particle skin for a slot; GL objects are rebuilt on the next frame. */
    public void setAsset(int slot, HumanAsset a, ai.dvine.anre.ParticleSet p) {
        setAsset(slot, a, p, null);
    }

    /** As above, plus the inner anatomy's particles (corner indices already offset past the skin's vertices). */
    public void setAsset(int slot, HumanAsset a, ai.dvine.anre.ParticleSet p, ai.dvine.anre.ParticleSet inner) {
        Slot s = slots[slot];
        synchronized (lock) {
            s.asset = a;
            int n = a.mesh.vertexCount();
            s.vertices = n;
            s.innerVertices = inner == null || a.inner == null ? 0 : a.inner.vertexCount();
            s.texH = (n + s.innerVertices + TEX_W - 1) / TEX_W;
            s.innerParticles = s.innerVertices == 0 ? 0 : inner.count;
            if (s.innerParticles > 0) {
                float[] ic = new float[inner.count * 4], ib = new float[inner.count * 4];
                for (int i = 0; i < inner.count; i++) {
                    ic[i * 4] = inner.corners[i * 3];
                    ic[i * 4 + 1] = inner.corners[i * 3 + 1];
                    ic[i * 4 + 2] = inner.corners[i * 3 + 2];
                    ic[i * 4 + 3] = inner.part[i];
                    ib[i * 4] = inner.u[i];
                    ib[i * 4 + 1] = inner.v[i];
                    ib[i * 4 + 2] = inner.size[i];
                    ib[i * 4 + 3] = inner.order[i];
                }
                s.innerCorner = floats(ic);
                s.innerBary = floats(ib);
            }
            s.tri = ints(a.mesh.tri);
            float[] eye = new float[n];
            for (int e = 0; e < 2; e++) {
                float ex = a.eyeCenter[e * 3], ey = a.eyeCenter[e * 3 + 1], ez = a.eyeCenter[e * 3 + 2];
                for (int v = a.eyeRange[e * 2]; v < a.eyeRange[e * 2] + a.eyeRange[e * 2 + 1] && v < n; v++) {
                    float x = a.mesh.pos[v * 3] - ex, y = a.mesh.pos[v * 3 + 1] - ey, z = a.mesh.pos[v * 3 + 2] - ez;
                    float l = (float) Math.sqrt(x * x + y * y + z * z);
                    eye[v] = 2f + (l > 0f ? z / l : 0f);
                }
            }
            s.eye = floats(eye);
            s.mat = floats(SkinMaterial.perVertex(a));
            s.rest = floats(java.util.Arrays.copyOf(a.mesh.pos, n * 3));
            s.garments = ai.dvine.human.Clothing.design(a, a.hairStyle());
            s.hairStyle = java.util.Arrays.asList(ai.dvine.face.Look.HAIR_STYLES).indexOf(a.hairStyle());
            float[] hf = a.headFrame;
            for (int k = 0; k < 7; k++) {
                s.headFrame[k] = k < hf.length ? hf[k] : hf[4];
            }
            s.triIndices = a.mesh.tri.length;
            int np = p.count;
            s.particles = np;
            float[] corner = new float[np * 4], bary = new float[np * 4], aux = new float[np * 4];
            for (int i = 0; i < np; i++) {
                corner[i * 4] = p.corners[i * 3];
                corner[i * 4 + 1] = p.corners[i * 3 + 1];
                corner[i * 4 + 2] = p.corners[i * 3 + 2];
                corner[i * 4 + 3] = p.part[i];
                bary[i * 4] = p.u[i];
                bary[i * 4 + 1] = p.v[i];
                bary[i * 4 + 2] = p.size[i];
                bary[i * 4 + 3] = p.order[i];
                // a fixed outward scatter direction per particle (for assembly and dissolving)
                double th = hash(i * 3L + 1) * Math.PI * 2, ph = Math.acos(2 * hash(i * 3L + 2) - 1);
                float r = 0.35f + 0.65f * (float) hash(i * 3L + 3);
                aux[i * 4] = (float) (Math.sin(ph) * Math.cos(th)) * r;
                aux[i * 4 + 1] = (float) Math.cos(ph) * r;
                aux[i * 4 + 2] = (float) (Math.sin(ph) * Math.sin(th)) * r;
                aux[i * 4 + 3] = 0f;
            }
            s.corner = floats(corner);
            s.bary = floats(bary);
            s.aux = floats(aux);
            s.target = floats(new float[np * 4]);
            s.previous = floats(new float[np * 4]);
            s.objTri = null;
            s.objIndices = 0;
            s.pos = FloatBuffer.allocate((n + s.innerVertices) * 3);
            s.nor = FloatBuffer.allocate((n + s.innerVertices) * 3);
            s.texPos = direct(TEX_W * s.texH * 3);
            s.texNor = direct(TEX_W * s.texH * 3);
            int all = n + s.innerVertices;
            s.pending.pos = new float[all * 3];
            s.pending.nor = new float[all * 3];
            s.shown.pos = new float[all * 3];
            s.shown.nor = new float[all * 3];
            s.dirtyStatic = true;
            s.hasPending = false;
        }
    }

    /**
     * The object this slot's particles can become: its surface (object space,
     * for the dark occluding fill) and one target per particle (xyz, component
     * id, build order 0..1), as made by the object engine.
     */
    public void setObject(int slot, float[] objPos, float[] objNor, int[] objTri, float[] targets) {
        Slot s = slots[slot];
        synchronized (lock) {
            if (s.asset == null || targets.length != s.particles * 5) {
                return;
            }
            float[] t = new float[s.particles * 4];
            float[] aux = new float[s.particles * 4];
            s.aux.position(0);
            s.aux.get(aux);
            for (int i = 0; i < s.particles; i++) {
                t[i * 4] = targets[i * 5];
                t[i * 4 + 1] = targets[i * 5 + 1];
                t[i * 4 + 2] = targets[i * 5 + 2];
                t[i * 4 + 3] = targets[i * 5 + 3];
                aux[i * 4 + 3] = targets[i * 5 + 4];
            }
            s.previous = s.target; // the version it was, for a direct morph to the new one
            s.target = floats(t);
            s.aux = floats(aux);
            s.objPos = floats(objPos);
            s.objNor = floats(objNor);
            s.objTri = ints(objTri);
            s.objIndices = objTri.length;
            s.objVertices = objPos.length / 3;
            s.dirtyTarget = true;
        }
    }

    /** The transition and object state for this frame (set together with {@link #submit}). */
    public void transition(int slot, int from, int to, float progress, boolean orderByObject, boolean headFirst,
                           float[] object, float selected, boolean bodyFill, float[] scatter) {
        Slot s = slots[slot];
        synchronized (lock) {
            Frame f = s.pending;
            if (scatter != null) {
                System.arraycopy(scatter, 0, f.scatter, 0, 4);
            }
            f.from = from;
            f.to = to;
            f.progress = progress;
            f.orderByObject = orderByObject;
            f.headFirst = headFirst;
            if (object != null) {
                System.arraycopy(object, 0, f.object, 0, 16);
            }
            f.selected = selected;
            f.bodyFill = bodyFill;
        }
    }

    /** Hands over this frame's positions (body units) and look for a slot. Copies; never blocks for long. */
    public void submit(int slot, float[] pos, float[] nor, float r, float g, float b, float alpha, float lod, boolean visible) {
        Slot s = slots[slot];
        synchronized (lock) {
            if (s.asset == null) {
                return;
            }
            Frame f = s.pending;
            if (visible) {
                System.arraycopy(pos, 0, f.pos, 0, s.vertices * 3);
                System.arraycopy(nor, 0, f.nor, 0, s.vertices * 3);
            }
            f.r = r;
            f.g = g;
            f.b = b;
            f.alpha = alpha;
            f.lod = lod;
            f.visible = visible;
            s.hasPending = true;
        }
    }

    // ------------------------------------------------------------ the particle display (everything shown that isn't a body)

    /** Layers of the particle display: a panel in the stage (the download bar), captions on the screen, a thought above the head, a drawing. */
    public static final int FIELD_PANEL = 0, FIELD_CAPTION = 1, FIELD_THOUGHT = 2, FIELD_DRAWING = 3;

    private static final class Field {
        int vbo, uploaded = -1, count, shown;
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
     * them), placed at (x, y, z) and scaled: in body units in the stage, or in screen units (-1..1, y up) when
     * {@code screen}. {@code size} is the dot size (pixels on screen; in the stage it shrinks with distance).
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

    private void drawFields() {
        if (fieldProg == 0) {
            return;
        }
        int p = fieldProg;
        boolean any = false;
        for (Field f : fields) {
            any |= f.count > 0;
        }
        if (!any) {
            return;
        }
        gl.useProgram(p);
        gl.uniformMatrix4(gl.uniform(p, "uMvp"), camera.mvp);
        gl.uniform4f(gl.uniform(p, "uAspect"), height / (float) Math.max(1, width), 1f, density(), 0f);
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.ONE, GlApi.ONE);
        gl.depthMask(false);
        for (Field f : fields) {
            int n;
            synchronized (lock) {
                n = f.count;
                if (n > 0 && f.dirty) {
                    gl.bindBuffer(GlApi.ARRAY_BUFFER, f.vbo);
                    int bytes = n * ai.dvine.render.field.ParticleField.STRIDE * 4;
                    if (f.uploaded < n) {
                        gl.bufferData(GlApi.ARRAY_BUFFER, bytes, floats(java.util.Arrays.copyOf(f.data, n * ai.dvine.render.field.ParticleField.STRIDE)), GlApi.DYNAMIC_DRAW);
                        f.uploaded = n;
                    } else {
                        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, bytes, floats(java.util.Arrays.copyOf(f.data, n * ai.dvine.render.field.ParticleField.STRIDE)));
                    }
                    f.dirty = false;
                    f.shown = n;
                }
            }
            if (n <= 0 || f.shown <= 0) {
                continue;
            }
            if (f.screen) {
                gl.disable(GlApi.DEPTH_TEST);
            } else {
                gl.enable(GlApi.DEPTH_TEST);
            }
            gl.uniform4f(gl.uniform(p, "uPlace"), f.place[0], f.place[1], f.place[2], f.place[3]);
            gl.uniform4f(gl.uniform(p, "uMode"), f.screen ? 1f : 0f, f.size, f.crisp ? 1f : 0f, 0f);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, f.vbo);
            gl.vertexAttrib(0, 3, 28, 0);
            gl.vertexAttrib(1, 4, 28, 12);
            gl.drawArrays(GlApi.POINTS, 0, f.shown);
            gl.disableAttrib(1);
        }
        gl.enable(GlApi.DEPTH_TEST);
        gl.depthMask(true);
        gl.disable(GlApi.BLEND);
    }

    /** The screen's width over its height. */
    public float aspect() {
        return width / (float) Math.max(1, height);
    }

    /** Screen density for the particle display's dot sizes (1 when unknown). */
    private float density() {
        return displayDensity;
    }

    /** Set by the view: pixels per dp, so dots are the same size on every screen. */
    public volatile float displayDensity = 1f;

    static final String FIELD_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec4 aCol;\n"
            + "uniform mat4 uMvp; uniform vec4 uPlace; uniform vec4 uMode; uniform vec4 uAspect; out vec4 vCol;\n"
            + "void main(){ vec3 p = aPos * uPlace.w;\n"
            + "  if (uMode.x > 0.5) { gl_Position = vec4((p.x + uPlace.x) * uAspect.x, p.y + uPlace.y, 0.0, 1.0); gl_PointSize = uMode.y * uAspect.z; }\n"
            // in the stage the dots shrink with distance, like everything else there
            + "  else { gl_Position = uMvp * vec4(p + uPlace.xyz, 1.0); gl_PointSize = clamp(uMode.y * uAspect.z * 4.4 / max(gl_Position.w, 0.1), 1.0, 18.0); }\n"
            + "  vCol = aCol; }\n";
    static final String FIELD_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec4 vCol; uniform highp vec4 uMode; out vec4 o;\n"
            // a soft glowing dot with a bright core; for words a crisp round dot with a clean edge
            + "void main(){ vec2 c = gl_PointCoord * 2.0 - 1.0; float d = dot(c, c); if (d > 1.0) discard;\n"
            + "  float a = (uMode.z > 0.5 ? (1.0 - smoothstep(0.45, 1.0, d)) * 0.95 : exp(-d * 3.0) * 0.8 + exp(-d * 12.0) * 0.6) * vCol.a;\n"
            + "  o = vec4(vCol.rgb * a, a); }\n";

    /** The cartoon look (Ghibli style) for the lifelike bodies and a painted backdrop instead of the studio. */
    public volatile boolean toon;

    /** Lifelike skin on or off for a slot, and its tone as an sRGB colour (0xRRGGBB). */
    public void setSkin(int slot, boolean on, int rgb) {
        Slot s = slots[slot];
        synchronized (lock) {
            Frame f = s.pending;
            f.skin = on;
            f.tone[0] = linear(((rgb >> 16) & 0xff) / 255f);
            f.tone[1] = linear(((rgb >> 8) & 0xff) / 255f);
            f.tone[2] = linear((rgb & 0xff) / 255f);
        }
    }

    private static float linear(float c) {
        return (float) Math.pow(c, 2.2);
    }

    /** This frame's skinned inner anatomy (after {@link #submit}); ignored when the slot has none. */
    public void submitInner(int slot, float[] pos, float[] nor) {
        Slot s = slots[slot];
        synchronized (lock) {
            if (s.innerVertices == 0 || pos == null) {
                return;
            }
            System.arraycopy(pos, 0, s.pending.pos, s.vertices * 3, s.innerVertices * 3);
            System.arraycopy(nor, 0, s.pending.nor, s.vertices * 3, s.innerVertices * 3);
        }
    }

    /** Mode B for a slot: which layers and parts to reveal (null parts: all of the shown layers). Off with anatomy false. */
    public void anatomy(int slot, boolean on, boolean[] layers, boolean[] parts) {
        Slot s = slots[slot];
        synchronized (lock) {
            Frame f = s.pending;
            f.anatomy = on && s.innerParticles > 0;
            for (int i = 0; i < 4; i++) {
                f.showLayer[i] = layers != null && i < layers.length && layers[i] ? 1f : 0f;
            }
            for (int i = 0; i < MAX_PARTS; i++) {
                f.showPart[i] = parts == null || i < parts.length && parts[i] ? 1f : 0f;
            }
        }
    }

    public void setCamera(Camera c, float floor) {
        synchronized (lock) {
            copy(c, pendingCamera);
            floorY = floor;
        }
    }

    // ------------------------------------------------------------ GL thread

    /** A (new) GL context: everything is created from scratch (old handles died with the old context). */
    public void onCreated() {
        liveObjects = 0;
        fillProg = prog(FILL_VS, FILL_FS);
        particleProg = prog(PARTICLE_VS, PARTICLE_FS);
        innerProg = prog(INNER_VS, PARTICLE_FS);
        gridProg = prog(GRID_VS, GRID_FS);
        skinProg = prog(SKIN_VS, SKIN_FS);
        studioProg = prog(STUDIO_VS, STUDIO_FS);
        fieldProg = prog(FIELD_VS, FIELD_FS);
        for (Field fl : fields) {
            fl.vbo = buf();
            fl.uploaded = -1;
        }
        // the studio: a full-screen backdrop triangle (clip space), then a big floor square (body units, x and z)
        studioVbo = buf();
        float[] st = {-1f, -1f, 3f, -1f, -1f, 3f, -40f, -40f, 40f, -40f, 40f, 40f, -40f, -40f, 40f, 40f, -40f, 40f};
        gl.bindBuffer(GlApi.ARRAY_BUFFER, studioVbo);
        gl.bufferData(GlApi.ARRAY_BUFFER, st.length * 4, floats(st), GlApi.STATIC_DRAW);
        gridVbo = buf();
        float[] g = grid();
        gridCount = g.length / 4;
        gl.bindBuffer(GlApi.ARRAY_BUFFER, gridVbo);
        gl.bufferData(GlApi.ARRAY_BUFFER, g.length * 4, floats(g), GlApi.STATIC_DRAW);
        for (Slot s : slots) {
            s.created = false;
            s.dirtyStatic = s.asset != null;
        }
    }

    public void onSize(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        gl.viewport(width, height);
    }

    public void onDraw(long nanos) {
        stats.frame(nanos);
        seconds = (nanos % 3_600_000_000_000L) / 1e9f;
        synchronized (lock) {
            copy(pendingCamera, camera);
            for (Slot s : slots) {
                if (s.dirtyStatic && s.asset != null) {
                    createSlot(s);
                }
                if (s.dirtyTarget && s.created) {
                    uploadTargets(s);
                }
                if (s.hasPending) {
                    Frame a = s.pending, b = s.shown;
                    float[] tp = b.pos, tn = b.nor;
                    b.pos = a.pos;
                    b.nor = a.nor;
                    a.pos = tp;
                    a.nor = tn;
                    b.r = a.r;
                    b.g = a.g;
                    b.b = a.b;
                    b.alpha = a.alpha;
                    b.lod = a.lod;
                    b.visible = a.visible;
                    b.from = a.from;
                    b.to = a.to;
                    b.progress = a.progress;
                    b.orderByObject = a.orderByObject;
                    b.headFirst = a.headFirst;
                    System.arraycopy(a.object, 0, b.object, 0, 16);
                    b.selected = a.selected;
                    b.bodyFill = a.bodyFill;
                    b.anatomy = a.anatomy;
                    b.skin = a.skin;
                    System.arraycopy(a.tone, 0, b.tone, 0, 3);
                    System.arraycopy(a.showLayer, 0, b.showLayer, 0, 4);
                    System.arraycopy(a.showPart, 0, b.showPart, 0, MAX_PARTS);
                    System.arraycopy(a.scatter, 0, b.scatter, 0, 4);
                    s.hasPending = false;
                    if (b.visible) {
                        upload(s);
                    }
                }
            }
        }
        camera.width = width;
        camera.height = height;
        camera.update();
        gl.clear(0f, 0f, 0f);
        gl.enable(GlApi.DEPTH_TEST);
        gl.depthFunc(GlApi.LEQUAL);
        boolean studio = false;
        for (Slot s : slots) {
            studio |= s.created && s.shown.visible && s.shown.skin;
        }
        if (studio && studioProg != 0) {
            drawStudio();
        } else {
            drawGrid();
        }
        for (Slot s : slots) {
            if (s.created && s.shown.visible && s.shown.alpha > 0.01f) {
                drawSlot(s);
            }
        }
        drawFields();
        int err = gl.error();
        if (err != GlApi.NO_ERROR) {
            glErrors++;
            if (fault == null) {
                fault = String.format(java.util.Locale.US, "graphics error 0x%x while drawing", err);
            }
        }
    }

    /** Frees every GL object (view detached). Safe to call twice. */
    public void dispose() {
        for (Slot s : slots) {
            if (s.created) {
                deleteSlot(s);
            }
        }
        if (fillProg != 0) {
            for (int p : new int[]{fillProg, particleProg, innerProg, gridProg, skinProg, studioProg, fieldProg}) {
                if (p != 0) {
                    gl.deleteProgram(p);
                    liveObjects--;
                }
            }
            fillProg = particleProg = innerProg = gridProg = skinProg = studioProg = fieldProg = 0;
        }
        if (studioVbo != 0) {
            gl.deleteBuffer(studioVbo);
            liveObjects--;
            studioVbo = 0;
        }
        for (Field fl : fields) {
            if (fl.vbo != 0) {
                gl.deleteBuffer(fl.vbo);
                liveObjects--;
                fl.vbo = 0;
            }
        }
        if (gridVbo != 0) {
            gl.deleteBuffer(gridVbo);
            liveObjects--;
            gridVbo = 0;
        }
    }

    private void createSlot(Slot s) {
        if (s.created) {
            deleteSlot(s);
        }
        s.vboPos = buf();
        s.vboNor = buf();
        s.vboEye = buf();
        s.vboMat = buf();
        s.vboRest = buf();
        s.ibo = buf();
        s.vboCorner = buf();
        s.vboBary = buf();
        s.vboAux = buf();
        s.vboTarget = buf();
        s.vboPrev = buf();
        s.objVbo = buf();
        s.objNbo = buf();
        s.objIbo = buf();
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.triIndices * 4, s.tri, GlApi.STATIC_DRAW);
        s.eye.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboEye);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 4, s.eye, GlApi.STATIC_DRAW);
        s.mat.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboMat);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 16, s.mat, GlApi.STATIC_DRAW);
        s.rest.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboRest);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, s.rest, GlApi.STATIC_DRAW);
        s.garmentIbo = new int[s.garments.length];
        for (int g = 0; g < s.garments.length; g++) {
            s.garmentIbo[g] = buf();
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.garmentIbo[g]);
            gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.garments[g].tri.length * 4, ints(s.garments[g].tri), GlApi.STATIC_DRAW);
        }
        s.corner.position(0);
        s.bary.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboCorner);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.corner, GlApi.STATIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboBary);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.bary, GlApi.STATIC_DRAW);
        s.dirtyTarget = true;
        if (s.innerParticles > 0) {
            s.vboInCorner = buf();
            s.vboInBary = buf();
            s.innerCorner.position(0);
            s.innerBary.position(0);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboInCorner);
            gl.bufferData(GlApi.ARRAY_BUFFER, s.innerParticles * 16, s.innerCorner, GlApi.STATIC_DRAW);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboInBary);
            gl.bufferData(GlApi.ARRAY_BUFFER, s.innerParticles * 16, s.innerBary, GlApi.STATIC_DRAW);
        }
        s.tPos = gl.floatTexture(TEX_W, s.texH);
        s.tNor = gl.floatTexture(TEX_W, s.texH);
        liveObjects += 2;
        s.created = true;
        s.dirtyStatic = false;
    }

    private void deleteSlot(Slot s) {
        for (int b : s.garmentIbo) {
            gl.deleteBuffer(b);
            liveObjects--;
        }
        s.garmentIbo = new int[0];
        for (int b : new int[]{s.vboPos, s.vboNor, s.vboEye, s.vboMat, s.vboRest, s.ibo, s.vboCorner, s.vboBary, s.vboAux, s.vboTarget, s.vboPrev, s.objVbo, s.objNbo, s.objIbo}) {
            gl.deleteBuffer(b);
            liveObjects--;
        }
        if (s.innerParticles > 0) {
            gl.deleteBuffer(s.vboInCorner);
            gl.deleteBuffer(s.vboInBary);
            liveObjects -= 2;
        }
        gl.deleteTexture(s.tPos);
        gl.deleteTexture(s.tNor);
        liveObjects -= 2;
        s.created = false;
    }

    /** Object targets, scatter directions and the object's surface (changes only when the object does). */
    private void uploadTargets(Slot s) {
        s.aux.position(0);
        s.target.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboAux);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.aux, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboTarget);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.target, GlApi.DYNAMIC_DRAW);
        s.previous.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPrev);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.previous, GlApi.DYNAMIC_DRAW);
        if (s.objTri != null) {
            s.objPos.position(0);
            s.objTri.position(0);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.objVbo);
            gl.bufferData(GlApi.ARRAY_BUFFER, s.objVertices * 12, s.objPos, GlApi.STATIC_DRAW);
            s.objNor.position(0);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.objNbo);
            gl.bufferData(GlApi.ARRAY_BUFFER, s.objVertices * 12, s.objNor, GlApi.STATIC_DRAW);
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.objIbo);
            gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.objIndices * 4, s.objTri, GlApi.STATIC_DRAW);
        }
        s.dirtyTarget = false;
    }

    private void upload(Slot s) {
        Frame f = s.shown;
        float fx = 0f, fz = 0f;
        int nf = 0;
        for (int i = 1; i < f.pos.length; i += 3) {
            if (f.pos[i] < floorY + 0.45f) {
                fx += f.pos[i - 1];
                fz += f.pos[i + 1];
                nf++;
            }
        }
        s.feet[0] = nf > 0 ? fx / nf : 0f;
        s.feet[1] = nf > 0 ? fz / nf : 0f;
        s.feet[2] = nf > 20 ? 1f : 0f;
        s.pos.clear();
        s.pos.put(f.pos);
        s.pos.flip();
        s.nor.clear();
        s.nor.put(f.nor);
        s.nor.flip();
        FloatBuffer dp = s.texPos, dn = s.texNor;
        dp.clear();
        dp.put(f.pos);
        while (dp.position() < dp.capacity()) {
            dp.put(0f);
        }
        dp.flip();
        dn.clear();
        dn.put(f.nor);
        while (dn.position() < dn.capacity()) {
            dn.put(0f);
        }
        dn.flip();
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, s.vertices * 12, dp);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, s.vertices * 12, dn);
        gl.updateFloatTexture(s.tPos, TEX_W, s.texH, dp);
        gl.updateFloatTexture(s.tNor, TEX_W, s.texH, dn);
    }

    private void drawSlot(Slot s) {
        Frame f = s.shown;
        float a = f.alpha;
        boolean intact = f.from == PLACE_BODY && f.to == PLACE_BODY;
        boolean objectShown = f.from == PLACE_OBJECT && f.to == PLACE_OBJECT;
        // 1. the hologram skin: a dark translucent-looking surface whose edges glow (fresnel rim), written
        //    with depth (pushed back) so it also hides the particles behind it; then a soft halo just outside it
        boolean xray = intact && f.anatomy;
        if (intact && f.skin && !xray && f.bodyFill && skinProg != 0) {
            // A: lifelike skin; the particles rest (they only carry transformations)
            drawSkin(s, f);
            return;
        }
        boolean surface = intact && f.bodyFill && !xray || objectShown && s.objIndices > 0;
        if (surface) {
            float[] mvp = intact ? camera.mvp : mul(camera.mvp, f.object);
            float[] nrm = intact ? camera.rot3 : normalMatrix(camera.rot3, f.object);
            int vbo = intact ? s.vboPos : s.objVbo, nbo = intact ? s.vboNor : s.objNbo, ibo = intact ? s.ibo : s.objIbo;
            int count = intact ? s.triIndices : s.objIndices;
            gl.useProgram(fillProg);
            gl.uniformMatrix4(gl.uniform(fillProg, "uMvp"), mvp);
            gl.uniformMatrix3(gl.uniform(fillProg, "uRot"), nrm);
            gl.uniform4f(gl.uniform(fillProg, "uColor"), f.r, f.g, f.b, a);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, vbo);
            gl.vertexAttrib(0, 3, 12, 0);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, nbo);
            gl.vertexAttrib(1, 3, 12, 0);
            if (intact) {
                gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboEye);
                gl.vertexAttrib(2, 1, 4, 0);
            }
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, ibo);
            gl.disable(GlApi.BLEND);
            gl.depthMask(true);
            gl.enable(GlApi.POLYGON_OFFSET_FILL);
            gl.polygonOffset(1.5f, 2f);
            gl.uniform1f(gl.uniform(fillProg, "uPush"), 0f);
            gl.uniform1f(gl.uniform(fillProg, "uHalo"), 0f);
            gl.drawElements(GlApi.TRIANGLES, count, 0);
            gl.disable(GlApi.POLYGON_OFFSET_FILL);
            gl.enable(GlApi.BLEND);
            gl.blendFunc(GlApi.ONE, GlApi.ONE);
            gl.depthMask(false);
            // the halo hugs the outline: thinner as the camera comes in for the face
            float push = 0.03f * Math.min(1f, Math.max(0.2f, camera.distance / 4.4f));
            gl.uniform1f(gl.uniform(fillProg, "uPush"), intact ? push : push / Math.max(1e-3f, scaleOf(f.object)));
            gl.uniform1f(gl.uniform(fillProg, "uHalo"), 1f);
            gl.drawElements(GlApi.TRIANGLES, count, 0);
            gl.disableAttrib(1);
            if (intact) {
                gl.disableAttrib(2);
            }
        }

        if (xray) {
            // Mode B: the skin is only a faint glowing shell (rim only, no depth), so the inside shows through
            gl.useProgram(fillProg);
            gl.uniformMatrix4(gl.uniform(fillProg, "uMvp"), camera.mvp);
            gl.uniformMatrix3(gl.uniform(fillProg, "uRot"), camera.rot3);
            gl.uniform4f(gl.uniform(fillProg, "uColor"), f.r, f.g, f.b, a * 0.8f);
            gl.uniform1f(gl.uniform(fillProg, "uPush"), 0f);
            gl.uniform1f(gl.uniform(fillProg, "uHalo"), 1f);
            gl.enable(GlApi.BLEND);
            gl.blendFunc(GlApi.ONE, GlApi.ONE);
            gl.depthMask(false);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
            gl.vertexAttrib(0, 3, 12, 0);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
            gl.vertexAttrib(1, 3, 12, 0);
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
            gl.drawElements(GlApi.TRIANGLES, s.triIndices, 0);
            gl.disableAttrib(1);
            drawInner(s, f, a);
        }

        // 2. the particles (additive glow, depth-tested, no depth writes)
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.ONE, GlApi.ONE);
        gl.depthMask(false);
        int p = particleProg;
        gl.useProgram(p);
        gl.uniformMatrix4(gl.uniform(p, "uMvp"), camera.mvp);
        gl.uniformMatrix3(gl.uniform(p, "uRot"), camera.rot3);
        gl.uniformMatrix4(gl.uniform(p, "uObject"), f.object);
        gl.uniform1i(gl.uniform(p, "uTexW"), TEX_W);
        gl.uniform1i(gl.uniform(p, "uPos"), 0);
        gl.uniform1i(gl.uniform(p, "uNor"), 1);
        gl.uniform1i(gl.uniform(p, "uFrom"), f.from);
        gl.uniform1i(gl.uniform(p, "uTo"), f.to);
        gl.uniform1f(gl.uniform(p, "uProgress"), f.progress);
        gl.uniform1f(gl.uniform(p, "uByObject"), f.orderByObject ? 1f : 0f);
        gl.uniform1f(gl.uniform(p, "uHeadFirst"), f.headFirst ? 1f : 0f);
        gl.uniform1f(gl.uniform(p, "uSelected"), f.selected);
        gl.uniform1f(gl.uniform(p, "uSize"), 2.4f * density * f.lod);
        // on the hologram skin the particles are a fine shimmer; in flight (and on their own) they carry the shape
        gl.uniform4f(gl.uniform(p, "uColor"), f.r, f.g, f.b, a * (surface ? 0.4f : xray ? 0.18f : 1f));
        gl.uniform4f(gl.uniform(p, "uScatter"), f.scatter[0], f.scatter[1], f.scatter[2], f.scatter[3]);
        gl.uniform3fv(gl.uniform(p, "uPalette"), PALETTE);
        gl.bindTexture(0, s.tPos);
        gl.bindTexture(1, s.tNor);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboCorner);
        gl.vertexAttrib(0, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboBary);
        gl.vertexAttrib(1, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboTarget);
        gl.vertexAttrib(2, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboAux);
        gl.vertexAttrib(3, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPrev);
        gl.vertexAttrib(4, 4, 16, 0);
        gl.drawArrays(GlApi.POINTS, 0, s.particles);
        gl.disableAttrib(4);
        gl.disableAttrib(1);
        gl.disableAttrib(2);
        gl.disableAttrib(3);
        gl.depthMask(true);
        gl.disable(GlApi.BLEND);
    }

    /** Mode A: the lit skin, opaque and depth-tested like any solid. */
    private void drawSkin(Slot s, Frame f) {
        int p = skinProg;
        gl.useProgram(p);
        gl.uniformMatrix4(gl.uniform(p, "uMvp"), camera.mvp);
        gl.uniformMatrix3(gl.uniform(p, "uRot"), camera.rot3);
        gl.uniform4f(gl.uniform(p, "uTone"), f.tone[0], f.tone[1], f.tone[2], f.alpha);
        float[] hf = s.headFrame;
        gl.uniform4f(gl.uniform(p, "uHeadA"), hf[0], hf[1], hf[2], hf[3]);
        gl.uniform4f(gl.uniform(p, "uHeadB"), hf[4], hf[5], hf[6], s.asset.female ? 1f : 0f);
        gl.uniform1f(gl.uniform(p, "uHairStyle"), s.hairStyle);
        gl.uniform1f(gl.uniform(p, "uBeard"), s.asset.beard ? 1f : 0f);
        gl.uniform1f(gl.uniform(p, "uToon"), toon ? 1f : 0f);
        gl.disable(GlApi.BLEND);
        gl.depthMask(true);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.vertexAttrib(0, 3, 12, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.vertexAttrib(1, 3, 12, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboEye);
        gl.vertexAttrib(2, 1, 4, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboMat);
        gl.vertexAttrib(3, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboRest);
        gl.vertexAttrib(4, 3, 12, 0);
        gl.uniform4f(gl.uniform(p, "uCloth"), 0f, 0f, 0f, 0f);
        gl.uniform1f(gl.uniform(p, "uPush"), 0f);
        gl.uniform1f(gl.uniform(p, "uKind"), -1f);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.drawElements(GlApi.TRIANGLES, s.triIndices, 0);
        // the clothes: the same skinned surface, pushed out a few millimetres, shaded as fabric
        for (int g = 0; g < s.garments.length && g < s.garmentIbo.length; g++) {
            ai.dvine.human.Clothing.Garment gm = s.garments[g];
            if (gm.tri.length == 0) {
                continue;
            }
            gl.uniform4f(gl.uniform(p, "uCloth"), gm.color[0], gm.color[1], gm.color[2], 1f);
            gl.uniform4f(gl.uniform(p, "uClipA"), gm.clip[0], gm.clip[1], gm.clip[2], gm.clip[3]);
            gl.uniform4f(gl.uniform(p, "uClipB"), gm.clip[4], gm.clip[5], gm.clip[6], gm.clip.length > 7 ? gm.clip[7] : 0f);
            float[] jt = s.asset.joints;
            int sh = ai.dvine.face.nano.DotBody.ARM_L * 3, el = ai.dvine.face.nano.DotBody.FORE_L * 3;
            gl.uniform4f(gl.uniform(p, "uShoulder"), jt[sh], jt[sh + 1], jt[sh + 2], 0f);
            gl.uniform4f(gl.uniform(p, "uElbow"), jt[el], jt[el + 1], jt[el + 2], 0f);
            int th = ai.dvine.face.nano.DotBody.THIGH_L * 3;
            gl.uniform4f(gl.uniform(p, "uLegs"), Math.abs(jt[th]), 0f, 0f, 0f);
            gl.uniform1f(gl.uniform(p, "uWeave"), gm.weave);
            gl.uniform1f(gl.uniform(p, "uPush"), gm.push);
            gl.uniform1f(gl.uniform(p, "uKind"), gm.clip[0]);
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.garmentIbo[g]);
            gl.drawElements(GlApi.TRIANGLES, gm.tri.length, 0);
        }
        gl.disableAttrib(1);
        gl.disableAttrib(2);
        gl.disableAttrib(3);
        gl.disableAttrib(4);
    }

    private static final float[] PALETTE = new float[48];

    static {
        for (int i = 0; i < 16; i++) {
            System.arraycopy(ai.dvine.create.Materials.RGB[i], 0, PALETTE, i * 3, 3);
        }
    }

    /** Mode B: particles on the chosen inner structures, tinted by layer (bone, muscle, organ, vessel). */
    private void drawInner(Slot s, Frame f, float a) {
        if (s.innerParticles == 0) {
            return;
        }
        int p = innerProg;
        gl.useProgram(p);
        gl.uniformMatrix4(gl.uniform(p, "uMvp"), camera.mvp);
        gl.uniformMatrix3(gl.uniform(p, "uRot"), camera.rot3);
        gl.uniform1i(gl.uniform(p, "uTexW"), TEX_W);
        gl.uniform1i(gl.uniform(p, "uPos"), 0);
        gl.uniform1i(gl.uniform(p, "uNor"), 1);
        gl.uniform1f(gl.uniform(p, "uSize"), 2.2f * density * f.lod);
        gl.uniform4f(gl.uniform(p, "uColor"), f.r, f.g, f.b, a);
        gl.uniform4f(gl.uniform(p, "uLayers"), f.showLayer[0], f.showLayer[1], f.showLayer[2], f.showLayer[3]);
        gl.uniform1fv(gl.uniform(p, "uShow"), f.showPart);
        gl.bindTexture(0, s.tPos);
        gl.bindTexture(1, s.tNor);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboInCorner);
        gl.vertexAttrib(0, 4, 16, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboInBary);
        gl.vertexAttrib(1, 4, 16, 0);
        gl.drawArrays(GlApi.POINTS, 0, s.innerParticles);
        gl.disableAttrib(1);
    }

    /** Uniform scale of an object matrix (its first column's length). */
    static float scaleOf(float[] m) {
        return (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    }

    /** View rotation · the object's rotation (scale removed), as the 3x3 normal matrix. */
    static float[] normalMatrix(float[] rot3, float[] m) {
        float k = 1f / Math.max(1e-6f, scaleOf(m));
        float[] o = new float[9];
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                float v = 0f;
                for (int q = 0; q < 3; q++) {
                    v += rot3[q * 3 + r] * m[c * 4 + q] * k;
                }
                o[c * 3 + r] = v;
            }
        }
        return o;
    }

    /** a·b for column-major 4x4 matrices. */
    static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float v = 0f;
                for (int k = 0; k < 4; k++) {
                    v += a[k * 4 + r] * b[c * 4 + k];
                }
                o[c * 4 + r] = v;
            }
        }
        return o;
    }

    static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static double hash(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    /** The lifelike look's set: a charcoal studio backdrop lit from above, and a glowing teal floor of linked rings. */
    /** Seconds, for the backdrop's slow drift. */
    private float seconds;

    private void drawStudio() {
        gl.useProgram(studioProg);
        gl.uniform1f(gl.uniform(studioProg, "uTime"), seconds);
        gl.uniformMatrix4(gl.uniform(studioProg, "uMvp"), camera.mvp);
        gl.uniform1f(gl.uniform(studioProg, "uFloor"), floorY);
        gl.uniform1f(gl.uniform(studioProg, "uPaint"), toon ? 1f : 0f);
        float[] fe = new float[4];
        float[] on = new float[2];
        for (int k = 0; k < 2; k++) {
            Slot sl = slots[k];
            boolean shown = sl.created && sl.shown.visible && sl.shown.skin && sl.feet[2] > 0.5f;
            fe[k * 2] = sl.feet[0];
            fe[k * 2 + 1] = sl.feet[1];
            on[k] = shown ? sl.shown.alpha : 0f;
        }
        gl.uniform4f(gl.uniform(studioProg, "uFeet"), fe[0], fe[1], fe[2], fe[3]);
        gl.uniform4f(gl.uniform(studioProg, "uFeetOn"), on[0], on[1], 0f, 0f);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, studioVbo);
        gl.vertexAttrib(0, 2, 8, 0);
        gl.depthMask(false);
        gl.uniform1f(gl.uniform(studioProg, "uMode"), 0f);
        gl.drawArrays(GlApi.TRIANGLES, 0, 3);
        gl.uniform1f(gl.uniform(studioProg, "uMode"), 1f);
        gl.drawArrays(GlApi.TRIANGLES, 3, 6);
        gl.depthMask(true);
    }

    private void drawGrid() {
        if (gridProg == 0) {
            return;
        }
        gl.useProgram(gridProg);
        gl.uniformMatrix4(gl.uniform(gridProg, "uMvp"), camera.mvp);
        gl.uniform1f(gl.uniform(gridProg, "uFloor"), floorY);
        gl.uniform1f(gl.uniform(gridProg, "uSize"), 1.6f * density);
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.ONE, GlApi.ONE);
        gl.depthMask(false);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, gridVbo);
        gl.vertexAttrib(0, 4, 16, 0);
        gl.drawArrays(GlApi.POINTS, 0, gridCount);
        gl.depthMask(true);
        gl.disable(GlApi.BLEND);
    }

    /** Dotted floor: x, z, kind (0 plain, 1 x axis, 2 depth axis), unused. Body units. */
    private static float[] grid() {
        java.util.List<float[]> g = new java.util.ArrayList<float[]>();
        for (int k = -14; k <= 14; k++) {
            float c = k * 1.25f;
            for (int j = -70; j <= 70; j++) {
                float t = j * 0.25f;
                if (k == 0 || (j & 1) == 0) {
                    g.add(new float[]{t, c, k == 0 ? 1f : 0f, 0f});
                    g.add(new float[]{c, t, k == 0 ? 2f : 0f, 0f});
                }
            }
        }
        float[] out = new float[g.size() * 4];
        for (int i = 0; i < g.size(); i++) {
            System.arraycopy(g.get(i), 0, out, i * 4, 4);
        }
        return out;
    }

    // ------------------------------------------------------------ helpers

    private int prog(String vs, String fs) {
        int p = gl.program(vs, fs);
        if (p != 0) {
            liveObjects++;
        } else {
            glErrors++;
            if (fault == null) {
                fault = "a shader failed to compile on this phone";
            }
        }
        return p;
    }

    private int buf() {
        liveObjects++;
        return gl.buffer();
    }

    private static void copy(Camera a, Camera b) {
        b.width = a.width;
        b.height = a.height;
        b.cx = a.cx;
        b.cy = a.cy;
        b.unit = a.unit;
        b.distance = a.distance;
        b.yawDeg = a.yawDeg;
        b.pitchDeg = a.pitchDeg;
        b.pivot = a.pivot;
    }

    private static FloatBuffer direct(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static FloatBuffer floats(float[] a) {
        FloatBuffer b = direct(a.length);
        b.put(a).flip();
        return b;
    }

    private static IntBuffer ints(int[] a) {
        IntBuffer b = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        b.put(a).flip();
        return b;
    }

    // ------------------------------------------------------------ shaders

    /**
     * The hologram skin: dark where the surface faces you, glowing where it
     * turns away (fresnel rim), with a soft key light for the form. The halo
     * pass pushes the surface out a little along its normals and adds only
     * the rim, a soft glow just outside the silhouette.
     */
    /**
     * Mode A, lifelike skin. Lights in view space: a warm key from the upper left, a cool
     * fill from the right, a rim from behind, and a sky/ground ambient. Subsurface
     * scattering is approximated by wrap lighting that wraps red furthest (light bleeds
     * through skin as red), with a warm tint in the terminator. Two specular lobes (a
     * tight sheen and a broad one) with Schlick fresnel; fine procedural pores bend the
     * normal and a low-frequency noise varies the tone. Per vertex: material (skin, lips,
     * teeth, inner mouth), warmth (cheeks, nose, ears), brow and short-hair weights; the
     * eyeballs get sclera, a brown iris, a pupil and a corneal glint. Reinhard tone map, gamma 2.2.
     */
    static final String SKIN_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor; layout(location=2) in float aEye;\n"
            + "layout(location=3) in vec4 aMat; layout(location=4) in vec3 aRest;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform vec4 uHeadA; uniform vec4 uHeadB; uniform float uPush; uniform float uKind; uniform vec4 uClipB;\n"
            + "out vec3 vN; out vec3 vR; out vec3 vH; out float vEye; out vec4 vMat;\n"
            + "void main(){ vec3 nn = normalize(aNor + vec3(1e-6));\n"
            // the rest position: skin detail stays on the skin as it moves; in head units it places the face features
            + "  vR = aRest; float lip = uHeadA.y - 0.61 * uHeadB.x;\n"
            + "  float hy = aRest.y >= lip ? (aRest.y - uHeadA.y) / uHeadB.x : -0.61 + (aRest.y - lip) / uHeadB.z;\n"
            + "  vH = vec3((aRest.x - uHeadA.x) / uHeadA.w, hy, (aRest.z - uHeadA.z) / uHeadB.y);\n"
            // hair and beard thin out toward their fading edges (the line-up at the forehead keeps its full edge)
            + "  float push = uPush; float ax = abs(vH.x);\n"
            + "  if (uKind > 2.5 && uKind < 3.5 && !(vH.z > 0.25 && ax < 0.52)) push *= mix(0.25, 1.0, smoothstep(0.44, 0.72, vH.y));\n"
            // a short afro rounds off at its hairline instead of ending in a step
            + "  if (uKind > 5.5) { float bk = clamp((0.1 - vH.z) / 0.8, 0.0, 1.0); bk *= bk; float fr = 0.6 - 0.18 * min(1.0, (ax / 0.55) * (ax / 0.55));\n"
            + "    push *= mix(0.2, 1.0, smoothstep(0.0, 0.3, vH.y - mix(fr, -0.8, bk) + 0.12)); }\n"
            + "  if (uKind > 3.5 && uKind < 4.5) { float t = clamp((ax - 0.12) / 0.5, 0.0, 1.0); float ch = -0.43 + 0.42 * t * t * (3.0 - 2.0 * t) - sin(3.14159 * t) * 0.12;\n"
            + "    float inBeard = smoothstep(0.0, 0.14, ch - vH.y);\n"
            // the sideburns stay close to the skin and get closer as they rise into the fade
            + "    float sb = smoothstep(0.53, 0.6, ax) * smoothstep(-0.16, -0.06, vH.z) * smoothstep(0.3, 0.22, vH.z);\n"
            + "    float rise = mix(0.55, 0.15, smoothstep(-0.1, 0.4, vH.y));\n"
            + "    push *= mix(mix(0.3, 1.0, inBeard) * mix(0.3, 1.0, smoothstep(-1.13, -0.98, vH.y)), rise, sb * (1.0 - inBeard)); }\n"
            // joggers: roomy in the leg, gathered into the waistband and pulled in at the ankle cuff
            + "  if (uKind > 0.5 && uKind < 1.5) push *= mix(0.3, 1.0, smoothstep(uClipB.z, uClipB.z + 0.4, aRest.y)) * mix(0.55, 1.0, smoothstep(uClipB.x, uClipB.x - 0.35, aRest.y));\n"
            + "  gl_Position = uMvp * vec4(aPos + nn * push, 1.0); vN = uRot * nn;\n"
            + "  vEye = aEye; vMat = aMat; }\n";
    static final String SKIN_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec3 vN; in vec3 vR; in vec3 vH; in float vEye; in vec4 vMat; uniform vec4 uTone; uniform mat3 uRot; uniform vec4 uHeadB;\n"
            + "uniform vec4 uCloth; uniform float uWeave; uniform vec4 uClipA; uniform vec4 uClipB; uniform vec4 uShoulder; uniform vec4 uElbow;\n"
            + "uniform vec4 uLegs; uniform float uHairStyle; uniform float uBeard; uniform float uToon;\n"
            + "float lips(vec3 h, float fem); float vnoise(vec3 x);\n"
            + "float cheekLine(float ax){ float t = clamp((ax - 0.12) / 0.5, 0.0, 1.0);\n"
            + "  return -0.43 + 0.42 * t * t * (3.0 - 2.0 * t) - sin(3.14159 * t) * 0.12; }\n"
            // a garment's exact edges on the rest surface: neckline (front, back, scoop), hem, sleeve end; waist and cuff; shoe top
            + "bool outside(vec3 r){ float kind = uClipA.x;\n"
            + "  if (kind < 0.5) { vec3 ra = vec3(abs(r.x), r.y, r.z); vec3 ax = uElbow.xyz - uShoulder.xyz;\n"
            + "    float along = dot(ra - uShoulder.xyz, ax) / dot(ax, ax);\n"
            + "    float arm = smoothstep(0.85, 1.0, ra.x / max(0.01, uShoulder.x));\n"
            + "    if (arm > 0.5 && along > uClipB.y) return true;\n"
            + "    float front = smoothstep(-0.12, 0.12, r.z);\n"
            + "    float neckline = mix(uClipA.z, uClipA.y + uClipA.w * min(1.0, abs(r.x) / 0.35), front);\n"
            // away from the neck the fabric carries on over the shoulders
            + "    neckline += 4.0 * max(0.0, abs(r.x) - mix(0.2, 0.26, uHeadB.w));\n"
            + "    return r.y > neckline || r.y < uClipB.x; }\n"
            + "  if (kind < 1.5) return r.y > uClipB.x || r.y < uClipB.z;\n"
            + "  if (kind < 2.5) return r.y > uClipB.x;\n"
            + "  if (kind > 4.5 && kind < 5.5) return r.y > uClipB.x || r.y < uClipB.z;\n"
            // Obi's hair and beard, the same shapes as Clothing.topHair and Clothing.beard, in head units
            + "  vec3 h = vH; float ax = abs(h.x);\n"
            // edges thin out instead of stopping: coverage falls off and clumps of hair drop out (a dithered blend)
            + "  float clump = vnoise(vR * 330.0) * 0.7 + vnoise(vR * 90.0) * 0.3;\n"
            // the short afro (Clothing.afroLine): full over the crown, its rim breaking into coils
            + "  if (kind > 5.5 && ax > 0.58 && h.y < 0.2 && abs(h.z + 0.05) < 0.3) return true;\n"
            + "  if (kind > 5.5) { float bk = clamp((0.1 - h.z) / 0.8, 0.0, 1.0); bk *= bk; float fr = 0.6 - 0.18 * min(1.0, (ax / 0.55) * (ax / 0.55));\n"
            + "    return clump > smoothstep(-0.12, 0.06, h.y - mix(fr, -0.8, bk)); }\n"
            + "  if (kind < 3.5) { bool lineUp = h.z > 0.25 && ax < 0.52;\n"
            + "    if (lineUp) return h.y < 0.63;\n"
            + "    return clump > smoothstep(0.42, 0.6, h.y); }\n"
            + "  if (lips(h, 0.0) > 0.12) return true;\n"
            + "  float ear = smoothstep(0.66, 0.72, ax) * smoothstep(0.22, 0.05, abs(h.z + 0.05)) * smoothstep(0.42, 0.28, abs(h.y + 0.05));\n"
            + "  if (ear > 0.5) return true;\n"
            // along the jaw the beard thins toward the ear instead of stopping at a line
            + "  float beardCov = smoothstep(0.0, 0.1, cheekLine(ax) - h.y) * smoothstep(-1.16, -1.06, h.y) * smoothstep(0.82, 0.72, ax) * smoothstep(-0.42, -0.16, h.z);\n"
            + "  float side = smoothstep(0.53, 0.59, ax) * smoothstep(0.8, 0.74, ax) * smoothstep(-0.16, -0.06, h.z) * smoothstep(0.3, 0.22, h.z)\n"
            + "    * (1.0 - smoothstep(0.08, 0.46, h.y)) * smoothstep(-0.25, -0.12, h.y);\n"
            + "  return clump > max(beardCov, side * 1.15); }\n"
            + "out vec4 o;\n"
            + "float h3(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }\n"
            + "float vnoise(vec3 x){ vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);\n"
            + "  return mix(mix(mix(h3(i), h3(i + vec3(1,0,0)), f.x), mix(h3(i + vec3(0,1,0)), h3(i + vec3(1,1,0)), f.x), f.y),\n"
            + "             mix(mix(h3(i + vec3(0,0,1)), h3(i + vec3(1,0,1)), f.x), mix(h3(i + vec3(0,1,1)), h3(i + vec3(1,1,1)), f.x), f.y), f.z); }\n"
            + "vec3 wrapDiffuse(float ndl, vec3 w){ return clamp((vec3(ndl) + w) / (1.0 + w), 0.0, 1.0); }\n"
            // coily hair: near black with a little warm brown, matte, the surface broken into tight coils (normal bend returned in g)
            + "vec3 coily(vec3 r, float scale, out vec3 g){ vec3 q = r * scale; float c0 = vnoise(q), c1 = vnoise(q * 2.3 + 5.0);\n"
            + "  float e = 0.25; g = vec3(vnoise(q + vec3(e,0,0)) - c0, vnoise(q + vec3(0,e,0)) - c0, vnoise(q + vec3(0,0,e)) - c0) / e;\n"
            + "  float coil = 0.55 * c0 + 0.45 * c1;\n"
            + "  return mix(vec3(0.0035, 0.0028, 0.0025), vec3(0.016, 0.009, 0.006), coil * coil); }\n"
            // the same brow, hairline and lip shapes as SkinMaterial, per pixel in head units
            + "float brow(vec3 h, float fem){ float ax = abs(h.x); if (h.z < 0.35 || ax < 0.07 || ax > 0.6) return 0.0;\n"
            + "  float u = (ax - 0.07) / 0.5; float arch = mix(0.045, 0.07, fem);\n"
            + "  float c = 0.02 + mix(0.195, 0.215, fem) + arch * sin(3.14159 * min(1.0, u * 1.25)) - 0.05 * u * u;\n"
            + "  float half_ = mix(0.042, 0.028, fem) * (1.0 - 0.55 * u * u) + 0.012 * (1.0 - u);\n"
            // short hairs slanting out from the inner end: streaks of noise along that direction, a soft edge
            + "  vec2 along = vec2(abs(h.x) * 1.0 - h.y * 0.35, h.y + abs(h.x) * 0.25);\n"
            + "  float hairs = 0.45 + 0.55 * vnoise(vec3(along.x * 30.0, along.y * 420.0, 0.0));\n"
            + "  float edge = (half_ - abs(h.y - c)) / 0.02 + 0.5 + 0.35 * (vnoise(vec3(h.xy * 120.0, 3.0)) - 0.5);\n"
            + "  return smoothstep(0.0, 1.0, edge) * smoothstep(0.0, 0.12, u) * smoothstep(0.0, 0.2, 1.0 - u) * hairs; }\n"
            + "float hairCap(vec3 h, float fem){ float ax = abs(h.x); float front = mix(0.63, 0.58, fem);\n"
            + "  float back = smoothstep(0.35, -0.25, h.z); float line = front + (-0.85 - front) * back;\n"
            + "  float temple = smoothstep(0.45, 0.6, ax) * smoothstep(-0.1, 0.3, h.z); line -= 0.35 * temple * (1.0 - back);\n"
            + "  float ragged = 0.04 * (vnoise(vec3(h.x * 26.0, h.z * 26.0, 7.0)) - 0.5) + 0.02 * (vnoise(vec3(h.x * 90.0, h.z * 90.0, 1.0)) - 0.5);\n"
            + "  float ear = smoothstep(0.55, 0.67, ax) * smoothstep(0.25, 0.05, abs(h.z + 0.05)) * smoothstep(0.42, 0.27, abs(h.y + 0.05));\n"
            + "  return smoothstep(-0.02, 0.06, h.y - line - ragged) * (1.0 - ear); }\n"
            + "float lips(vec3 h, float fem){ float w = mix(0.205, 0.215, fem); float dy = h.y + 0.61;\n"
            + "  float ly = dy / (dy > 0.0 ? mix(0.085, 0.095, fem) : mix(0.1, 0.11, fem));\n"
            + "  float lx = h.x / (w * (1.0 - 0.25 * clamp(ly * ly, 0.0, 1.0)));\n"
            + "  float cupid = dy > 0.0 ? 0.25 * exp(-h.x * h.x * 180.0) : 0.0;\n"
            + "  float r = lx * lx + ly * ly + cupid;\n"
            + "  return (1.0 - smoothstep(0.75, 1.0, r)) * smoothstep(0.45, 0.62, h.z); }\n"
            + "void main(){\n"
            + "  vec3 n = normalize(vN); vec3 v = vec3(0.0, 0.0, 1.0); float fem = uHeadB.w; vec3 n0 = n;\n"
            + "  float mat = vMat.x; float warm = vMat.y; float face = vMat.z;\n"
            + "  vec3 alb = uTone.rgb; float rough = 0.36; float specK = 0.036; vec3 sss = vec3(0.45, 0.22, 0.14);\n"
            + "  float detail = 1.0; float fuzz = 0.0;\n"
            + "  if (uCloth.a > 0.5 && outside(vR)) discard;\n"
            + "  if (uCloth.a > 0.5 && ((uClipA.x > 2.5 && uClipA.x < 4.5) || uClipA.x > 5.5)) {\n"
            + "    vec3 g4; alb = coily(vR, uClipA.x > 3.5 && uClipA.x < 4.5 ? 110.0 : uClipA.x > 5.5 ? 60.0 : 80.0, g4);\n"
            + "    n = normalize(n - uRot * g4 * 0.3); rough = 1.0; specK = 0.004; sss = vec3(0.04); detail = 0.0; fuzz = 1.0;\n"
            + "  } else if (uCloth.a > 0.5) {\n"
            // fabric: a fine weave (twill diagonals for denim), soft folds, no oily shine, a velvety sheen at grazing angles
            + "    float tw = vnoise(vec3((vR.x + vR.y) * 220.0, (vR.y - vR.z) * 40.0, vR.z * 220.0));\n"
            + "    float knit = vnoise(vR * 260.0);\n"
            // ribbed knit (tees, socks): fine vertical ribs running round the body
            + "    float rib = 0.5 + 0.5 * sin(atan(vR.x, vR.z + 0.001) * 320.0 + vR.x * 40.0);\n"
            + "    float wv = uWeave < -0.5 ? mix(knit, rib, 0.7) : mix(knit, tw, uWeave);\n"
            + "    float fold = vnoise(vR * vec3(5.0, 1.6, 5.0));\n"
            + "    alb = uCloth.rgb * (0.86 + 0.28 * wv) * (0.85 + 0.3 * fold);\n"
            // cargo joggers: side pockets with seams and a flap on each thigh, a drawstring, a ribbed waistband and cuffs
            + "    if (uClipA.x > 0.5 && uClipA.x < 1.5 && uClipA.y > 0.5) {\n"
            + "      float lx = abs(vR.x) - uLegs.x; float py = vR.y - uClipA.w;\n"
            + "      float inP = step(0.1, lx) * step(abs(vR.z - 0.01), 0.16) * step(0.22, py) * step(py, 0.8);\n"
            + "      float edge = min(min(abs(py - 0.22), abs(py - 0.8)), abs(abs(vR.z - 0.01) - 0.16));\n"
            + "      float flap = abs(py - 0.66);\n"
            + "      float seam = inP * (1.0 - smoothstep(0.004, 0.012, min(edge, flap)));\n"
            + "      alb *= mix(1.0, 0.86, inP) * (1.0 - 0.45 * seam);\n"
            + "      alb *= 1.0 + 0.08 * inP * step(0.66, py);\n"
            + "      float cuffB = step(vR.y, uClipB.z + uClipA.z) * step(uClipB.z, vR.y);\n"
            + "      float waistB = step(uClipB.x - 0.1, vR.y);\n"
            + "      float hr = 0.5 + 0.5 * sin(vR.y * 1300.0);\n"
            + "      alb *= mix(1.0, 0.8 + 0.2 * hr, max(cuffB, waistB * 0.7));\n"
            + "      float cord = step(0.0, vR.z) * step(abs(abs(vR.x) - 0.045 - 0.01 * sin(vR.y * 60.0)), 0.008) * step(uClipB.x - 0.32, vR.y) * step(vR.y, uClipB.x - 0.04);\n"
            + "      alb = mix(alb, vec3(0.78, 0.74, 0.66), cord);\n"
            + "    }\n"
            // Obi's shirt: a light-blue tie from the knot under the collar to its point, a collar fold, small buttons
            + "    if (uClipA.x < 0.5 && uClipB.w > 0.5 && uClipB.w < 1.5 && vR.z > 0.0) {\n"
            + "      float top = uClipA.y, ax = abs(vR.x);\n"
            + "      float tw = mix(0.05, 0.12, smoothstep(top - 0.12, top - 0.75, vR.y));\n"
            + "      float tip = top - 1.3;\n"
            + "      float inTie = step(ax, tw) * step(vR.y, top - 0.03) * step(tip + ax * 1.2, vR.y);\n"
            + "      float knot = step(top - 0.15, vR.y) * step(ax, 0.06);\n"
            + "      vec3 tie = vec3(0.42, 0.62, 0.88) * (0.85 + 0.2 * step(0.5, fract((vR.x + vR.y) * 60.0)) * step(0.5, fract((vR.x - vR.y) * 60.0)));\n"
            + "      alb = mix(alb, tie * mix(1.0, 0.85, knot), inTie);\n"
            + "      float collar = step(top - 0.09, vR.y) * step(0.06, ax) * (1.0 - inTie);\n"
            + "      alb *= 1.0 - 0.12 * collar;\n"
            + "    }\n"
            // Obi's jacket: two columns of gold buttons, the lapel edges, a breast pocket
            + "    if (uClipA.x < 0.5 && uClipB.w > 1.5 && vR.z > 0.0) {\n"
            + "      float ax = abs(vR.x), vb = uClipA.y;\n"
            + "      float b = 0.0;\n"
            + "      for (int i = 0; i < 2; i++) { vec2 d = vec2(ax - 0.17, vR.y - (vb - 0.08 - float(i) * 0.36)); b = max(b, 1.0 - smoothstep(0.026, 0.034, length(d))); }\n"
            + "      alb = mix(alb, vec3(0.62, 0.44, 0.12) * (0.8 + 0.4 * smoothstep(0.03, 0.0, length(vec2(ax - 0.165, 0.0)))), b);\n"
            + "      rough = mix(rough, 0.3, b); specK = mix(specK, 0.3, b);\n"
            + "      float lapel = uClipA.y + uClipA.w * min(1.0, ax / 0.35);\n"
            + "      float edge = 1.0 - smoothstep(0.0, 0.025, abs(vR.y - lapel + 0.07));\n"
            + "      alb *= 1.0 - 0.35 * edge * step(ax, 0.4);\n"
            + "      float pocket = step(0.24, vR.x) * step(vR.x, 0.42) * (1.0 - smoothstep(0.0, 0.012, abs(vR.y - (vb + 0.62))));\n"
            + "      alb *= 1.0 - 0.3 * pocket;\n"
            + "    }\n"
            + "    rough = 0.9; specK = 0.02; sss = vec3(0.12, 0.1, 0.1); detail = 0.0;\n"
            + "    float e2 = 0.004; vec3 q2 = vR * 7.0;\n"
            + "    float f0 = vnoise(q2); vec3 g2 = vec3(vnoise(q2 + vec3(e2*7.0,0,0)) - f0, vnoise(q2 + vec3(0,e2*7.0,0)) - f0, vnoise(q2 + vec3(0,0,e2*7.0)) - f0);\n"
            + "    n = normalize(n - uRot * g2 * 6.0);\n"
            + "  } else if (vEye > 0.5) {\n"
            + "    float d = vEye - 2.0;\n"
            + "    float iris = smoothstep(0.85, 0.875, d); float pupil = smoothstep(0.962, 0.972, d);\n"
            + "    float fib = 0.7 + 0.3 * vnoise(vec3(atan(vR.x, vR.y) * 40.0, d * 90.0, 0.0));\n"
            + "    vec3 sclera = vec3(0.47, 0.43, 0.39) * mix(1.0, 0.6, smoothstep(0.45, -0.5, d));\n"
            + "    vec3 irisC = mix(vec3(0.035, 0.016, 0.008), vec3(0.10, 0.05, 0.02), smoothstep(0.95, 0.88, d)) * fib;\n"
            + "    alb = mix(mix(sclera, irisC, iris), vec3(0.004), pupil); rough = 0.08; specK = 0.06; sss = vec3(0.1); detail = 0.0;\n"
            // trainers (their own geometry, placed by along / across / up on the shoe): a leather upper with a toe cap
            // and heel counter on stitched seams, crossed laces through eyelets, the opening round the ankle, a pale
            // midsole with a groove and a rubber outsole (Obi: white leather, grey rubber; Ivie: chunky cream)
            + "  } else if (mat > 3.5) {\n"
            + "    float u = vMat.y, ac = vMat.z, up = vMat.w;\n"
            + "    vec3 upper = fem > 0.5 ? vec3(0.6, 0.53, 0.43) : vec3(0.075, 0.04, 0.022);\n"
            + "    float soleTop = fem > 0.5 ? 0.3 : 0.2;\n"
            + "    float cap = u + 0.07 * ac * ac, counter = u - 0.08 * (up - 0.5);\n"
            + "    alb = upper * mix(1.0, 0.92, max(smoothstep(0.785, 0.8, cap), smoothstep(0.26, 0.245, counter)));\n"
            + "    float seam = max(1.0 - smoothstep(0.0, 0.005, abs(cap - 0.79)), 1.0 - smoothstep(0.0, 0.005, abs(counter - 0.252)));\n"
            + "    float stitch = max(1.0 - smoothstep(0.0, 0.003, abs(cap - 0.802)), 1.0 - smoothstep(0.0, 0.003, abs(counter - 0.24))) * step(0.5, fract((up + ac) * 45.0));\n"
            + "    alb *= 1.0 - 0.3 * seam - 0.18 * stitch;\n"
            + "    float lacing = step(0.36, u) * step(u, 0.76) * smoothstep(0.8, 0.86, up);\n"
            + "    float tongue = lacing * step(abs(ac), 0.36);\n"
            + "    alb *= 1.0 - 0.12 * tongue;\n"
            + "    float row = fract((u - 0.36) / 0.4 * 6.0); float bar = smoothstep(0.3, 0.16, abs(row - 0.5) + abs(ac) * 0.35);\n"
            + "    float lace = tongue * bar * step(abs(ac), 0.3);\n"
            + "    alb = mix(alb, fem > 0.5 ? vec3(0.7, 0.64, 0.54) : vec3(0.03, 0.02, 0.015), lace);\n"
            + "    float eyelet = lacing * smoothstep(0.32, 0.18, length(vec2(row - 0.5, (abs(ac) - 0.36) * 7.0)));\n"
            + "    alb = mix(alb, vec3(0.12, 0.12, 0.12), eyelet * 0.8);\n"
            + "    float opening = smoothstep(0.93, 0.97, up) * smoothstep(0.36, 0.3, u);\n"
            + "    alb = mix(alb, vec3(0.03, 0.03, 0.03), opening);\n"
            + "    float mid = 1.0 - smoothstep(soleTop - 0.008, soleTop + 0.008, up);\n"
            + "    float out_ = 1.0 - smoothstep(0.03, 0.045, up);\n"
            + "    float groove = 1.0 - smoothstep(0.0, 0.01, abs(up - soleTop * 0.55));\n"
            + "    vec3 soleC = (fem > 0.5 ? vec3(0.86, 0.85, 0.82) : vec3(0.05, 0.035, 0.025)) * (1.0 - 0.25 * groove);\n"
            + "    alb = mix(alb, soleC, mid); alb = mix(alb, fem > 0.5 ? vec3(0.7, 0.66, 0.58) : vec3(0.22, 0.22, 0.23), out_);\n"
            + "    alb *= 0.96 + 0.05 * vnoise(vR * 420.0);\n"
            + "    rough = mix(0.42, 0.75, mid); specK = 0.035; sss = vec3(0.12, 0.11, 0.1); detail = 0.0;\n"
            + "  } else if (mat > 2.5) {\n"
            + "    vec3 g3; alb = coily(vR, 70.0, g3);\n"
            + "    n = normalize(n - uRot * g3 * 0.35); rough = 1.0; specK = 0.004; sss = vec3(0.04); detail = 0.0; fuzz = 1.0;\n"
            + "  } else if (mat > 1.5) { alb = vec3(0.55, 0.52, 0.45); rough = 0.3; specK = 0.04; sss = vec3(0.2); detail = 0.0;\n"
            + "  } else {\n"
            + "    float blotch = vnoise(vR * 2.6) * 0.6 + vnoise(vR * 7.0) * 0.4;\n"
            + "    alb *= 0.95 + 0.08 * blotch;\n"
            + "    alb = mix(alb, alb * vec3(1.12, 0.9, 0.86), clamp(warm, 0.0, 1.0) * 0.45);\n"
            // the head mask blends smoothly (per-vertex 1 head, 0.5 ear, 0 body), so edges never follow triangles
            + "    float onHead = smoothstep(0.55, 0.95, face);\n"
            + "    float lp = lips(vH, fem) * onHead;\n"
            + "    alb = mix(alb, alb * vec3(0.62, 0.40, 0.42), lp); rough = mix(rough, 0.3, lp); specK = mix(specK, 0.055, lp);\n"
            + "    vec3 hairC = vec3(0.010, 0.008, 0.007);\n"
            + "    float b = brow(vH, fem) * onHead; float hc = hairCap(vH, fem) * smoothstep(0.0, 0.5, face) * smoothstep(-1.15, -0.95, vH.y);\n"
            // Obi: a high skin fade, stubble thinning to bare skin from the top block down to the ears,
            // the forehead and temples below the line-up are clean
            + "    float style = uHairStyle;\n"
            + "    float dots = smoothstep(0.35, 0.75, vnoise(vR * 420.0));\n"
            + "    if (style > 0.5 && style < 1.5) { bool frontZone = vH.z > 0.25 && abs(vH.x) < 0.52;\n"
            + "      float fade = frontZone ? 0.0 : smoothstep(0.0, 0.44, vH.y) * (0.35 + 0.65 * dots);\n"
            + "      hc = fade * 0.85 * smoothstep(0.0, 0.5, face); }\n"
            + "    if (style > 4.5) hc = 0.0;\n"
            // cornrows: braids running from the forehead back over the crown, bare partings between them, each braid a zigzag of plaits
            + "    if (style > 2.5 && style < 3.5) { float row = fract(vH.x * 8.0 + 0.5); float part = smoothstep(0.0, 0.05, row) * smoothstep(1.0, 0.95, row);\n"
            + "      float along = vH.y * 0.6 - vH.z; float plait = 0.35 + 0.65 * abs(sin(along * 55.0 + abs(row - 0.5) * 9.0));\n"
            + "      hc *= part * plait; }\n"
            // bantu knots: the scalp split into sections by partings, a knot sitting on each (the geometry)
            + "    if (style > 3.5 && style < 4.5) { vec2 cell = fract(vec2(vH.x * 3.2 + 0.5, (vH.y - vH.z) * 2.6));\n"
            + "      hc *= smoothstep(0.0, 0.06, cell.x) * smoothstep(1.0, 0.94, cell.x) * smoothstep(0.0, 0.06, cell.y) * smoothstep(1.0, 0.94, cell.y); }\n"
            + "    if (style > 1.5 && style < 2.5) hc = min(1.0, hc * 1.2);\n"
            + "    if (fem < 0.5) {\n"
            // a short-stubble shadow on the skin just outside the beard and up the sideburns, so the shell's edge melts in
            + "      float ax2 = abs(vH.x); float edge = cheekLine(ax2) - vH.y;\n"
            + "      float shadow = smoothstep(-0.09, 0.02, edge) * smoothstep(0.72, 0.62, ax2) * smoothstep(-0.2, -0.05, vH.z) * smoothstep(-1.2, -1.08, vH.y);\n"
            // the sideburns: dense short hair in front of the ears, thinning upward into the fade (Clothing.sideburn)
            + "      float sideS = smoothstep(0.53, 0.59, ax2) * smoothstep(0.8, 0.74, ax2) * smoothstep(-0.16, -0.06, vH.z) * smoothstep(0.3, 0.22, vH.z)\n"
            + "        * (1.0 - smoothstep(0.1, 0.44, vH.y)) * smoothstep(-0.2, -0.1, vH.y);\n"
            + "      float shortHair = 0.55 + 0.45 * vnoise(vR * vec3(300.0, 120.0, 300.0));\n"
            + "      if (uBeard > 0.5) hc = max(hc, shadow * (lips(vH, 0.0) > 0.1 ? 0.0 : 1.0) * (0.35 + 0.65 * dots) * 0.8 * onHead);\n"
            // clean shaven, the sideburns are short and tidy: they stop level with the middle of the ear, as close-cut as the fade
            + "      if (uBeard < 0.5) sideS *= smoothstep(-0.03, 0.03, vH.y) * (0.5 + 0.5 * dots) * 0.62;\n"
            + "      if (style < 4.5) hc = max(hc, min(1.0, sideS * 1.3) * shortHair * smoothstep(0.0, 0.5, face)); }\n"
            + "    float grain = 0.55 + 0.45 * vnoise(vR * vec3(160.0, 40.0, 160.0));\n"
            // Ivie: hair pulled back sleek toward the puffs, lines running front to back, and a fine centre part
            + "    if (style < 0.5) { grain = 0.6 + 0.4 * vnoise(vec3(vH.x * 260.0, vH.y * 18.0, vH.z * 18.0));\n"
            + "      float side = smoothstep(0.004, 0.016, abs(vH.x)); hc *= side + (1.0 - side) * smoothstep(-0.25, -0.45, vH.z); }\n"
            + "    float hb = clamp(max(b * 0.85, hc * (0.8 + 0.2 * grain)), 0.0, 1.0);\n"
            + "    alb = mix(alb, hairC * (0.6 + 0.8 * grain), hb);\n"
            + "    rough = mix(rough, 1.0, hb); specK *= 1.0 - 0.9 * hb; detail = 1.0 - hb; fuzz = hb;\n"
            + "  }\n"
            // fine pores: a small bend of the normal by a high-frequency noise on the rest surface
            + "  if (detail > 0.0) { float e = 0.003; vec3 q = vR * 170.0;\n"
            + "    float c0 = vnoise(q); vec3 g = vec3(vnoise(q + vec3(e*170.0,0,0)) - c0, vnoise(q + vec3(0,e*170.0,0)) - c0, vnoise(q + vec3(0,0,e*170.0)) - c0);\n"
            + "    n = normalize(n - uRot * g * 0.12 * detail); }\n"
            + "  vec3 L1 = normalize(vec3(-0.5, 0.55, 0.68)), L2 = normalize(vec3(0.75, 0.1, 0.45)), L3 = normalize(vec3(0.55, 0.35, -0.75));\n"
            // studio light: a strong warm key from the front left, a dim cool fill, a cool rim from behind
            + "  vec3 c1 = vec3(2.7, 2.45, 2.2), c2 = vec3(0.26, 0.3, 0.38), c3 = vec3(0.75, 0.95, 1.05);\n"
            + "  vec3 diff = c1 * wrapDiffuse(dot(n, L1), sss) + c2 * wrapDiffuse(dot(n, L2), sss);\n"
            + "  float term = max(0.0, 1.0 - abs(dot(n, L1) * 2.2)) * 0.25; diff += c1 * term * vec3(0.45, 0.12, 0.06) * sss.r;\n"
            + "  vec3 amb = mix(vec3(0.05, 0.04, 0.035), vec3(0.1, 0.1, 0.12), n.y * 0.5 + 0.5);\n"
            // teal light bouncing up from the glowing floor
            + "  amb += vec3(0.0, 0.05, 0.06) * smoothstep(0.1, -0.7, n.y);\n"
            + "  float fres = pow(1.0 - max(0.0, n.z), 5.0);\n"
            + "  vec3 col = alb * (diff + amb);\n"
            + "  float spec = 0.0;\n"
            + "  for (int i = 0; i < 2; i++) { vec3 L = i == 0 ? L1 : L2; vec3 c = i == 0 ? c1 : c2; vec3 hh = normalize(L + v);\n"
            + "    float ndh = max(0.0, dot(n, hh)); float ndl = max(0.0, dot(n, L));\n"
            + "    float tight = pow(ndh, 2.0 / max(0.02, rough * rough * 0.35)); float broad = pow(ndh, 2.0 / max(0.05, rough * rough));\n"
            + "    spec += c.r * (0.6 * tight + 0.4 * broad) * ndl; }\n"
            + "  float F = specK + (1.0 - specK) * fres;\n"
            + "  col += vec3(spec) * F * (vEye > 0.5 ? 3.0 : 1.0) * (1.0 - 0.85 * fuzz);\n"
            + "  col += c3 * pow(1.0 - max(0.0, n.z), 3.0) * max(0.0, dot(n, L3) + 0.4) * 0.2 * (alb + 0.08);\n"
            + "  if (uCloth.a > 0.5 && uClipA.x < 2.5) col += alb * pow(1.0 - max(0.0, n.z), 2.0) * 0.35;\n"
            + "  if (fuzz > 0.5) col += vec3(0.02, 0.012, 0.008) * pow(1.0 - max(0.0, n.z), 1.5);\n"
            + "  col = col / (1.0 + col);\n"
            // the cartoon look (Ghibli style): soft cel bands, warm light and cool violet shadow, a golden back-rim,
            // pen hatching in the shadows, watercolour pigment and paper grain, an ink line at the silhouette
            + "  if (uToon > 0.5) {\n"
            + "    vec3 base = pow(clamp(alb * 1.15, 0.0, 1.0), vec3(1.0 / 2.2));\n"
            // shaded from the smooth surface (no pores or weave bumps): clean shapes, one soft terminator
            + "    vec3 m = n0;\n"
            + "    float dl = dot(m, L1);\n"
            + "    float lit = smoothstep(-0.05, 0.22, dl), top = smoothstep(0.7, 0.85, dl);\n"
            + "    vec3 toon = base * mix(vec3(0.42, 0.44, 0.62), vec3(1.02, 0.97, 0.88), lit) * (1.0 + 0.06 * top);\n"
            + "    vec3 back = normalize(vec3(-0.35, 0.55, -0.75));\n"
            + "    toon += vec3(0.95, 0.66, 0.3) * 0.45 * pow(1.0 - max(0.0, m.z), 2.5) * smoothstep(-0.2, 0.4, dot(m, back));\n"
            + "    float hl = step(0.975, max(0.0, dot(n, normalize(L1 + v)))) * (vEye > 0.5 ? 1.0 : 0.0);\n"
            + "    toon += vec3(0.22, 0.2, 0.18) * hl;\n"
            + "    float hq = (vR.x * 0.7 + vR.y * 1.1 + vR.z * 0.5) * 80.0;\n"
            + "    float hatch = 1.0 - smoothstep(0.08, 0.2, abs(fract(hq) - 0.5));\n"
            + "    toon *= 1.0 - 0.22 * hatch * (1.0 - lit) * (1.0 - 0.6 * fuzz);\n"
            + "    float wash = vnoise(vR * 2.4) * 0.6 + vnoise(vR * 9.0) * 0.4;\n"
            + "    float grain = vnoise(vR * 140.0);\n"
            + "    toon *= (0.9 + 0.2 * wash) * (0.95 + 0.07 * grain);\n"
            + "    float ink = 1.0 - smoothstep(0.2, 0.36, m.z);\n"
            + "    toon = mix(toon, vec3(0.11, 0.07, 0.06), ink * 0.9);\n"
            + "    o = vec4(clamp(toon, 0.0, 1.0) * uTone.a, 1.0); return;\n"
            + "  }\n"
            + "  o = vec4(pow(col * uTone.a, vec3(1.0 / 2.2)), 1.0); }\n";

    static final String FILL_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor; layout(location=2) in float aEye;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform float uPush; out vec3 vN; out float vEye;\n"
            + "void main(){ vec3 n = normalize(aNor + vec3(1e-6)); vEye = aEye;\n"
            + "  gl_Position = uMvp * vec4(aPos + n * (aEye > 0.5 ? 0.0 : uPush), 1.0); vN = uRot * n; }\n";
    static final String FILL_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 vN; in float vEye; uniform vec4 uColor; uniform float uHalo; out vec4 o;\n"
            + "void main(){ vec3 n = normalize(vN); float rim = 1.0 - abs(n.z);\n"
            + "  float key = max(0.0, dot(n, normalize(vec3(-0.45, 0.55, 0.7))));\n"
            + "  float k = uHalo > 0.5 ? 0.22 * pow(rim, 3.0) : 0.025 + 0.09 * key + 1.05 * pow(rim, 2.6);\n"
            // the eyes glow so the gaze reads: soft whites, a bright iris ring, a dark pupil (all turn with the eyeball)
            + "  if (vEye > 0.5) { float d = vEye - 2.0;\n"
            + "    float iris = smoothstep(0.84, 0.88, d) * (1.0 - smoothstep(0.965, 0.975, d));\n"
            + "    float limbus = smoothstep(0.84, 0.87, d) * (1.0 - smoothstep(0.87, 0.9, d));\n"
            + "    k = uHalo > 0.5 ? 0.0 : (d < 0.86 ? 0.16 + 0.1 * key : 0.03) + 0.75 * iris + 0.6 * limbus; }\n"
            + "  o = vec4(uColor.rgb * k * uColor.a, 1.0); }\n";

    /**
     * One particle. Its body place is u·A + v·B + w·C of three skinned
     * vertices; its object place is its target through uObject; its scatter
     * place is the body place pushed out along a fixed direction. In a
     * transition each particle leaves after a delay set by its order (body
     * height, head first or last, or the object's build order) and travels on
     * an arc, a little brighter while it flies.
     */
    static final String PARTICLE_VS = "#version 300 es\n"
            + "precision highp float; precision highp sampler2D;\n"
            + "layout(location=0) in vec4 aCorner; layout(location=1) in vec4 aBary;\n"
            + "layout(location=2) in vec4 aTo; layout(location=3) in vec4 aAux; layout(location=4) in vec4 aPrev;\n"
            + "uniform sampler2D uPos; uniform sampler2D uNor; uniform int uTexW;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform mat4 uObject;\n"
            + "uniform int uFrom; uniform int uTo; uniform float uProgress; uniform float uByObject; uniform float uHeadFirst;\n"
            + "uniform float uSelected; uniform float uSize; uniform vec4 uScatter; uniform vec3 uPalette[16];\n"
            + "out float vLight; out float vSel; out vec3 vMat; out float vObj;\n"
            + "vec3 fetch(sampler2D t, float i){ int k = int(i + 0.5); return texelFetch(t, ivec2(k % uTexW, k / uTexW), 0).xyz; }\n"
            + "vec3 bodyAt(out vec3 n){ float u = aBary.x, v = aBary.y, w = 1.0 - u - v;\n"
            + "  n = normalize(u * fetch(uNor, aCorner.x) + v * fetch(uNor, aCorner.y) + w * fetch(uNor, aCorner.z) + vec3(1e-5));\n"
            + "  return u * fetch(uPos, aCorner.x) + v * fetch(uPos, aCorner.y) + w * fetch(uPos, aCorner.z); }\n"
            + "vec3 place(int k, out vec3 n){\n"
            + "  if (k == 1 || k == 3) { n = normalize(mat3(uObject) * vec3(0.0, 0.3, 1.0));\n"
            + "    return (uObject * vec4(k == 1 ? aTo.xyz : aPrev.xyz, 1.0)).xyz; }\n"
            + "  if (k == 2) { vec3 b = bodyAt(n); return b + uScatter.xyz + aAux.xyz * uScatter.w; }\n" // a loose cloud in the body's own shape
            + "  return bodyAt(n); }\n"
            + "void main(){\n"
            + "  vec3 n0, n1; vec3 p0 = place(uFrom, n0); vec3 p1 = place(uTo, n1);\n"
            + "  float order = uByObject > 0.5 ? aAux.w : aBary.w; if (uHeadFirst > 0.5) order = 1.0 - order;\n"
            + "  float spread = 0.6;\n"
            + "  float e = clamp((uProgress * (1.0 + spread) - order * spread), 0.0, 1.0);\n"
            + "  e = e * e * (3.0 - 2.0 * e);\n"
            + "  float fly = sin(e * 3.14159265);\n"
            + "  vec3 arc = (vec3(0.0, 0.35, 0.0) + aAux.xyz * 0.22) * fly * min(1.0, length(p1 - p0) * 0.3);\n"
            + "  vec3 p = mix(p0, p1, e) + arc;\n"
            + "  vec3 n = uRot * normalize(mix(n0, n1, e) + vec3(1e-5));\n"
            + "  gl_Position = uMvp * vec4(p, 1.0);\n"
            + "  float rim = 1.0 - abs(n.z);\n"
            + "  float key = max(0.0, dot(n, normalize(vec3(-0.45, 0.55, 0.7))));\n" // a soft key light shows the form
            + "  float lit = 0.07 + 0.95 * key * key + 0.45 * rim * rim;\n"
            + "  float shade = floor(aTo.w / 4096.0) / 15.0;\n"
            + "  float onObj = ((uTo == 1 || uTo == 3) ? e : 0.0) + ((uFrom == 1 || uFrom == 3) ? 1.0 - e : 0.0);\n"
            + "  lit = mix(lit, 0.12 + 0.9 * shade, clamp(onObj, 0.0, 1.0));\n" // object particles carry their own surface shading
            + "  vLight = lit * (1.0 + 0.6 * fly);\n"
            + "  float comp = mod(aTo.w, 256.0); vMat = uPalette[int(aTo.w / 256.0) & 15];\n"
            + "  vObj = ((uTo == 1 || uTo == 3) ? e : 0.0) + ((uFrom == 1 || uFrom == 3) ? 1.0 - e : 0.0);\n"
            + "  vSel = (uFrom == 1 && uTo == 1 && uSelected >= 0.0 && abs(comp - uSelected) < 0.5) ? 1.0 : 0.0;\n"
            + "  gl_PointSize = uSize * aBary.z * (1.0 + 0.35 * fly + 0.6 * vSel);\n"
            + "}\n";
    /** An inner-anatomy particle: on its skinned triangle like the skin's, shown only for the chosen layers and parts. */
    static final String INNER_VS = "#version 300 es\n"
            + "precision highp float; precision highp sampler2D;\n"
            + "layout(location=0) in vec4 aCorner; layout(location=1) in vec4 aBary;\n"
            + "uniform sampler2D uPos; uniform sampler2D uNor; uniform int uTexW;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform float uSize; uniform vec4 uLayers; uniform float uShow[80];\n"
            + "out float vLight; out float vSel; out vec3 vMat; out float vObj;\n"
            + "vec3 fetch(sampler2D t, float i){ int k = int(i + 0.5); return texelFetch(t, ivec2(k % uTexW, k / uTexW), 0).xyz; }\n"
            + "void main(){\n"
            + "  int layer = int(aBary.w + 0.5); int part = int(aCorner.w + 0.5);\n"
            + "  float show = uLayers[layer] * (part < 80 ? uShow[part] : 1.0);\n"
            + "  float u = aBary.x, v = aBary.y, w = 1.0 - u - v;\n"
            + "  vec3 p = u * fetch(uPos, aCorner.x) + v * fetch(uPos, aCorner.y) + w * fetch(uPos, aCorner.z);\n"
            + "  vec3 n = uRot * normalize(u * fetch(uNor, aCorner.x) + v * fetch(uNor, aCorner.y) + w * fetch(uNor, aCorner.z) + vec3(1e-5));\n"
            + "  gl_Position = show > 0.5 ? uMvp * vec4(p, 1.0) : vec4(2.0, 2.0, 2.0, 1.0);\n"
            + "  float key = max(0.0, dot(n, normalize(vec3(-0.45, 0.55, 0.7)))); float rim = 1.0 - abs(n.z);\n"
            + "  vLight = 0.25 + 0.85 * key * key + 0.4 * rim * rim; vSel = 0.0; vObj = 0.85;\n"
            + "  vMat = layer == 0 ? vec3(0.95, 0.96, 0.9) : layer == 1 ? vec3(1.0, 0.38, 0.34) : layer == 2 ? vec3(1.0, 0.6, 0.62) : vec3(1.0, 0.22, 0.28);\n"
            + "  gl_PointSize = show > 0.5 ? uSize * aBary.z : 0.0;\n"
            + "}\n";

    static final String PARTICLE_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float vLight; in float vSel; in vec3 vMat; in float vObj; uniform vec4 uColor; out vec4 o;\n"
            + "void main(){ vec2 c = gl_PointCoord * 2.0 - 1.0; float d = dot(c, c); if (d > 1.0) discard;\n"
            + "  float core = exp(-d * 4.0); float k = core * vLight * uColor.a;\n"
            + "  vec3 base = mix(uColor.rgb, mix(uColor.rgb, vMat, 0.6), clamp(vObj, 0.0, 1.0));\n"
            + "  vec3 col = mix(base, vec3(1.0), 0.25 * core + 0.45 * vSel);\n"
            + "  o = vec4(col * k, 1.0); }\n";

    static final String STUDIO_VS = "#version 300 es\n"
            + "layout(location=0) in vec2 aP; uniform mat4 uMvp; uniform float uFloor; uniform float uMode; out vec2 vP; out vec2 vS;\n"
            + "void main(){ vP = aP; if (uMode < 0.5) { gl_Position = vec4(aP, 0.99999, 1.0); vS = aP; }\n"
            + "  else { gl_Position = uMvp * vec4(aP.x, uFloor, aP.y, 1.0); vS = gl_Position.xy / gl_Position.w; } }\n";
    static final String STUDIO_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec2 vP; in vec2 vS; uniform float uMode; uniform vec4 uFeet; uniform vec4 uFeetOn; uniform float uPaint; uniform float uTime; out vec4 o;\n"
            + "float h2(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n"
            + "float n2(vec2 p){ vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);\n"
            + "  return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y); }\n"
            + "float fbm(vec2 p){ return 0.5 * n2(p) + 0.27 * n2(p * 2.1 + 3.0) + 0.15 * n2(p * 4.3 + 7.0) + 0.08 * n2(p * 8.7); }\n"
            // the cartoon studio's wall at a screen point (shared by the floor, which melts into it at the horizon)
            + "vec3 wallAt(vec2 s){\n"
            + "  vec3 col = mix(vec3(0.075, 0.17, 0.19), vec3(0.03, 0.055, 0.085), smoothstep(-0.6, 1.0, s.y));\n"
            + "  vec2 g = s - vec2(0.0, 0.05); float glow = exp(-dot(g * vec2(1.1, 0.75), g * vec2(1.1, 0.75)) * 2.2);\n"
            + "  col += vec3(0.04, 0.3, 0.24) * glow * 0.55;\n"
            + "  vec2 w = s - vec2(-1.05, 0.15); col += vec3(0.42, 0.2, 0.06) * exp(-dot(w * vec2(1.0, 0.7), w * vec2(1.0, 0.7)) * 2.6) * 0.45;\n"
            + "  float haze = fbm(s * vec2(1.4, 2.2) + vec2(uTime * 0.012, uTime * 0.006));\n"
            + "  col *= 0.86 + 0.28 * haze;\n"
            // the hexagons: thin edges, faint, strongest high up and gone toward the floor
            + "  vec2 h = s * vec2(9.0, 9.0 * 1.1547) + vec2(0.0, uTime * 0.01); vec2 r = vec2(1.0, 1.7320508); vec2 hh = r * 0.5;\n"
            + "  vec2 a1 = mod(h, r) - hh, b1 = mod(h - hh, r) - hh; vec2 gv = dot(a1, a1) < dot(b1, b1) ? a1 : b1;\n"
            + "  vec2 av = abs(gv); float edge = 0.5 - max(dot(av, normalize(vec2(1.0, 1.7320508))), av.x);\n"
            + "  float line = 1.0 - smoothstep(0.0, 0.04 + fwidth(edge) * 1.5, edge);\n"
            + "  col += vec3(0.1, 0.55, 0.45) * line * 0.06 * smoothstep(-0.3, 0.9, s.y) * (0.5 + haze);\n"
            + "  col *= 1.0 - 0.35 * smoothstep(0.7, 1.5, length(s * vec2(0.9, 0.7)));\n"
            + "  col *= 0.97 + 0.05 * n2(gl_FragCoord.xy * 0.7);\n"
            + "  return col; }\n"
            + "void main(){\n"
            // the cartoon studio (after the character sheets): deep slate fading to teal, an emerald glow behind the
            // figures and a warm amber light from the left, a slow drifting haze and a faint hexagon lattice; the floor
            // dark and glossy with a glowing ring around each figure's feet and soft contact shadows
            + "  if (uPaint > 0.5) {\n"
            + "    if (uMode < 0.5) { o = vec4(wallAt(vS), 1.0); return; }\n"
            + "    float far = clamp(1.0 - length(vP) / 20.0, 0.0, 1.0);\n"
            // dark and glossy close by, rising toward the wall's own colour at the horizon so there is no seam
            + "    vec3 fl = mix(wallAt(vS) * 0.92, vec3(0.025, 0.06, 0.075), smoothstep(0.0, 0.8, far));\n"
            + "    fl *= 0.9 + 0.2 * fbm(vP * 0.25);\n"
            + "    vec2 e0 = vP - uFeet.xy, e1 = vP - uFeet.zw; float l0 = length(e0), l1 = length(e1);\n"
            // the glowing stage ring and a soft emerald pool inside it
            + "    float ring = uFeetOn.x * exp(-pow((l0 - 1.45) / 0.05, 2.0)) + uFeetOn.y * exp(-pow((l1 - 1.45) / 0.05, 2.0));\n"
            + "    float ring2 = uFeetOn.x * exp(-pow((l0 - 1.9) / 0.03, 2.0)) + uFeetOn.y * exp(-pow((l1 - 1.9) / 0.03, 2.0));\n"
            + "    float pool = uFeetOn.x * exp(-l0 * l0 / 2.4) + uFeetOn.y * exp(-l1 * l1 / 2.4);\n"
            + "    fl += vec3(0.06, 0.73, 0.5) * ring * 0.75 + vec3(0.43, 0.9, 0.72) * ring2 * 0.25 + vec3(0.03, 0.22, 0.17) * pool * 0.6;\n"
            + "    float sh = max(uFeetOn.x * (0.6 * exp(-l0 * l0 / 1.2) + 0.4 * exp(-l0 * l0 / 0.25)), uFeetOn.y * (0.6 * exp(-l1 * l1 / 1.2) + 0.4 * exp(-l1 * l1 / 0.25)));\n"
            + "    fl *= 1.0 - min(0.85, sh);\n"
            + "    o = vec4(fl, 1.0); return;\n"
            + "  }\n"
            // the backdrop: charcoal, a soft pool of light high behind the figures, darker toward the corners
            + "  vec2 c = vS - vec2(0.0, 0.35); float pool = exp(-dot(c * vec2(0.9, 1.3), c * vec2(0.9, 1.3)) * 1.6);\n"
            + "  vec3 wall = mix(vec3(0.018, 0.019, 0.021), vec3(0.105, 0.105, 0.11), pool);\n"
            + "  if (uMode < 0.5) { o = vec4(wall, 1.0); return; }\n"
            // the floor: interlocking rings on a square lattice, each a glowing teal tube, fading into the dark with distance
            + "  vec2 q = vP / 1.1; vec2 b = floor(q); float line = 9.0;\n"
            + "  for (int i = 0; i < 4; i++) { vec2 cc = b + vec2(float(i & 1), float(i >> 1)); line = min(line, abs(length(q - cc) - 0.72)); }\n"
            + "  float w = max(0.035, fwidth(line) * 1.2);\n"
            + "  float tube = exp(-(line / w) * (line / w)); float core = exp(-(line / (w * 0.35)) * (line / (w * 0.35)));\n"
            + "  float far = clamp(1.0 - length(vP) / 22.0, 0.0, 1.0); far *= far;\n"
            // fine lines melt into one soft glow far away instead of aliasing
            + "  float blur = smoothstep(0.035, 0.2, fwidth(line)); float glow = mix(tube, 0.28, blur);\n"
            + "  vec3 teal = vec3(0.06, 0.46, 0.56) * glow + vec3(0.35, 0.8, 0.85) * core * (1.0 - blur) * 0.6;\n"
            + "  vec3 floorC = vec3(0.004, 0.012, 0.016) + teal * far;\n"
            // soft contact shadows where each person stands: a wide penumbra and a darker core under the feet
            + "  vec2 d0 = vP - uFeet.xy, d1 = vP - uFeet.zw;\n"
            + "  float r0 = dot(d0, d0), r1 = dot(d1, d1);\n"
            + "  float sh = max(uFeetOn.x * (0.7 * exp(-r0 / 2.2) + 0.3 * exp(-r0 / 0.35)), uFeetOn.y * (0.7 * exp(-r1 / 2.2) + 0.3 * exp(-r1 / 0.35)));\n"
            + "  floorC *= 1.0 - min(0.92, sh);\n"
            + "  o = vec4(mix(wall * 0.5, floorC, smoothstep(0.0, 0.25, far + 0.1)), 1.0); }\n";

    static final String GRID_VS = "#version 300 es\n"
            + "layout(location=0) in vec4 aGrid;\n"
            + "uniform mat4 uMvp; uniform float uFloor; uniform float uSize; out float vKind; out float vFade;\n"
            + "void main(){ vec4 p = uMvp * vec4(aGrid.x, uFloor, aGrid.y, 1.0); gl_Position = p; gl_PointSize = uSize;\n"
            + "  vKind = aGrid.z; vFade = clamp(1.0 - length(aGrid.xy) / 16.0, 0.0, 1.0); }\n";
    static final String GRID_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float vKind; in float vFade; out vec4 o;\n"
            + "void main(){ vec3 c = vKind > 1.5 ? vec3(0.42, 0.8, 0.29) : vKind > 0.5 ? vec3(0.88, 0.31, 0.29) : vec3(0.35, 0.39, 0.44);\n"
            + "  float a = (vKind > 0.5 ? 0.55 : 0.28) * vFade; o = vec4(c * a, 1.0); }\n";
}
