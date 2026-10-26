package ai.dvine.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import ai.dvine.human.HumanAsset;

/**
 * Draws the digital humans as luminous triangulated wireframes on black:
 * <ol>
 *   <li>a dark fill that writes depth (pushed slightly back), so edges on the
 *   far side are hidden and the inside reads as dark surfaces;</li>
 *   <li>every mesh edge as a screen-space quad (so line width works on every
 *   GPU), in a wide faint glow pass and a sharp core pass, brighter where the
 *   surface turns away (the glowing silhouette of the reference);</li>
 *   <li>feature lines (lids, brows, lips, iris rings, hair) a little brighter;</li>
 *   <li>a soft dot on every vertex;</li>
 * </ol>
 * plus a dotted Blender-like floor. All geometry is batched into a few
 * buffers per persona; per frame only positions and normals are uploaded.
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
        /** Fraction of edges to draw (most important first), 0.3..1. */
        public float lod = 1f;
        public boolean visible;
    }

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
    private int fillProg, lineProg, pointProg, gridProg;
    private int gridVbo, gridCount;
    /** Count of live GL objects (for leak checks). */
    public int liveObjects;
    public int glErrors;

    private static final class Slot {
        HumanAsset asset;
        boolean dirtyStatic;
        int vertices, triIndices, edgeQuads, lineQuads, texH;
        IntBuffer tri;
        FloatBuffer quad;
        IntBuffer quadIdx;
        // double-buffered dynamic data
        FloatBuffer pos, nor, texPos, texNor;
        final Frame pending = new Frame();
        final Frame shown = new Frame();
        boolean hasPending;
        // GL handles
        int vboPos, vboNor, ibo, quadVbo, quadIbo, tPos, tNor;
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

    /** Sets (or replaces) the asset for a slot; GL objects are rebuilt on the next frame. */
    public void setAsset(int slot, HumanAsset a) {
        Slot s = slots[slot];
        synchronized (lock) {
            s.asset = a;
            int n = a.mesh.vertexCount();
            s.vertices = n;
            s.texH = (n + TEX_W - 1) / TEX_W;
            s.tri = ints(a.mesh.tri);
            s.triIndices = a.mesh.tri.length;
            int ne = a.mesh.edgeCount(), nl = a.lines.length / 2;
            s.edgeQuads = ne;
            s.lineQuads = nl;
            float[] q = new float[(ne + nl) * 4 * 4];
            int[] qi = new int[(ne + nl) * 6];
            for (int e = 0; e < ne + nl; e++) {
                int va = e < ne ? a.mesh.edge[e * 2] : a.lines[(e - ne) * 2];
                int vb = e < ne ? a.mesh.edge[e * 2 + 1] : a.lines[(e - ne) * 2 + 1];
                for (int k = 0; k < 4; k++) {
                    int o = (e * 4 + k) * 4;
                    q[o] = va;
                    q[o + 1] = vb;
                    q[o + 2] = (k & 1) == 0 ? -1f : 1f; // side
                    q[o + 3] = k < 2 ? 0f : 1f;          // end
                }
                int base = e * 4;
                int[] six = {base, base + 1, base + 2, base + 2, base + 1, base + 3};
                System.arraycopy(six, 0, qi, e * 6, 6);
            }
            s.quad = floats(q);
            s.quadIdx = ints(qi);
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
        lineProg = prog(LINE_VS, LINE_FS);
        pointProg = prog(POINT_VS, POINT_FS);
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
            for (int p : new int[]{fillProg, lineProg, pointProg, gridProg}) {
                if (p != 0) {
                    gl.deleteProgram(p);
                    liveObjects--;
                }
            }
            fillProg = lineProg = pointProg = gridProg = 0;
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
        s.quadVbo = buf();
        s.quadIbo = buf();
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.vertices * 12, null, GlApi.DYNAMIC_DRAW);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.triIndices * 4, s.tri, GlApi.STATIC_DRAW);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.quadVbo);
        gl.bufferData(GlApi.ARRAY_BUFFER, s.quad.capacity() * 4, s.quad, GlApi.STATIC_DRAW);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.quadIbo);
        gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, s.quadIdx.capacity() * 4, s.quadIdx, GlApi.STATIC_DRAW);
        s.tPos = gl.floatTexture(TEX_W, s.texH);
        s.tNor = gl.floatTexture(TEX_W, s.texH);
        liveObjects += 2;
        s.created = true;
        s.dirtyStatic = false;
    }

    private void deleteSlot(Slot s) {
        for (int b : new int[]{s.vboPos, s.vboNor, s.ibo, s.quadVbo, s.quadIbo}) {
            gl.deleteBuffer(b);
            liveObjects--;
        }
        gl.deleteTexture(s.tPos);
        gl.deleteTexture(s.tNor);
        liveObjects -= 2;
        s.created = false;
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
        // 1. dark fill, writing depth, pushed back so the edges on top win the depth test
        gl.useProgram(fillProg);
        gl.uniformMatrix4(gl.uniform(fillProg, "uMvp"), camera.mvp);
        gl.uniform4f(gl.uniform(fillProg, "uColor"), 0.004f + f.r * 0.03f, 0.006f + f.g * 0.03f, 0.008f + f.b * 0.03f, 1f);
        gl.disable(GlApi.BLEND);
        gl.depthMask(true);
        gl.enable(GlApi.POLYGON_OFFSET_FILL);
        gl.polygonOffset(1.5f, 2f);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.vertexAttrib(0, 3, 12, 0);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.ibo);
        gl.drawElements(GlApi.TRIANGLES, s.triIndices, 0);
        gl.disable(GlApi.POLYGON_OFFSET_FILL);

        // 2-3. glowing edges and feature lines (additive, depth-tested, no depth writes)
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.ONE, GlApi.ONE);
        gl.depthMask(false);
        gl.useProgram(lineProg);
        gl.uniformMatrix4(gl.uniform(lineProg, "uMvp"), camera.mvp);
        gl.uniformMatrix3(gl.uniform(lineProg, "uRot"), camera.rot3);
        gl.uniform2f(gl.uniform(lineProg, "uViewport"), width, height);
        gl.uniform1i(gl.uniform(lineProg, "uTexW"), TEX_W);
        gl.uniform1i(gl.uniform(lineProg, "uPos"), 0);
        gl.uniform1i(gl.uniform(lineProg, "uNor"), 1);
        gl.bindTexture(0, s.tPos);
        gl.bindTexture(1, s.tNor);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.quadVbo);
        gl.vertexAttrib(0, 4, 16, 0);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, s.quadIbo);
        int edges = Math.max(1, (int) (s.edgeQuads * Math.max(0.3f, Math.min(1f, f.lod))));
        line(f, a * 0.16f, 5.5f * density, 0.25f, 0.7f, edges, 0);          // glow
        line(f, a * 0.85f, 1.3f * density, 0.45f, 0.9f, edges, 0);          // core
        if (s.lineQuads > 0) {
            line(f, a * 0.2f, 6f * density, 0.6f, 0.4f, s.lineQuads, s.edgeQuads);
            line(f, a, 1.6f * density, 0.9f, 0.4f, s.lineQuads, s.edgeQuads);
        }
        // 4. vertex dots
        gl.useProgram(pointProg);
        gl.uniformMatrix4(gl.uniform(pointProg, "uMvp"), camera.mvp);
        gl.uniformMatrix3(gl.uniform(pointProg, "uRot"), camera.rot3);
        gl.uniform1f(gl.uniform(pointProg, "uSize"), 3.4f * density);
        gl.uniform4f(gl.uniform(pointProg, "uColor"), f.r, f.g, f.b, a);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboPos);
        gl.vertexAttrib(0, 3, 12, 0);
        gl.bindBuffer(GlApi.ARRAY_BUFFER, s.vboNor);
        gl.vertexAttrib(1, 3, 12, 0);
        gl.drawArrays(GlApi.POINTS, 0, s.vertices);
        gl.disableAttrib(1);
        gl.depthMask(true);
        gl.disable(GlApi.BLEND);
    }

    private void line(Frame f, float alpha, float widthPx, float base, float rim, int count, int first) {
        gl.uniform1f(gl.uniform(lineProg, "uWidth"), widthPx);
        gl.uniform4f(gl.uniform(lineProg, "uColor"), f.r, f.g, f.b, alpha);
        gl.uniform1f(gl.uniform(lineProg, "uBase"), base);
        gl.uniform1f(gl.uniform(lineProg, "uRim"), rim);
        gl.drawElements(GlApi.TRIANGLES, count * 6, first * 6 * 4);
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

    /** Each edge is a quad: both ends are fetched from the position texture and pushed apart in screen space. */
    static final String LINE_VS = "#version 300 es\n"
            + "precision highp float; precision highp sampler2D;\n"
            + "layout(location=0) in vec4 aEdge;\n" // index a, index b, side, end
            + "uniform sampler2D uPos; uniform sampler2D uNor;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform vec2 uViewport; uniform float uWidth; uniform int uTexW;\n"
            + "out float vRim; out float vSide;\n"
            + "vec3 fetch(sampler2D t, float i){ int k = int(i + 0.5); return texelFetch(t, ivec2(k % uTexW, k / uTexW), 0).xyz; }\n"
            + "void main(){\n"
            + "  vec3 pa = fetch(uPos, aEdge.x), pb = fetch(uPos, aEdge.y);\n"
            + "  vec4 ca = uMvp * vec4(pa, 1.0), cb = uMvp * vec4(pb, 1.0);\n"
            + "  vec2 sa = ca.xy / max(ca.w, 1e-4) * uViewport * 0.5, sb = cb.xy / max(cb.w, 1e-4) * uViewport * 0.5;\n"
            + "  vec2 d = sb - sa; d = length(d) > 1e-4 ? normalize(d) : vec2(1.0, 0.0);\n"
            + "  vec2 n = vec2(-d.y, d.x);\n"
            + "  vec4 c = aEdge.w < 0.5 ? ca : cb;\n"
            + "  c.xy += n * aEdge.z * uWidth / uViewport * c.w;\n"
            + "  gl_Position = c;\n"
            + "  vec3 nn = uRot * fetch(uNor, aEdge.w < 0.5 ? aEdge.x : aEdge.y);\n"
            + "  vRim = 1.0 - abs(nn.z); vSide = aEdge.z;\n"
            + "}\n";
    static final String LINE_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float vRim; in float vSide;\n"
            + "uniform vec4 uColor; uniform float uBase; uniform float uRim; out vec4 o;\n"
            + "void main(){\n"
            + "  float across = 1.0 - vSide * vSide;\n"
            + "  float k = (uBase + uRim * vRim * vRim) * across * uColor.a;\n"
            + "  o = vec4(uColor.rgb * k, 1.0);\n"
            + "}\n";

    static final String POINT_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor;\n"
            + "uniform mat4 uMvp; uniform mat3 uRot; uniform float uSize; out float vRim;\n"
            + "void main(){ gl_Position = uMvp * vec4(aPos, 1.0); gl_PointSize = uSize; vec3 n = uRot * aNor; vRim = 1.0 - abs(n.z); }\n";
    static final String POINT_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float vRim; uniform vec4 uColor; out vec4 o;\n"
            + "void main(){ vec2 c = gl_PointCoord * 2.0 - 1.0; float d = dot(c, c); if (d > 1.0) discard;\n"
            + "  float k = (1.0 - d) * (0.5 + 0.6 * vRim) * uColor.a; o = vec4(uColor.rgb * k, 1.0); }\n";

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
