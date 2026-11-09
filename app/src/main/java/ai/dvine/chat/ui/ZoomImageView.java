package ai.dvine.chat.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * A picture that opens fitted to the screen and centred; pinch to zoom (up to {@link #MOST} times), drag to move
 * around it (never past its edges), double-tap to zoom in or back to fit, as in a gallery app.
 */
public final class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
    /** The most it zooms in, relative to fitting the screen. */
    static final float MOST = 6f;

    private final Matrix matrix = new Matrix();
    private float fit = 1f;
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;

    public ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                matrix.postTranslate(-dx, -dy);
                keepInside();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zoom() > 1.05f) {
                    fitToView();
                } else {
                    zoomBy(2.5f, e.getX(), e.getY());
                }
                return true;
            }
        });
    }

    /** How far it is zoomed in, relative to fitting the screen (1 = fitted). */
    public float zoom() {
        float[] v = new float[9];
        matrix.getValues(v);
        return v[Matrix.MSCALE_X] / fit;
    }

    @Override
    public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        fitToView();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    /** The whole picture on the screen, centred. */
    public void fitToView() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) {
            return;
        }
        float w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        fit = Math.min(getWidth() / w, getHeight() / h);
        matrix.reset();
        matrix.postScale(fit, fit);
        matrix.postTranslate((getWidth() - w * fit) / 2f, (getHeight() - h * fit) / 2f);
        setImageMatrix(matrix);
    }

    private void zoomBy(float factor, float x, float y) {
        float now = zoom();
        float next = Math.max(1f, Math.min(MOST, now * factor));
        matrix.postScale(next / now, next / now, x, y);
        keepInside();
    }

    /** No empty space past the picture's edges; a picture smaller than the screen stays centred. */
    private void keepInside() {
        Drawable d = getDrawable();
        if (d == null) {
            return;
        }
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = r.width() <= getWidth() ? (getWidth() - r.width()) / 2f - r.left
                : r.left > 0 ? -r.left : r.right < getWidth() ? getWidth() - r.right : 0;
        float dy = r.height() <= getHeight() ? (getHeight() - r.height()) / 2f - r.top
                : r.top > 0 ? -r.top : r.bottom < getHeight() ? getHeight() - r.bottom : 0;
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        if (!pinch.isInProgress()) {
            gestures.onTouchEvent(e);
        }
        return true;
    }
}
