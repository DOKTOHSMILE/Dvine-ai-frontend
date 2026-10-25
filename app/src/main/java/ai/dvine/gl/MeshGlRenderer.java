package ai.dvine.gl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ai.dvine.face.LowPolyHead;

/** Filled 3D mesh with lighting. Not lines. */
public final class MeshGlRenderer implements GLSurfaceView.Renderer {
    private static final String VERT =
            "attribute vec3 aPos;"
                    + "attribute vec3 aNrm;"
                    + "uniform mat4 uMvp;"
                    + "uniform mat3 uNrm;"
                    + "varying vec3 vN;"
                    + "void main(){"
                    + "  gl_Position=uMvp*vec4(aPos,1.0);"
                    + "  vN=normalize(uNrm*aNrm);"
                    + "}";
    private static final String FRAG =
            "precision mediump float;"
                    + "varying vec3 vN;"
                    + "uniform vec4 uColor;"
                    + "uniform vec3 uLight;"
                    + "void main(){"
                    + "  float d=max(0.15,dot(normalize(vN),normalize(uLight)));"
                    + "  gl_FragColor=vec4(uColor.rgb*d,uColor.a);"
                    + "}";

    private final LowPolyHead obi = LowPolyHead.create(true);
    private final LowPolyHead ivie = LowPolyHead.create(false);
    private final float[] deformed = new float[Math.max(obi.rest.length, ivie.rest.length)];
    private final float[] triPos = new float[16384];
    private final float[] triNrm = new float[16384];
    private FloatBuffer posBuf;
    private FloatBuffer nrmBuf;
    private int program;
    private int posLoc;
    private int nrmLoc;
    private int mvpLoc;
    private int nrmMatLoc;
    private int colorLoc;
    private int lightLoc;
    private int viewW = 1;
    private int viewH = 1;
    private volatile boolean live;

    private volatile boolean showIvie;
    private volatile float yawDeg;
    private volatile float smile;
    private volatile float jaw;
    private volatile float browL;
    private volatile float browR;
    private volatile float time;
    private volatile float alpha = 1f;

    public boolean isLive() { return live; }

    public void publish(boolean ivieOn, float yaw, float unitPx, float smile, float jaw,
                        float browL, float browR, float time, float alpha) {
        this.showIvie = ivieOn;
        this.yawDeg = yaw;
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
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        program = program(VERT, FRAG);
        posLoc = GLES20.glGetAttribLocation(program, "aPos");
        nrmLoc = GLES20.glGetAttribLocation(program, "aNrm");
        mvpLoc = GLES20.glGetUniformLocation(program, "uMvp");
        nrmMatLoc = GLES20.glGetUniformLocation(program, "uNrm");
        colorLoc = GLES20.glGetUniformLocation(program, "uColor");
        lightLoc = GLES20.glGetUniformLocation(program, "uLight");
        posBuf = buffer(triPos.length);
        nrmBuf = buffer(triNrm.length);
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
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (!live || program == 0) return;
        LowPolyHead head = showIvie ? ivie : obi;
        head.deform(smile, jaw, browL, browR, time, deformed);
        int verts = head.rest.length / 3;
        int count = 0;
        for (int t = 0; t + 2 < head.tris.length && count + 9 <= triPos.length; t += 3) {
            int a = head.tris[t], b = head.tris[t + 1], c = head.tris[t + 2];
            if (a >= verts || b >= verts || c >= verts) continue;
            putTri(count, a, b, c);
            count += 9;
        }
        float yaw = (float) Math.toRadians(yawDeg);
        float[] mvp = mvp(yaw, viewW, viewH);
        float[] nrm = nrmMat(yaw);
        float r = showIvie ? 1f : 0f;
        float g = showIvie ? 0.70f : 0.85f;
        float b = showIvie ? 0.1f : 1f;
        GLES20.glUseProgram(program);
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glEnableVertexAttribArray(nrmLoc);
        posBuf.clear();
        posBuf.put(triPos, 0, count);
        posBuf.position(0);
        nrmBuf.clear();
        nrmBuf.put(triNrm, 0, count);
        nrmBuf.position(0);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, posBuf);
        GLES20.glVertexAttribPointer(nrmLoc, 3, GLES20.GL_FLOAT, false, 0, nrmBuf);
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);
        GLES20.glUniformMatrix3fv(nrmMatLoc, 1, false, nrm, 0);
        GLES20.glUniform4f(colorLoc, r, g, b, alpha);
        GLES20.glUniform3f(lightLoc, 0.4f, 0.6f, 0.7f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count / 3);
        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(nrmLoc);
    }

    private void putTri(int at, int a, int b, int c) {
        float ax = deformed[a*3], ay = deformed[a*3+1], az = deformed[a*3+2];
        float bx = deformed[b*3], by = deformed[b*3+1], bz = deformed[b*3+2];
        float cx = deformed[c*3], cy = deformed[c*3+1], cz = deformed[c*3+2];
        float nx = (by-ay)*(cz-az) - (bz-az)*(cy-ay);
        float ny = (bz-az)*(cx-ax) - (bx-ax)*(cz-az);
        float nz = (bx-ax)*(cy-ay) - (by-ay)*(cx-ax);
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
        for (int i = 0; i < 3; i++) {
            int o = at + i * 3;
            triPos[o] = (i==0?ax:i==1?bx:cx);
            triPos[o+1] = (i==0?ay:i==1?by:cy);
            triPos[o+2] = (i==0?az:i==1?bz:cz);
            triNrm[o] = nx; triNrm[o+1] = ny; triNrm[o+2] = nz;
        }
    }

    private float[] mvp(float yaw, int w, int h) {
        float aspect = (float) w / h;
        float[] m = new float[16];
        // Simple perspective + yaw
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        m[0] = cy / aspect; m[1] = 0; m[2] = sy; m[3] = 0;
        m[4] = 0; m[5] = 1; m[6] = 0; m[7] = 0;
        m[8] = -sy; m[9] = 0; m[10] = cy; m[11] = 0;
        m[12] = 0; m[13] = 0; m[14] = -2.5f; m[15] = 1;
        return m;
    }

    private float[] nrmMat(float yaw) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        return new float[]{cy, 0, sy, 0, 1, 0, -sy, 0, cy};
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
