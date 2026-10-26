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

    /** Default picks: different voices for Obi and Ivie, matching gender when the engine says. */
    public static String[] pick(List<Candidate> ranked) {
        if (ranked.isEmpty()) {
            return new String[]{null, null};
        }
        int best = score(ranked.get(0));
        String male = null;
        String female = null;
        for (Candidate c : ranked) {
            if (score(c) != best) {
                continue;
            }
            if (male == null && "male".equals(c.gender)) male = c.name;
            if (female == null && "female".equals(c.gender)) female = c.name;
        }
        String obi = male != null ? male : ranked.get(0).name;
        String ivie = female;
        if (ivie == null) {
            for (Candidate c : ranked) {
                if (!c.name.equals(obi)) {
                    ivie = c.name;
                    break;
                }
            }
        }
        if (ivie == null) {
            ivie = obi;
        }
        return new String[]{obi, ivie};
    }

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
