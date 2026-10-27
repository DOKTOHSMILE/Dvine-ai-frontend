package ai.dvine.render;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * A GL that records every call (and the data handed to it) as JSON and a binary blob, for tools/gl_replay to play
 * back in WebGL2 and screenshot: what a phone would show, without a phone. Draws nothing itself.
 */
public final class RecordingGl implements GlApi {
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
    public int imageTexture(int w, int h, Buffer d) { int id = next++; c("[\"imgTex\"," + id + "," + w + "," + h + "," + blob(d, w * h * 4) + "]"); return id; }
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
