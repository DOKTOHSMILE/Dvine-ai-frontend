package ai.dvine.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.Gravity;
import android.widget.FrameLayout;

/** OpenGL ES 2 surface for the one bust. The rest of the screen stays on the canvas. */
public final class MeshGlView extends GLSurfaceView {
    private final MeshGlRenderer renderer;

    public MeshGlView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new MeshGlRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public boolean isLive() {
        return renderer.isLive();
    }

    public void publish(boolean ivieOn, float yaw, float unitPx, float smile, float jaw,
                        float browL, float browR, float time, float alpha) {
        renderer.publish(ivieOn, yaw, unitPx, smile, jaw, browL, browR, time, alpha);
    }

    public void place(int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = getLayoutParams() instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) getLayoutParams()
                : null;
        if (lp == null) {
            return;
        }
        int w = Math.max(1, right - left);
        int h = Math.max(1, bottom - top);
        if (lp.width == w && lp.height == h && lp.leftMargin == left && lp.topMargin == top) {
            return;
        }
        lp.width = w;
        lp.height = h;
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        setLayoutParams(lp);
    }
}
