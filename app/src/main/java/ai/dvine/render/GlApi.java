package ai.dvine.render;

import java.nio.Buffer;

/**
 * The few OpenGL ES 3 calls the renderer uses, behind an interface so tests
 * can check resource handling (create, recreate after a lost context,
 * dispose) without a GPU. {@link AndroidGl} forwards to GLES30.
 */
public interface GlApi {
    int ARRAY_BUFFER = 0x8892, ELEMENT_ARRAY_BUFFER = 0x8893;
    int STATIC_DRAW = 0x88E4, DYNAMIC_DRAW = 0x88E8;
    int FLOAT = 0x1406, UNSIGNED_INT = 0x1405;
    int TRIANGLES = 0x0004, LINES = 0x0001, POINTS = 0x0000;
    int DEPTH_TEST = 0x0B71, BLEND = 0x0BE2, POLYGON_OFFSET_FILL = 0x8037, CULL_FACE = 0x0B44;
    int ONE = 1, SRC_ALPHA = 0x0302, ONE_MINUS_SRC_ALPHA = 0x0303;
    int LEQUAL = 0x0203, LESS = 0x0201;
    int COLOR_BUFFER_BIT = 0x4000, DEPTH_BUFFER_BIT = 0x0100;
    int TEXTURE_2D = 0x0DE1, TEXTURE0 = 0x84C0;
    int NO_ERROR = 0;

    /** Compiles and links; returns 0 (and logs) on failure. */
    int program(String vertex, String fragment);

    void deleteProgram(int p);

    void useProgram(int p);

    int uniform(int program, String name);

    void uniform1f(int loc, float v);

    void uniform1i(int loc, int v);

    void uniform2f(int loc, float a, float b);

    void uniform4f(int loc, float a, float b, float c, float d);

    /** A vec3 array uniform (count = values.length / 3). */
    void uniform3fv(int loc, float[] values);

    void uniformMatrix4(int loc, float[] m);

    void uniformMatrix3(int loc, float[] m);

    int buffer();

    void deleteBuffer(int b);

    void bindBuffer(int target, int b);

    void bufferData(int target, int bytes, Buffer data, int usage);

    void bufferSubData(int target, int offset, int bytes, Buffer data);

    void vertexAttrib(int index, int size, int stride, int offset);

    void disableAttrib(int index);

    /** A float RGB texture of the given size (for vertex fetch in the line shader). */
    int floatTexture(int width, int height);

    void updateFloatTexture(int tex, int width, int height, Buffer data);

    void deleteTexture(int t);

    void bindTexture(int unit, int tex);

    void enable(int cap);

    void disable(int cap);

    void depthMask(boolean on);

    void depthFunc(int f);

    void blendFunc(int src, int dst);

    void polygonOffset(float factor, float units);

    void viewport(int w, int h);

    void clear(float r, float g, float b);

    void drawArrays(int mode, int first, int count);

    void drawElements(int mode, int count, int offsetBytes);

    int error();
}
