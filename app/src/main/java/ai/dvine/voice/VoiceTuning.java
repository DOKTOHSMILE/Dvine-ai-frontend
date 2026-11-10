package ai.dvine.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What each persona's voice is: pitch, speed, and how a voice is
 * chosen for it. The two settings are the two people the app
 * speaks as: Obi, the deeper male voice, and Ivie, the lighter
 * female voice. {@link TalkBridge} uses them when it speaks and
 * reuses them for a spoken thought (lower, softer, slower).
 */
public final class VoiceTuning {

    /** A voice the phone can use for speaking. */
    public static final class Candidate {
        public final String name;
        public final String country;
        public final boolean network;
        /** "male", "female", or "" when the engine doesn't say. */
        public final String gender;

        /** The engine's own rating (Android's Voice.QUALITY_*: 500 very high, 400 high, 300 normal, 200 low). */
        public final int quality;

        public Candidate(String name, String country, boolean network, String gender) {
            this(name, country, network, gender, 300);
        }

        public Candidate(String name, String country, boolean network, String gender, int quality) {
            this.name = name;
            this.country = country;
            this.network = network;
            this.gender = gender == null ? "" : gender;
            this.quality = quality;
        }
    }

    /**
     * An inner thought is read in the same voice but more softly:
     * this much of the normal volume.
     */
    public static final float THOUGHT_VOLUME = 0.6f;

    /**
     * The sharp edges are taken off every pitch the app speaks at.
     * A system voice pushed above about 1.1 gets thin, whiny and
     * electronic; slightly below 0.8 it buzzes. Everything that
     * reaches {@link TalkBridge} is kept inside that calm middle,
     * so the voice keeps its character but loses the sharp,
     * artificial edge.
     */
    private static final float WARM_FLOOR = 0.78f;

    /**
     * How far above neutral a pitch may ever be lifted, and how
     * fast that lift fades as the request gets higher: above
     * neutral the requested lift is eased towards this ceiling
     * instead of being clamped flat, so a higher voice still
     * sounds higher — just softer and rounder, never reaching
     * the thin, whiny zone above about 1.08.
     */
    private static final float WARM_LIFT = 0.08f, WARM_FADE = 0.6f;

    /**
     * Ibiko's voice, when the robot voice is on: 10 % brighter
     * than Obi's own pitch and 5 % slower than his pace. The lift
     * reads as the bright little robot next to Obi's baritone and
     * stays under the 1.25 that the system voice answered with
     * when it went sharp and whiny; hurried syllables are what
     * make a voice sound like a machine, so the pace eases off
     * rather than up. Both stay far inside the calm middle
     * ({@link #warm} would ease anything above 1.08 back down).
     */
    public static final float ROBOT_BRIGHT = 1.10f, ROBOT_QUICK = 0.95f;

    /**
     * The pitch each persona speaks at. Defaults: Obi 0.80, the
     * deeper voice of the two; Ivie 0.97, the lighter one. Both
     * sit inside {@link #warm} so neither gets sharp or buzzy.
     */
    public float pitch = 1f;

    /** The speed of the voice: 1 is the engine's normal speed. */
    public float rate = 1f;

    /** The voice this persona speaks with, or null to let the app pick one. */
    public String voice;

    /** Same pitch, lower and slower, for a thought: not for long, not out loud. */
    public static final float THOUGHT_PITCH = 0.94f;
    /** Same pitch, lower and slower, for a thought: not out loud. */
    public static final float THOUGHT_RATE = 0.96f;

    /**
     * A new tuning with the same starting pitch and speed: two
     * steps to set up each persona in {@link TalkBridge}.
     */
    public VoiceTuning() {
    }

    /** The same tuning a copy can be made from. */
    public VoiceTuning(VoiceTuning from) {
        this.pitch = from.pitch;
        this.rate = from.rate;
        this.voice = from.voice;
    }

    /**
     * The starting pitch and speed: Obi 0.80, the deeper voice,
     * and Ivie 0.97, the lighter one — a natural pair, neither
     * sharp nor too low, so both sound like people talking.
     */
    public static VoiceTuning defaults(boolean ivie) {
        VoiceTuning t = new VoiceTuning();
        t.pitch = ivie ? 0.97f : 0.80f;
        t.rate = ivie ? 1.00f : 0.98f;
        return t;
    }

    /** How a spoken thought sounds: lower, slower and softer than a reply. */
    public float thoughtPitch() {
        // lower than the reply, but never down into the buzzy bottom
        return Math.max(pitch * THOUGHT_PITCH, Math.min(0.78f, pitch - 0.01f));
    }

    /** How a spoken thought sounds: lower, slower and softer than a reply. */
    public float thoughtRate() {
        return rate * THOUGHT_RATE;
    }

    /**
     * Puts a pitch in the calm middle of the voice engine's range:
     * away from the thin, sharp top and the buzzy bottom, where a
     * system voice stops sounding like a person. Every pitch spoken
     * by {@link TalkBridge} passes through this.
     * <p>
     * At or below neutral the pitch is passed through untouched
     * (bar the floor), so a deliberately deep voice stays deep.
     * Above neutral the requested lift is eased towards
     * {@link #WARM_LIFT} instead of being clamped flat: a higher
     * request still sounds higher every time, but the higher it
     * goes the more of the sharpness is taken off, and it never
     * reaches the thin, whiny zone.
     */
    public static float warm(float p) {
        if (p <= 1f) {
            return Math.max(WARM_FLOOR, p);
        }
        float lift = p - 1f;
        return 1f + WARM_LIFT * lift / (lift + WARM_FADE);
    }

    /**
     * A spoken command for this voice. The words the app
     * understands are "higher", "lower", "slower", "faster", "your
     * voice", "another voice" and "test". Returns true when the
     * command changed this tuning. A voice can also be set by
     * passing a name in {@code voice}.
     */
    public boolean apply(String command, boolean ivie, List<Candidate> ranked, String otherVoice) {
        if (command == null) {
            return false;
        }
        String c = command.trim().toUpperCase(Locale.US);
        if (c.startsWith("VOICE ")) {
            c = c.substring(6);
        }
        switch (c) {
            case "VOICE_HIGHER":
            case "HIGHER":
            case "UP":
                pitch = clamp(pitch + 0.08f, 0.55f, 1.6f);
                return true;
            case "VOICE_LOWER":
            case "LOWER":
            case "DEEPER":
            case "DOWN":
                pitch = clamp(pitch - 0.08f, 0.55f, 1.6f);
                return true;
            case "VOICE_SLOWER":
            case "SLOWER":
                rate = clamp(rate - 0.10f, 0.6f, 1.8f);
                return true;
            case "VOICE_FASTER":
            case "FASTER":
                rate = clamp(rate + 0.10f, 0.6f, 1.8f);
                return true;
            case "VOICE_TEST":
            case "TEST":
            case "TEST_VOICE":
                return true; // TalkBridge reads a short line when this comes back
            case "VOICE_RESET":
            case "RESET":
                VoiceTuning d = defaults(ivie);
                pitch = d.pitch;
                rate = d.rate;
                return true;
            case "VOICE_NEXT":
            case "ANOTHER_VOICE":
            case "NEXT_VOICE":
            case "CHANGE_VOICE": {
                // one more voice this persona can use, never the other persona's
                if (ranked == null || ranked.isEmpty()) {
                    return false;
                }
                List<String> names = new ArrayList<String>();
                for (Candidate c2 : ranked) {
                    if (!c2.name.equals(otherVoice)) {
                        names.add(c2.name);
                    }
                }
                if (names.isEmpty()) {
                    return false;
                }
                int i = voice == null ? -1 : names.indexOf(voice);
                voice = names.get((i + 1) % names.size());
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Offline English voices, best first: Nigerian English, then other African
     * English, then British, then any English.
     */
    public static List<Candidate> rank(List<Candidate> all) {
        return rank(all, false);
    }

    /**
     * The voices best first: the most natural (the engine's quality rating) first, then the accent (Nigerian, other
     * African, British, any English). {@code natural}: the online voices (the most human-sounding, they need the
     * internet) may be used too; otherwise only voices on the device.
     */
    public static List<Candidate> rank(List<Candidate> all, boolean natural) {
        List<Candidate> out = new ArrayList<Candidate>();
        for (Candidate c : all) {
            if (natural || !c.network) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            out.addAll(all);
        }
        Collections.sort(out, (a, b) -> {
            int qa = band(a.quality), qb = band(b.quality);
            if (qa != qb) {
                return qb - qa; // the more natural voice first
            }
            if (a.network != b.network) {
                return a.network ? -1 : 1; // the same rating: the online (neural) one sounds more human
            }
            int ra = score(a);
            int rb = score(b);
            return ra != rb ? ra - rb : a.name.compareTo(b.name);
        });
        return out;
    }

    /** Quality in bands, so a small difference in rating doesn't beat the accent: very high, high, the rest. */
    static int band(int quality) {
        return quality >= 500 ? 2 : quality >= 400 ? 1 : 0;
    }

    /**
     * Default picks: a male voice for Obi and a female voice for Ivie. Gender
     * comes first, then the accent ({@link #rank} order): a male American voice
     * beats a Nigerian voice of unknown gender for Obi. Only when the phone
     * has no voice of a gender does a persona borrow another, and then
     * {@link #pitchFor} shifts it toward the persona.
     */
    public static String[] pick(List<Candidate> ranked) {
        if (ranked.isEmpty()) {
            return new String[]{null, null};
        }
        String obi = first(ranked, "male", null);
        if (obi == null) obi = first(ranked, "", null);
        if (obi == null) obi = ranked.get(0).name;
        String ivie = first(ranked, "female", obi);
        if (ivie == null) ivie = first(ranked, "", obi);
        if (ivie == null) ivie = first(ranked, "male", obi);
        if (ivie == null) ivie = obi;
        return new String[]{obi, ivie};
    }

    private static String first(List<Candidate> ranked, String gender, String not) {
        for (Candidate c : ranked) {
            if (gender.equals(c.gender) && !c.name.equals(not)) {
                return c.name;
            }
        }
        return null;
    }

    /** Which gender has no voice on this phone ("male", "female"), or null when both do. */
    public static String missing(List<Candidate> ranked) {
        boolean male = false, female = false;
        for (Candidate c : ranked) {
            male |= "male".equals(c.gender);
            female |= "female".equals(c.gender);
        }
        return !male ? "male" : !female ? "female" : null;
    }

    /**
     * The pitch to speak with: the persona's own, shifted when the voice is
     * of the other gender (a borrowed voice), so Obi still sounds deeper
     * than Ivie on a phone with only one kind of voice. The borrowed shift
     * runs a little shorter now (0.85 / 1.15) because a big jump toward a
     * wrong-gender voice is where a system voice starts to sound synthetic:
     * the character stays, the harsh edge doesn't.
     * <p>
     * The result is a raw pitch, not the one spoken: every call site in
     * {@link TalkBridge} puts it through {@link #warm} exactly once (after
     * any brightening), so the tuning commands the user gives still move
     * the voice but nothing reaches the engine in the thin, whiny zone
     * above about 1.08 or the buzzy one below 0.78.
     */
    public float pitchFor(boolean ivie, String voiceGender) {
        if (!ivie && "female".equals(voiceGender)) {
            return clamp(pitch * 0.85f, 0.55f, 1.6f);
        }
        if (ivie && "male".equals(voiceGender)) {
            return clamp(pitch * 1.15f, 0.55f, 1.6f);
        }
        return pitch;
    }

    /**
     * The gender of a system voice. Engines rarely say it in the features, so
     * known voice names are read too: Google's three-letter voice codes
     * ("en-us-x-iom-local"), old Google names ("…#male_1-local") and Samsung's
     * ("en-US-SMTf00"). Unknown voices return "".
     */
    public static String genderOf(String name, java.util.Set<String> features) {
        if (features != null) {
            for (String f : features) {
                String l = f.toLowerCase(Locale.US);
                if (l.equals("gender=female") || l.equals("female")) return "female";
                if (l.equals("gender=male") || l.equals("male")) return "male";
            }
        }
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        if (n.contains("#female") || n.matches(".*\\bfemale\\b.*")) return "female";
        if (n.contains("#male") || n.matches(".*\\bmale\\b.*")) return "male";
        java.util.regex.Matcher smt = java.util.regex.Pattern.compile("smt([fm])\\d").matcher(n);
        if (smt.find()) {
            return smt.group(1).equals("f") ? "female" : "male";
        }
        java.util.regex.Matcher g = java.util.regex.Pattern.compile("^[a-z]{2,3}-[a-z]{2}-x-([a-z]{3})").matcher(n);
        if (g.find()) {
            String code = g.group(1);
            if (GOOGLE_FEMALE.contains(code)) return "female";
            if (GOOGLE_MALE.contains(code)) return "male";
        }
        return "";
    }

    /** Google TTS English voice codes by gender (US, GB, AU, IN). Others are left unknown rather than guessed. */
    static final java.util.Set<String> GOOGLE_FEMALE = new java.util.HashSet<String>(java.util.Arrays.asList(
            "sfg", "iob", "iog", "tpc", "tpf", "gba", "gbc", "gbg", "afh", "aua", "auc", "ahp", "cxx", "ene"));
    static final java.util.Set<String> GOOGLE_MALE = new java.util.HashSet<String>(java.util.Arrays.asList(
            "iol", "iom", "tpd", "gbb", "gbd", "rjs", "aub", "aud", "ena", "end"));

    static int score(Candidate c) {
        String country = c.country.toUpperCase(Locale.US);
        if ("NG".equals(country)) return 0;
        if ("GH".equals(country) || "KE".equals(country) || "ZA".equals(country) || "TZ".equals(country)) return 1;
        if ("GB".equals(country)) return 2;
        return 3;
    }

    /** Saved form "voice;pitch;rate", kept so stored voice settings survive updates. */
    public String serialize() {
        return String.format(Locale.US, "%s;%.2f;%.2f", voice == null ? "" : voice, pitch, rate);
    }

    public static VoiceTuning parse(String s, boolean ivie) {
        VoiceTuning t = defaults(ivie);
        if (s == null) {
            return t;
        }
        String[] p = s.split(";", -1);
        if (p.length != 3) {
            return t;
        }
        try {
            t.voice = p[0].isEmpty() ? null : p[0];
            t.pitch = clamp(Float.parseFloat(p[1]), 0.7f, 1.3f);
            t.rate = clamp(Float.parseFloat(p[2]), 0.6f, 1.4f);
        } catch (NumberFormatException bad) {
            return defaults(ivie);
        }
        return t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
