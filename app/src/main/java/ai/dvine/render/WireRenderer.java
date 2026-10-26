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
        /** Centre and radius of the scattered cloud (body units). */
        public final float[] scatter = {0f, -1.6f, 0f, 2.2f};
        /** Object placement (object space → body space), and the component to highlight (-1 none). */
        public final float[] object = identity();
        public float selected = -1f;
        /** Body fill on (hides particles behind the body); off while the body is particles in flight. */
        public boolean bodyFill = true;
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
    private int fillProg, particleProg, gridProg;
    private int gridVbo, gridCount;
    /** Count of live GL objects (for leak checks). */
    public int liveObjects;
    public int glErrors;

    private static final class Slot {
        HumanAsset asset;
        boolean dirtyStatic;
        int vertices, triIndices, particles, texH;
        IntBuffer tri;
        FloatBuffer corner, bary, aux;
        // the object the particles can become (targets aligned one-to-one with the particles)
        FloatBuffer target, previous;
        boolean dirtyTarget;
        IntBuffer objTri;
        FloatBuffer objPos;
        int objIndices, objVertices;
        // double-buffered dynamic data
        FloatBuffer pos, nor, texPos, texNor;
        final Frame pending = new Frame();
        final Frame shown = new Frame();
        boolean hasPending;
        // GL handles
        int vboPos, vboNor, ibo, vboCorner, vboBary, vboAux, vboTarget, vboPrev, objVbo, objIbo, tPos, tNor;
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
        Slot s = slots[slot];
        synchronized (lock) {
            s.asset = a;
            int n = a.mesh.vertexCount();
            s.vertices = n;
            s.texH = (n + TEX_W - 1) / TEX_W;
            s.tri = ints(a.mesh.tri);
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
            s.pos = FloatBuffer.allocate(n * 3);
            s.nor = FloatBuffer.allocate(n * 3);
            s.texPos = direct(TEX_W * s.texH * 3);
            s.texNor = direct(TEX_W * s.texH * 3);
            s.pending.pos = new float[n * 3];
            s.pending.nor = new float[n * 3];
            s.shown.pos = new float[n * 3];
            s.shown.nor = new float[n * 3];
            s.dirtyStatic = true;
            s.hasPending = false;
        }
    }

    /**
     * The object this slot's particles can become: its surface (object space,
     * for the dark occluding fill) and one target per particle (xyz, component
     * id, build order 0..1), as made by the object engine.
     */
    public void setObject(int slot, float[] objPos, int[] objTri, float[] targets) {
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
                System.arraycopy(pos, 0, f.pos, 0, f.pos.length);
                System.arraycopy(nor, 0, f.nor, 0, f.nor.length);
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
        gridProg = prog(GRID_VS, GRID_FS);
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
            for (int p : new int[]{fillProg, particleProg, gridProg}) {
                if (p != 0) {
                    gl.deleteProgram(p);
                    liveObjects--;
                }
            }
            fillProg = particleProg = gridProg = 0;
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
        s.ibo = buf();
        s.vboCorner = buf();
        s.vboBary = buf();
        s.vboAux = buf();
        s.vboTarget = buf();
        s.vboPrev = buf();
        s.objVbo = buf();
        s.objIbo = buf();
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.triIndices * 4, s.tri, GlApi.STATIC_DRAW);
        s.corner.position(0);
        s.bary.position(0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboCorner);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.corner, GlApi.STATIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboBary);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.particles * 16, s.bary, GlApi.STATIC_DRAW);
        s.dirtyTarget = true;
        s.tPos = gl.floatTexture(TEX_W, s.texH);
        s.tNor = gl.floatTexture(TEX_W, s.texH);
        liveObjects += 2;
        s.created = true;
        s.dirtyStatic = false;
    }

    private void deleteSlot(Slot s) {
        for (int b : new int[]{s.vboPos, s.vboNor, s.ibo, s.vboCorner, s.vboBary, s.vboAux, s.vboTarget, s.vboPrev, s.objVbo, s.objIbo}) {
            gl.deleteBuffer(b);
            liveObjects--;
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
        // 1. dark fill writing depth, pushed back: hides the particles behind the body (or the finished object)
        gl.useProgram(fillProg);
        gl.uniform4f(gl.uniform(fillProg, "uColor"), 0.004f + f.r * 0.02f, 0.006f + f.g * 0.02f, 0.008f + f.b * 0.02f, 1f);
        gl.disable(GlApi.BLEND);
        gl.depthMask(true);
        gl.enable(GlApi.POLYGON_OFFSET_FILL);
        gl.polygonOffset(1.5f, 2f);
        if (intact && f.bodyFill) {
            gl.uniformMatrix4(gl.uniform(fillProg, "uMvp"), camera.mvp);
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
            gl.vertexAttrib(0, 3, 12, 0);
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
            gl.drawElements(GlApi.TRIANGLES, s.triIndices, 0);
        } else if (objectShown && s.objIndices > 0) {
            gl.uniformMatrix4(gl.uniform(fillProg, "uMvp"), mul(camera.mvp, f.object));
            gl.bindBuffer(GlApi.ARRAY_BUFFER, s.objVbo);
            gl.vertexAttrib(0, 3, 12, 0);
            gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.objIbo);
            gl.drawElements(GlApi.TRIANGLES, s.objIndices, 0);
        }
        gl.disable(GlApi.POLYGON_OFFSET_FILL);

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
        gl.uniform4f(gl.uniform(p, "uColor"), f.r, f.g, f.b, a);
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

    private static final float[] PALETTE = new float[48];

    static {
        for (int i = 0; i < 16; i++) {
            System.arraycopy(ai.dvine.create.Materials.RGB[i], 0, PALETTE, i * 3, 3);
        }
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

    static final String FILL_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos;\n"
            + "uniform mat4 uMvp;\n"
            + "void main(){ gl_Position = uMvp * vec4(aPos, 1.0); }\n";
    static final String FILL_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "uniform vec4 uColor; out vec4 o;\n"
            + "void main(){ o = uColor; }\n";

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
            + "  if (k == 2) { n = normalize(aAux.xyz + vec3(1e-4)); return uScatter.xyz + aAux.xyz * uScatter.w; }\n"
            + "  return bodyAt(n); }\n"
            + "void main(){\n"
            + "  vec3 n0, n1; vec3 p0 = place(uFrom, n0); vec3 p1 = place(uTo, n1);\n"
            + "  float order = uByObject > 0.5 ? aAux.w : aBary.w; if (uHeadFirst > 0.5) order = 1.0 - order;\n"
            + "  float spread = 0.6;\n"
            + "  float e = clamp((uProgress * (1.0 + spread) - order * spread), 0.0, 1.0);\n"
            + "  e = e * e * (3.0 - 2.0 * e);\n"
            + "  float fly = sin(e * 3.14159265);\n"
            + "  vec3 arc = (vec3(0.0, 0.6, 0.0) + aAux.xyz * 0.8) * fly * min(1.0, length(p1 - p0) * 0.35);\n"
            + "  vec3 p = mix(p0, p1, e) + arc;\n"
            + "  vec3 n = uRot * normalize(mix(n0, n1, e) + vec3(1e-5));\n"
            + "  gl_Position = uMvp * vec4(p, 1.0);\n"
            + "  float rim = 1.0 - abs(n.z);\n"
            + "  vLight = (0.42 + 0.58 * rim * rim) * (1.0 + 0.6 * fly);\n"
            + "  float comp = mod(aTo.w, 256.0); vMat = uPalette[int(aTo.w / 256.0) & 15];\n"
            + "  vObj = ((uTo == 1 || uTo == 3) ? e : 0.0) + ((uFrom == 1 || uFrom == 3) ? 1.0 - e : 0.0);\n"
            + "  vSel = (uFrom == 1 && uTo == 1 && uSelected >= 0.0 && abs(comp - uSelected) < 0.5) ? 1.0 : 0.0;\n"
            + "  gl_PointSize = uSize * aBary.z * (1.0 + 0.35 * fly + 0.6 * vSel);\n"
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
