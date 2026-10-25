package ai.dvine.face;

import java.util.Locale;

/** Maps a server task cue onto the one face that should be on screen. */
public final class FaceEvents {
    public enum Who {
        OBI,
        IVIE,
        SAME
    }

    private FaceEvents() {
    }

    public static FaceMode modeFor(String state, String cue) {
        if (cue != null) {
            String c = cue.toUpperCase(Locale.US);
            if ("THINK".equals(c)) {
                return FaceMode.THINKING;
            }
            if ("SAY".equals(c)) {
                return FaceMode.SPEAKING;
            }
        }
        if (state == null) {
            return FaceMode.CONTENT;
        }
        String s = state.toUpperCase(Locale.US);
        if ("RECEIVED".equals(s) || "AUTHORIZED".equals(s) || "LISTENING".equals(s) || "VOICE".equals(s)) {
            return FaceMode.LISTENING;
        }
        if ("UNDERSTOOD".equals(s) || "PLAN_CREATED".equals(s) || "POLICY_CHECK".equals(s)
                || "QUEUED".equals(s) || "EXECUTING".equals(s) || "VERIFYING".equals(s)
                || "THINKING".equals(s)) {
            return FaceMode.THINKING;
        }
        if ("SPEAKING".equals(s) || "TTS".equals(s)) {
            return FaceMode.SPEAKING;
        }
        if ("AWAITING_APPROVAL".equals(s) || "GATE".equals(s)) {
            return FaceMode.APPROVAL;
        }
        if ("COMPLETED".equals(s) || "DONE".equals(s)) {
            return FaceMode.DONE;
        }
        if ("FAILED".equals(s) || "ERROR".equals(s) || "CANCELLED".equals(s) || "EXPIRED".equals(s)) {
            return FaceMode.ERROR;
        }
        return FaceMode.CONTENT;
    }

    public static Who who(String state, String agent) {
        if (agent != null) {
            String a = agent.toLowerCase(Locale.US).trim();
            if ("ivie".equals(a)) {
                return Who.IVIE;
            }
            if ("obi".equals(a)) {
                return Who.OBI;
            }
        }
        if (state != null) {
            String s = state.toUpperCase(Locale.US);
            if ("AWAITING_APPROVAL".equals(s) || "GATE".equals(s)) {
                return Who.IVIE;
            }
        }
        return Who.SAME;
    }
}
