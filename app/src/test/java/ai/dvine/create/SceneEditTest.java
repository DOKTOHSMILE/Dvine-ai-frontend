package ai.dvine.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.Test;

import ai.dvine.geom.TriMesh;
import ai.dvine.ops.pipe.CreationRouter;
import ai.dvine.stage.SolidSurface;

/** Detach and restore a real part. The motorcycle is procedural, not a measurement. */
public class SceneEditTest {
    private static ObjectModel motorcycle() {
        ObjectRequestInterpreter.Request r = ObjectRequestInterpreter.interpret("build a motorcycle", null);
        assertNotNull(r);
        assertEquals(ObjectRequestInterpreter.Kind.CREATE, r.kind);
        ObjectModel m = ProceduralModelingEngine.build(r.spec, 1, new ArrayList<Provenance>());
        assertTrue(m.problems.toString(), m.valid());
        return m;
    }

    private static Component part(ObjectModel m, String name) {
        for (Component c : m.components) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static int triangles(ObjectModel m) {
        SolidSurface s = SolidSurface.pack(m);
        assertNotNull(s);
        return s.triangles;
    }

    @Test
    public void aFrontTyreDetachesAndReturnsToTheSamePlace() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "dvine-edit-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        ObjectSession session = new ObjectSession();
        CreationDirector director = new CreationDirector(session, dir);
        assertNotNull(session.create(ObjectRequestInterpreter.interpret("build a motorcycle", null).spec, null));
        ObjectModel m = session.model();
        Component tyre = part(m, "front tyre");
        assertEquals("tyre-front-1", SceneCommands.stableId(tyre));
        assertEquals("wheel-front", SceneCommands.parentId(tyre));
        float[] before = SceneCommands.centroid(tyre);
        int all = triangles(m);
        TriMesh mesh = tyre.mesh;

        CreationDirector.Outcome off = director.perform(ObjectRequestInterpreter.interpret("Remove the front tyre but keep it.", m.spec));
        assertEquals(CreationDirector.Action.CHANGED, off.action);
        assertFalse(tyre.visible);
        assertTrue(tyre.retained);
        assertTrue(tyre.mesh == mesh);
        assertEquals(before[0], SceneCommands.centroid(tyre)[0], 1e-4);
        assertEquals(before[1], SceneCommands.centroid(tyre)[1], 1e-4);
        assertEquals(before[2], SceneCommands.centroid(tyre)[2], 1e-4);
        int hidden = triangles(m);
        assertTrue(hidden < all);
        assertTrue(off.lines.get(0).toLowerCase().contains("not a measurement") || off.lines.get(0).toLowerCase().contains("not deleted"));

        ObjectRequestInterpreter.Request typed = ObjectRequestInterpreter.interpret("Remove the front tyre but keep it.", m.spec);
        ObjectRequestInterpreter.Request spoken = ObjectRequestInterpreter.interpret("remove the front tyre but keep it", m.spec);
        assertEquals(ObjectRequestInterpreter.Kind.PART, typed.kind);
        assertEquals(typed.detail, spoken.detail);

        CreationRouter.Route text = new CreationRouter().classify("Remove the front tyre but keep it.");
        CreationRouter.Route voice = new CreationRouter().classify("remove the front tyre but keep it");
        assertEquals(CreationRouter.Capability.SCENE_EDIT, text.capability);
        assertEquals(text.operation, voice.operation);
        assertEquals("front tyre", text.subject);
        assertEquals("DETACH", text.operation);

        CreationDirector.Outcome back = director.perform(ObjectRequestInterpreter.interpret("put the front tyre back", m.spec));
        assertEquals(CreationDirector.Action.CHANGED, back.action);
        assertTrue(tyre.visible);
        assertEquals(all, triangles(m));
        assertEquals(before[0], SceneCommands.centroid(tyre)[0], 1e-4);

        director.perform(ObjectRequestInterpreter.interpret("detach the front tyre", m.spec));
        assertFalse(tyre.visible);
        CreationDirector.Outcome undone = director.perform(ObjectRequestInterpreter.interpret("undo", m.spec));
        assertEquals(CreationDirector.Action.CHANGED, undone.action);
        assertTrue(tyre.visible);
        assertEquals(all, triangles(m));
    }

    @Test
    public void anAmbiguousTyreAndAnUnconfirmedDeleteChangeNothing() {
        ObjectModel m = motorcycle();
        Component front = part(m, "front tyre");
        int all = triangles(m);
        SceneCommands.Report vague = SceneCommands.apply(m, "detach", "tyre");
        assertTrue(vague.clarify);
        assertFalse(vague.applied);
        assertEquals("", vague.targetId);
        assertTrue(front.visible);
        assertEquals(all, triangles(m));

        SceneCommands.Report del = SceneCommands.apply(m, "delete", "front tyre");
        assertFalse(del.applied);
        assertEquals("tyre-front-1", del.targetId);
        assertTrue(front.visible);
        assertTrue(front.retained);
        assertTrue(del.speech.toLowerCase().contains("have not deleted"));

        front.locked = true;
        SceneCommands.Report locked = SceneCommands.apply(m, "detach", "front tyre");
        assertFalse(locked.applied);
        assertTrue(front.visible);
        front.locked = false;
    }

    @Test
    public void aDetachedTyreIsStillDetachedAfterSaveAndReopen() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "dvine-moto-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        ObjectSession session = new ObjectSession();
        CreationDirector director = new CreationDirector(session, dir);
        session.create(ObjectRequestInterpreter.interpret("build a red motorcycle", null).spec, null);
        float[] pose = SceneCommands.centroid(part(session.model(), "front tyre"));
        director.perform(ObjectRequestInterpreter.interpret("take off the front tyre but keep it", session.model().spec));
        director.perform(ObjectRequestInterpreter.interpret("save it", session.model().spec));

        ObjectSession again = new ObjectSession();
        ObjectModel opened = new ObjectStore(dir).open("motorcycle");
        assertNotNull(opened);
        again.adopt(opened);
        Component tyre = part(again.model(), "front tyre");
        assertFalse(tyre.visible);
        assertTrue(tyre.retained);
        assertEquals(pose[0], SceneCommands.centroid(tyre)[0], 1e-3);
        assertEquals(pose[1], SceneCommands.centroid(tyre)[1], 1e-3);
        int hidden = triangles(again.model());
        new CreationDirector(again, dir).perform(ObjectRequestInterpreter.interpret("put the front tyre back", again.model().spec));
        assertTrue(tyre.visible);
        assertTrue(triangles(again.model()) > hidden);
    }

    @Test
    public void aPictureAndSeveralPhotosAreNotAMeasuredMesh() {
        ObjectRequestInterpreter.Request picture = ObjectRequestInterpreter.interpret("show me a picture of a motorcycle", null);
        assertEquals(ObjectRequestInterpreter.Kind.IMAGE, picture.kind);
        ObjectRequestInterpreter.Request photos = ObjectRequestInterpreter.interpret("reconstruct a motorcycle from these photographs", null);
        assertEquals(ObjectRequestInterpreter.Kind.RECON, photos.kind);
        CreationRouter router = new CreationRouter();
        CreationRouter.Route image = router.classify("picture of a 3D motorcycle");
        assertEquals(CreationRouter.Capability.IMAGE_GENERATION, image.capability);
        assertFalse(image.available);
        CreationRouter.Route views = router.classify("reconstruct this from these photos");
        assertEquals(CreationRouter.Capability.MULTI_VIEW_RECONSTRUCTION, views.capability);
        assertFalse(views.available);
        CreationRouter.Route model = router.classify("build an editable motorcycle");
        assertEquals(CreationRouter.Capability.THREE_D_GENERATION, model.capability);
        assertTrue(model.available);
        assertTrue(model.note.toLowerCase().contains("not a measurement"));
    }
}
