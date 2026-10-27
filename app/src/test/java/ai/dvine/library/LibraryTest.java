package ai.dvine.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

import ai.dvine.render.field.MeshExport;
import ai.dvine.render.field.SolidSpec;

/** Saving drawings by name, finding them by name or description, and saving them as 3D files. */
public class LibraryTest {
    private static final SolidSpec CAR = SolidSpec.parse("body | box | red | middle centre | w70 h16 d32\n"
            + "cabin | box | red | above body | w38 h14 d28\nfront wheel | disc | navy | below body, right, near | w15 h15 d5", "car");
    private static final SolidSpec CAT = SolidSpec.parse("body | oval | orange | middle centre | w46 h20 d20\n"
            + "head | sphere | orange | right of body, top | w18 h17 d17\ntail | cylinder | orange | left of body, top | w4 h24 d4", "cat");

    @Test
    public void readsTheWords() {
        LibraryWords w = LibraryWords.parse("Save it as Robo");
        assertEquals(LibraryWords.Kind.SAVE, w.kind);
        assertEquals("Robo", w.name);
        w = LibraryWords.parse("save this drawing, call it Speedy: my red car with one wheel");
        assertEquals("Speedy", w.name);
        assertEquals("my red car with one wheel", w.description);
        assertNull(LibraryWords.parse("save it").name);
        assertEquals("Tom", LibraryWords.parse("name it Tom").name);
        assertEquals(LibraryWords.Kind.EXPORT, LibraryWords.parse("save it to my phone").kind);
        assertEquals(LibraryWords.Kind.EXPORT, LibraryWords.parse("save it as an STL").kind);
        assertEquals(LibraryWords.Kind.EXPORT, LibraryWords.parse("export it for 3D printing").kind);
        assertEquals(LibraryWords.Kind.EXPORT, LibraryWords.parse("save it for my game").kind);
        assertEquals(LibraryWords.Kind.SAVE, LibraryWords.parse("save it as Game Boy").kind);
        assertEquals(LibraryWords.Kind.OPEN, LibraryWords.parse("show me Robo").kind);
        assertTrue(LibraryWords.parse("open my drawing of a red car").plainlySaved);
        assertFalse(LibraryWords.parse("show me a lion").plainlySaved);
        assertEquals(LibraryWords.Kind.LIST, LibraryWords.parse("what drawings have I saved?").kind);
        assertEquals(LibraryWords.Kind.DELETE, LibraryWords.parse("delete the drawing called Robo").kind);
        assertEquals("Robo", LibraryWords.nameFrom("call it Robo."));
        assertNull(LibraryWords.parse("what time is it"));
    }

    @Test
    public void formats() {
        assertEquals(MeshExport.Format.STL, MeshExport.formatIn("save it for my 3d printer"));
        assertEquals(MeshExport.Format.OBJ, MeshExport.formatIn("export it as obj"));
        assertEquals(MeshExport.Format.PLY, MeshExport.formatIn("as a ply file"));
        assertEquals(MeshExport.Format.GLB, MeshExport.formatIn("save it to my phone"));
    }

    @Test
    public void savesFindsAndExplainsAClash() throws Exception {
        File dir = Files.createTempDirectory("lib").toFile();
        DrawingLibrary lib = new DrawingLibrary(dir);
        lib.save("Speedy", "car", "my red car", CAR.copy().fixFrame(), 1000);
        lib.save("Tom", "cat", "", CAT, 2000);
        assertNotNull(lib.named("speedy")); // the name is taken whatever the case
        assertEquals(100, lib.find("Speedy").score);
        assertEquals("Tom", lib.find("my drawing called Tom").entries.get(0).name);
        DrawingLibrary.Found byWords = lib.find("the red car");
        assertEquals("Speedy", byWords.entries.get(0).name);
        assertTrue(byWords.score >= 35);
        assertEquals("Tom", lib.find("orange cat with a tail").entries.get(0).name);
        assertEquals(0, lib.find("giraffe").score);
        // it lasts: a new library on the same folder finds it, parts and all
        DrawingLibrary again = new DrawingLibrary(dir);
        assertEquals(2, again.size());
        SolidSpec back = again.open(again.named("Speedy"));
        assertEquals(CAR.parts.size(), back.parts.size());
        assertEquals(CAR.parts.get(2).x, back.parts.get(2).x, 1e-4f);
        assertEquals("body", back.parts.get(2).parent.name);
        assertTrue(again.delete(again.named("Tom")));
        assertNull(new DrawingLibrary(dir).named("Tom"));
    }

    @Test
    public void meshFilesAreWellFormed() {
        String obj = MeshExport.obj(CAR, "car", "car.mtl");
        assertTrue(obj.contains("mtllib car.mtl"));
        assertTrue(obj.contains("o front_wheel"));
        assertTrue(MeshExport.mtl(CAR).contains("newmtl m2"));
        byte[] stl = MeshExport.stl(CAR, "car");
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(stl).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(84 + b.getInt(80) * 50, stl.length);
        byte[] ply = MeshExport.ply(CAT);
        assertTrue(new String(ply, 0, 40).startsWith("ply\nformat binary_little_endian"));
        assertEquals("orange", DrawingLibrary.colourName(CAT.parts.get(0).colour));
    }
}
