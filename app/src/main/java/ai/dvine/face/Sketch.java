package ai.dvine.face;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * An explanation drawing. Strokes live in a -1..1 box and each starts at its
 * own time, so the stage can draw them line by line like a whiteboard video.
 */
public final class Sketch {
    public enum Kind {
        LINE,
        CIRCLE,
        ARROW,
        LABEL,
        DOT
    }

    public static final class Stroke {
        public final Kind kind;
        /** LINE/ARROW: x,y pairs. CIRCLE/DOT: cx, cy, r. LABEL: x, y. */
        public final float[] pts;
        public final String text;
        /** Seconds after the sketch starts. */
        public final float start;
        public final float duration;

        Stroke(Kind kind, float[] pts, String text, float start, float duration) {
            this.kind = kind;
            this.pts = pts;
            this.text = text;
            this.start = start;
            this.duration = duration;
        }
    }

    public final String title;
    public final List<Stroke> strokes;
    public final float length;

    private Sketch(String title, List<Stroke> strokes) {
        this.title = title;
        this.strokes = Collections.unmodifiableList(strokes);
        float end = 0f;
        for (Stroke s : strokes) {
            end = Math.max(end, s.start + s.duration);
        }
        this.length = end;
    }

    /** Builds strokes in order; each one starts after the previous. */
    public static final class Builder {
        private final String title;
        private final List<Stroke> strokes = new ArrayList<Stroke>();
        private float t;

        public Builder(String title) {
            this.title = title;
        }

        public Builder line(float duration, float... pts) {
            strokes.add(new Stroke(Kind.LINE, pts, null, t, duration));
            t += duration;
            return this;
        }

        public Builder arrow(float duration, float x0, float y0, float x1, float y1) {
            strokes.add(new Stroke(Kind.ARROW, new float[]{x0, y0, x1, y1}, null, t, duration));
            t += duration;
            return this;
        }

        public Builder circle(float duration, float cx, float cy, float r) {
            strokes.add(new Stroke(Kind.CIRCLE, new float[]{cx, cy, r}, null, t, duration));
            t += duration;
            return this;
        }

        public Builder dot(float cx, float cy, float r) {
            strokes.add(new Stroke(Kind.DOT, new float[]{cx, cy, r}, null, t, 0.12f));
            t += 0.12f;
            return this;
        }

        public Builder label(float x, float y, String text) {
            strokes.add(new Stroke(Kind.LABEL, new float[]{x, y}, text, t, 0.3f));
            t += 0.3f;
            return this;
        }

        public Builder pause(float seconds) {
            t += seconds;
            return this;
        }

        public Sketch build() {
            return new Sketch(title, strokes);
        }
    }

    // ------------------------------------------------------------ library

    /** A drawing for "draw a …" or "explain …", or null when nothing fits. */
    public static Sketch forRequest(String lower) {
        if (lower == null) {
            return null;
        }
        String t = lower.toLowerCase(Locale.US);
        if (!(t.startsWith("draw") || t.startsWith("explain") || t.startsWith("show me") || t.startsWith("illustrate"))) {
            return null;
        }
        java.util.regex.Matcher add = java.util.regex.Pattern.compile("(\\d{1,2}) (?:plus|\\+|and) (\\d{1,2})").matcher(t);
        if (add.find()) {
            int a = Integer.parseInt(add.group(1));
            int b = Integer.parseInt(add.group(2));
            if (a >= 0 && b >= 0 && a + b <= 20) {
                return addition(a, b);
            }
        }
        if (t.contains("half")) return fraction(2, "one half");
        if (t.contains("quarter")) return fraction(4, "one quarter");
        if (t.contains("third")) return fraction(3, "one third");
        if (t.contains("solar system") || t.contains("planets")) return solarSystem();
        if (t.contains("steps") || t.contains("plan") || t.contains("timeline")) return steps();
        if (t.contains("triangle")) return polygon("triangle", 3, (float) (-Math.PI / 2));
        if (t.contains("square")) return polygon("square", 4, (float) (Math.PI / 4));
        if (t.contains("pentagon")) return polygon("pentagon", 5, (float) (-Math.PI / 2));
        if (t.contains("hexagon")) return polygon("hexagon", 6, 0f);
        if (t.contains("circle")) return new Builder("circle").circle(1.2f, 0f, 0f, 0.7f).build();
        if (t.contains("star")) return star();
        if (t.contains("heart")) return heart();
        if (t.contains("arrow")) return new Builder("arrow").arrow(0.9f, -0.7f, 0f, 0.7f, 0f).build();
        if (t.contains("water cycle")) return waterCycle();
        return null;
    }

    static Sketch addition(int a, int b) {
        Builder s = new Builder(a + " plus " + b);
        float r = 0.06f;
        for (int i = 0; i < a; i++) {
            s.dot(-0.85f + (i % 5) * 0.16f, 0.25f - (i / 5) * 0.16f, r);
        }
        s.label(-0.1f, 0.2f, "+");
        for (int i = 0; i < b; i++) {
            s.dot(0.15f + (i % 5) * 0.16f, 0.25f - (i / 5) * 0.16f, r);
        }
        s.pause(0.3f).line(0.5f, -0.9f, -0.25f, 0.9f, -0.25f);
        s.label(0f, -0.55f, "= " + (a + b));
        return s.build();
    }

    static Sketch fraction(int parts, String name) {
        Builder s = new Builder(name);
        s.circle(1.0f, 0f, 0f, 0.7f);
        for (int i = 0; i < parts; i++) {
            double ang = Math.PI / 2 + i * 2 * Math.PI / parts;
            s.line(0.35f, 0f, 0f, 0.7f * (float) Math.cos(ang), 0.7f * (float) Math.sin(ang));
        }
        double mid = Math.PI / 2 + Math.PI / parts;
        s.label(0.35f * (float) Math.cos(mid), 0.35f * (float) Math.sin(mid), "1/" + parts);
        return s.build();
    }

    static Sketch polygon(String name, int n, float rot) {
        float[] pts = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = rot + i * 2 * Math.PI / n;
            pts[i * 2] = 0.7f * (float) Math.cos(a);
            pts[i * 2 + 1] = 0.7f * (float) Math.sin(a);
        }
        return new Builder(name).line(0.4f * n, pts).label(0f, -0.95f, n + " sides").build();
    }

    static Sketch star() {
        float[] pts = new float[11 * 2];
        for (int i = 0; i <= 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? 0.75f : 0.3f;
            pts[i * 2] = r * (float) Math.cos(a);
            pts[i * 2 + 1] = r * (float) Math.sin(a);
        }
        return new Builder("star").line(1.6f, pts).build();
    }

    static Sketch heart() {
        int n = 40;
        float[] pts = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double t = i * 2 * Math.PI / n;
            float x = (float) (16 * Math.pow(Math.sin(t), 3));
            float y = (float) (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
            pts[i * 2] = x / 22f;
            pts[i * 2 + 1] = y / 22f + 0.05f;
        }
        return new Builder("heart").line(1.6f, pts).build();
    }

    static Sketch solarSystem() {
        Builder s = new Builder("solar system");
        s.circle(0.5f, 0f, 0f, 0.1f).label(0f, -0.2f, "Sun");
        String[] names = {"Mercury", "Venus", "Earth", "Mars"};
        for (int i = 0; i < names.length; i++) {
            float r = 0.25f + i * 0.17f;
            s.circle(0.6f, 0f, 0f, r);
            double a = 0.6 + i * 1.3;
            s.dot(r * (float) Math.cos(a), r * (float) Math.sin(a), 0.035f);
        }
        s.label(0f, 0.95f, "Four inner planets");
        return s.build();
    }

    static Sketch steps() {
        Builder s = new Builder("steps");
        s.line(0.8f, -0.85f, 0f, 0.85f, 0f);
        String[] labels = {"Goal", "Plan", "Check", "Do"};
        for (int i = 0; i < 4; i++) {
            float x = -0.75f + i * 0.5f;
            s.circle(0.3f, x, 0f, 0.07f).label(x, -0.22f, labels[i]);
        }
        s.arrow(0.4f, 0.55f, 0.25f, 0.85f, 0.25f);
        return s.build();
    }

    static Sketch waterCycle() {
        Builder s = new Builder("water cycle");
        s.line(0.6f, -0.9f, -0.6f, 0.9f, -0.6f).label(-0.5f, -0.8f, "sea");
        s.arrow(0.5f, -0.5f, -0.5f, -0.5f, 0.4f).label(-0.75f, 0f, "rises");
        s.circle(0.4f, 0f, 0.6f, 0.18f).label(0f, 0.9f, "cloud");
        s.arrow(0.5f, 0.5f, 0.4f, 0.5f, -0.5f).label(0.78f, 0f, "rain");
        return s.build();
    }
}
