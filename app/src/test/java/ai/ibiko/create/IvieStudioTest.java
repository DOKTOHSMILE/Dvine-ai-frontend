package ai.ibiko.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.ibiko.render.field.MeshExport;

/** Ivie's studio: a model-drawn car, edited part by part, undone, and exported in every format. */
public class IvieStudioTest {
    private static final String CAR = "body | box | red | middle centre | large wide\n"
            + "cabin | box | sky | above body | medium\n"
            + "wheel | disc | black | below body, both sides, left | medium sideways\n"
            + "wheel | disc | black | below body, both sides, right | medium sideways\n"
            + "headlight | sphere | yellow | in front of body, both sides | small\n";

    private static final class Script implements IvieStudio.Model {
        final List<String> replies = new ArrayList<String>(), asked = new ArrayList<String>();

        Script(String... r) {
            for (String s : r) {
                replies.add(s);
            }
        }

        @Override
        public String ask(String system, String prompt, int maxTokens) {
            asked.add(prompt);
            return replies.isEmpty() ? null : replies.remove(0);
        }
    }

    @Test
    public void drawsEditsUndoesAndExports() {
        Script model = new Script(CAR, "spoiler | box | black | above body, right | small wide flat");
        IvieStudio s = new IvieStudio(model);
        assertEquals("dragon", IvieStudio.drawSubject("Draw me a dragon"));
        assertEquals("phoenix", IvieStudio.drawSubject("make a 3d model of a phoenix"));
        assertNull(IvieStudio.drawSubject("what time is it"));

        IvieStudio.Result r = s.draw("car", null, 2000);
        assertNotNull(r.line, r.spec);
        assertTrue(r.line, r.line.startsWith("Here's a car in 3D"));
        int parts = s.spec().parts.size();

        r = s.edit("take out the tyres", 2000); // "tyres" are its wheels
        assertTrue(r.line, r.line.startsWith("Took out the"));
        assertEquals(parts - countWheels(r.spec, parts), s.spec().parts.size());
        assertTrue(s.spec().parts.stream().noneMatch(p -> p.name.contains("wheel")));

        r = s.edit("put the tyres back", 2000);
        assertTrue(r.line, r.line.startsWith("Put back the"));
        assertEquals(parts, s.spec().parts.size());

        r = s.edit("make the cabin blue", 2000);
        assertTrue(r.line, r.line.startsWith("Recoloured the cabin"));

        r = s.edit("add a spoiler", 2000);
        assertTrue(r.line, r.line.startsWith("Added spoiler"));
        assertEquals(parts + 1, s.spec().parts.size());

        r = s.edit("undo", 2000);
        assertEquals("Undone.", r.line);
        assertEquals(parts, s.spec().parts.size());
        assertNull(s.edit("tell me a joke", 2000)); // not an edit: the chat answers

        assertTrue(IvieStudio.wantsExport("export it as an STL for 3d printing"));
        assertTrue(IvieStudio.wantsExport("send me the file"));
        assertFalse(IvieStudio.wantsExport("what colour is the car"));
        IvieStudio.Export glb = s.export("export it");
        assertEquals("car.glb", glb.name);
        assertEquals("glTF", new String(glb.bytes, 0, 4, StandardCharsets.US_ASCII));
        IvieStudio.Export obj = s.export("save it as an obj for blender");
        assertEquals(MeshExport.Format.OBJ, obj.format);
        assertTrue(new String(obj.bytes, StandardCharsets.UTF_8).contains("mtllib car.mtl"));
        assertEquals("car.mtl", obj.extra.name);
        IvieStudio.Export stl = s.export("for 3d printing");
        assertEquals("car.stl", stl.name);
        assertTrue(stl.bytes.length > 84);
        assertTrue(new String(s.export("as a ply").bytes, 0, 3, StandardCharsets.US_ASCII).equals("ply"));
    }

    private static int countWheels(ai.ibiko.render.field.SolidSpec after, int before) {
        return before - after.parts.size();
    }

    @Test
    public void aThinPlanIsAskedAgainAndAFailureSaysSo() {
        Script model = new Script("body | box | red | middle centre | large", CAR);
        IvieStudio s = new IvieStudio(model);
        assertNotNull(s.draw("car", null, 2000).spec);
        assertEquals(2, model.asked.size());
        IvieStudio none = new IvieStudio(new Script());
        IvieStudio.Result r = none.draw("car", null, 2000);
        assertNull(r.spec);
        assertTrue(r.line.startsWith("The model didn't answer"));
        assertFalse(none.has());
    }
}
