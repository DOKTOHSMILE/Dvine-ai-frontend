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
            assertTrue(n, g.length == (a.female ? 4 : 6));
            assertTrue(n + " top covers the chest", covers(g[0], a.landmark("chest")));
            assertTrue(n + " trousers cover the knee", covers(g[1], a.landmark("left knee")));
            for (int k = 4; k < g.length; k++) {
                // Obi's hair and beard: on the head, never over the eyes or nose
                assertTrue(g[k].name, g[k].tri.length > 100);
                assertTrue(g[k].name + " off the nose", !covers(g[k], a.landmark("nose tip")));
                for (int v : g[k].tri) {
                    assertTrue(g[k].name + " on the head", (a.mesh.region[v] & 0xff) <= Regions.NECK);
                }
            }
            for (int k = 0; k < 4; k++) {
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
    public void browsAndHairlineSitOnTheHead() {
        // in head units: the eyes at y 0.02, the crown near y 1
        assertTrue(ai.dvine.render.SkinMaterialAccess.brow(0.3f, 0.26f, 0.8f, false) > 0.5f);
        assertTrue("no brow on the eye itself", ai.dvine.render.SkinMaterialAccess.brow(0.3f, 0.02f, 0.8f, false) < 0.01f);
        assertTrue("hair on the crown", ai.dvine.render.SkinMaterialAccess.hair(0f, 0.95f, 0f, false) > 0.9f);
        assertTrue("no hair on the forehead", ai.dvine.render.SkinMaterialAccess.hair(0f, 0.4f, 0.8f, false) < 0.01f);
        assertTrue("hair at the back of the head", ai.dvine.render.SkinMaterialAccess.hair(0f, -0.3f, -0.8f, false) > 0.9f);
    }
}
