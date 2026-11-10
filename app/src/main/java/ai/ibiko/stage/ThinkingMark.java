package ai.ibiko.stage;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The live "working" mark beside what DVINE is doing now: a four-point star in the accent colour that looks left and
 * right like an eye thinking. It rolls to one side (turning as it goes, like a ball), rests, rolls across to the
 * other side, rests, and comes back, inside a faint still glow like an eye's socket, while the work goes on.
 * Drawn, not an image, so it stays sharp at any size. It stops animating when it leaves the screen, and stands still
 * when the phone's "remove animations" setting is on.
 */
public final class ThinkingMark extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path spark = new Path();
    private ValueAnimator spin;
    private long drawnAt;
    private float phase;
    /** One full look: centre, left, rest, right, rest, centre. */
    static final long GLANCE_MS = 3200;

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
        spin.setDuration(GLANCE_MS);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            // redrawn about 12 times a second, not at the screen's 60-120: a mark that turns for a whole task must not
            // keep the display and graphics chip at full rate (that warms the device)
            long now = android.os.SystemClock.uptimeMillis();
            if (now - drawnAt >= 80) {
                drawnAt = now;
                invalidate();
            }
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

    /**
     * Where the star looks at {@code t} (0..1 of a glance), -1 far left to 1 far right: quick eased moves with rests
     * between, the way eyes glance rather than slide.
     */
    static float look(float t) {
        if (t < 0.12f) return -ease(t / 0.12f);                 // centre to left
        if (t < 0.38f) return -1f;                              // rest on the left
        if (t < 0.56f) return -1f + 2f * ease((t - 0.38f) / 0.18f); // across to the right
        if (t < 0.82f) return 1f;                               // rest on the right
        if (t < 0.94f) return 1f - ease((t - 0.82f) / 0.12f);   // back to centre
        return 0f;                                              // a short rest in the middle
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        // the faint socket of light stays still
        glow.setAlpha(50);
        canvas.drawCircle(cx, cy, r * 0.9f, glow);
        // the star rolls inside it: moving sideways turns it as a ball would turn (angle = distance / radius)
        float outer = r * 0.6f, inner = outer * 0.28f, reach = r * 0.32f;
        float x0 = cx + look(phase) * reach;
        float turn = (float) Math.toDegrees((x0 - cx) / outer);
        spark.reset();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i - Math.PI / 2;
            float len = i % 2 == 0 ? outer : inner;
            float x = x0 + (float) (Math.cos(a) * len), y = cy + (float) (Math.sin(a) * len);
            if (i == 0) {
                spark.moveTo(x, y);
            } else {
                spark.lineTo(x, y);
            }
        }
        spark.close();
        fill.setAlpha(240);
        canvas.save();
        canvas.rotate(turn, x0, cy);
        canvas.drawPath(spark, fill);
        canvas.restore();
    }
}
