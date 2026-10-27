package ai.dvine.render;

import java.io.*;
import java.nio.*;
import java.util.*;
import org.junit.Test;
import ai.dvine.face.FaceRig;
import ai.dvine.human.HumanAsset;

/**
 * Records the real renderer's GL calls for a few seconds of the face close-up (or the body view), so
 * tools/gl_replay can replay them in Chromium's WebGL2 and screenshot what a phone would show. The JVM
 * tests' fake GL draws nothing. Runs only when GLCAP_OUT is set:
 * GLCAP_OUT=dir [GLCAP_WHO=obi|ivie GLCAP_E=0..1 GLCAP_YAW=deg GLCAP_FRAMES=n] gradle testDebugUnitTest --tests ai.dvine.render.GlCapture --rerun
 */
public class GlCapture {
    static final class Rec implements GlApi {
        final StringBuilder cmds = new StringBuilder("[");
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int next = 1;
        final Map<String, Integer> locs = new HashMap<>();
        boolean first = true;
        void c(String s) { if (!first) cmds.append(",\n"); first = false; cmds.append(s); }
        static String q(String s) { StringBuilder b = new StringBuilder("\""); for (char ch : s.toCharArray()) { if (ch == '"' || ch == '\\') b.append('\\').append(ch); else if (ch == '\n') b.append("\\n"); else b.append(ch);} return b.append('"').toString(); }
        long blob(Buffer buf, int bytes) {
            long off = data.size();
            if (buf == null) return -1;
            byte[] out = new byte[bytes];
            ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
            if (buf instanceof FloatBuffer) { FloatBuffer f = ((FloatBuffer) buf).duplicate(); f.limit(f.position() + bytes / 4); bb.asFloatBuffer().put(f); }
            else if (buf instanceof IntBuffer) { IntBuffer f = ((IntBuffer) buf).duplicate(); f.limit(f.position() + bytes / 4); bb.asIntBuffer().put(f); }
            else if (buf instanceof ShortBuffer) { ShortBuffer f = ((ShortBuffer) buf).duplicate(); f.limit(f.position() + bytes / 2); bb.asShortBuffer().put(f); }
            else { ByteBuffer f = ((ByteBuffer) buf).duplicate(); f.limit(f.position() + bytes); bb.put(f); }
            data.write(out, 0, out.length);
            return off;
        }
        static String fa(float[] v) { StringBuilder b = new StringBuilder("["); for (int i = 0; i < v.length; i++) { if (i > 0) b.append(','); b.append(Float.isFinite(v[i]) ? Float.toString(v[i]) : "0"); } return b.append(']').toString(); }
        public int program(String v, String f) { int id = next++; c("[\"program\"," + id + "," + q(v) + "," + q(f) + "]"); return id; }
        public void deleteProgram(int p) { c("[\"delProgram\"," + p + "]"); }
        public void useProgram(int p) { c("[\"use\"," + p + "]"); }
        public int uniform(int p, String name) { String k = p + ":" + name; Integer id = locs.get(k); if (id == null) { id = next++; locs.put(k, id); c("[\"loc\"," + id + "," + p + "," + q(name) + "]"); } return id; }
        public void uniform1f(int l, float v) { c("[\"u1f\"," + l + "," + fa(new float[]{v}) + "]"); }
        public void uniform1i(int l, int v) { c("[\"u1i\"," + l + "," + v + "]"); }
        public void uniform2f(int l, float a, float b) { c("[\"u2f\"," + l + "," + fa(new float[]{a, b}) + "]"); }
        public void uniform4f(int l, float a, float b, float cc, float d) { c("[\"u4f\"," + l + "," + fa(new float[]{a, b, cc, d}) + "]"); }
        public void uniform3fv(int l, float[] v) { c("[\"u3fv\"," + l + "," + fa(v) + "]"); }
        public void uniform1fv(int l, float[] v) { c("[\"u1fv\"," + l + "," + fa(v) + "]"); }
        public void uniformMatrix4(int l, float[] m) { c("[\"um4\"," + l + "," + fa(m) + "]"); }
        public void uniformMatrix3(int l, float[] m) { c("[\"um3\"," + l + "," + fa(m) + "]"); }
        public int buffer() { int id = next++; c("[\"buffer\"," + id + "]"); return id; }
        public void deleteBuffer(int b) { c("[\"delBuffer\"," + b + "]"); }
        public void bindBuffer(int t, int b) { c("[\"bind\"," + t + "," + b + "]"); }
        public void bufferData(int t, int bytes, Buffer d, int u) { c("[\"bufData\"," + t + "," + bytes + "," + blob(d, bytes) + "," + u + "]"); }
        public void bufferSubData(int t, int o, int bytes, Buffer d) { c("[\"bufSub\"," + t + "," + o + "," + bytes + "," + blob(d, bytes) + "]"); }
        public void vertexAttrib(int i, int s, int st, int o) { c("[\"attrib\"," + i + "," + s + "," + st + "," + o + "]"); }
        public void disableAttrib(int i) { c("[\"noAttrib\"," + i + "]"); }
        public int floatTexture(int w, int h) { int id = next++; c("[\"tex\"," + id + "," + w + "," + h + "]"); return id; }
        public void updateFloatTexture(int t, int w, int h, Buffer d) { c("[\"texData\"," + t + "," + w + "," + h + "," + blob(d, w * h * 12) + "]"); }
        public void deleteTexture(int t) { c("[\"delTex\"," + t + "]"); }
        public void bindTexture(int u, int t) { c("[\"bindTex\"," + u + "," + t + "]"); }
        public void enable(int cap) { c("[\"enable\"," + cap + "]"); }
        public void disable(int cap) { c("[\"disable\"," + cap + "]"); }
        public void depthMask(boolean on) { c("[\"depthMask\"," + on + "]"); }
        public void depthFunc(int f) { c("[\"depthFunc\"," + f + "]"); }
        public void blendFunc(int s, int d) { c("[\"blend\"," + s + "," + d + "]"); }
        public void polygonOffset(float f, float u) { c("[\"polyOff\"," + f + "," + u + "]"); }
        public void viewport(int w, int h) { c("[\"viewport\"," + w + "," + h + "]"); }
        public void clear(float r, float g, float b) { c("[\"clear\"," + r + "," + g + "," + b + "]"); }
        public void drawArrays(int m, int f, int n) { c("[\"drawArrays\"," + m + "," + f + "," + n + "]"); }
        public void drawElements(int m, int n, int o) { c("[\"drawElements\"," + m + "," + n + "," + o + "]"); }
        public int error() { return 0; }
    }

    @Test
    public void capture() throws Exception {
        String outDir = System.getProperty("glcap.out", System.getenv("GLCAP_OUT"));
        if (outDir == null) return;
        String who = System.getenv().getOrDefault("GLCAP_WHO", "obi");
        float e = Float.parseFloat(System.getenv().getOrDefault("GLCAP_E", "0"));
        float yaw = Float.parseFloat(System.getenv().getOrDefault("GLCAP_YAW", "0"));
        boolean ivie = who.equals("ivie");
        HumanAsset a;
        try (FileInputStream in = new FileInputStream("src/main/assets/human/" + who + ".dvhm")) { a = HumanAsset.read(in); }
        Rec gl = new Rec();
        WireRenderer r = new WireRenderer(gl);
        int W = 1080, H = 2400;
        r.onCreated();
        r.onSize(W, H);
        HumanStage stage = new HumanStage(r);
        stage.install(ivie ? 1 : 0, a, true);
        String arrangement = System.getenv("GLCAP_SCENE");
        if (arrangement != null) {
            HumanAsset other;
            try (FileInputStream in = new FileInputStream("src/main/assets/human/" + (ivie ? "obi" : "ivie") + ".dvhm")) { other = HumanAsset.read(in); }
            stage.install(ivie ? 0 : 1, other, true);
        }
        FaceRig rig = new FaceRig(ivie);
        float padTop = 110, padBottom = 140, usable = H - padTop - padBottom;
        float bigUnit = Math.min(W * 0.30f, usable * 0.17f);
        float faceCy = padTop + usable * 0.40f;
        float FS = ai.dvine.face.nano.DotBody.FACE_SCALE, FY = ai.dvine.face.nano.DotBody.FACE_Y;
        int frames = Integer.parseInt(System.getenv().getOrDefault("GLCAP_FRAMES", "90"));
        for (int k = 0; k < frames; k++) {
            float t = k / 15f;
            rig.tick(1 / 15f);
            Camera c = stage.camera;
            c.width = W; c.height = H; c.cx = W * 0.5f;
            if (arrangement != null && k == 2) stage.arrange(arrangement);
            String gk = System.getenv("GLCAP_GESTURE");
            if (gk != null && k == 30) stage.gesture(ivie, ai.dvine.face.Gestures.find(gk), 10f); // by name: "wave", "thumbs up"
            float u0 = bigUnit / FS, u1 = Math.min(W * 0.42f, usable * 0.36f);
            float midX = 0f;
            if (stage.demo()) {
                float[] span = stage.sceneSpan();
                midX = span[0];
                u1 = Math.min(u1, W * 0.92f / ((2f * span[1] + 2.4f) * ai.dvine.human.HumanInstance.OUT));
            }
            c.unit = (float) Math.exp(lerp((float) Math.log(u0), (float) Math.log(u1), e));
            c.cx = W * 0.5f - midX * ai.dvine.human.HumanInstance.OUT * c.unit * e;
            float hy = stage.headCameraY(ivie); c.cy = lerp(faceCy, padTop + usable * 0.55f, e) + lerp(hy, 0f, e) * c.unit;
            c.distance = (float) Math.exp(lerp((float) Math.log(0.75f), (float) Math.log(4.4f), e));
            c.pivot = lerp(hy, 0f, e);
            c.yawDeg = yaw;
            c.pitchDeg = lerp(rig.pitch.value + 6f, 8f, e);
            c.update();
            r.setCamera(c, -4.22f);
            boolean skin = !"wire".equals(System.getenv("GLCAP_STYLE"));
            for (int sl = 0; sl < 2; sl++) {
                r.setSkin(sl, skin, ai.dvine.face.Look.defaults(sl == 1).skinColor());
            }
            stage.frame(t, ivie, rig, e > 0.5f, false, 1f);
            r.onDraw((long) (t * 1e9));
        }
        new File(outDir).mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outDir + "/cmds.json"), "UTF-8")) { w.write(gl.cmds.append("]").toString()); }
        try (FileOutputStream o = new FileOutputStream(outDir + "/data.bin")) { gl.data.writeTo(o); }
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
