package ai.dvine.human;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

/** The lifelike look's clothes cover the torso, legs and feet, never the face or hands, and the skin features sit on the head. */
public class ClothingTest {
    private static HumanAsset load(String n) throws Exception {
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/" + n + ".dvhm"))) {
            return HumanAsset.read(in);
        }
    }

    private static boolean covers(Clothing.Garment g, int vertex) {
        for (int v : g.tri) {
            if (v == vertex) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void garmentsCoverTheBodyButNotTheFaceOrHands() throws Exception {
        for (String n : new String[]{"obi", "ivie"}) {
            HumanAsset a = load(n);
            Clothing.Garment[] g = Clothing.design(a);
            assertTrue(n, g.length == (a.female ? 3 : 5));
            assertTrue(n + " top covers the chest", covers(g[0], a.landmark("chest")));
            assertTrue(n + " trousers cover the knee", covers(g[1], a.landmark("left knee")));
            for (int k = 3; k < g.length; k++) {
                if (!g[k].name.matches("hair|beard")) {
                    continue; // Obi's shirt sits under his jacket
                }
                // Obi's hair (and beard, when grown): on the head, never over the eyes or nose
                assertTrue(g[k].name, g[k].tri.length > 100);
                assertTrue(g[k].name + " off the nose", !covers(g[k], a.landmark("nose tip")));
                for (int v : g[k].tri) {
                    assertTrue(g[k].name + " on the head", (a.mesh.region[v] & 0xff) <= Regions.NECK);
                }
            }
            for (int k = 0; k < 3; k++) {
                Clothing.Garment gm = g[k];
                assertTrue(n + " " + gm.name + " has triangles", gm.tri.length > 300);
                assertTrue(gm.push > 0f && gm.push < 0.1f);
                for (int v : gm.tri) {
                    int r = a.mesh.region[v] & 0xff;
                    assertTrue(n + " " + gm.name + " stays off the face", r > Regions.TEETH || r == Regions.NECK);
                    assertTrue(n + " " + gm.name + " stays off the hands", r < Regions.PALM_L || r > Regions.LITTLE_R);
                }
            }
            assertTrue(n + " nose and chin are bare", !covers(g[0], a.landmark("nose tip")) && !covers(g[0], a.landmark("chin")));
        }
    }

    @Test
    public void everyHairstyleBuildsAndTheShortAfroIsAShell() throws Exception {
        for (String n : new String[]{"obi", "ivie"}) {
            HumanAsset a = load(n);
            for (String style : ai.dvine.face.Look.HAIR_STYLES) {
                Clothing.Garment[] g = Clothing.design(a, style);
                boolean shell = style.equals(ai.dvine.face.Look.FADE) || style.equals(ai.dvine.face.Look.SHORT_AFRO);
                assertTrue(n + " " + style, g.length == 3 + (shell ? 1 : 0) + (a.female ? 0 : 1));
                if (style.equals(ai.dvine.face.Look.SHORT_AFRO)) {
                    Clothing.Garment hair = g[g.length - 1];
                    assertTrue(hair.clip[0] == 6f && hair.tri.length > 100);
                    assertTrue(n + " afro off the nose", !covers(hair, a.landmark("nose tip")));
                }
            }
        }
        assertTrue(Clothing.afroLine(0f, 0.9f) > 0.55f && Clothing.afroLine(0f, -0.8f) < -0.7f && Clothing.afroLine(0.6f, -0.05f) > 0f);
    }

    @Test
    public void hairstylesAreAskedForByName() {
        String[][] cases = {{"give ivie cornrows", "cornrows"}, {"make your hair bantu knots", "bantu knots"},
                {"i want a short afro", "short afro"}, {"afro puffs please", "afro puffs"}, {"get a low cut fade", "fade"}};
        ai.dvine.face.Look obi = ai.dvine.face.Look.defaults(false);
        assertTrue(!obi.beard && obi.apply("grow a beard") != null && obi.beard);
        assertTrue(obi.apply("remove your beard") != null && !obi.beard);
        for (String[] c : cases) {
            ai.dvine.face.Look l = ai.dvine.face.Look.defaults(true);
            assertTrue(c[0], l.apply(c[0]) != null && l.hairStyle.equals(c[1]));
            ai.dvine.face.Look back = ai.dvine.face.Look.parse(l.serialize(), true);
            assertTrue(c[0] + " saved", back.hairStyle.equals(c[1]));
        }
    }

    @Test
    public void browsAndHairlineSitOnTheHead() {
        // in head units: the eyes at y 0.02, the crown near y 1
        assertTrue(ai.dvine.render.SkinMaterialAccess.brow(0.3f, 0.26f, 0.8f, false) > 0.5f);
        assertTrue("no brow on the eye itself", ai.dvine.render.SkinMaterialAccess.brow(0.3f, 0.02f, 0.8f, false) < 0.01f);
        assertTrue("hair on the crown", ai.dvine.render.SkinMaterialAccess.hair(0f, 0.95f, 0f, false) > 0.9f);
        assertTrue("no hair on the forehead", ai.dvine.render.SkinMaterialAccess.hair(0f, 0.4f, 0.8f, false) < 0.01f);
        assertTrue("hair at the back of the head", ai.dvine.render.SkinMaterialAccess.hair(0f, -0.3f, -0.8f, false) > 0.9f);
    }
}
