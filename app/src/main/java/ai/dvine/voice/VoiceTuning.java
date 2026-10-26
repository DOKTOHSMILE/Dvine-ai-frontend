package ai.dvine.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How one persona sounds: which system voice, pitch and speed, plus the
 * softer inner-thought variant. Pure Java so the choices are testable; the
 * user tunes it by voice ("another voice", "speak slower", "deeper").
 */
public final class VoiceTuning {
    /** A system voice reduced to what the choice needs. */
    public static final class Candidate {
        public final String name;
        public final String country;
        public final boolean network;
        public final String gender; // "male", "female" or ""

        public Candidate(String name, String country, boolean network, String gender) {
            this.name = name;
            this.country = country == null ? "" : country;
            this.network = network;
            this.gender = gender == null ? "" : gender;
        }
    }

    public String voice;
    public float pitch;
    public float rate;

    public static VoiceTuning defaults(boolean ivie) {
        VoiceTuning t = new VoiceTuning();
        // close to natural: big pitch shifts make TTS sound robotic
        t.pitch = ivie ? 1.04f : 0.93f;
        t.rate = ivie ? 0.94f : 0.90f;
        return t;
    }

    /** Inner thoughts: same voice, a touch lower and slower. Volume is set separately. */
    public float thoughtPitch() {
        return clamp(pitch * 0.96f, 0.6f, 1.6f);
    }

    public float thoughtRate() {
        return clamp(rate * 0.92f, 0.5f, 1.6f);
    }

    public static final float THOUGHT_VOLUME = 0.72f;

    /**
     * Applies VOICE_NEXT, VOICE_SLOWER, VOICE_FASTER, VOICE_LOWER,
     * VOICE_HIGHER or VOICE_RESET. Returns true when something changed.
     */
    public boolean apply(String command, boolean ivie, List<Candidate> ranked, String otherVoice) {
        switch (command) {
            case "VOICE_SLOWER": rate = clamp(rate - 0.08f, 0.6f, 1.4f); return true;
            case "VOICE_FASTER": rate = clamp(rate + 0.08f, 0.6f, 1.4f); return true;
            case "VOICE_LOWER": pitch = clamp(pitch - 0.06f, 0.7f, 1.3f); return true;
            case "VOICE_HIGHER": pitch = clamp(pitch + 0.06f, 0.7f, 1.3f); return true;
            case "VOICE_RESET": {
                VoiceTuning d = defaults(ivie);
                pitch = d.pitch;
                rate = d.rate;
                voice = null;
                return true;
            }
            case "VOICE_NEXT": {
                if (ranked.isEmpty()) {
                    return false;
                }
                // another voice of the persona's own gender first, if the phone has one
                String want = ivie ? "female" : "male";
                List<Candidate> same = new ArrayList<Candidate>();
                for (Candidate c : ranked) {
                    if (want.equals(c.gender)) {
                        same.add(c);
                    }
                }
                if (same.size() > 1 || same.size() == 1 && !same.get(0).name.equals(voice)) {
                    ranked = same;
                }
                int at = -1;
                for (int i = 0; i < ranked.size(); i++) {
                    if (ranked.get(i).name.equals(voice)) {
                        at = i;
                    }
                }
                for (int step = 1; step <= ranked.size(); step++) {
                    Candidate c = ranked.get((at + step + ranked.size()) % ranked.size());
                    if (!c.name.equals(otherVoice) || ranked.size() == 1) {
                        voice = c.name;
                        return true;
                    }
                }
                return false;
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
        List<Candidate> out = new ArrayList<Candidate>();
        for (Candidate c : all) {
            if (!c.network) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            out.addAll(all);
        }
        Collections.sort(out, (a, b) -> {
            int ra = score(a);
            int rb = score(b);
            return ra != rb ? ra - rb : a.name.compareTo(b.name);
        });
        return out;
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
     * than Ivie on a phone with only one kind of voice.
     */
    public float pitchFor(boolean ivie, String voiceGender) {
        if (!ivie && "female".equals(voiceGender)) {
            return clamp(pitch * 0.8f, 0.55f, 1.6f);
        }
        if (ivie && "male".equals(voiceGender)) {
            return clamp(pitch * 1.2f, 0.55f, 1.6f);
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
