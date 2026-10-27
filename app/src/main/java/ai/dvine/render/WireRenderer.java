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
    private int fillProg, particleProg, innerProg, gridProg, skinProg;
    private int gridVbo, gridCount;
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
            s.garments = ai.dvine.human.Clothing.design(a);
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
        drawGrid();
        for (Slot s : slots) {
            if (s.created && s.shown.visible && s.shown.alpha > 0.01f) {
                drawSlot(s);
            }
        }
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
            for (int p : new int[]{fillProg, particleProg, innerProg, gridProg, skinProg}) {
                if (p != 0) {
                    gl.deleteProgram(p);
                    liveObjects--;
                }
            }
            fillProg = particleProg = innerProg = gridProg = skinProg = 0;
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
            gl.uniform4f(gl.uniform(p, "uClipB"), gm.clip[4], gm.clip[5], gm.clip[6], 0f);
            float[] jt = s.asset.joints;
            int sh = ai.dvine.face.nano.DotBody.ARM_L * 3, el = ai.dvine.face.nano.DotBody.FORE_L * 3;
            gl.uniform4f(gl.uniform(p, "uShoulder"), jt[sh], jt[sh + 1], jt[sh + 2], 0f);
            gl.uniform4f(gl.uniform(p, "uElbow"), jt[el], jt[el + 1], jt[el + 2], 0f);
            gl.uniform1f(gl.uniform(p, "uWeave"), gm.weave);
            gl.uniform1f(gl.uniform(p, "uPush"), gm.push);
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
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform vec4 uHeadA; uniform vec4 uHeadB; uniform float uPush;\n"
            + "out vec3 vN; out vec3 vR; out vec3 vH; out float vEye; out vec4 vMat;\n"
            + "void main(){ vec3 nn = normalize(aNor + vec3(1e-6)); gl_Position = uMvp * vec4(aPos + nn * uPush, 1.0); vN = uRot * nn;\n"
            // the rest position: skin detail stays on the skin as it moves; in head units it places the face features
            + "  vR = aRest; float lip = uHeadA.y - 0.61 * uHeadB.x;\n"
            + "  float hy = aRest.y >= lip ? (aRest.y - uHeadA.y) / uHeadB.x : -0.61 + (aRest.y - lip) / uHeadB.z;\n"
            + "  vH = vec3((aRest.x - uHeadA.x) / uHeadA.w, hy, (aRest.z - uHeadA.z) / uHeadB.y);\n"
            + "  vEye = aEye; vMat = aMat; }\n";
    static final String SKIN_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "in vec3 vN; in vec3 vR; in vec3 vH; in float vEye; in vec4 vMat; uniform vec4 uTone; uniform mat3 uRot; uniform vec4 uHeadB;\n"
            + "uniform vec4 uCloth; uniform float uWeave; uniform vec4 uClipA; uniform vec4 uClipB; uniform vec4 uShoulder; uniform vec4 uElbow;\n"
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
            + "  return r.y > uClipB.x; }\n"
            + "out vec4 o;\n"
            + "float h3(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }\n"
            + "float vnoise(vec3 x){ vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);\n"
            + "  return mix(mix(mix(h3(i), h3(i + vec3(1,0,0)), f.x), mix(h3(i + vec3(0,1,0)), h3(i + vec3(1,1,0)), f.x), f.y),\n"
            + "             mix(mix(h3(i + vec3(0,0,1)), h3(i + vec3(1,0,1)), f.x), mix(h3(i + vec3(0,1,1)), h3(i + vec3(1,1,1)), f.x), f.y), f.z); }\n"
            + "vec3 wrapDiffuse(float ndl, vec3 w){ return clamp((vec3(ndl) + w) / (1.0 + w), 0.0, 1.0); }\n"
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
            + "  vec3 n = normalize(vN); vec3 v = vec3(0.0, 0.0, 1.0); float fem = uHeadB.w;\n"
            + "  float mat = vMat.x; float warm = vMat.y; float face = vMat.z;\n"
            + "  vec3 alb = uTone.rgb; float rough = 0.5; float specK = 0.028; vec3 sss = vec3(0.45, 0.22, 0.14);\n"
            + "  float detail = 1.0;\n"
            + "  if (uCloth.a > 0.5 && outside(vR)) discard;\n"
            + "  if (uCloth.a > 0.5) {\n"
            // fabric: a fine weave (twill diagonals for denim), soft folds, no oily shine, a velvety sheen at grazing angles
            + "    float tw = vnoise(vec3((vR.x + vR.y) * 220.0, (vR.y - vR.z) * 40.0, vR.z * 220.0));\n"
            + "    float knit = vnoise(vR * 260.0);\n"
            + "    float wv = mix(knit, tw, uWeave);\n"
            + "    float fold = vnoise(vR * vec3(5.0, 1.6, 5.0));\n"
            + "    alb = uCloth.rgb * (0.86 + 0.28 * wv) * (0.85 + 0.3 * fold);\n"
            + "    if (uClipA.x > 1.5) { float sole = smoothstep(-4.08, -4.12, vR.y); alb = mix(alb, vec3(0.02), sole * 0.8); }\n"
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
            + "  } else if (mat > 1.5) { alb = vec3(0.55, 0.52, 0.45); rough = 0.3; specK = 0.04; sss = vec3(0.2); detail = 0.0;\n"
            + "  } else {\n"
            + "    float blotch = vnoise(vR * 2.6) * 0.6 + vnoise(vR * 7.0) * 0.4;\n"
            + "    alb *= 0.93 + 0.14 * blotch;\n"
            + "    alb = mix(alb, alb * vec3(1.12, 0.9, 0.86), clamp(warm, 0.0, 1.0) * 0.45);\n"
            // the head mask blends smoothly (per-vertex 1 head, 0.5 ear, 0 body), so edges never follow triangles
            + "    float onHead = smoothstep(0.55, 0.95, face);\n"
            + "    float lp = lips(vH, fem) * onHead;\n"
            + "    alb = mix(alb, alb * vec3(0.62, 0.40, 0.42), lp); rough = mix(rough, 0.3, lp); specK = mix(specK, 0.055, lp);\n"
            + "    vec3 hairC = vec3(0.010, 0.008, 0.007);\n"
            + "    float b = brow(vH, fem) * onHead; float hc = hairCap(vH, fem) * smoothstep(0.0, 0.5, face) * smoothstep(-1.15, -0.95, vH.y);\n"
            + "    float grain = 0.55 + 0.45 * vnoise(vR * vec3(160.0, 40.0, 160.0));\n"
            + "    float hb = clamp(max(b * 0.85, hc * (0.8 + 0.2 * grain)), 0.0, 1.0);\n"
            + "    alb = mix(alb, hairC * (0.6 + 0.8 * grain), hb);\n"
            + "    rough = mix(rough, 0.8, hb); detail = 1.0 - hb;\n"
            + "  }\n"
            // fine pores: a small bend of the normal by a high-frequency noise on the rest surface
            + "  if (detail > 0.0) { float e = 0.003; vec3 q = vR * 170.0;\n"
            + "    float c0 = vnoise(q); vec3 g = vec3(vnoise(q + vec3(e*170.0,0,0)) - c0, vnoise(q + vec3(0,e*170.0,0)) - c0, vnoise(q + vec3(0,0,e*170.0)) - c0);\n"
            + "    n = normalize(n - uRot * g * 0.22 * detail); }\n"
            + "  vec3 L1 = normalize(vec3(-0.5, 0.55, 0.68)), L2 = normalize(vec3(0.75, 0.1, 0.45)), L3 = normalize(vec3(0.15, 0.45, -0.9));\n"
            + "  vec3 c1 = vec3(2.3, 2.15, 2.0), c2 = vec3(0.38, 0.44, 0.55), c3 = vec3(0.9, 0.88, 0.85);\n"
            + "  vec3 diff = c1 * wrapDiffuse(dot(n, L1), sss) + c2 * wrapDiffuse(dot(n, L2), sss);\n"
            + "  float term = max(0.0, 1.0 - abs(dot(n, L1) * 2.2)) * 0.25; diff += c1 * term * vec3(0.45, 0.12, 0.06) * sss.r;\n"
            + "  vec3 amb = mix(vec3(0.05, 0.04, 0.035), vec3(0.12, 0.13, 0.16), n.y * 0.5 + 0.5);\n"
            + "  float fres = pow(1.0 - max(0.0, n.z), 5.0);\n"
            + "  vec3 col = alb * (diff + amb);\n"
            + "  float spec = 0.0;\n"
            + "  for (int i = 0; i < 2; i++) { vec3 L = i == 0 ? L1 : L2; vec3 c = i == 0 ? c1 : c2; vec3 hh = normalize(L + v);\n"
            + "    float ndh = max(0.0, dot(n, hh)); float ndl = max(0.0, dot(n, L));\n"
            + "    float tight = pow(ndh, 2.0 / max(0.02, rough * rough * 0.35)); float broad = pow(ndh, 2.0 / max(0.05, rough * rough));\n"
            + "    spec += c.r * (0.6 * tight + 0.4 * broad) * ndl; }\n"
            + "  float F = specK + (1.0 - specK) * fres;\n"
            + "  col += vec3(spec) * F * (vEye > 0.5 ? 3.0 : 1.0);\n"
            + "  col += c3 * pow(1.0 - max(0.0, n.z), 3.0) * max(0.0, dot(n, L3) + 0.4) * 0.12 * (alb + 0.08);\n"
            + "  if (uCloth.a > 0.5) col += alb * pow(1.0 - max(0.0, n.z), 2.0) * 0.35;\n"
            + "  col = col / (1.0 + col);\n"
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
