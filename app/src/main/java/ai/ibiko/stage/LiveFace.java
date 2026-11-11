package ai.ibiko.stage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Ibiko's face while it works: the app icon's face (a black rounded square, two glowing cyan eyes, circuit traces at
 * the sides) drawn live, not played from a short GIF. The eyes blink at random moments (sometimes twice), glide
 * smoothly to where they look, and the face breathes a little; nothing is on a fixed loop, so it never visibly
 * repeats. Each kind of work has its own look ({@link Mood}): thinking glances up and about, reading scans along
 * lines, writing looks down and steps along, working sweeps steadily side to side, replying smiles with its eyes.
 * Still (eyes open, centred) when the device's animations are off.
 */
public final class LiveFace extends View {
    public enum Mood { THINKING, READING, WRITING, WORKING, REPLYING }

    private final Random random = new Random();
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            trace = new Paint(Paint.ANTI_ALIAS_FLAG), eye = new Paint(Paint.ANTI_ALIAS_FLAG),
            core = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final boolean moving;
    private Mood mood = Mood.THINKING;

    // where the eyes look (-1..1), where they are going, and how fast they get there
    private float gx, gy, tx, ty, vx, vy;
    private long nextLook, nextBlink, blinkStart = -1, start, moodSince;
    private boolean doubleBlink;
    private float squint = 1f, squintTarget = 1f;
    private int sizeCached = -1;

    static final int CYAN = 0xFF2BE4FF, CORE = 0xFFE9FDFF;

    public LiveFace(Context c) {
        super(c);
        moving = android.provider.Settings.Global.getFloat(c.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        rim.setStyle(Paint.Style.STROKE);
        trace.setStyle(Paint.Style.STROKE);
        trace.setStrokeCap(Paint.Cap.ROUND);
        eye.setColor(CYAN);
        core.setColor(CORE);
        setContentDescription("Working");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        start = now();
        nextBlink = start + 900 + random.nextInt(2500);
        nextLook = start + 300;
    }

    /** What Ibiko is doing now: the face changes how it looks, smoothly. */
    public void setMood(Mood m) {
        if (m != null && m != mood) {
            mood = m;
            moodSince = now();
            nextLook = moodSince; // a new look at once
        }
    }

    public Mood mood() {
        return mood;
    }

    /** The mood for a step as the chat words it ("Read app/Main.java", "Edited 2 files", "Ran 3 commands"…). */
    public static Mood moodOf(String step) {
        String s = step == null ? "" : step.toLowerCase(java.util.Locale.US);
        if (s.matches("(?s)^(read|reading|explored|exploring|searched|searching|looked|looking|survey|listed|opened).*")) {
            return Mood.READING;
        }
        if (s.matches("(?s)^(edited|editing|wrote|writing|created|creating|changed|updated|made|making|saved).*")) {
            return Mood.WRITING;
        }
        if (s.matches("(?s)^(ran|running|run|cloning|cloned|pull|push|install|build|test|download|unzip|fetch|git|used).*")) {
            return Mood.WORKING;
        }
        if (s.matches("(?s)^(replying|answering|writing the reply|the reply).*")) {
            return Mood.REPLYING;
        }
        return Mood.THINKING;
    }

    private static long now() {
        return android.os.SystemClock.uptimeMillis();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        float side = Math.min(w, h);
        if (side <= 0) {
            return;
        }
        long t = now();
        float sec = (t - start) / 1000f;
        if (moving) {
            step(t, sec);
        }
        if ((int) side != sizeCached) {
            sizeCached = (int) side;
            face.setShader(new LinearGradient(0, 0, 0, side, 0xFF0B1116, 0xFF020405, Shader.TileMode.CLAMP));
            rim.setStrokeWidth(Math.max(1f, side * 0.025f));
            rim.setColor(0x3330D8F0);
            trace.setStrokeWidth(Math.max(0.8f, side * 0.018f));
            trace.setColor(0x6030D8F0);
        }
        // breathing: a slight swell, on two unrelated slow rhythms so it never lines up the same way twice
        float breathe = moving ? 1f + 0.018f * (float) Math.sin(sec * 1.37) + 0.008f * (float) Math.sin(sec * 0.61 + 1.3) : 1f;
        // the face sits inside its square with room around it (zoomed out), corners curved
        float s = side * 0.86f * breathe;
        float cx = w / 2f, cy = h / 2f;
        r.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        float corner = s * 0.28f;
        c.drawRoundRect(r, corner, corner, face);
        c.drawRoundRect(r, corner, corner, rim);
        drawTraces(c, r, s, sec);
        drawEyes(c, r, s, t, sec);
        if (moving && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    /** Circuit traces down both sides, a faint pulse running along them. */
    private void drawTraces(Canvas c, RectF r, float s, float sec) {
        float pulse = moving ? 0.55f + 0.45f * (float) Math.sin(sec * 2.1) : 0.8f;
        trace.setAlpha((int) (70 + 60 * pulse));
        for (int side = 0; side < 2; side++) {
            float x0 = side == 0 ? r.left + s * 0.10f : r.right - s * 0.10f;
            float dir = side == 0 ? 1 : -1;
            c.drawLine(x0, r.top + s * 0.18f, x0, r.bottom - s * 0.18f, trace);
            c.drawLine(x0, r.top + s * 0.40f, x0 + dir * s * 0.06f, r.top + s * 0.46f, trace);
            c.drawLine(x0, r.top + s * 0.62f, x0 + dir * s * 0.07f, r.top + s * 0.62f, trace);
            c.drawCircle(x0 + dir * s * 0.06f, r.top + s * 0.46f, s * 0.018f, trace);
            c.drawCircle(x0 + dir * s * 0.07f, r.top + s * 0.62f, s * 0.018f, trace);
        }
    }

    private void drawEyes(Canvas c, RectF r, float s, long t, float sec) {
        float open = blinkOpen(t);
        float ew = s * 0.13f, eh = s * 0.30f * squint * open;
        float spread = s * 0.155f;
        float ox = gx * s * 0.075f, oy = gy * s * 0.07f;
        float ecy = r.centerY() + oy;
        boolean happy = mood == Mood.REPLYING && moving && smiling;
        float glowPulse = mood == Mood.WORKING && moving ? 0.8f + 0.2f * (float) Math.sin(sec * 3.0) : 1f;
        for (int i = 0; i < 2; i++) {
            float ecx = r.centerX() + (i == 0 ? -spread : spread) + ox;
            float glowR = s * 0.22f;
            glow.setShader(new RadialGradient(ecx, ecy, glowR, (int) (0x55 * glowPulse) << 24 | 0x2BE4FF, 0x002BE4FF, Shader.TileMode.CLAMP));
            c.drawCircle(ecx, ecy, glowR, glow);
            if (happy && open > 0.6f) {
                // a smile in the eyes: an upturned arc
                eye.setStyle(Paint.Style.STROKE);
                eye.setStrokeWidth(ew * 0.55f);
                eye.setStrokeCap(Paint.Cap.ROUND);
                RectF arc = new RectF(ecx - ew * 0.75f, ecy - ew * 0.35f, ecx + ew * 0.75f, ecy + ew * 1.15f);
                c.drawArc(arc, 200, 140, false, eye);
                eye.setStyle(Paint.Style.FILL);
                continue;
            }
            float hh = Math.max(eh, ew * 0.22f);
            RectF e = new RectF(ecx - ew / 2, ecy - hh / 2, ecx + ew / 2, ecy + hh / 2);
            c.drawRoundRect(e, ew / 2, Math.min(ew / 2, hh / 2), eye);
            RectF k = new RectF(ecx - ew * 0.24f, ecy - hh * 0.36f, ecx + ew * 0.24f, ecy + hh * 0.36f);
            c.drawRoundRect(k, ew * 0.24f, Math.min(ew * 0.24f, hh * 0.36f), core);
        }
    }

    /** 1 open, 0 shut: a blink is a quick close and a slightly slower open; sometimes two in a row. */
    private float blinkOpen(long t) {
        if (!moving || blinkStart < 0) {
            return 1f;
        }
        float k = (t - blinkStart) / 1f;
        float close = 70, hold = 40, reopen = 110;
        if (k < close) {
            return 1f - k / close;
        }
        if (k < close + hold) {
            return 0.06f;
        }
        if (k < close + hold + reopen) {
            return (k - close - hold) / reopen;
        }
        blinkStart = -1;
        return 1f;
    }

    /** Where the eyes go next, and the spring that takes them there smoothly. */
    private void step(long t, float sec) {
        if (blinkStart < 0 && t >= nextBlink) {
            blinkStart = t;
            if (doubleBlink) {
                doubleBlink = false;
                nextBlink = t + 1800 + random.nextInt(4200);
            } else {
                doubleBlink = random.nextFloat() < 0.18f;
                nextBlink = t + (doubleBlink ? 260 : 1800 + random.nextInt(4200));
            }
        }
        switch (mood) {
            case WORKING:
                // a steady, even sweep from side to side, a little lower when it turns
                tx = (float) Math.sin(sec * 1.6);
                ty = 0.1f + 0.08f * (float) Math.cos(sec * 3.2);
                squintTarget = 0.82f;
                break;
            case READING:
                // along a line at an even pace, back to the start, and down a line
                if (t >= nextLook) {
                    float lineDur = 1400 + random.nextInt(700);
                    nextLook = t + (long) lineDur;
                    readLine = (readLine + 1) % 4;
                    readStart = t;
                    readDur = lineDur;
                }
                float p = Math.min(1f, (t - readStart) / Math.max(1f, readDur));
                tx = p < 0.88f ? -0.9f + 1.8f * (p / 0.88f) : 0.9f - 1.8f * ((p - 0.88f) / 0.12f);
                ty = -0.35f + readLine * 0.22f;
                squintTarget = 0.9f;
                break;
            case WRITING:
                // looking down at the work, stepping along it
                if (t >= nextLook) {
                    nextLook = t + 380 + random.nextInt(520);
                    tx = tx > 0.7f ? -0.7f : tx + 0.25f + random.nextFloat() * 0.15f;
                    ty = 0.55f + random.nextFloat() * 0.15f;
                }
                squintTarget = 0.85f;
                break;
            case REPLYING:
                // talking: looking at you, a smile in the eyes now and then, a little nod with the words
                if (t >= nextLook) {
                    nextLook = t + 900 + random.nextInt(1600);
                    tx = (random.nextFloat() - 0.5f) * 0.5f;
                    smiling = !smiling && random.nextFloat() < 0.55f;
                }
                ty = -0.1f + 0.12f * (float) Math.sin(sec * 4.3) * (float) Math.max(0, Math.sin(sec * 0.9));
                squintTarget = 1f;
                break;
            case THINKING:
            default:
                // glancing up and about, resting a while in between, now and then narrowing in thought
                if (t >= nextLook) {
                    nextLook = t + 700 + random.nextInt(2300);
                    float a = random.nextFloat();
                    if (a < 0.35f) {
                        tx = -0.6f - random.nextFloat() * 0.3f;
                        ty = -0.6f;
                    } else if (a < 0.7f) {
                        tx = 0.6f + random.nextFloat() * 0.3f;
                        ty = -0.6f;
                    } else if (a < 0.85f) {
                        tx = (random.nextFloat() - 0.5f) * 0.4f;
                        ty = -0.8f;
                    } else {
                        tx = 0;
                        ty = 0;
                    }
                    squintTarget = random.nextFloat() < 0.25f ? 0.72f : 1f;
                }
                break;
        }
        // a critically damped spring: the eyes glide to where they look, no jumps, no overshoot; it follows the real
        // time since the last frame, so a slow device moves them at the same speed in fewer frames
        float elapsed = lastStep == 0 ? 1f / 60f : Math.min(0.25f, (t - lastStep) / 1000f);
        lastStep = t;
        float k = mood == Mood.WORKING || mood == Mood.READING ? 120f : 70f, d = 2f * (float) Math.sqrt(k);
        for (float done = 0; done < elapsed; done += 1f / 120f) {
            float dt = Math.min(1f / 120f, elapsed - done);
            vx += (k * (tx - gx) - d * vx) * dt;
            vy += (k * (ty - gy) - d * vy) * dt;
            gx += vx * dt;
            gy += vy * dt;
            squint += (squintTarget - squint) * Math.min(1f, 5f * dt);
        }
    }

    private long lastStep;
    private boolean smiling;

    private int readLine;
    private long readStart;
    private float readDur = 1;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }
}
