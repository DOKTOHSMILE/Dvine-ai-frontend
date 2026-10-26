package ai.dvine.human.face;

import java.util.Locale;

import ai.dvine.face.FaceRig;
import ai.dvine.human.HumanInstance;

/**
 * Turns the persona's face state into blend-shape weights on the mesh: the
 * routine (breathing jaw, smiles, brow kicks, blinks, glances) from
 * {@link FaceRig}, a requested expression (smile, frown, surprise,
 * attention, sad, angry) that eases in and out, and speech visemes from the
 * word being spoken. Speech articulation wins over expression on the mouth,
 * so words stay readable while smiling.
 */
public final class FaceDrive {
    public static final String[] EXPRESSIONS = {"smile", "frown", "surprise", "attention", "sad", "angry"};

    public final Visemes speech = new Visemes();
    private String expression;
    private float level;
    private float until = -1f;
    private float last = -1f;

    /** Shows an expression for a few seconds (null or unknown clears it). */
    public void setExpression(String name, float seconds, float time) {
        expression = known(name) ? name : null;
        until = time + seconds;
    }

    public String expression() {
        return level > 0.05f ? expression : null;
    }

    public static boolean known(String name) {
        if (name == null) {
            return false;
        }
        for (String e : EXPRESSIONS) {
            if (e.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Which expression a request names ("smile for me", "look surprised"…), or null. */
    public static String expressionFor(String text) {
        String t = text.toLowerCase(Locale.US);
        if (t.matches(".*\\b(smile|grin)\\b.*")) return "smile";
        if (t.matches(".*\\b(frown|look (serious|stern))\\b.*")) return "frown";
        if (t.matches(".*\\b(surprised?|shocked|amazed)\\b.*")) return "surprise";
        if (t.matches(".*\\b(pay attention|look attentive|attention|focus)\\b.*")) return "attention";
        if (t.matches(".*\\b(sad|unhappy)\\b.*")) return "sad";
        if (t.matches(".*\\b(angry|mad|cross)\\b.*")) return "angry";
        return null;
    }

    public void apply(HumanInstance in, FaceRig rig, float time) {
        float dt = last < 0f ? 0f : Math.min(0.1f, time - last);
        last = time;
        level += ((time < until && expression != null ? 1f : 0f) - level) * Math.min(1f, dt * 4f);
        String e = level > 0.01f ? expression : null;

        float smile = clamp((rig.smile.value - 0.35f) * 1.4f);
        float frown = clamp((0.28f - rig.smile.value) * 2.5f);
        float browUpL = clamp(rig.browL.value * 3f), browUpR = clamp(rig.browR.value * 3f);
        float browDown = clamp(-Math.min(rig.browL.value, rig.browR.value) * 4f);
        float wide = clamp((rig.wide.value - 0.36f) * 4f);
        float jaw = clamp((rig.jaw.value - 0.03f) * 3.2f);
        float round = 0f, lipsWide = 0f, press = 0f;
        if (e != null) {
            float k = level;
            switch (e) {
                case "smile":
                    smile = Math.max(smile, 0.9f * k);
                    break;
                case "frown":
                    frown = Math.max(frown, 0.9f * k);
                    browDown = Math.max(browDown, 0.7f * k);
                    smile *= 1f - k;
                    break;
                case "surprise":
                    browUpL = browUpR = Math.max(browUpL, k);
                    wide = Math.max(wide, k);
                    jaw = Math.max(jaw, 0.35f * k);
                    break;
                case "attention":
                    browUpL = browUpR = Math.max(browUpL, 0.35f * k);
                    wide = Math.max(wide, 0.5f * k);
                    break;
                case "sad":
                    frown = Math.max(frown, 0.6f * k);
                    browUpL = browUpR = Math.max(browUpL, 0.3f * k);
                    smile *= 1f - k;
                    break;
                case "angry":
                    browDown = Math.max(browDown, k);
                    press = Math.max(press, 0.6f * k);
                    smile *= 1f - k;
                    break;
                default:
                    break;
            }
        }
        if (speech.active(time)) {
            float[] v = speech.at(time);
            jaw = v[0];
            round = v[1];
            lipsWide = v[2];
            press = Math.max(press * 0.3f, v[3]);
            smile *= 0.6f; // keep the mouth shapes readable
        }
        in.setWeight("jawOpen", jaw);
        in.setWeight("smileL", smile);
        in.setWeight("smileR", smile);
        in.setWeight("frownL", frown);
        in.setWeight("frownR", frown);
        in.setWeight("browUpL", browUpL);
        in.setWeight("browUpR", browUpR);
        in.setWeight("browDown", browDown);
        in.setWeight("blinkL", rig.blink.value);
        in.setWeight("blinkR", rig.blink.value);
        in.setWeight("wideL", wide);
        in.setWeight("wideR", wide);
        in.setWeight("lipsRound", round);
        in.setWeight("lipsWide", lipsWide);
        in.setWeight("lipsPress", press);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
