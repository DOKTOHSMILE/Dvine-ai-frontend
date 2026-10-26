package ai.dvine.face;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Emoji gesture library for {@link WireHand}. 🖕 is deliberately absent. */
public final class Gestures {
    public enum Motion {
        NONE,
        WAVE,
        SHAKE,
        TAP,
        CLAP,
        SCRIBBLE
    }

    public static final class Gesture {
        public final String key;
        public final String name;
        public final WireHand.Pose pose = new WireHand.Pose();
        public final boolean twoHands;
        /** Horizontal offset of the hand(s) from centre, in head units. */
        public final float x;
        public final float y;
        public final Motion motion;

        Gesture(String key, String name, float[] curl, float spread, float roll, float pitch, float yaw,
                boolean twoHands, float x, float y, Motion motion) {
            this.key = key;
            this.name = name;
            System.arraycopy(curl, 0, pose.curl, 0, 5);
            pose.yaw[1] = 0.10f * spread;
            pose.yaw[2] = 0.02f * spread;
            pose.yaw[3] = -0.08f * spread;
            pose.yaw[4] = -0.18f * spread;
            pose.rollDeg = roll;
            pose.pitchDeg = pitch;
            pose.yawDeg = yaw;
            this.twoHands = twoHands;
            this.x = x;
            this.y = y;
            this.motion = motion;
        }

        Gesture yaws(float i, float m, float r, float p) {
            pose.yaw[1] = i;
            pose.yaw[2] = m;
            pose.yaw[3] = r;
            pose.yaw[4] = p;
            return this;
        }

        Gesture thumbOut(float v) {
            pose.thumbOut = v;
            return this;
        }
    }

    private static final Map<String, Gesture> BY_KEY = new LinkedHashMap<String, Gesture>();
    private static final Map<String, String> BY_WORDS = new LinkedHashMap<String, String>();

    private static final float[] OPEN = {0f, 0f, 0f, 0f, 0f};
    private static final float[] FIST = {0.75f, 1f, 1f, 1f, 1f};
    private static final float[] POINT = {0.75f, 0f, 1f, 1f, 1f};

    private static void add(Gesture g, String... words) {
        BY_KEY.put(strip(g.key), g);
        for (String w : words) {
            BY_WORDS.put(w, strip(g.key));
        }
    }

    private static Gesture g(String key, String name, float[] curl, float spread, float roll, float pitch, float yaw,
                             boolean two, float x, float y, Motion m) {
        return new Gesture(key, name, curl, spread, roll, pitch, yaw, two, x, y, m);
    }

    static {
        add(g("👍", "thumbs up", new float[]{0f, 1f, 1f, 1f, 1f}, 0f, 90f, 0f, 0f, false, 0.85f, -1.25f, Motion.NONE), "thumbs up", "thumb up");
        add(g("👎", "thumbs down", new float[]{0f, 1f, 1f, 1f, 1f}, 0f, -90f, 0f, 0f, false, 0.85f, -0.9f, Motion.NONE), "thumbs down", "thumb down");
        add(g("👋", "wave", OPEN, 1f, -10f, 0f, 0f, false, 0.9f, -1.3f, Motion.WAVE), "wave", "hello hand", "bye bye");
        add(g("🤚", "raised back of hand", OPEN, 0.2f, 0f, 0f, 180f, false, 0.85f, -1.35f, Motion.NONE), "back of hand");
        add(g("🖐", "hand with fingers splayed", OPEN, 1.6f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE), "splayed fingers", "five");
        add(g("✋", "raised hand", OPEN, 0.3f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE), "raised hand", "stop", "high five");
        add(g("🖖", "vulcan salute", new float[]{0f, 0f, 0f, 0f, 0f}, 0f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE)
                .yaws(0.12f, 0.10f, -0.18f, -0.20f), "vulcan", "live long");
        add(g("👌", "ok hand", new float[]{0.55f, 0.62f, 0f, 0f, 0f}, 0.8f, 0f, 0f, 0f, false, 0.85f, -1.3f, Motion.NONE), "ok hand", "okay sign", "perfect");
        add(g("🤌", "pinched fingers", new float[]{0.5f, 0.55f, 0.55f, 0.55f, 0.55f}, -0.5f, 0f, -35f, 0f, false, 0.85f, -1.3f, Motion.TAP), "pinched fingers", "what do you want");
        add(g("🤏", "pinching hand", new float[]{0.3f, 0.5f, 1f, 1f, 1f}, 0f, -20f, 0f, 0f, false, 0.85f, -1.3f, Motion.NONE), "a little bit", "pinch");
        add(g("✌", "victory hand", new float[]{0.8f, 0f, 0f, 1f, 1f}, 1.8f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE), "peace", "victory", "two fingers");
        add(g("🤞", "crossed fingers", new float[]{0.8f, 0f, 0f, 1f, 1f}, 0f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE)
                .yaws(-0.05f, 0.16f, 0f, 0f), "fingers crossed", "crossed fingers", "good luck");
        add(g("🫰", "finger heart", new float[]{0.4f, 0.55f, 1f, 1f, 1f}, 0f, -15f, 0f, 0f, false, 0.85f, -1.3f, Motion.NONE), "finger heart");
        add(g("🤟", "love you gesture", new float[]{0f, 0f, 1f, 1f, 0f}, 0.6f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.NONE), "love you sign", "i love you sign");
        add(g("🤘", "rock on", new float[]{0.8f, 0f, 1f, 1f, 0f}, 0.6f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.SHAKE), "rock on", "horns");
        add(g("🤙", "call me hand", new float[]{0f, 1f, 1f, 1f, 0f}, 0.5f, -25f, 0f, 0f, false, 0.85f, -1.3f, Motion.SHAKE), "call me", "hang loose", "shaka");
        add(g("👈", "pointing left", POINT, 0f, 90f, 0f, 0f, false, -0.7f, -1.2f, Motion.TAP), "point left");
        add(g("👉", "pointing right", POINT, 0f, -90f, 0f, 0f, false, 0.7f, -1.2f, Motion.TAP), "point right");
        add(g("👆", "pointing up", POINT, 0f, 0f, 0f, 0f, false, 0.85f, -1.35f, Motion.TAP), "point up");
        add(g("☝", "index pointing up", POINT, 0f, 0f, 0f, 180f, false, 0.85f, -1.35f, Motion.NONE), "one moment", "one", "attention");
        add(g("👇", "pointing down", POINT, 0f, 180f, 0f, 0f, false, 0.85f, -0.75f, Motion.TAP), "point down");
        add(g("🫵", "pointing at you", POINT, 0f, 0f, -85f, 0f, false, 0.6f, -1.25f, Motion.TAP), "point at me", "you");
        add(g("✊", "raised fist", FIST, 0f, 0f, 0f, 180f, false, 0.85f, -1.35f, Motion.NONE), "raised fist", "solidarity", "power");
        add(g("👊", "fist bump", FIST, 0f, 0f, -80f, 0f, false, 0.6f, -1.25f, Motion.TAP), "fist bump", "punch");
        add(g("🤛", "left fist", FIST, 0f, 90f, 0f, 0f, false, -0.6f, -1.2f, Motion.NONE), "left fist");
        add(g("🤜", "right fist", FIST, 0f, -90f, 0f, 0f, false, 0.6f, -1.2f, Motion.NONE), "right fist");
        add(g("🫳", "palm down", new float[]{0.1f, 0.1f, 0.1f, 0.1f, 0.1f}, 0.3f, 0f, 75f, 0f, false, 0.8f, -1.2f, Motion.NONE), "palm down", "calm down");
        add(g("🫴", "palm up", new float[]{0.1f, 0.15f, 0.15f, 0.15f, 0.15f}, 0.3f, 0f, -75f, 0f, false, 0.8f, -1.35f, Motion.NONE), "palm up", "here you go");
        add(g("🫱", "rightwards hand", OPEN, 0.2f, -90f, 0f, 0f, false, 0.6f, -1.2f, Motion.NONE), "rightwards hand");
        add(g("🫲", "leftwards hand", OPEN, 0.2f, 90f, 0f, 0f, false, -0.6f, -1.2f, Motion.NONE), "leftwards hand");
        add(g("🫸", "pushing right", OPEN, 0.2f, -15f, 0f, 0f, false, 0.75f, -1.3f, Motion.TAP), "push");
        add(g("🫷", "pushing left", OPEN, 0.2f, 15f, 0f, 0f, false, -0.75f, -1.3f, Motion.TAP), "push left");
        add(g("🙏", "folded hands", new float[]{0.2f, 0f, 0f, 0f, 0f}, 0f, 0f, 0f, 82f, true, 0.12f, -1.35f, Motion.NONE), "pray", "praying", "please", "thank you hands");
        add(g("🙌", "raising hands", OPEN, 0.8f, 0f, 0f, 0f, true, 0.75f, -0.95f, Motion.TAP), "celebrate", "hallelujah", "raise hands");
        add(g("👐", "open hands", OPEN, 1f, 25f, 0f, 30f, true, 0.85f, -1.3f, Motion.NONE), "open hands", "welcome");
        add(g("🤲", "palms up together", new float[]{0.25f, 0.3f, 0.3f, 0.3f, 0.3f}, 0f, 10f, -70f, 0f, true, 0.3f, -1.4f, Motion.NONE), "cupped hands", "receive", "comfort");
        add(g("🫶", "heart hands", new float[]{0.5f, 0.55f, 0.55f, 0.55f, 0.55f}, -0.3f, 40f, 0f, 0f, true, 0.22f, -1.3f, Motion.NONE), "heart hands", "love");
        add(g("🤝", "handshake", new float[]{0.3f, 0.45f, 0.45f, 0.45f, 0.45f}, 0f, 90f, 0f, 0f, true, 0.32f, -1.25f, Motion.SHAKE), "handshake", "deal", "agreed");
        add(g("✍", "writing hand", new float[]{0.4f, 0.45f, 0.5f, 0.9f, 0.9f}, 0f, -40f, -30f, 0f, false, 0.75f, -1.3f, Motion.SCRIBBLE), "writing", "write");
        add(g("🤳", "selfie", new float[]{0.5f, 0.6f, 0.6f, 0.6f, 0.6f}, 0f, -10f, -20f, 20f, false, 0.85f, -1.0f, Motion.NONE), "selfie");
        add(g("💅", "nail polish", new float[]{0.1f, 0.2f, 0.25f, 0.3f, 0.35f}, 0.4f, 35f, -20f, 0f, false, 0.85f, -1.3f, Motion.NONE), "nails", "unbothered");
        add(g("👏", "clapping", OPEN, 0.1f, 0f, 0f, 80f, true, 0.2f, -1.3f, Motion.CLAP), "clap", "applause", "well done");
    }

    private Gestures() {
    }

    public static Gesture get(String key) {
        return key == null ? null : BY_KEY.get(strip(key));
    }

    public static int count() {
        return BY_KEY.size();
    }

    /** Finds a gesture in user text: an emoji, or "show me a thumbs up". */
    public static String find(String text) {
        if (text == null) {
            return null;
        }
        String clean = strip(text);
        for (String k : BY_KEY.keySet()) {
            if (clean.contains(k)) {
                return k;
            }
        }
        String lower = text.toLowerCase(Locale.US);
        if (!(lower.contains("show me") || lower.contains("give me") || lower.contains("do a") || lower.contains("gesture")
                || lower.startsWith("make a") || lower.contains("sign"))) {
            return null;
        }
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : BY_WORDS.entrySet()) {
            if (lower.contains(e.getKey()) && e.getKey().length() > bestLen) {
                best = e.getValue();
                bestLen = e.getKey().length();
            }
        }
        return best;
    }

    /** Removes skin-tone modifiers and variation selectors so 👍🏽 matches 👍. */
    static String strip(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 0x1F3FB && cp <= 0x1F3FF) || cp == 0xFE0F || cp == 0x200D) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }
}
