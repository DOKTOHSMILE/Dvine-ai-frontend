package ai.dvine.stage;

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
        DOT,
        /** A solid ball of dots (cx, cy, r): drawn as a shaded sphere, not an outline. */
        BALL
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

        public Builder ball(float duration, float cx, float cy, float r) {
            strokes.add(new Stroke(Kind.BALL, new float[]{cx, cy, r}, null, t, duration));
            t += duration;
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
        if (t.contains("house") || t.contains("home")) return house();
        if (t.contains("tree")) return tree();
        if (t.contains("sun") && !t.contains("sunday")) return sun();
        if (t.contains("flower")) return flower();
        if (t.contains("person") || t.contains("man") || t.contains("woman") || t.contains("people")) return person();
        if (t.contains("car")) return car();
        if (t.contains("fish")) return fish();
        if (t.contains("mountain")) return mountains();
        if (t.contains("boat") || t.contains("ship")) return boat();
        if (t.contains("moon")) return moon();
        if (t.contains("eye")) return eye();
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
        s.ball(0.5f, 0f, 0f, 0.11f).label(0f, -0.2f, "Sun");
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

    static Sketch house() {
        return new Builder("house")
                .line(0.9f, -0.6f, -0.7f, -0.6f, 0.1f, 0.6f, 0.1f, 0.6f, -0.7f, -0.6f, -0.7f)
                .line(0.6f, -0.72f, 0.1f, 0f, 0.7f, 0.72f, 0.1f)
                .line(0.5f, -0.15f, -0.7f, -0.15f, -0.25f, 0.15f, -0.25f, 0.15f, -0.7f)
                .line(0.4f, 0.3f, -0.15f, 0.5f, -0.15f, 0.5f, 0.05f, 0.3f, 0.05f, 0.3f, -0.15f)
                .label(0f, -0.92f, "walls, roof, door, window").build();
    }

    static Sketch tree() {
        Builder s = new Builder("tree");
        s.line(0.5f, -0.08f, -0.8f, -0.08f, -0.1f).line(0.5f, 0.08f, -0.8f, 0.08f, -0.1f);
        s.ball(0.6f, 0f, 0.3f, 0.36f).ball(0.5f, -0.32f, 0.08f, 0.25f).ball(0.5f, 0.32f, 0.08f, 0.25f);
        s.line(0.3f, -0.6f, -0.8f, 0.6f, -0.8f).label(0f, -0.95f, "roots, trunk, leaves");
        return s.build();
    }

    static Sketch sun() {
        Builder s = new Builder("sun").ball(0.7f, 0f, 0f, 0.3f);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            s.line(0.12f, 0.4f * (float) Math.cos(a), 0.4f * (float) Math.sin(a), 0.65f * (float) Math.cos(a), 0.65f * (float) Math.sin(a));
        }
        return s.build();
    }

    static Sketch flower() {
        Builder s = new Builder("flower");
        s.line(0.5f, 0f, -0.85f, 0f, 0.1f).line(0.3f, 0f, -0.45f, 0.3f, -0.25f);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            s.circle(0.25f, 0.2f * (float) Math.cos(a), 0.3f + 0.2f * (float) Math.sin(a), 0.13f);
        }
        return s.ball(0.3f, 0f, 0.3f, 0.09f).build();
    }

    static Sketch person() {
        return new Builder("person").ball(0.5f, 0f, 0.6f, 0.17f)
                .line(0.4f, 0f, 0.42f, 0f, -0.2f)
                .line(0.4f, -0.4f, 0.2f, 0f, 0.3f, 0.4f, 0.2f)
                .line(0.4f, -0.3f, -0.8f, 0f, -0.2f, 0.3f, -0.8f).build();
    }

    static Sketch car() {
        return new Builder("car")
                .line(1.0f, -0.8f, -0.3f, -0.8f, 0f, -0.45f, 0.05f, -0.25f, 0.35f, 0.3f, 0.35f, 0.5f, 0.05f, 0.8f, 0f, 0.8f, -0.3f, -0.8f, -0.3f)
                .circle(0.4f, -0.45f, -0.32f, 0.16f).circle(0.4f, 0.45f, -0.32f, 0.16f)
                .line(0.3f, 0.02f, 0.35f, 0.02f, 0.05f).build();
    }

    static Sketch fish() {
        return new Builder("fish")
                .line(1.0f, -0.6f, 0f, -0.2f, 0.3f, 0.35f, 0.15f, 0.5f, 0f, 0.35f, -0.15f, -0.2f, -0.3f, -0.6f, 0f)
                .line(0.5f, 0.5f, 0f, 0.85f, 0.3f, 0.85f, -0.3f, 0.5f, 0f)
                .dot(-0.35f, 0.07f, 0.04f).build();
    }

    static Sketch mountains() {
        return new Builder("mountains")
                .line(1.2f, -0.95f, -0.6f, -0.45f, 0.45f, -0.1f, -0.1f, 0.3f, 0.6f, 0.95f, -0.6f)
                .line(0.4f, -0.58f, 0.2f, -0.45f, 0.45f, -0.32f, 0.2f)
                .ball(0.4f, 0.65f, 0.7f, 0.12f).build();
    }

    static Sketch boat() {
        return new Builder("boat")
                .line(0.7f, -0.7f, -0.2f, 0.7f, -0.2f, 0.5f, -0.5f, -0.5f, -0.5f, -0.7f, -0.2f)
                .line(0.4f, 0f, -0.2f, 0f, 0.75f).line(0.5f, 0f, 0.75f, 0.5f, -0.1f, 0f, -0.1f)
                .line(0.6f, -0.9f, -0.7f, -0.5f, -0.62f, 0f, -0.7f, 0.5f, -0.62f, 0.9f, -0.7f).build();
    }

    static Sketch moon() {
        int n = 24;
        float[] pts = new float[(n * 2 + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI / 2 + i * Math.PI / n;
            pts[i * 2] = 0.6f * (float) Math.cos(a);
            pts[i * 2 + 1] = 0.6f * (float) Math.sin(a);
        }
        for (int i = 1; i <= n; i++) {
            double a = -Math.PI / 2 + i * Math.PI / n;
            pts[(n + i) * 2] = -0.25f - 0.35f * (float) Math.cos(a) * -1f;
            pts[(n + i) * 2 + 1] = -0.6f * (float) Math.sin(a);
        }
        return new Builder("moon").line(1.4f, pts).dot(0.5f, 0.5f, 0.03f).dot(0.7f, 0.1f, 0.025f).build();
    }

    static Sketch eye() {
        Builder s = new Builder("eye");
        s.line(0.6f, -0.8f, 0f, -0.4f, 0.35f, 0f, 0.45f, 0.4f, 0.35f, 0.8f, 0f);
        s.line(0.6f, -0.8f, 0f, -0.4f, -0.3f, 0f, -0.38f, 0.4f, -0.3f, 0.8f, 0f);
        s.circle(0.5f, 0f, 0.03f, 0.28f).ball(0.3f, 0f, 0.03f, 0.12f);
        return s.label(0f, -0.75f, "lid, iris, pupil").build();
    }

    static Sketch waterCycle() {
        Builder s = new Builder("water cycle");
        s.line(0.6f, -0.9f, -0.6f, 0.9f, -0.6f).label(-0.5f, -0.8f, "sea");
        s.arrow(0.5f, -0.5f, -0.5f, -0.5f, 0.4f).label(-0.75f, 0f, "rises");
        s.ball(0.3f, -0.1f, 0.6f, 0.14f).ball(0.3f, 0.08f, 0.64f, 0.17f).ball(0.2f, 0.24f, 0.58f, 0.12f).label(0f, 0.9f, "cloud");
        s.arrow(0.5f, 0.5f, 0.4f, 0.5f, -0.5f).label(0.78f, 0f, "rain");
        return s.build();
    }
}
