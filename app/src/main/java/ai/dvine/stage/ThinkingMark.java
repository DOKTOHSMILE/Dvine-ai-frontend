package ai.dvine.stage;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The live "working" mark beside what DVINE is doing now: a four-point spark in the accent colour that turns slowly
 * and breathes (grows a little and fades a little), with a faint ring of light behind it, while the work goes on.
 * Drawn, not an image, so it stays sharp at any size. It stops animating when it leaves the screen, and stands still
 * when the phone's "remove animations" setting is on.
 */
public final class ThinkingMark extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path spark = new Path();
    private ValueAnimator spin;
    private float phase;

    public ThinkingMark(Context c, int colour) {
        super(c);
        fill.setColor(colour);
        fill.setStyle(Paint.Style.FILL);
        glow.setColor(colour);
        glow.setStyle(Paint.Style.FILL);
        setContentDescription("Working");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (android.provider.Settings.Global.getFloat(getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            return; // animations off: a still mark
        }
        spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(2400);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        spin.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spin != null) {
            spin.cancel();
            spin = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        double beat = Math.sin(phase * 2 * Math.PI); // one breath a turn
        // the faint ring of light behind
        glow.setAlpha((int) (40 + 30 * beat));
        canvas.drawCircle(cx, cy, r * (0.78f + 0.12f * (float) beat), glow);
        // the spark: four points, slim waist, turning a quarter turn per breath
        float outer = r * (0.82f + 0.08f * (float) beat), inner = outer * 0.26f;
        spark.reset();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i - Math.PI / 2;
            float len = i % 2 == 0 ? outer : inner;
            float x = cx + (float) (Math.cos(a) * len), y = cy + (float) (Math.sin(a) * len);
            if (i == 0) {
                spark.moveTo(x, y);
            } else {
                spark.lineTo(x, y);
            }
        }
        spark.close();
        fill.setAlpha((int) (210 + 45 * beat));
        canvas.save();
        canvas.rotate(phase * 90f, cx, cy);
        canvas.drawPath(spark, fill);
        canvas.restore();
    }
}
