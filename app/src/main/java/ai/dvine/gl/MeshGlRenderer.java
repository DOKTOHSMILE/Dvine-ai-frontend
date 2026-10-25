package ai.dvine.gl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ai.dvine.face.LowPolyHead;

/** Draws the formed bust as lines and nodes. Not a point cloud. */
public final class MeshGlRenderer implements GLSurfaceView.Renderer {
    private static final String VERT =
            "attribute vec2 aPos;"
                    + "void main(){gl_Position=vec4(aPos,0.0,1.0);gl_PointSize=3.5;}";
    private static final String FRAG =
            "precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}";

    private final LowPolyHead obi = LowPolyHead.create(true);
    private final LowPolyHead ivie = LowPolyHead.create(false);
    private final float[] deformed = new float[Math.max(obi.rest.length, ivie.rest.length)];
    private final float[] lines = new float[8192];
    private final float[] nodes = new float[2048];
    private FloatBuffer lineBuf;
    private FloatBuffer nodeBuf;
    private int program;
    private int posLoc;
    private int colorLoc;
    private int viewW = 1;
    private int viewH = 1;
    private volatile boolean live;

    private volatile boolean showIvie;
    private volatile float yawDeg = -28f;
    private volatile float unit = 200f;
    private volatile float smile;
    private volatile float jaw;
    private volatile float browL;
    private volatile float browR;
    private volatile float time;
    private volatile float alpha = 1f;

    public boolean isLive() {
        return live;
    }

    public void publish(boolean ivieOn, float yaw, float unitPx, float smile, float jaw,
                        float browL, float browR, float time, float alpha) {
        this.showIvie = ivieOn;
        this.yawDeg = yaw;
        this.unit = unitPx;
        this.smile = smile;
        this.jaw = jaw;
        this.browL = browL;
        this.browR = browR;
        this.time = time;
        this.alpha = alpha;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        program = program(VERT, FRAG);
        posLoc = GLES20.glGetAttribLocation(program, "aPos");
        colorLoc = GLES20.glGetUniformLocation(program, "uColor");
        lineBuf = buffer(lines.length);
        nodeBuf = buffer(nodes.length);
        live = true;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        GLES20.glViewport(0, 0, viewW, viewH);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!live || program == 0) {
            return;
        }
        LowPolyHead head = showIvie ? ivie : obi;
        head.deform(smile, jaw, browL, browR, time, deformed);
        int verts = head.rest.length / 3;
        int lineCount = 0;
        int nodeCount = 0;
        float yaw = (float) Math.toRadians(yawDeg);
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float halfW = viewW * 0.5f;
        float halfH = viewH * 0.5f;
        for (int i = 0; i < verts && nodeCount + 2 <= nodes.length; i++) {
            float[] p = project(deformed[i * 3], deformed[i * 3 + 1], deformed[i * 3 + 2], cy, sy, halfW, halfH);
            nodes[nodeCount++] = p[0];
            nodes[nodeCount++] = p[1];
        }
        for (int t = 0; t + 2 < head.tris.length && lineCount + 12 <= lines.length; t += 3) {
            int a = head.tris[t];
            int b = head.tris[t + 1];
            int c = head.tris[t + 2];
            if (a >= verts || b >= verts || c >= verts) {
                continue;
            }
            putEdge(lineCount, a, b);
            lineCount += 4;
            putEdge(lineCount, b, c);
            lineCount += 4;
            putEdge(lineCount, c, a);
            lineCount += 4;
        }
        float r = showIvie ? 1f : 0f;
        float g = showIvie ? 0.70f : 0.90f;
        float b = showIvie ? 0f : 1f;
        GLES20.glUseProgram(program);
        GLES20.glEnableVertexAttribArray(posLoc);
        draw(lineBuf, lines, lineCount, r, g, b, alpha * 0.9f, GLES20.GL_LINES);
        draw(nodeBuf, nodes, nodeCount, r, g, b, alpha, GLES20.GL_POINTS);
        GLES20.glDisableVertexAttribArray(posLoc);
    }

    private void putEdge(int at, int a, int b) {
        lines[at] = nodes[a * 2];
        lines[at + 1] = nodes[a * 2 + 1];
        lines[at + 2] = nodes[b * 2];
        lines[at + 3] = nodes[b * 2 + 1];
    }

    private float[] project(float x, float y, float z, float cy, float sy, float halfW, float halfH) {
        float x3 = x * cy + z * sy;
        float z3 = -x * sy + z * cy;
        float s = unit * (3.15f / (4.5f - z3));
        scratch[0] = (x3 * s) / halfW;
        scratch[1] = (y * s) / halfH;
        return scratch;
    }

    private final float[] scratch = new float[2];

    private void draw(FloatBuffer buf, float[] data, int count, float r, float g, float b, float a, int mode) {
        if (count < 2) {
            return;
        }
        buf.clear();
        buf.put(data, 0, count);
        buf.position(0);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, buf);
        GLES20.glUniform4f(colorLoc, r, g, b, a);
        GLES20.glDrawArrays(mode, 0, count / 2);
    }

    private static FloatBuffer buffer(int floats) {
        ByteBuffer raw = ByteBuffer.allocateDirect(floats * 4);
        raw.order(ByteOrder.nativeOrder());
        return raw.asFloatBuffer();
    }

    private static int program(String vert, String frag) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vert);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, frag);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static int shader(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        return s;
    }
}
