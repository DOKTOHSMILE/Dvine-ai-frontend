package ai.dvine.human.wrap;

import java.util.LinkedHashMap;
import java.util.Map;

import ai.dvine.face.nano.DotBody;
import ai.dvine.human.HumanInstance;

/**
 * The body's landmarks as it stands now, in the drawn body's space: the asset's named surface points (crown, eyes,
 * nose, mouth, chin, ears, toes, index tips …) and its joints (shoulders, elbows, wrists, hips, knees, ankles), named
 * as on a character sheet ("_l" is the person's own left, on +x). Pure Java.
 */
public final class BodyMarks {
    private BodyMarks() {
    }

    public static Map<String, float[]> of(HumanInstance h) {
        Map<String, float[]> m = new LinkedHashMap<String, float[]>();
        String[][] surface = {{"crown", "crown"}, {"chin", "chin"}, {"nose", "nose tip"}, {"eye_l", "eye left"}, {"eye_r", "eye right"},
                {"mouth_l", "mouth left"}, {"mouth_r", "mouth right"}, {"ear_l", "ear left"}, {"ear_r", "ear right"},
                {"neck", "neck front"}, {"chest", "chest"}, {"navel", "navel"}, {"index_l", "left index tip"}, {"index_r", "right index tip"},
                {"toe_l", "left toe"}, {"toe_r", "right toe"}};
        for (String[] s : surface) {
            int v = h.asset.landmark(s[1]);
            if (v >= 0 && v * 3 + 2 < h.pos.length) {
                m.put(s[0], new float[]{h.pos[v * 3], h.pos[v * 3 + 1], h.pos[v * 3 + 2]});
            }
        }
        int[][] joints = {{DotBody.ARM_L, 0}, {DotBody.ARM_R, 1}, {DotBody.FORE_L, 2}, {DotBody.FORE_R, 3}, {DotBody.HAND_L, 4},
                {DotBody.HAND_R, 5}, {DotBody.THIGH_L, 6}, {DotBody.THIGH_R, 7}, {DotBody.SHIN_L, 8}, {DotBody.SHIN_R, 9},
                {DotBody.FOOT_L, 10}, {DotBody.FOOT_R, 11}};
        String[] names = {"shoulder_l", "shoulder_r", "elbow_l", "elbow_r", "wrist_l", "wrist_r", "hip_l", "hip_r", "knee_l", "knee_r",
                "ankle_l", "ankle_r"};
        float[] j = h.rig.joint;
        for (int[] b : joints) {
            m.put(names[b[1]], new float[]{j[b[0] * 3], j[b[0] * 3 + 1], j[b[0] * 3 + 2]});
        }
        // the back of the head and the nape: the furthest-back skin at eye height and just above the chin
        float[] crown = m.get("crown"), chin = m.get("chin"), eye = m.get("eye_l");
        if (crown != null && chin != null && eye != null) {
            float hh = crown[1] - chin[1];
            float[] back = null, nape = null;
            int n = h.solidCount();
            for (int v = 0; v < n; v++) {
                float x = h.pos[v * 3], y = h.pos[v * 3 + 1], z = h.pos[v * 3 + 2];
                if (Math.abs(x - crown[0]) > hh * 0.12f) {
                    continue;
                }
                if (Math.abs(y - eye[1]) < hh * 0.08f && (back == null || z < back[2])) {
                    back = new float[]{x, y, z};
                }
                if (Math.abs(y - (chin[1] - hh * 0.1f)) < hh * 0.06f && z < crown[2] && (nape == null || z < nape[2])) {
                    nape = new float[]{x, y, z};
                }
            }
            if (back != null) {
                m.put("head_back", back);
            }
            if (nape != null) {
                m.put("nape", nape);
            }
        }
        return m;
    }
}
