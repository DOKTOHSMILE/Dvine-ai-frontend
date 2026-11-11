package ai.ibiko.stage;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The floating player while Ibiko reads aloud: play or pause, back 15 seconds, forward 15 seconds, the speed
 * (1x, 1.25x, 1.5x, 2x, 0.75x), the time read so far, and close. It floats over the chat above the message box and
 * goes when the reading ends.
 */
public final class PlayerBar extends LinearLayout {
    /** What the buttons do. */
    public interface Control {
        void playPause();

        /** Back (negative) or forward (positive) by about this many seconds; false when this reading can't move. */
        boolean skip(int seconds);

        void speed(float s);

        void close();
    }

    static final float[] SPEEDS = {1f, 1.25f, 1.5f, 2f, 0.75f};

    private final Control control;
    private final ImageView play;
    /** The ring in place of play or pause while the voice is still getting ready. */
    private final android.widget.ProgressBar ring;
    private boolean loading;
    private final TextView time, speed, back, forward;
    private int speedAt;
    private boolean paused, reading;
    private long elapsedMs, playingSince;

    public PlayerBar(Context c, Control control) {
        super(c);
        this.control = control;
        float dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int p = (int) (6 * dp);
        setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF02A2A2E);
        bg.setCornerRadius(40 * dp);
        setBackground(bg);
        setElevation(8 * dp);
        play = icon(c, ai.ibiko.R.drawable.ic_pause, "Pause", v -> control.playPause());
        back = word(c, "↺15", "Back 15 seconds", v -> moved(-15));
        forward = word(c, "15↻", "Forward 15 seconds", v -> moved(15));
        speed = word(c, "1x", "Speed", v -> {
            speedAt = (speedAt + 1) % SPEEDS.length;
            float s = SPEEDS[speedAt];
            ((TextView) v).setText((s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x");
            control.speed(s);
        });
        time = new TextView(c);
        time.setTextColor(0xFFE6E6E6);
        time.setTextSize(15);
        time.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        time.setText("0:00");
        ImageView close = icon(c, ai.ibiko.R.drawable.ic_close, "Close", v -> control.close());
        ring = new android.widget.ProgressBar(c);
        ring.setIndeterminate(true);
        ring.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        ring.setPadding((int) (9 * dp), (int) (9 * dp), (int) (9 * dp), (int) (9 * dp));
        ring.setBackground(circle(c));
        ring.setContentDescription("The voice is getting ready");
        ring.setVisibility(GONE);
        int side = (int) (44 * dp);
        android.widget.FrameLayout first = new android.widget.FrameLayout(c);
        first.addView(play, new android.widget.FrameLayout.LayoutParams(side, side));
        first.addView(ring, new android.widget.FrameLayout.LayoutParams(side, side));
        addView(first, spaced(side, side, 0, dp));
        addView(back, spaced(side, side, GAP_DP, dp));
        addView(forward, spaced(side, side, GAP_DP, dp));
        addView(speed, spaced(side, side, GAP_DP, dp));
        addView(time, spaced(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, GAP_DP, dp));
        addView(close, spaced(side, side, GAP_DP, dp));
        setVisibility(GONE);
    }

    /** The space between two buttons. */
    static final int GAP_DP = 8;

    private static LayoutParams spaced(int w, int h, int gapDp, float dp) {
        LayoutParams lp = new LayoutParams(w, h);
        lp.leftMargin = (int) (gapDp * dp);
        return lp;
    }

    /** The voice is getting ready (true) or has started or stopped (false): a ring turns where play or pause is. */
    public void loading(boolean now) {
        loading = now;
        removeCallbacks(giveUp);
        if (now) {
            postDelayed(giveUp, 45000); // never a ring that turns for ever (a voice that failed without a word)
        }
        ring.setVisibility(now ? VISIBLE : GONE);
        play.setVisibility(now ? INVISIBLE : VISIBLE);
        if (now) {
            setVisibility(VISIBLE);
        } else if (!reading) {
            setVisibility(GONE);
        }
    }

    private final Runnable giveUp = () -> loading(false);

    public boolean isLoading() {
        return loading;
    }

    private static ImageView icon(Context c, int res, String says, View.OnClickListener tap) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(0xFFFFFFFF);
        int p = (int) (ai.ibiko.IconSize.pad(44, ai.ibiko.IconSize.STANDARD) * c.getResources().getDisplayMetrics().density);
        i.setPadding(p, p, p, p);
        i.setContentDescription(says);
        i.setOnClickListener(tap);
        i.setBackground(circle(c));
        return i;
    }

    private static TextView word(Context c, String text, String says, View.OnClickListener tap) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFFE6E6E6);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(says);
        t.setOnClickListener(tap);
        t.setBackground(circle(c));
        return t;
    }

    private static GradientDrawable circle(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x22FFFFFF);
        return d;
    }

    private void moved(int seconds) {
        if (control.skip(seconds)) {
            elapsedMs = Math.max(0, current() + seconds * 1000L);
            playingSince = paused ? 0 : now();
            tick();
        }
    }

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }

    private long current() {
        return elapsedMs + (playingSince > 0 ? now() - playingSince : 0);
    }

    /** Reading started, paused, carried on or ended (from the voice); {@code skippable}: back and forward work. */
    public void state(boolean reading, boolean isPaused, boolean skippable) {
        if (reading && !this.reading) {
            elapsedMs = 0;
            playingSince = 0;
            speedAt = 0;
            speed.setText("1x");
        }
        if (reading) {
            if (isPaused && playingSince > 0) {
                elapsedMs = current();
                playingSince = 0;
            } else if (!isPaused && playingSince == 0) {
                playingSince = now();
            }
        }
        this.reading = reading;
        paused = isPaused;
        play.setImageResource(isPaused ? ai.ibiko.R.drawable.ic_play : ai.ibiko.R.drawable.ic_pause);
        play.setContentDescription(isPaused ? "Play" : "Pause");
        back.setAlpha(skippable ? 1f : 0.35f);
        forward.setAlpha(skippable ? 1f : 0.35f);
        back.setEnabled(skippable);
        forward.setEnabled(skippable);
        setVisibility(reading || loading ? VISIBLE : GONE);
        removeCallbacks(clock);
        if (reading) {
            tick();
        }
    }

    private final Runnable clock = this::tick;

    private void tick() {
        long s = current() / 1000;
        time.setText(s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60);
        removeCallbacks(clock);
        if (reading) {
            postDelayed(clock, 500);
        }
    }
}
