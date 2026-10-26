package ai.dvine.render;

import android.opengl.GLES30;
import android.util.Log;

import java.nio.Buffer;

/** {@link GlApi} on Android's GLES30. Call only on the GL thread. */
public final class AndroidGl implements GlApi {
    private static final String TAG = "DvineGL";
    private final int[] one = new int[1];

    @Override
    public int program(String vertex, String fragment) {
        int vs = shader(GLES30.GL_VERTEX_SHADER, vertex), fs = shader(GLES30.GL_FRAGMENT_SHADER, fragment);
        if (vs == 0 || fs == 0) {
            return 0;
        }
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, one, 0);
        if (one[0] == 0) {
            Log.e(TAG, "link: " + GLES30.glGetProgramInfoLog(p));
            GLES30.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private int shader(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, one, 0);
        if (one[0] == 0) {
            Log.e(TAG, "compile: " + GLES30.glGetShaderInfoLog(s));
            GLES30.glDeleteShader(s);
            return 0;
        }
        return s;
    }

    @Override public void deleteProgram(int p) { GLES30.glDeleteProgram(p); }
    @Override public void useProgram(int p) { GLES30.glUseProgram(p); }
    @Override public int uniform(int program, String name) { return GLES30.glGetUniformLocation(program, name); }
    @Override public void uniform1f(int loc, float v) { GLES30.glUniform1f(loc, v); }
    @Override public void uniform1i(int loc, int v) { GLES30.glUniform1i(loc, v); }
    @Override public void uniform2f(int loc, float a, float b) { GLES30.glUniform2f(loc, a, b); }
    @Override public void uniform4f(int loc, float a, float b, float c, float d) { GLES30.glUniform4f(loc, a, b, c, d); }

    @Override
    public void uniform3fv(int loc, float[] values) {
        GLES30.glUniform3fv(loc, values.length / 3, values, 0);
    }
    @Override public void uniformMatrix4(int loc, float[] m) { GLES30.glUniformMatrix4fv(loc, 1, false, m, 0); }
    @Override public void uniformMatrix3(int loc, float[] m) { GLES30.glUniformMatrix3fv(loc, 1, false, m, 0); }

    @Override
    public int buffer() {
        GLES30.glGenBuffers(1, one, 0);
        return one[0];
    }

    @Override
    public void deleteBuffer(int b) {
        one[0] = b;
        GLES30.glDeleteBuffers(1, one, 0);
    }

    @Override public void bindBuffer(int target, int b) { GLES30.glBindBuffer(target, b); }
    @Override public void bufferData(int target, int bytes, Buffer data, int usage) { GLES30.glBufferData(target, bytes, data, usage); }
    @Override public void bufferSubData(int target, int offset, int bytes, Buffer data) { GLES30.glBufferSubData(target, offset, bytes, data); }

    @Override
    public void vertexAttrib(int index, int size, int stride, int offset) {
        GLES30.glEnableVertexAttribArray(index);
        GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset);
    }

    @Override public void disableAttrib(int index) { GLES30.glDisableVertexAttribArray(index); }

    @Override
    public int floatTexture(int width, int height) {
        GLES30.glGenTextures(1, one, 0);
        int t = one[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB32F, width, height, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, null);
        return t;
    }

    @Override
    public void updateFloatTexture(int tex, int width, int height, Buffer data) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES30.GL_RGB, GLES30.GL_FLOAT, data);
    }

    @Override
    public void deleteTexture(int t) {
        one[0] = t;
        GLES30.glDeleteTextures(1, one, 0);
    }

    @Override
    public void bindTexture(int unit, int tex) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
    }

    @Override public void enable(int cap) { GLES30.glEnable(cap); }
    @Override public void disable(int cap) { GLES30.glDisable(cap); }
    @Override public void depthMask(boolean on) { GLES30.glDepthMask(on); }
    @Override public void depthFunc(int f) { GLES30.glDepthFunc(f); }
    @Override public void blendFunc(int src, int dst) { GLES30.glBlendFunc(src, dst); }
    @Override public void polygonOffset(float factor, float units) { GLES30.glPolygonOffset(factor, units); }
    @Override public void viewport(int w, int h) { GLES30.glViewport(0, 0, w, h); }

    @Override
    public void clear(float r, float g, float b) {
        GLES30.glClearColor(r, g, b, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
    }

    @Override public void drawArrays(int mode, int first, int count) { GLES30.glDrawArrays(mode, first, count); }
    @Override public void drawElements(int mode, int count, int offsetBytes) { GLES30.glDrawElements(mode, count, GLES30.GL_UNSIGNED_INT, offsetBytes); }
    @Override public int error() { return GLES30.glGetError(); }
}
