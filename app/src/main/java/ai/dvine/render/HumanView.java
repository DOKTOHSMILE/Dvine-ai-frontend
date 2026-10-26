package ai.dvine.render;

import android.content.Context;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The OpenGL ES 3 surface the digital human is drawn on, under the
 * transparent stage overlay. Draws on demand (the stage requests a frame
 * after each update). A new GL context (after the app was in the
 * background) rebuilds every GL object; detaching frees them.
 */
public final class HumanView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public final WireRenderer renderer;

    public HumanView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new WireRenderer(new AndroidGl());
        renderer.setDensity(getResources().getDisplayMetrics().density);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        renderer.onCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        renderer.onSize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        renderer.onDraw(System.nanoTime());
    }

    @Override
    protected void onDetachedFromWindow() {
        queueEvent(renderer::dispose);
        super.onDetachedFromWindow();
    }
}
