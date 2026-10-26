package ai.dvine.human;

/**
 * The body-part registry: a stable id, a dotted key and a spoken name for
 * every touchable region. Ids are stored per vertex in the assets, so they
 * never change once shipped; new regions are only ever appended.
 */
public final class Regions {
    private Regions() {
    }

    public static final String[][] TABLE = {
            {"head.scalp", "head"},            // 0
            {"head.forehead", "forehead"},     // 1
            {"face.eye.left", "left eye"},     // 2
            {"face.eye.right", "right eye"},   // 3
            {"face.cheek.left", "left cheek"}, // 4
            {"face.cheek.right", "right cheek"}, // 5
            {"face.nose", "nose"},             // 6
            {"face.lips", "lips"},             // 7
            {"face.chin", "chin"},             // 8
            {"face.jaw", "jaw"},               // 9
            {"head.ear.left", "left ear"},     // 10
            {"head.ear.right", "right ear"},   // 11
            {"body.neck", "neck"},             // 12
            {"head.hair", "hair"},             // 13
            {"face.teeth", "teeth"},           // 14
            {"body.chest", "chest"},           // 15
            {"body.back", "back"},             // 16
            {"body.belly", "belly"},           // 17
            {"body.hips", "hips"},             // 18
            {"body.shoulder.left", "left shoulder"},   // 19
            {"body.shoulder.right", "right shoulder"}, // 20
            {"arm.upper.left", "left upper arm"},      // 21
            {"arm.upper.right", "right upper arm"},    // 22
            {"arm.forearm.left", "left forearm"},      // 23
            {"arm.forearm.right", "right forearm"},    // 24
            {"hand.palm.left", "left hand"},           // 25
            {"hand.palm.right", "right hand"},         // 26
            {"hand.thumb.left", "left thumb"},         // 27
            {"hand.thumb.right", "right thumb"},       // 28
            {"hand.index.left", "left index finger"},  // 29
            {"hand.index.right", "right index finger"}, // 30
            {"hand.middle.left", "left middle finger"}, // 31
            {"hand.middle.right", "right middle finger"}, // 32
            {"hand.ring.left", "left ring finger"},    // 33
            {"hand.ring.right", "right ring finger"},  // 34
            {"hand.little.left", "left little finger"}, // 35
            {"hand.little.right", "right little finger"}, // 36
            {"leg.thigh.left", "left thigh"},          // 37
            {"leg.thigh.right", "right thigh"},        // 38
            {"leg.knee.left", "left knee"},            // 39
            {"leg.knee.right", "right knee"},          // 40
            {"leg.shin.left", "left shin"},            // 41
            {"leg.shin.right", "right shin"},          // 42
            {"foot.left", "left foot"},                // 43
            {"foot.right", "right foot"},              // 44
    };

    public static final int SCALP = 0, FOREHEAD = 1, EYE_L = 2, EYE_R = 3, CHEEK_L = 4, CHEEK_R = 5, NOSE = 6, LIPS = 7,
            CHIN = 8, JAW = 9, EAR_L = 10, EAR_R = 11, NECK = 12, HAIR = 13, TEETH = 14, CHEST = 15, BACK = 16,
            BELLY = 17, HIPS = 18, SHOULDER_L = 19, SHOULDER_R = 20, UPPER_ARM_L = 21, UPPER_ARM_R = 22,
            FOREARM_L = 23, FOREARM_R = 24, PALM_L = 25, PALM_R = 26, THUMB_L = 27, THUMB_R = 28, INDEX_L = 29,
            INDEX_R = 30, MIDDLE_L = 31, MIDDLE_R = 32, RING_L = 33, RING_R = 34, LITTLE_L = 35, LITTLE_R = 36,
            THIGH_L = 37, THIGH_R = 38, KNEE_L = 39, KNEE_R = 40, SHIN_L = 41, SHIN_R = 42, FOOT_L = 43, FOOT_R = 44;

    public static final int COUNT = TABLE.length;

    public static String key(int id) {
        return id >= 0 && id < COUNT ? TABLE[id][0] : null;
    }

    public static String spoken(int id) {
        return id >= 0 && id < COUNT ? TABLE[id][1] : null;
    }

    public static boolean isFace(int id) {
        return id >= FOREHEAD && id <= TEETH && id != NECK && id != HAIR || id == SCALP || id == HAIR;
    }

    /** The persona's left is +x (the viewer's right). Mirror a left region to its right twin and back. */
    public static int mirror(int id) {
        String k = key(id);
        if (k == null) {
            return id;
        }
        String m = k.endsWith(".left") ? k.substring(0, k.length() - 5) + ".right"
                : k.endsWith(".right") ? k.substring(0, k.length() - 6) + ".left" : k;
        for (int i = 0; i < COUNT; i++) {
            if (TABLE[i][0].equals(m)) {
                return i;
            }
        }
        return id;
    }
}
