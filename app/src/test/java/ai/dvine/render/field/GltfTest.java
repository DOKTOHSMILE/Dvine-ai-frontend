package ai.dvine.render.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A 3D drawing saved for games: a valid .glb, one node per part, each part a joint under the part it hangs from. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GltfTest {
    @Test
    public void exportsAGameReadyGlbWithJoints() throws Exception {
        SolidSpec cat = Blueprints.forSubject("cat");
        byte[] glb = Gltf.glb(cat, "cat");
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(glb).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546C67, b.getInt(0)); // "glTF"
        assertEquals(2, b.getInt(4));
        assertEquals(glb.length, b.getInt(8));
        int jsonLen = b.getInt(12);
        assertEquals(0, jsonLen % 4);
        String json = new String(glb, 20, jsonLen, "UTF-8").trim();
        int binLen = b.getInt(20 + jsonLen);
        assertEquals(0x004E4942, b.getInt(24 + jsonLen));
        assertEquals(glb.length, 28 + jsonLen + binLen);
        org.json.JSONObject j = new org.json.JSONObject(json);
        assertEquals(cat.parts.size() + 1, j.getJSONArray("nodes").length());
        assertEquals(cat.parts.size(), j.getJSONArray("meshes").length());
        // the ears hang from the head, the head from the body
        org.json.JSONArray nodes = j.getJSONArray("nodes");
        int head = -1, ear = -1;
        for (int i = 0; i < nodes.length(); i++) {
            if (nodes.getJSONObject(i).optString("name").equals("head")) head = i;
            if (nodes.getJSONObject(i).optString("name").equals("left ear")) ear = i;
        }
        org.json.JSONArray kids = nodes.getJSONObject(head).getJSONArray("children");
        boolean found = false;
        for (int i = 0; i < kids.length(); i++) {
            found |= kids.getInt(i) == ear;
        }
        assertTrue(found);
        // every view fits the buffer
        org.json.JSONArray views = j.getJSONArray("bufferViews");
        for (int i = 0; i < views.length(); i++) {
            org.json.JSONObject v = views.getJSONObject(i);
            assertTrue(v.getInt("byteOffset") + v.getInt("byteLength") <= binLen);
        }
        assertEquals("red-sports-car.glb", Gltf.fileName("a red sports car!"));
    }

}
