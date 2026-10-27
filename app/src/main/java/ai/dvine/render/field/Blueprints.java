package ai.dvine.render.field;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hand-made 3D plans for the things people ask for most, in the part format the brain also writes
 * ({@link SolidSpec}): each part named the way people say it (ear, tail, front wheel, door, roof), so it draws
 * right every time and every part can be taken out, recoloured or resized by name. Animals and vehicles are
 * seen from the side, facing right; each has exact sizes (w, h, d in the 100-unit canvas). The brain plans
 * anything not here. Pure Java.
 */
public final class Blueprints {
    private Blueprints() {
    }

    private static final Map<String, String> PLANS = new LinkedHashMap<String, String>();
    private static final Map<String, String> ALSO = new LinkedHashMap<String, String>();

    static {
        put("cat", "body | oval | orange | middle centre | w46 h20 d20\n"
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
                + "tail | cylinder | orange | left of body, top | w4 h24 d4", "kitten", "kitty");
        put("dog", "body | oval | brown | middle centre | w50 h22 d22\n"
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
                + "tail | cylinder | brown | left of body, top | w4 h18 d4", "puppy", "doggy");
        put("horse", "body | oval | brown | middle centre | w52 h24 d22\n"
                + "neck | cylinder | brown | above body, right | w10 h22 d10\n"
                + "head | box | brown | above neck, right | w20 h9 d9\n"
                + "mane | box | navy | left of neck | w3 h22 d6\n"
                + "left ear | cone | brown | above head, left, near | w3 h5 d3\n"
                + "right ear | cone | brown | above head, left, far | w3 h5 d3\n"
                + "eye | sphere | navy | on head | w2.5 h2.5 d2\n"
                + "front leg | cylinder | brown | below body, right, near | w6 h30 d6\n"
                + "back leg | cylinder | brown | below body, left, near | w6 h30 d6\n"
                + "far front leg | cylinder | brown | below body, right, far | w6 h30 d6\n"
                + "far back leg | cylinder | brown | below body, left, far | w6 h30 d6\n"
                + "tail | cone | navy | left of body | w6 h24 d6", "pony");
        put("cow", "body | box | white | middle centre | w52 h26 d26\n"
                + "head | box | white | right of body, top | w15 h15 d14\n"
                + "muzzle | box | pink | right of head, bottom | w6 h8 d11\n"
                + "left horn | cone | beige | above head, near | w3 h7 d3\n"
                + "right horn | cone | beige | above head, far | w3 h7 d3\n"
                + "eye | sphere | navy | on head, top | w2.5 h2.5 d2\n"
                + "spot | disc | navy | on body | w14 h10 d1\n"
                + "udder | sphere | pink | below body | w9 h6 d9\n"
                + "front leg | cylinder | white | below body, right, near | w6 h22 d6\n"
                + "back leg | cylinder | white | below body, left, near | w6 h22 d6\n"
                + "far front leg | cylinder | white | below body, right, far | w6 h22 d6\n"
                + "far back leg | cylinder | white | below body, left, far | w6 h22 d6\n"
                + "tail | cylinder | white | left of body | w3 h20 d3");
        put("rabbit", "body | oval | grey | middle centre | w34 h28 d24\n"
                + "head | sphere | grey | above body, right | w18 h17 d16\n"
                + "left ear | oval | grey | above head, near | w5 h20 d3\n"
                + "right ear | oval | grey | above head, far | w5 h20 d3\n"
                + "eye | sphere | navy | right of head, top | w3 h3 d3\n"
                + "nose | sphere | pink | right of head | w2.5 h2.5 d2.5\n"
                + "front paw | oval | grey | below body, right | w9 h5 d8\n"
                + "back foot | oval | grey | below body, left | w18 h6 d10\n"
                + "tail | sphere | white | left of body | w8 h8 d8", "bunny", "hare");
        put("bird", "body | oval | sky | middle centre | w34 h22 d20\n"
                + "head | sphere | sky | above body, right | w15 h15 d14\n"
                + "beak | cone | orange | right of head | sideways w7 h4 d4\n"
                + "left eye | sphere | navy | on head, top | w2.5 h2.5 d2\n"
                + "left wing | oval | blue | in front of body, top | w24 h9 d4\n"
                + "right wing | oval | blue | behind body, top | w24 h9 d4\n"
                + "tail | box | blue | left of body, top | w16 h4 d10\n"
                + "left leg | cylinder | orange | below body, near | w2 h9 d2\n"
                + "right leg | cylinder | orange | below body, far | w2 h9 d2", "parrot", "sparrow", "eagle", "pigeon");
        put("fish", "body | oval | orange | middle centre | w44 h24 d14\n"
                + "tail | box | orange | left of body | w12 h20 d2\n"
                + "top fin | cone | gold | above body | w10 h9 d3\n"
                + "bottom fin | cone | gold | below body | w8 h6 d3\n"
                + "eye | sphere | white | right of body, top | w5 h5 d5\n"
                + "pupil | sphere | navy | in front of eye | w2.5 h2.5 d2", "goldfish");
        put("elephant", "body | oval | grey | middle centre | w58 h34 d30\n"
                + "head | sphere | grey | right of body, top | w24 h24 d22\n"
                + "trunk | cylinder | grey | below head, right | w6 h26 d6\n"
                + "left ear | disc | grey | in front of head | w18 h20 d2\n"
                + "right ear | disc | grey | behind head | w18 h20 d2\n"
                + "tusk | cone | white | right of head, bottom | sideways w9 h3 d3\n"
                + "eye | sphere | navy | right of head, top | w3 h3 d3\n"
                + "front leg | cylinder | grey | below body, right, near | w11 h24 d11\n"
                + "back leg | cylinder | grey | below body, left, near | w11 h24 d11\n"
                + "far front leg | cylinder | grey | below body, right, far | w11 h24 d11\n"
                + "far back leg | cylinder | grey | below body, left, far | w11 h24 d11\n"
                + "tail | cylinder | grey | left of body | w2.5 h18 d2.5");
        put("lion", "body | oval | gold | middle centre | w50 h24 d22\n"
                + "mane | sphere | brown | right of body, top | w26 h26 d22\n"
                + "head | sphere | gold | right of mane | w16 h16 d15\n"
                + "left ear | sphere | gold | above mane, right, near | w5 h5 d4\n"
                + "right ear | sphere | gold | above mane, right, far | w5 h5 d4\n"
                + "eye | sphere | navy | right of head, top | w3 h3 d3\n"
                + "nose | sphere | brown | right of head | w4 h3 d4\n"
                + "front leg | cylinder | gold | below body, right, near | w7 h18 d7\n"
                + "back leg | cylinder | gold | below body, left, near | w7 h18 d7\n"
                + "far front leg | cylinder | gold | below body, right, far | w7 h18 d7\n"
                + "far back leg | cylinder | gold | below body, left, far | w7 h18 d7\n"
                + "tail | cylinder | gold | left of body | sideways w22 h3 d3\n"
                + "tail tip | sphere | brown | left of tail | w5 h5 d5", "tiger", "lioness");
        put("penguin", "body | oval | navy | middle centre | w22 h36 d20\n"
                + "belly | oval | white | on body | w16 h28 d4\n"
                + "head | sphere | navy | above body | w15 h15 d15\n"
                + "beak | cone | orange | in front of head | forward w3 h3 d6\n"
                + "left eye | sphere | white | on head, left, top | w2.5 h2.5 d2\n"
                + "right eye | sphere | white | on head, right, top | w2.5 h2.5 d2\n"
                + "left wing | oval | navy | left of body | w5 h22 d10\n"
                + "right wing | oval | navy | right of body | w5 h22 d10\n"
                + "left foot | oval | orange | below body, left | w8 h3 d10\n"
                + "right foot | oval | orange | below body, right | w8 h3 d10");
        put("turtle", "shell | sphere | darkgreen | middle centre | w44 h20 d34\n"
                + "belly | oval | tan | below shell | w42 h5 d32\n"
                + "head | sphere | green | right of shell, bottom | w11 h10 d10\n"
                + "eye | sphere | navy | right of head, top | w2.5 h2.5 d2.5\n"
                + "front leg | oval | green | below shell, right, near | w9 h8 d8\n"
                + "back leg | oval | green | below shell, left, near | w9 h8 d8\n"
                + "far front leg | oval | green | below shell, right, far | w9 h8 d8\n"
                + "far back leg | oval | green | below shell, left, far | w9 h8 d8\n"
                + "tail | cone | green | left of shell, bottom | sideways w7 h3 d3", "tortoise");
        put("person", "body | box | blue | middle centre | w22 h30 d12\n"
                + "head | sphere | skin | above body | w14 h16 d14\n"
                + "hair | oval | brown | above head | w15 h6 d15\n"
                + "left eye | sphere | navy | on head, left, top | w2.5 h2.5 d2\n"
                + "right eye | sphere | navy | on head, right, top | w2.5 h2.5 d2\n"
                + "mouth | box | red | on head, bottom | w5 h1.5 d1\n"
                + "left arm | cylinder | blue | left of body | w5 h28 d5\n"
                + "right arm | cylinder | blue | right of body | w5 h28 d5\n"
                + "left hand | sphere | skin | below left arm | w5 h5 d5\n"
                + "right hand | sphere | skin | below right arm | w5 h5 d5\n"
                + "left leg | cylinder | navy | below body, left | w8 h32 d8\n"
                + "right leg | cylinder | navy | below body, right | w8 h32 d8\n"
                + "left foot | box | brown | below left leg | w8 h4 d12\n"
                + "right foot | box | brown | below right leg | w8 h4 d12", "man", "woman", "human", "boy", "girl", "child", "kid");
        put("robot", "body | box | silver | middle centre | w30 h32 d18\n"
                + "head | box | silver | above body | w20 h16 d16\n"
                + "left eye | sphere | cyan | on head, left | w4 h4 d2\n"
                + "right eye | sphere | cyan | on head, right | w4 h4 d2\n"
                + "mouth | box | grey | on head, bottom | w10 h2 d1\n"
                + "antenna | cylinder | grey | above head | w1.5 h8 d1.5\n"
                + "light | sphere | red | above antenna | w4 h4 d4\n"
                + "chest light | disc | cyan | on body, top | w8 h8 d1\n"
                + "left arm | cylinder | grey | left of body | w6 h28 d6\n"
                + "right arm | cylinder | grey | right of body | w6 h28 d6\n"
                + "left hand | sphere | silver | below left arm | w7 h7 d7\n"
                + "right hand | sphere | silver | below right arm | w7 h7 d7\n"
                + "left leg | box | grey | below body, left | w10 h26 d10\n"
                + "right leg | box | grey | below body, right | w10 h26 d10\n"
                + "left foot | box | silver | below left leg | w12 h4 d14\n"
                + "right foot | box | silver | below right leg | w12 h4 d14", "android", "droid");
        put("car", "body | box | red | middle centre | w70 h16 d32\n"
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
                + "door | box | red | on body | w20 h12 d1", "automobile", "sedan", "taxi", "racing car", "sports car");
        put("truck", "cargo box | box | white | middle centre | w56 h32 d30\n"
                + "cab | box | blue | right of cargo box, bottom | w22 h24 d28\n"
                + "window | box | sky | on cab, top | w14 h8 d1\n"
                + "front wheel | disc | navy | below cab, near | w14 h14 d5\n"
                + "far front wheel | disc | navy | below cab, far | w14 h14 d5\n"
                + "back wheel | disc | navy | below cargo box, left, near | w14 h14 d5\n"
                + "far back wheel | disc | navy | below cargo box, left, far | w14 h14 d5\n"
                + "middle wheel | disc | navy | below cargo box, right, near | w14 h14 d5\n"
                + "far middle wheel | disc | navy | below cargo box, right, far | w14 h14 d5\n"
                + "headlight | sphere | yellow | right of cab, bottom, near | w3 h3 d4", "lorry");
        put("bus", "body | box | yellow | middle centre | w84 h30 d28\n"
                + "window | box | sky | on body, top | w70 h9 d1\n"
                + "door | box | grey | on body, right | w9 h20 d1\n"
                + "front wheel | disc | navy | below body, right, near | w14 h14 d5\n"
                + "back wheel | disc | navy | below body, left, near | w14 h14 d5\n"
                + "far front wheel | disc | navy | below body, right, far | w14 h14 d5\n"
                + "far back wheel | disc | navy | below body, left, far | w14 h14 d5\n"
                + "headlight | sphere | white | right of body, bottom, near | w3 h3 d4", "school bus", "coach");
        put("bicycle", "frame | box | red | middle centre | w34 h3 d3\n"
                + "front wheel | ring | navy | below frame, right | w23 h23 d3\n"
                + "back wheel | ring | navy | below frame, left | w23 h23 d3\n"
                + "seat post | cylinder | grey | above frame, left | w2 h10 d2\n"
                + "seat | oval | navy | above seat post | w10 h3 d5\n"
                + "handlebar post | cylinder | grey | above frame, right | w2 h12 d2\n"
                + "handlebar | cylinder | grey | above handlebar post | forward w2 h2 d16\n"
                + "pedal | box | grey | below frame | w4 h2 d8", "bike", "cycle");
        put("motorcycle", "body | oval | red | middle centre | w40 h16 d14\n"
                + "seat | oval | navy | above body, left | w20 h5 d12\n"
                + "tank | oval | red | above body, right | w14 h9 d12\n"
                + "handlebar | cylinder | silver | above tank, right | forward w2 h2 d22\n"
                + "headlight | sphere | yellow | right of body, top | w5 h5 d5\n"
                + "engine | box | grey | below body | w14 h10 d12\n"
                + "front wheel | ring | navy | below body, right | w24 h24 d5\n"
                + "back wheel | ring | navy | below body, left | w24 h24 d5\n"
                + "exhaust | cylinder | silver | below body, left, near | sideways w20 h3 d3", "motorbike", "scooter");
        put("airplane", "fuselage | cylinder | white | middle centre | sideways w80 h12 d12\n"
                + "nose | cone | white | right of fuselage | sideways w12 h12 d12\n"
                + "window | box | sky | on fuselage, top | w44 h3 d1\n"
                + "left wing | box | silver | in front of fuselage | w22 h2 d40\n"
                + "right wing | box | silver | behind fuselage | w22 h2 d40\n"
                + "tail fin | box | red | above fuselage, left | w12 h16 d2\n"
                + "left tail wing | box | silver | in front of fuselage, left | w10 h2 d14\n"
                + "right tail wing | box | silver | behind fuselage, left | w10 h2 d14\n"
                + "left engine | cylinder | grey | in front of fuselage, bottom | sideways w14 h6 d6\n"
                + "right engine | cylinder | grey | behind fuselage, bottom | sideways w14 h6 d6", "plane", "aeroplane", "jet", "aircraft", "airliner");
        put("helicopter", "body | oval | green | middle centre | w36 h24 d22\n"
                + "window | sphere | sky | right of body | w12 h14 d16\n"
                + "tail | cylinder | green | left of body, top | sideways w34 h5 d5\n"
                + "tail fin | box | green | left of tail | w4 h10 d2\n"
                + "tail rotor | disc | grey | in front of tail fin | w10 h10 d1\n"
                + "mast | cylinder | grey | above body | w3 h6 d3\n"
                + "rotor | box | grey | above mast | w80 h1.5 d5\n"
                + "cross rotor | box | grey | above mast | w5 h1.5 d80\n"
                + "left skid | cylinder | grey | below body, near | sideways w40 h2 d2\n"
                + "right skid | cylinder | grey | below body, far | sideways w40 h2 d2");
        put("rocket", "body | cylinder | white | middle centre | w18 h56 d18\n"
                + "nose cone | cone | red | above body | w18 h20 d18\n"
                + "window | disc | sky | on body, top | w8 h8 d1\n"
                + "left fin | pyramid | red | left of body, bottom | w10 h16 d3\n"
                + "right fin | pyramid | red | right of body, bottom | w10 h16 d3\n"
                + "front fin | pyramid | red | in front of body, bottom | w3 h16 d10\n"
                + "engine | cone | grey | below body | w14 h8 d14\n"
                + "flame | cone | orange | below engine | w10 h14 d10", "spaceship", "space shuttle");
        put("boat", "hull | box | brown | middle centre | w60 h14 d22\n"
                + "deck | box | tan | above hull | w60 h2 d22\n"
                + "cabin | box | white | above deck, right | w18 h10 d14\n"
                + "window | box | sky | on cabin | w12 h4 d1\n"
                + "mast | cylinder | brown | above deck, left | w2 h50 d2\n"
                + "sail | box | white | right of mast | w22 h38 d1", "ship", "sailboat", "yacht");
        put("house", "walls | box | beige | middle centre | w60 h36 d44\n"
                + "roof | pyramid | red | above walls | w66 h26 d50\n"
                + "door | box | brown | on walls, bottom | w10 h18 d1\n"
                + "left window | box | sky | on walls, left, top | w10 h10 d1\n"
                + "right window | box | sky | on walls, right, top | w10 h10 d1\n"
                + "chimney | box | red | above walls, right | w7 h20 d7", "home", "cottage", "building", "hut");
        put("castle", "keep | box | grey | middle centre | w44 h40 d30\n"
                + "left tower | cylinder | grey | left of keep | w16 h56 d16\n"
                + "right tower | cylinder | grey | right of keep | w16 h56 d16\n"
                + "left roof | cone | navy | above left tower | w18 h16 d18\n"
                + "right roof | cone | navy | above right tower | w18 h16 d18\n"
                + "gate | box | brown | on keep, bottom | w14 h18 d1\n"
                + "window | box | navy | on keep, top | w8 h8 d1\n"
                + "flag pole | cylinder | grey | above keep | w1.5 h14 d1.5\n"
                + "flag | box | red | right of flag pole, top | w8 h5 d1", "fort", "palace");
        put("tree", "trunk | cylinder | brown | middle centre | w10 h44 d10\n"
                + "crown | sphere | green | above trunk | w44 h36 d40\n"
                + "left leaves | sphere | darkgreen | left of crown, bottom | w22 h20 d22\n"
                + "right leaves | sphere | darkgreen | right of crown, bottom | w22 h20 d22\n"
                + "top leaves | sphere | darkgreen | above crown | w22 h16 d22", "oak", "palm tree");
        put("flower", "stem | cylinder | green | middle centre | w2.5 h50 d2.5\n"
                + "centre | sphere | yellow | above stem | w9 h9 d5\n"
                + "left petal | oval | pink | left of centre | w10 h7 d3\n"
                + "right petal | oval | pink | right of centre | w10 h7 d3\n"
                + "top petal | oval | pink | above centre | w7 h10 d3\n"
                + "bottom petal | oval | pink | below centre | w7 h10 d3\n"
                + "leaf | oval | green | right of stem | w12 h5 d5", "rose", "sunflower", "tulip");
        put("table", "top | box | brown | middle centre | w60 h4 d34\n"
                + "front left leg | cylinder | brown | below top, left, near | w4 h30 d4\n"
                + "front right leg | cylinder | brown | below top, right, near | w4 h30 d4\n"
                + "back left leg | cylinder | brown | below top, left, far | w4 h30 d4\n"
                + "back right leg | cylinder | brown | below top, right, far | w4 h30 d4", "desk");
        put("chair", "seat | box | brown | middle centre | w28 h4 d28\n"
                + "back | box | brown | above seat | w28 h30 d3\n"
                + "front left leg | cylinder | brown | below seat, left, near | w3 h24 d3\n"
                + "front right leg | cylinder | brown | below seat, right, near | w3 h24 d3\n"
                + "back left leg | cylinder | brown | below seat, left, far | w3 h24 d3\n"
                + "back right leg | cylinder | brown | below seat, right, far | w3 h24 d3", "stool");
        put("lamp", "base | cylinder | grey | middle centre | w20 h3 d20\n"
                + "pole | cylinder | silver | above base | w2.5 h40 d2.5\n"
                + "shade | cone | yellow | above pole | w24 h16 d24\n"
                + "bulb | sphere | white | below shade | w6 h6 d6", "desk lamp");
        put("cup", "body | cylinder | white | middle centre | w26 h30 d26\n"
                + "handle | ring | white | right of body | sideways w3 h16 d14\n"
                + "coffee | cylinder | brown | above body | w22 h1 d22", "mug", "coffee cup");
        put("guitar", "body | oval | orange | middle centre | w30 h34 d8\n"
                + "hole | disc | navy | on body, top | w9 h9 d1\n"
                + "neck | box | brown | above body | w5 h38 d3\n"
                + "head | box | brown | above neck | w8 h10 d3\n"
                + "strings | box | silver | on neck | w3 h36 d1");
        put("spider", "body | sphere | navy | middle centre | w22 h16 d20\n"
                + "head | sphere | navy | right of body | w12 h10 d11\n"
                + "eye | sphere | red | right of head, top | w2.5 h2.5 d2.5\n"
                + "front leg | cylinder | navy | in front of body, right | forward w2 h2 d26\n"
                + "middle leg | cylinder | navy | in front of body | forward w2 h2 d28\n"
                + "back leg | cylinder | navy | in front of body, left | forward w2 h2 d26\n"
                + "far front leg | cylinder | navy | behind body, right | forward w2 h2 d26\n"
                + "far middle leg | cylinder | navy | behind body | forward w2 h2 d28\n"
                + "far back leg | cylinder | navy | behind body, left | forward w2 h2 d26");
        put("giraffe", "body | oval | gold | middle centre | w44 h24 d20\n"
                + "neck | cylinder | gold | above body, right | w8 h44 d8\n"
                + "head | oval | gold | above neck, right | w14 h7 d8\n"
                + "left horn | cylinder | brown | above head, left, near | w1.5 h5 d1.5\n"
                + "right horn | cylinder | brown | above head, left, far | w1.5 h5 d1.5\n"
                + "spot | disc | brown | on body | w10 h8 d1\n"
                + "front leg | cylinder | gold | below body, right, near | w5 h38 d5\n"
                + "back leg | cylinder | gold | below body, left, near | w5 h38 d5\n"
                + "far front leg | cylinder | gold | below body, right, far | w5 h38 d5\n"
                + "far back leg | cylinder | gold | below body, left, far | w5 h38 d5\n"
                + "tail | cylinder | gold | left of body | w2 h18 d2");
        put("phone", "body | box | navy | middle centre | w28 h56 d4\n"
                + "screen | box | cyan | on body | w24 h48 d1\n"
                + "camera | disc | grey | on body, top | w4 h4 d1", "smartphone", "mobile phone");
        put("laptop", "bottom | box | grey | middle centre | w56 h3 d38\n"
                + "screen | box | grey | above bottom, far | w56 h36 d3\n"
                + "display | box | cyan | on screen | w50 h30 d1\n"
                + "keyboard | box | navy | above bottom, near | w46 h1 d18", "computer", "notebook");
    }

    private static void put(String key, String plan, String... also) {
        PLANS.put(key, plan);
        for (String a : also) {
            ALSO.put(a, key);
        }
    }

    /** The plan's key for a subject ("a cute kitten" → "cat"), or null when there is no plan for it. */
    public static String keyFor(String subject) {
        if (subject == null) {
            return null;
        }
        String s = " " + subject.toLowerCase(Locale.US).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim() + " ";
        for (Map.Entry<String, String> e : ALSO.entrySet()) {
            if (s.contains(" " + e.getKey() + " ") || s.contains(" " + e.getKey() + "s ")) {
                return e.getValue();
            }
        }
        for (String k : PLANS.keySet()) {
            if (s.contains(" " + k + " ") || s.contains(" " + k + "s ") || s.contains(" " + k + "es ")) {
                return k;
            }
        }
        return null;
    }

    /** The built plan for a subject, or null. */
    public static SolidSpec forSubject(String subject) {
        String k = keyFor(subject);
        return k == null ? null : SolidSpec.parse(PLANS.get(k), subject);
    }

    public static int count() {
        return PLANS.size();
    }

    public static Iterable<String> keys() {
        return PLANS.keySet();
    }
}
