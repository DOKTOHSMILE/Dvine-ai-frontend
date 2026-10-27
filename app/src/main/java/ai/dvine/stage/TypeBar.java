package ai.dvine.stage;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Typing instead of speaking: a text field drawn in particles (its outline is a row of glowing dots that twinkle),
 * and a Send button beside it. Double-tap the stage to show it; tap the field to bring up the keyboard; type; Send
 * (or the keyboard's send key) hands the words over and the field fades away. Hidden again with nothing sent when
 * it loses focus empty.
 */
public final class TypeBar extends LinearLayout {
    public interface Sender {
        void send(String text);
    }

    private final EditText input;
    private final TextView send;
    private final Sender sender;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private final long born = System.nanoTime();

    public TypeBar(Context context, Sender sender) {
        super(context);
        this.sender = sender;
        density = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        int pad = (int) (12 * density);
        setPadding(pad, (int) (6 * density), (int) (6 * density), (int) (6 * density));
        dot.setColor(0xFF9FE8FF);
        glow.setColor(0xFF4FC8FF);
        glow.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null); // the blur needs it

        input = new EditText(context);
        input.setBackground(null);
        input.setTextColor(0xFFE8F6FF);
        input.setHintTextColor(0x8890C8E0);
        input.setTextSize(16f);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        send = new TextView(context);
        send.setText("SEND");
        send.setTextColor(0xFF7FE3FF);
        send.setTextSize(14f);
        send.setLetterSpacing(0.18f);
        send.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int sp = (int) (14 * density);
        send.setPadding(sp, sp, sp, sp);
        send.setOnClickListener(v -> submit());
        addView(send, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    /** Shows the field (without the keyboard: a tap on it brings that up). */
    public void show(String hint) {
        input.setHint(hint);
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(220).start();
        postInvalidateOnAnimation();
    }

    public void hide() {
        InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) {
            im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        input.clearFocus();
        animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
    }

    private void submit() {
        String t = input.getText().toString().trim();
        input.setText("");
        hide();
        if (!t.isEmpty()) {
            sender.send(t);
        }
    }

    /** The outline: dots spaced along a rounded box, each twinkling on its own, with a soft glow under them. */
    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 3f * density, r = 18f * density;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float t = (System.nanoTime() - born) / 1e9f;
        float step = 6f * density;
        float w = box.width() - 2 * r, h = box.height() - 2 * r;
        float per = 2 * (w + h) + (float) (2 * Math.PI * r);
        int n = (int) (per / step);
        for (int i = 0; i < n; i++) {
            float[] p = along(i * per / n, w, h, r);
            float tw = 0.55f + 0.45f * (float) Math.sin(t * 3f + i * 1.7f);
            float travel = (float) Math.exp(-Math.pow(((i / (float) n - t * 0.15f) % 1f + 1f) % 1f * 10f - 5f, 2));
            glow.setAlpha((int) (60 * tw + 120 * travel));
            canvas.drawCircle(p[0], p[1], 2.6f * density, glow);
            dot.setAlpha((int) (150 * tw + 105 * travel));
            canvas.drawCircle(p[0], p[1], 1.2f * density, dot);
        }
        // the send button's own small ring of dots
        float cx = send.getLeft() + send.getWidth() / 2f, cy = send.getTop() + send.getHeight() / 2f;
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18 + t * 0.8;
            dot.setAlpha((int) (120 + 100 * Math.sin(t * 4 + i)));
            canvas.drawCircle(cx + (float) Math.cos(a) * send.getWidth() * 0.55f,
                    cy + (float) Math.sin(a) * send.getHeight() * 0.45f, 1f * density, dot);
        }
        super.onDraw(canvas);
        if (getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    /** A point at distance {@code d} along the rounded box's outline (clockwise from the top left). */
    private float[] along(float d, float w, float h, float r) {
        float x0 = box.left, y0 = box.top;
        if (d < w) return new float[]{x0 + r + d, y0};
        d -= w;
        float q = (float) (Math.PI * r / 2);
        if (d < q) return arc(x0 + r + w, y0 + r, r, -Math.PI / 2 + d / r);
        d -= q;
        if (d < h) return new float[]{x0 + 2 * r + w, y0 + r + d};
        d -= h;
        if (d < q) return arc(x0 + r + w, y0 + r + h, r, d / r);
        d -= q;
        if (d < w) return new float[]{x0 + r + w - d, y0 + 2 * r + h};
        d -= w;
        if (d < q) return arc(x0 + r, y0 + r + h, r, Math.PI / 2 + d / r);
        d -= q;
        if (d < h) return new float[]{x0, y0 + r + h - d};
        d -= h;
        return arc(x0 + r, y0 + r, r, Math.PI + d / r);
    }

    private static float[] arc(float cx, float cy, float r, double a) {
        return new float[]{cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r};
    }
}
