package ai.dvine.ops.recon;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads node names from a glTF JSON chunk inside a GLB. It does not invent missing meshes. */
public final class GlbAssetImporter {
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    public EditableScene importBytes(byte[] glb, String fallbackLabel) {
        String json = jsonChunk(glb);
        EditableScene scene = new EditableScene();
        scene.label = fallbackLabel == null ? "Imported" : fallbackLabel;
        scene.approximation = false;
        if (json == null) {
            return scene;
        }
        Matcher m = NAME.matcher(json);
        int i = 0;
        while (m.find()) {
            String name = m.group(1);
            String id = name.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_]", "");
            if (id.isEmpty()) {
                id = "node_" + i;
            }
            scene.add(new EditableScene.Node(id, name, "grey", name.toLowerCase() + " | box | grey | middle centre | medium"));
            i++;
        }
        if (scene.nodes().isEmpty()) {
            scene.approximation = true;
        }
        return scene;
    }

    public static byte[] tinyGlb(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        int jsonPad = (4 - (body.length % 4)) % 4;
        int jsonChunk = body.length + jsonPad;
        int total = 12 + 8 + jsonChunk;
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x46546C67);
        buf.putInt(2);
        buf.putInt(total);
        buf.putInt(jsonChunk);
        buf.putInt(0x4E4F534A);
        buf.put(body);
        for (int i = 0; i < jsonPad; i++) {
            buf.put((byte) 0x20);
        }
        return buf.array();
    }

    static String jsonChunk(byte[] glb) {
        if (glb == null || glb.length < 20) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        if (magic != 0x46546C67) {
            return null;
        }
        buf.getInt();
        buf.getInt();
        int chunkLen = buf.getInt();
        int chunkType = buf.getInt();
        if (chunkType != 0x4E4F534A || chunkLen < 2 || buf.remaining() < chunkLen) {
            return null;
        }
        byte[] json = new byte[chunkLen];
        buf.get(json);
        return new String(json, StandardCharsets.UTF_8).trim();
    }
}
