package ai.dvine.render.field;

/**
 * Plans used only by the tests (the app writes every plan itself from what it finds online). Same line format as
 * the brain writes.
 */
final class TestPlans {
    private TestPlans() {
    }

    static SolidSpec of(String key) {
        switch (key) {
            case "cat":
                return SolidSpec.parse("body | oval | orange | middle centre | w46 h20 d20\n"
                + "head | sphere | orange | right of body, top | w18 h17 d17\n"
                + "left ear | cone | orange | above head, near | w6 h8 d5\n"
                + "right ear | cone | orange | above head, far | w6 h8 d5\n"
                + "left eye | sphere | green | right of head, top, near | w3 h3 d3\n"
                + "right eye | sphere | green | right of head, top, far | w3 h3 d3\n"
                + "nose | sphere | pink | right of head | w3 h2.5 d3\n"
                + "front leg | cylinder | orange | below body, right, near | w5 h14 d5\n"
                + "back leg | cylinder | orange | below body, left, near | w5 h14 d5\n"
                + "far front leg | cylinder | orange | below body, right, far | w5 h14 d5\n"
                + "far back leg | cylinder | orange | below body, left, far | w5 h14 d5\n"
                + "tail | cylinder | orange | left of body, top | w4 h24 d4", "cat");
            case "car":
                return SolidSpec.parse("body | box | red | middle centre | w70 h16 d32\n"
                + "cabin | box | red | above body | w38 h14 d28\n"
                + "window | box | sky | on cabin | w32 h9 d1\n"
                + "front wheel | disc | navy | below body, right, near | w15 h15 d5\n"
                + "back wheel | disc | navy | below body, left, near | w15 h15 d5\n"
                + "far front wheel | disc | navy | below body, right, far | w15 h15 d5\n"
                + "far back wheel | disc | navy | below body, left, far | w15 h15 d5\n"
                + "front hubcap | disc | silver | in front of front wheel | w7 h7 d1\n"
                + "back hubcap | disc | silver | in front of back wheel | w7 h7 d1\n"
                + "headlight | sphere | yellow | right of body, top, near | w3 h3 d5\n"
                + "taillight | sphere | red | left of body, top, near | w3 h3 d5\n"
                + "door | box | red | on body | w20 h12 d1", "car");
            case "dog":
                return SolidSpec.parse("body | oval | brown | middle centre | w50 h22 d22\n"
                + "head | sphere | brown | right of body, top | w19 h18 d17\n"
                + "snout | box | tan | right of head | w10 h8 d9\n"
                + "nose | sphere | navy | right of snout, top | w3 h3 d3\n"
                + "left ear | oval | brown | on head, near | w6 h12 d3\n"
                + "right ear | oval | brown | behind head | w6 h12 d3\n"
                + "left eye | sphere | navy | right of head, top, near | w3 h3 d3\n"
                + "right eye | sphere | navy | right of head, top, far | w3 h3 d3\n"
                + "front leg | cylinder | brown | below body, right, near | w6 h17 d6\n"
                + "back leg | cylinder | brown | below body, left, near | w6 h17 d6\n"
                + "far front leg | cylinder | brown | below body, right, far | w6 h17 d6\n"
                + "far back leg | cylinder | brown | below body, left, far | w6 h17 d6\n"
                + "tail | cylinder | brown | left of body, top | w4 h18 d4", "dog");
            case "bicycle":
                return SolidSpec.parse("frame | box | red | middle centre | w34 h3 d3\n"
                + "front wheel | ring | navy | below frame, right | w23 h23 d3\n"
                + "back wheel | ring | navy | below frame, left | w23 h23 d3\n"
                + "seat post | cylinder | grey | above frame, left | w2 h10 d2\n"
                + "seat | oval | navy | above seat post | w10 h3 d5\n"
                + "handlebar post | cylinder | grey | above frame, right | w2 h12 d2\n"
                + "handlebar | cylinder | grey | above handlebar post | forward w2 h2 d16\n"
                + "pedal | box | grey | below frame | w4 h2 d8", "bicycle");
            case "rocket":
                return SolidSpec.parse("body | cylinder | white | middle centre | w18 h56 d18\n"
                + "nose cone | cone | red | above body | w18 h20 d18\n"
                + "window | disc | sky | on body, top | w8 h8 d1\n"
                + "left fin | pyramid | red | left of body, bottom | w10 h16 d3\n"
                + "right fin | pyramid | red | right of body, bottom | w10 h16 d3\n"
                + "front fin | pyramid | red | in front of body, bottom | w3 h16 d10\n"
                + "engine | cone | grey | below body | w14 h8 d14\n"
                + "flame | cone | orange | below engine | w10 h14 d10", "rocket");
            case "cup":
                return SolidSpec.parse("body | cylinder | white | middle centre | w26 h30 d26\n"
                + "handle | ring | white | right of body | sideways w3 h16 d14\n"
                + "coffee | cylinder | brown | above body | w22 h1 d22", "cup");
            default:
                throw new IllegalArgumentException(key);
        }
    }
}
