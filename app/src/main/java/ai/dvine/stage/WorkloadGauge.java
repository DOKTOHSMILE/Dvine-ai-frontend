package ai.dvine.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The model's workload in the header: a small ring that fills with how much of its working memory (the context
 * window) the conversation uses, green while there is room, amber when it is filling, red when it is nearly full
 * (DVINE then sums up and clears it). The percentage is in its description for screen readers.
 */
public final class WorkloadGauge extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float load;

    public WorkloadGauge(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(3 * d);
        track.setColor(0x33888899);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(3 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(8.5f * d);
        label.setFakeBoldText(true);
        set(0f);
    }

    private android.animation.ValueAnimator easing;

    /**
     * 0..1 of the window in use. A big drop (the memory just cleared) slides down over a second, so the release is
     * seen; anything else shows at once.
     */
    public void set(float used) {
        final float to = Math.max(0f, Math.min(1f, used));
        if (easing != null) {
            easing.cancel();
            easing = null;
        }
        if (load - to > 0.2f && isAttachedToWindow()) {
            easing = android.animation.ValueAnimator.ofFloat(load, to);
            easing.setDuration(1100);
            easing.addUpdateListener(a -> show((float) a.getAnimatedValue()));
            easing.start();
            setContentDescription("Model workload " + Math.round(to * 100) + " percent, memory cleared");
            return;
        }
        show(to);
    }

    private void show(float used) {
        load = used;
        int colour = colour(load);
        arc.setColor(colour);
        label.setColor(colour);
        setContentDescription("Model workload " + Math.round(load * 100) + " percent" + (load >= HOT ? ", nearly full" : ""));
        invalidate();
    }

    public float load() {
        return load;
    }

    /** Above this, the working memory is summed up and cleared. */
    public static final float HOT = 0.85f;

    public static int colourOf(float load) {
        return colour(load);
    }

    static int colour(float load) {
        return load >= HOT ? 0xFFE5484D : load >= 0.6f ? 0xFFF5A524 : 0xFF3DD68C;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()), pad = track.getStrokeWidth();
        float left = (getWidth() - s) / 2f + pad, top = (getHeight() - s) / 2f + pad;
        box.set(left, top, left + s - 2 * pad, top + s - 2 * pad);
        canvas.drawOval(box, track);
        canvas.drawArc(box, -90f, 360f * Math.max(0.02f, load), false, arc);
        Paint.FontMetrics fm = label.getFontMetrics();
        canvas.drawText(String.valueOf(Math.round(load * 100)), box.centerX(), box.centerY() - (fm.ascent + fm.descent) / 2f, label);
    }
}
