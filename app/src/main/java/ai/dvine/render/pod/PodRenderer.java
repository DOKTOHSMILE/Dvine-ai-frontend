package ai.dvine.render.pod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import ai.dvine.render.GlApi;

/**
 * Draws the stage on the GL thread, back to front: dark space (faint nebulae, distant galaxies), the star field
 * and now and then a shooting star, the floating pod, and the light its emitter casts upward, where drawings
 * appear. The pod is how DVINE shows it is listening (its rim pulses), thinking (light runs round its rings and
 * rises faster) and speaking (the emitter beats with the words). No face. Everything shown is decided elsewhere
 * ({@link PodMotion}, {@link PodLayout}); this only draws what {@link #submit} handed over. Counts and effects drop
 * on a slow phone ({@link #quality}).
 */
public final class PodRenderer {
    private final GlApi gl;
    private final Object lock = new Object();
    private final Space sky = new Space();
    private PodLayout layout = PodLayout.compute(1080, 1920, 0, 0);
    private final float[] motion = new float[16];
    private final float[] shown = new float[16];
    private boolean drawingShown;
    /** 2 high, 1 medium, 0 low; lowered when frames run long, raised again when there is room. */
    public volatile int quality = 2;
    private float px = 2.5f;

    private int skyProg, starProg, meteorProg, podProg, glowProg, beamProg, dotProg;
    private int quadVbo, starVbo, dustVbo, meteorVbo, podVbo, podIbo, podCount, beamVbo, dotVbo;
    private boolean created;
    public int glObjects;
    private float time;
    private long lastNanos;
    private float slowFor, fastFor, sinceChange;

    public PodRenderer(GlApi gl) {
        this.gl = gl;
    }

    /** This frame's layout and pod state (copied). {@code drawing}: something is on show above the pod. */
    public void submit(PodLayout l, PodMotion m, boolean drawing, float density) {
        synchronized (lock) {
            layout = l;
            float[] a = motion;
            a[0] = m.listen;
            a[1] = m.think;
            a[2] = m.voice;
            a[3] = m.dim;
            a[4] = m.hover;
            a[5] = m.podTurn;
            a[6] = m.podPulse;
            a[7] = m.flash;
            a[8] = m.tint[0];
            a[9] = m.tint[1];
            a[10] = m.tint[2];
            drawingShown = drawing;
            px = Math.max(1f, density);
        }
    }

    // ------------------------------------------------------------ GL thread

    public void onCreated() {
        created = false;
        glObjects = 0;
        skyProg = prog(SKY_VS, SKY_FS);
        starProg = prog(STAR_VS, STAR_FS);
        meteorProg = prog(METEOR_VS, METEOR_FS);
        podProg = prog(POD_VS, POD_FS);
        glowProg = prog(GLOW_VS, GLOW_FS);
        beamProg = prog(BEAM_VS, BEAM_FS);
        dotProg = prog(DOT_VS, DOT_FS);
        quadVbo = buf(new float[]{-1f, -1f, 3f, -1f, -1f, 3f});
        starVbo = buf(sky.stars);
        dustVbo = buf(sky.dust);
        meteorVbo = buf(new float[16]);
        PodMesh.Mesh pod = PodMesh.build(160);
        podVbo = buf(pod.vertices);
        podIbo = ibuf(pod.indices);
        podCount = pod.indices.length;
        beamVbo = buf(new float[20]);
        java.util.Random r = new java.util.Random(5);
        float[] dots = new float[900 * 4];
        for (int i = 0; i < dots.length; i++) {
            dots[i] = r.nextFloat();
        }
        dotVbo = buf(dots);
        created = true;
    }

    public void onDraw(long nanos, int width, int height) {
        if (!created) {
            return;
        }
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        time += dt;
        adapt(dt);
        PodLayout l;
        boolean drawing;
        float density;
        synchronized (lock) {
            System.arraycopy(motion, 0, shown, 0, shown.length);
            l = layout;
            drawing = drawingShown;
            density = px;
        }
        sky.step(time);
        float[] m = shown;
        float dim = m[3];
        gl.viewport(width, height);
        gl.clear(0f, 0f, 0f);
        gl.disable(GlApi.DEPTH_TEST);
        gl.depthMask(false);
        gl.disable(GlApi.CULL_FACE);
        gl.disable(GlApi.BLEND);
        float[] centre = ndc(l, 0f, 0f, 0f);
        // 1. dark space
        gl.useProgram(skyProg);
        gl.uniform2f(gl.uniform(skyProg, "uRes"), width, height);
        gl.uniform1f(gl.uniform(skyProg, "uTime"), time);
        gl.uniform1i(gl.uniform(skyProg, "uOct"), quality == 0 ? 2 : quality == 1 ? 3 : 4);
        gl.uniform4f(gl.uniform(skyProg, "uHead"), centre[0], centre[1], 0.55f * l.unit / (height * 0.5f), dim);
        attrib(quadVbo, 0, 2, 8, 0);
        gl.drawArrays(GlApi.TRIANGLES, 0, 3);
        off(1);
        // 2. stars and dust (additive), a shooting star now and then
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.SRC_ALPHA, GlApi.ONE);
        gl.useProgram(starProg);
        gl.uniform1f(gl.uniform(starProg, "uTime"), time);
        gl.uniform1f(gl.uniform(starProg, "uPx"), density * 0.75f);
        gl.uniform1f(gl.uniform(starProg, "uDim"), 1f - 0.4f * dim);
        gl.uniform2f(gl.uniform(starProg, "uDrift"), 0.02f * (float) Math.sin(time * 0.013f), 0.015f * (float) Math.cos(time * 0.011f));
        gl.uniform1f(gl.uniform(starProg, "uDust"), 0f);
        attrib(starVbo, 0, 4, 32, 0);
        attrib(starVbo, 1, 4, 32, 16);
        gl.drawArrays(GlApi.POINTS, 0, quality == 2 ? Space.STARS : quality == 1 ? 750 : 400);
        gl.uniform1f(gl.uniform(starProg, "uDust"), 1f);
        attrib(dustVbo, 0, 4, 32, 0);
        attrib(dustVbo, 1, 4, 32, 16);
        gl.drawArrays(GlApi.POINTS, 0, quality == 2 ? Space.DUST : quality == 1 ? 90 : 50);
        off(2);
        if (sky.meteorLife >= 0f) {
            drawMeteor(width, height);
        }
        // 3. the glow under the pod
        float[] podModel = podModel(l, m);
        float[] podC = ndc(l, 0f, l.podY + 0.012f * l.podDiameter * m[4], -0.25f);
        float podHalf = l.podDiameter * 0.5f * l.unit / (width * 0.5f);
        glow(podC[0], podC[1] - 0.06f * l.unit / (height * 0.5f), podHalf * 1.25f, podHalf * 0.32f * width / height,
                m[8] * 0.4f, m[9] * 0.6f, m[10], 0.22f * (1f - 0.5f * dim), 2.2f);
        // 4. the pod (solid, depth written)
        gl.disable(GlApi.BLEND);
        gl.enable(GlApi.DEPTH_TEST);
        gl.depthFunc(GlApi.LEQUAL);
        gl.depthMask(true);
        gl.useProgram(podProg);
        gl.uniformMatrix4(gl.uniform(podProg, "uProj"), l.proj);
        gl.uniformMatrix4(gl.uniform(podProg, "uModel"), podModel);
        gl.uniform1f(gl.uniform(podProg, "uTime"), time);
        gl.uniform4f(gl.uniform(podProg, "uState"), Math.min(1f, m[6] * 0.6f + m[2] * 0.8f + m[7]), m[1], m[0], dim);
        gl.uniform3fv(gl.uniform(podProg, "uTint"), new float[]{m[8], m[9], m[10]});
        attrib(podVbo, 0, 3, 32, 0);
        attrib(podVbo, 1, 3, 32, 12);
        attrib(podVbo, 2, 2, 32, 24);
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, podIbo);
        gl.drawElements(GlApi.TRIANGLES, podCount, 0);
        off(3);
        // 5. the emitter's flare and the light rising from it (up to the drawing when one is shown)
        float[] emit = mul(podModel, 0f, 0.02f, 0f);
        float[] emitNdc = ndc(l, emit[0], emit[1], emit[2]);
        gl.enable(GlApi.BLEND);
        gl.blendFunc(GlApi.SRC_ALPHA, GlApi.ONE);
        gl.depthMask(false);
        gl.disable(GlApi.DEPTH_TEST);
        float pulse = 0.7f + 0.2f * m[6] + 0.35f * m[1] + 0.6f * m[2] + 0.5f * m[7];
        glow(emitNdc[0], emitNdc[1], podHalf * 0.22f, podHalf * 0.07f * width / height, m[8], m[9], m[10],
                Math.min(1.4f, 0.9f * pulse) * (1f - 0.5f * dim), 3f);
        float[] top = {0f, drawing ? -0.2f : emit[1] + 0.55f, -0.1f};
        drawBeam(l, emit, top, m, dim, drawing);
        drawDots(l, emit, top, m, dim, density, drawing);
        gl.disable(GlApi.BLEND);
        gl.depthMask(true);
    }

    private void drawBeam(PodLayout l, float[] bottom, float[] top, float[] m, float dim, boolean drawing) {
        float wb = 0.07f * l.podDiameter, wt = drawing ? 0.45f : 0.16f;
        float[] v = {bottom[0] - wb, bottom[1], bottom[2], -1f, 0f,
                bottom[0] + wb, bottom[1], bottom[2], 1f, 0f,
                top[0] - wt, top[1], top[2], -1f, 1f,
                top[0] + wt, top[1], top[2], 1f, 1f};
        gl.bindBuffer(GlApi.ARRAY_BUFFER, beamVbo);
        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, v.length * 4, floats(v));
        gl.useProgram(beamProg);
        gl.uniformMatrix4(gl.uniform(beamProg, "uProj"), l.proj);
        gl.uniform1f(gl.uniform(beamProg, "uTime"), time);
        gl.uniform2f(gl.uniform(beamProg, "uLevel"), (0.45f + 0.2f * m[6] + 0.5f * m[1] + 0.3f * m[0] + 0.4f * m[2])
                * (drawing ? 0.6f : 1f) * (1f - 0.55f * dim), 0f);
        gl.uniform3fv(gl.uniform(beamProg, "uTint"), new float[]{m[8], m[9], m[10]});
        attrib(beamVbo, 0, 3, 20, 0);
        attrib(beamVbo, 1, 2, 20, 12);
        gl.drawArrays(GlApi.TRIANGLES, 0, 3);
        gl.drawArrays(GlApi.TRIANGLES, 1, 3);
        off(2);
    }

    private void drawDots(PodLayout l, float[] bottom, float[] top, float[] m, float dim, float density, boolean drawing) {
        gl.useProgram(dotProg);
        gl.uniformMatrix4(gl.uniform(dotProg, "uProj"), l.proj);
        gl.uniform1f(gl.uniform(dotProg, "uTime"), time);
        gl.uniform3fv(gl.uniform(dotProg, "uEnds"), new float[]{bottom[0], bottom[1], bottom[2], top[0], top[1], top[2]});
        gl.uniform4f(gl.uniform(dotProg, "uState"), 0f, 0.07f * l.podDiameter, (1f + 1.5f * m[1] + 0.8f * m[2]) * 0.22f,
                (1f - 0.5f * dim) * (drawing ? 0.5f : 1f));
        gl.uniform1f(gl.uniform(dotProg, "uPx"), density);
        gl.uniform3fv(gl.uniform(dotProg, "uTint"), new float[]{m[8], m[9], m[10]});
        attrib(dotVbo, 0, 4, 16, 0);
        gl.drawArrays(GlApi.POINTS, 0, quality == 2 ? 900 : quality == 1 ? 550 : 280);
        off(1);
    }

    private void drawMeteor(int width, int height) {
        float life = sky.meteorLife;
        float[] mt = sky.meteor;
        float aspect = width / (float) height;
        float travel = mt[4] * 1.8f * life;
        float hx = mt[0] + mt[2] * travel, hy = mt[1] + mt[3] * travel * aspect;
        float tail = mt[4] * Math.min(1f, life * 3f) * (1f - 0.4f * life);
        float tx = hx - mt[2] * tail, ty = hy - mt[3] * tail * aspect;
        float nx = -mt[3], ny = mt[2] * aspect;
        float nl = (float) Math.sqrt(nx * nx + ny * ny);
        nx = nx / nl * mt[5];
        ny = ny / nl * mt[5] * aspect;
        float[] v = {tx - nx * 0.3f, ty - ny * 0.3f, 0f, -1f,
                tx + nx * 0.3f, ty + ny * 0.3f, 0f, 1f,
                hx - nx, hy - ny, 1f, -1f,
                hx + nx, hy + ny, 1f, 1f};
        gl.bindBuffer(GlApi.ARRAY_BUFFER, meteorVbo);
        gl.bufferSubData(GlApi.ARRAY_BUFFER, 0, v.length * 4, floats(v));
        gl.useProgram(meteorProg);
        float fade = (float) Math.sin(Math.PI * Math.min(1f, life));
        gl.uniform4f(gl.uniform(meteorProg, "uColor"), mt[6] == 1f ? 0.7f : mt[6] == 2f ? 0.8f : 1f,
                mt[6] == 2f ? 0.65f : 0.95f, 1f, fade);
        attrib(meteorVbo, 0, 4, 16, 0);
        gl.drawArrays(GlApi.TRIANGLES, 0, 3);
        gl.drawArrays(GlApi.TRIANGLES, 1, 3);
        off(1);
    }

    private void glow(float x, float y, float rx, float ry, float r, float g, float b, float a, float sharp) {
        gl.useProgram(glowProg);
        gl.uniform4f(gl.uniform(glowProg, "uRect"), x, y, rx, ry);
        gl.uniform4f(gl.uniform(glowProg, "uColor"), r, g, b, a);
        gl.uniform1f(gl.uniform(glowProg, "uSharp"), sharp);
        attrib(quadVbo, 0, 2, 8, 0);
        gl.drawArrays(GlApi.TRIANGLES, 0, 3);
        off(1);
    }

    /** Frame time watch: below 45 fps for 2 s steps the quality down; above 75 fps for 6 s steps it back up. */
    private void adapt(float dt) {
        if (dt <= 0f) {
            return;
        }
        sinceChange += dt;
        if (dt > 1f / 45f) {
            slowFor += dt;
            fastFor = 0f;
        } else if (dt < 1f / 75f) {
            fastFor += dt;
            slowFor = Math.max(0f, slowFor - dt);
        } else {
            slowFor = Math.max(0f, slowFor - dt * 0.5f);
            fastFor = 0f;
        }
        if (slowFor > 2f && quality > 0 && sinceChange > 4f) {
            quality--;
            slowFor = 0f;
            sinceChange = 0f;
        } else if (fastFor > 6f && quality < 2 && sinceChange > 4f) {
            quality++;
            fastFor = 0f;
            sinceChange = 0f;
        }
    }

    // ------------------------------------------------------------ matrices

    /** The pod: low in the scene, tipped toward the viewer, hovering and turning slowly. */
    public static float[] podModel(PodLayout l, float[] m) {
        float d = l.podDiameter;
        float y = l.podY + 0.012f * d * m[4];
        float[] tip = compose(new float[]{0f, y, -0.25f}, 0f, -15f, 0f, d);
        float[] turn = compose(new float[]{0f, 0f, 0f}, m[5], 0f, 0f, 1f);
        return multiply(tip, turn);
    }

    /** T · Ry(yaw) · Rx(-pitch) · Rz(roll) · S, column-major. */
    public static float[] compose(float[] t, float yawDeg, float pitchDeg, float rollDeg, float s) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(-pitchDeg), r = Math.toRadians(rollDeg);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y), cp = (float) Math.cos(p), sp = (float) Math.sin(p);
        float cr = (float) Math.cos(r), sr = (float) Math.sin(r);
        float[] ry = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
        float[] rx = {1, 0, 0, 0, 0, cp, sp, 0, 0, -sp, cp, 0, 0, 0, 0, 1};
        float[] rz = {cr, sr, 0, 0, -sr, cr, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        float[] m = multiply(ry, multiply(rx, rz));
        for (int i = 0; i < 12; i++) {
            m[i] *= s;
        }
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        return m;
    }

    static float[] multiply(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + r] * b[c * 4 + k];
                }
                o[c * 4 + r] = s;
            }
        }
        return o;
    }

    static float[] mul(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    static float[] ndc(PodLayout l, float x, float y, float z) {
        float[] p = l.project(x, y, z);
        return new float[]{p[0] / l.width * 2f - 1f, 1f - p[1] / l.height * 2f};
    }

    // ------------------------------------------------------------ GL helpers

    private void attrib(int vbo, int index, int size, int stride, int offset) {
        gl.bindBuffer(GlApi.ARRAY_BUFFER, vbo);
        gl.vertexAttrib(index, size, stride, offset);
    }

    private void off(int n) {
        for (int i = 0; i < n; i++) {
            gl.disableAttrib(i);
        }
    }

    private int prog(String vs, String fs) {
        int p = gl.program(vs, fs);
        glObjects++;
        return p;
    }

    private int buf(float[] data) {
        int b = gl.buffer();
        glObjects++;
        gl.bindBuffer(GlApi.ARRAY_BUFFER, b);
        gl.bufferData(GlApi.ARRAY_BUFFER, data.length * 4, floats(data), GlApi.STATIC_DRAW);
        return b;
    }

    private int ibuf(int[] data) {
        int b = gl.buffer();
        glObjects++;
        gl.bindBuffer(GlApi.ELEMENT_ARRAY_BUFFER, b);
        IntBuffer ib = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        ib.put(data).flip();
        gl.bufferData(GlApi.ELEMENT_ARRAY_BUFFER, data.length * 4, ib, GlApi.STATIC_DRAW);
        return b;
    }

    private static FloatBuffer floats(float[] a) {
        FloatBuffer b = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(a).flip();
        return b;
    }

    /** Frees everything (view detached). */
    public void dispose() {
        if (!created) {
            return;
        }
        for (int p : new int[]{skyProg, starProg, meteorProg, podProg, glowProg, beamProg, dotProg}) {
            gl.deleteProgram(p);
        }
        for (int b : new int[]{quadVbo, starVbo, dustVbo, meteorVbo, podVbo, podIbo, beamVbo, dotVbo}) {
            gl.deleteBuffer(b);
        }
        created = false;
        glObjects = 0;
    }

    // ------------------------------------------------------------ shaders

    static final String NOISE = ""
            + "float hash(vec3 p){p=fract(p*0.3183099+0.1);p*=17.0;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}\n"
            + "float noise(vec3 x){vec3 i=floor(x);vec3 f=fract(x);f=f*f*(3.0-2.0*f);"
            + "return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),"
            + "mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}\n";

    static final String SKY_VS = "#version 300 es\n"
            + "layout(location=0) in vec2 aPos;\n"
            + "void main(){gl_Position=vec4(aPos,0.0,1.0);}\n";

    static final String SKY_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "uniform vec2 uRes; uniform float uTime; uniform int uOct; uniform vec4 uHead;\n"
            + "out vec4 o;\n"
            + NOISE
            + "float fbm(vec3 p){float a=0.5;float s=0.0;for(int i=0;i<4;i++){if(i>=uOct)break;s+=a*noise(p);p*=2.03;a*=0.5;}return s;}\n"
            + "vec3 galaxy(vec2 q, vec2 c, float size, float tilt, vec3 tint){\n"
            + "  vec2 d=q-c; float ca=cos(tilt), sa=sin(tilt); d=vec2(ca*d.x-sa*d.y, (sa*d.x+ca*d.y)*2.4)/size;\n"
            + "  float r=length(d); float a=atan(d.y,d.x);\n"
            + "  float arms=0.5+0.5*sin(2.0*a+5.0*log(r+0.05));\n"
            + "  float body=exp(-r*r*2.2)*(0.35+0.65*arms)+exp(-r*r*30.0)*1.5;\n"
            + "  return tint*body;\n"
            + "}\n"
            + "void main(){\n"
            + "  vec2 p=gl_FragCoord.xy/uRes*2.0-1.0; float asp=uRes.x/uRes.y; vec2 q=vec2(p.x*asp,p.y);\n"
            + "  vec3 col=mix(vec3(0.003,0.004,0.014),vec3(0.008,0.012,0.04),0.5+0.35*p.y);\n"
            + "  float t=uTime/70.0;\n"
            + "  float n1=fbm(vec3(q*1.25+vec2(t*0.5,-t*0.25),t*0.7));\n"
            + "  float n2=fbm(vec3(q*2.2-vec2(t*0.35,0.0)+5.0,t*0.9+3.0));\n"
            + "  float m1=smoothstep(0.38,0.85,n1)*smoothstep(1.7,0.1,length(q-vec2(-0.5*asp-0.1,0.62)));\n"
            + "  float m2=smoothstep(0.42,0.9,n2)*smoothstep(1.5,0.1,length(q-vec2(0.55*asp+0.1,-0.42)));\n"
            + "  float m3=smoothstep(0.5,0.95,n2)*smoothstep(1.2,0.1,length(q-vec2(0.4*asp,0.75)));\n"
            + "  col+=vec3(0.26,0.10,0.42)*m1*0.55+vec3(0.06,0.16,0.42)*m2*0.5+vec3(0.12,0.08,0.3)*m3*0.35;\n"
            + "  col+=vec3(0.04,0.03,0.10)*smoothstep(0.3,0.9,n1)*0.5;\n"
            + "  col+=galaxy(q,vec2(0.62*asp,0.8),0.05,0.5,vec3(0.32,0.30,0.45))*0.35;\n"
            + "  col+=galaxy(q,vec2(-0.78*asp,-0.05),0.035,-0.3,vec3(0.4,0.32,0.5))*0.3;\n"
            + "  col+=galaxy(q,vec2(-0.45*asp,-0.82),0.04,1.1,vec3(0.28,0.34,0.55))*0.28;\n"
            + "  vec2 hq=vec2((p.x-uHead.x)*asp,p.y-uHead.y)/max(uHead.z,0.01);\n"
            + "  col+=vec3(0.22,0.36,0.75)*exp(-dot(hq,hq)*0.9)*0.10;\n"
            + "  col*=1.0-0.45*uHead.w;\n"
            + "  o=vec4(col,1.0);\n"
            + "}\n";

    static final String STAR_VS = "#version 300 es\n"
            + "layout(location=0) in vec4 aA; layout(location=1) in vec4 aB;\n"
            + "uniform float uTime, uPx, uDust, uDim; uniform vec2 uDrift;\n"
            + "out float vB; out vec3 vC; out float vSoft;\n"
            + "void main(){\n"
            + "  vec2 p=aA.xy; float b=aA.w;\n"
            + "  if(uDust>0.5){ p=p+aB.xy*uTime; p=mod(p+1.0,2.0)-1.0; b*=0.6+0.4*sin(uTime*0.5+aB.z); vC=vec3(0.7,0.85,1.0); vSoft=1.0; }\n"
            + "  else { p+=uDrift*aB.w; if(aB.x>0.0){ float tw=0.5+0.5*sin(uTime*aB.x+aB.y); b*=1.0-0.6*tw*tw; }\n"
            + "    vC=mix(vec3(0.72,0.82,1.0),vec3(1.0,0.93,0.86),aB.z); vSoft=0.0; }\n"
            + "  gl_Position=vec4(p,0.0,1.0); gl_PointSize=aA.z*uPx*(uDust>0.5?1.6:1.0); vB=b*uDim;\n"
            + "}\n";

    static final String STAR_FS = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float vB; in vec3 vC; in float vSoft; out vec4 o;\n"
            + "void main(){ float d=length(gl_PointCoord-0.5)*2.0; float a=vSoft>0.5?smoothstep(1.0,0.0,d)*0.45:pow(max(0.0,1.0-d),2.2);\n"
            + "  o=vec4(vC,a*vB); }\n";

    static final String METEOR_VS = "#version 300 es\n"
            + "layout(location=0) in vec4 aP; out vec2 vT;\n"
            + "void main(){ vT=aP.zw; gl_Position=vec4(aP.xy,0.0,1.0); }\n";

    static final String METEOR_FS = "#version 300 es\n"
            + "precision mediump float; uniform vec4 uColor; in vec2 vT; out vec4 o;\n"
            + "void main(){ float a=pow(vT.x,2.2)*(1.0-vT.y*vT.y); a+=smoothstep(0.85,1.0,vT.x)*(1.0-abs(vT.y))*0.8;\n"
            + "  o=vec4(uColor.rgb,a*uColor.a); }\n";

    static final String GLOW_VS = "#version 300 es\n"
            + "layout(location=0) in vec2 aPos; uniform vec4 uRect; out vec2 vUv;\n"
            + "void main(){ vec2 c=aPos; vUv=vec2(c.x,c.y); gl_Position=vec4(uRect.xy+c*uRect.zw*1.8,0.0,1.0); }\n";

    static final String GLOW_FS = "#version 300 es\n"
            + "precision mediump float; uniform vec4 uColor; uniform float uSharp; in vec2 vUv; out vec4 o;\n"
            + "void main(){ float r=length(vUv*1.8); o=vec4(uColor.rgb,exp(-r*r*uSharp)*uColor.a); }\n";

    static final String POD_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor; layout(location=2) in vec2 aAux;\n"
            + "uniform mat4 uProj, uModel;\n"
            + "out vec3 vLocal; out vec3 vWorld; out vec3 vN; out vec2 vAux;\n"
            + "void main(){ vec4 w=uModel*vec4(aPos,1.0); vLocal=aPos; vWorld=w.xyz; vN=mat3(uModel)*aNor; vAux=aAux; gl_Position=uProj*w; }\n";

    static final String POD_FS = "#version 300 es\n"
            + "precision highp float;\n"
            + "uniform float uTime; uniform vec4 uState; uniform vec3 uTint;\n"
            + "in vec3 vLocal; in vec3 vWorld; in vec3 vN; in vec2 vAux; out vec4 o;\n"
            + NOISE
            + "float ringAt(float r,float r0,float w){ return 1.0-smoothstep(0.0,w,abs(r-r0)); }\n"
            + "void main(){\n"
            + "  vec3 n=normalize(vN); vec3 v=normalize(vec3(0.0,0.0,6.0)-vWorld);\n"
            + "  vec3 L=normalize(vec3(-0.3,0.9,0.5)); vec3 hv=normalize(L+v);\n"
            + "  float spec=pow(max(dot(n,hv),0.0),48.0); float dif=max(dot(n,L),0.0);\n"
            + "  float r=vAux.x; int part=int(vAux.y+0.5);\n"
            + "  float ang=atan(vLocal.z,vLocal.x); float fw=max(fwidth(r),0.0008);\n"
            + "  vec3 base=vec3(0.045,0.05,0.06);\n"
            + "  vec3 col=base*(0.35+0.9*dif)+vec3(0.55,0.68,0.85)*spec*0.55+vec3(0.03,0.045,0.07)*(0.5+0.5*n.y);\n"
            + "  vec3 cyan=uTint; float pulse=uState.x; float think=uState.y; float listen=uState.z;\n"
            + "  float brushed=0.85+0.15*noise(vec3(r*900.0,ang*2.0,0.0));\n"
            + "  if(part==0){\n"
            + "    float core=exp(-r*r*1400.0)*3.0+exp(-r*r*180.0)*0.9;\n"
            + "    float wav=0.5+0.5*sin(r*260.0-uTime*3.0);\n"
            + "    col=col*0.6+cyan*(core*(0.8+0.4*pulse+0.6*think)+wav*0.18*smoothstep(0.125,0.02,r));\n"
            + "  } else if(part==1){\n"
            + "    col=col*brushed+vec3(0.06,0.08,0.11)*smoothstep(0.5,0.1,r)+vec3(0.10,0.13,0.17)*pow(max(dot(reflect(-v,n),vec3(0.0,0.6,0.8)),0.0),6.0);\n"
            + "    float g=0.0;\n"
            + "    g+=ringAt(r,0.15,fw*1.5)+ringAt(r,0.19,fw*1.5)+ringAt(r,0.235,fw*1.5)+ringAt(r,0.345,fw*1.5)+ringAt(r,0.37,fw*1.5)+ringAt(r,0.455,fw*1.5);\n"
            + "    col*=1.0-0.55*clamp(g,0.0,1.0);\n"
            + "    float lit=ringAt(r,0.132,fw*2.0)*1.4+ringAt(r,0.29,fw*1.6)*0.45+ringAt(r,0.478,fw*2.0)*1.1+ringAt(r,0.21,fw*1.4)*0.3;\n"
            + "    float a=fract(ang/6.2832*4.0-uTime*0.05);\n"
            + "    float trace=smoothstep(0.0,0.015,a)*smoothstep(0.12,0.06,a)*ringAt(r,0.29,fw*2.5)*2.2;\n"
            + "    float a2=fract(-ang/6.2832*3.0-uTime*0.035);\n"
            + "    trace+=smoothstep(0.0,0.02,a2)*smoothstep(0.09,0.05,a2)*ringAt(r,0.21,fw*2.0)*1.6;\n"
            + "    float tick=step(0.62,fract(ang/6.2832*96.0))*smoothstep(0.392,0.398,r)*smoothstep(0.418,0.412,r);\n"
            + "    float glyph=step(0.86,hash(vec3(floor(ang/6.2832*64.0),3.0,1.0)))*smoothstep(0.43,0.434,r)*smoothstep(0.446,0.442,r)*step(0.4,fract(ang/6.2832*64.0*3.0));\n"
            + "    float inner=smoothstep(0.30,0.13,r)*0.12*(0.6+0.4*pulse);\n"
            + "    col+=cyan*(lit*(0.7+0.3*pulse)+trace+tick*0.35+glyph*0.3+inner+think*0.15*ringAt(r,0.29,fw*4.0));\n"
            + "  } else if(part==2){\n"
            + "    col+=cyan*1.5*(0.85+0.15*pulse+0.6*listen*(0.5+0.5*sin(uTime*5.0)));\n"
            + "  } else if(part==3){\n"
            + "    float y=vLocal.y;\n"
            + "    col*=0.9+0.2*noise(vec3(ang*80.0,y*4.0,0.0));\n"
            + "    col+=cyan*(1.0-smoothstep(0.0,0.0025,abs(y+0.03)))*1.1;\n"
            + "    col+=cyan*step(0.97,fract(ang/6.2832*24.0))*(1.0-smoothstep(0.0,0.012,abs(y+0.045)))*0.25;\n"
            + "  } else if(part==5){\n"
            + "    col*=0.7; col+=cyan*(1.0-smoothstep(0.0,0.002,abs(vLocal.y+0.112)))*0.7;\n"
            + "  } else { col*=0.5; }\n"
            + "  col*=1.0-0.45*uState.w;\n"
            + "  o=vec4(col,1.0);\n"
            + "}\n";

    static final String BEAM_VS = "#version 300 es\n"
            + "layout(location=0) in vec3 aPos; layout(location=1) in vec2 aUv; uniform mat4 uProj; out vec2 vUv;\n"
            + "void main(){ vUv=aUv; gl_Position=uProj*vec4(aPos,1.0); }\n";

    static final String BEAM_FS = "#version 300 es\n"
            + "precision mediump float; uniform float uTime; uniform vec2 uLevel; uniform vec3 uTint; in vec2 vUv; out vec4 o;\n"
            + "float h1(float x){ return fract(sin(x*127.1)*43758.5453); }\n"
            + "void main(){ float u=abs(vUv.x); float v=vUv.y;\n"
            + "  float core=pow(1.0-u,2.5)*(0.25+0.75*pow(1.0-v,0.6));\n"
            + "  float lane=floor(vUv.x*22.0); float streak=step(0.55,h1(lane))*(0.5+0.5*sin(v*40.0-uTime*(3.0+h1(lane+3.0)*4.0)));\n"
            + "  float a=(core*0.38+streak*0.12*(1.0-u))*smoothstep(1.0,0.7,v)*uLevel.x;\n"
            + "  o=vec4(mix(uTint,vec3(1.0),0.25),a); }\n";

    static final String DOT_VS = "#version 300 es\n"
            + "layout(location=0) in vec4 aS;\n"
            + "uniform mat4 uProj; uniform float uTime, uPx; uniform vec3 uEnds[2]; uniform vec4 uState; uniform vec3 uTint;\n"
            + "out float vA; out vec3 vC;\n"
            + "void main(){\n"
            + "  float t=fract(aS.x+uTime*uState.z*(0.6+0.8*aS.y));\n"
            + "  float rad=mix(uState.y,0.2,t)*sqrt(aS.z); float an=aS.w*6.2832+uTime*0.25;\n"
            + "  vec3 p=mix(uEnds[0],uEnds[1],t)+vec3(cos(an)*rad,0.0,sin(an)*rad*0.5);\n"
            + "  vA=sin(3.1416*t)*(0.35+0.65*aS.z)*uState.w;\n"
            + "  vC=mix(mix(uTint,vec3(1.0),0.2),vec3(0.8,0.7,1.0),step(0.85,aS.y));\n"
            + "  gl_PointSize=uPx*(1.0+1.6*step(0.9,aS.w));\n"
            + "  gl_Position=uProj*vec4(p,1.0);\n"
            + "}\n";

    static final String DOT_FS = "#version 300 es\n"
            + "precision mediump float; in float vA; in vec3 vC; out vec4 o;\n"
            + "void main(){ float d=length(gl_PointCoord-0.5)*2.0; o=vec4(vC,pow(max(0.0,1.0-d),1.8)*vA); }\n";
}
