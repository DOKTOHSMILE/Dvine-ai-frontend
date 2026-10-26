package ai.dvine.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.junit.Test;

import ai.dvine.anre.TransformationController.State;
import ai.dvine.create.ObjectModel;
import ai.dvine.create.ObjectRequestInterpreter;
import ai.dvine.create.ProceduralModelingEngine;
import ai.dvine.create.Provenance;
import ai.dvine.face.FaceRig;
import ai.dvine.human.HumanAsset;

/** The whole loop on the real stage and renderer (fake GL): body → object → edits → body, cancel, and the download bar. */
public class HumanStageAnreTest {
    private float t;

    private void run(HumanStage stage, WireRenderer r, float seconds) {
        FaceRig rig = new FaceRig(false);
        for (float end = t + seconds; t < end; t += 1 / 30f) {
            stage.camera.width = 1080;
            stage.camera.height = 2000;
            stage.camera.cx = 540;
            stage.camera.cy = 1100;
            stage.camera.unit = 380;
            stage.camera.distance = 4.4f;
            stage.camera.update();
            r.setCamera(stage.camera, -4.22f);
            stage.frame(t, false, rig, true, false, 1f);
            r.onDraw((long) (t * 1e9));
        }
    }

    @Test
    public void theBodyBecomesAnObjectAndComesBackUnchanged() throws Exception {
        HumanAsset obi;
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/obi.dvhm"))) {
            obi = HumanAsset.read(in);
        }
        float[] canonical = obi.mesh.pos.clone();
        WireRendererTest.FakeGl gl = new WireRendererTest.FakeGl();
        WireRenderer r = new WireRenderer(gl);
        r.onCreated();
        r.onSize(1080, 2000);
        HumanStage stage = new HumanStage(r);
        stage.install(0, obi, true);
        run(stage, r, 0.5f);
        assertEquals("Mode C: it assembles first", State.BODY_RECONSTRUCTING, stage.morphState(false));
        run(stage, r, 4f);
        assertEquals(State.BODY_INTACT, stage.morphState(false));

        ObjectModel car = stage.objects.create(ObjectRequestInterpreter.interpret("build a car", null).spec, new ArrayList<Provenance>());
        assertNotNull(car);
        assertTrue(stage.becomeObject(false, car));
        run(stage, r, 1f);
        assertTrue(stage.objectMode(false));
        run(stage, r, 4f);
        assertEquals(State.OBJECT_COMPLETE, stage.morphState(false));
        // a tap on the object selects a real part (somewhere on it)
        boolean hitSomething = false;
        for (int y = 900; y < 1700 && !hitSomething; y += 40) {
            hitSomething = stage.pickObject(540, y, false) != null;
        }
        assertTrue("a tap on the car finds a part", hitSomething);
        // an edit re-forms the particles
        assertNotNull(stage.objects.modify(ObjectRequestInterpreter.interpret("make the wheels larger", stage.objects.model().spec).spec));
        assertTrue(stage.objectChanged(false));
        assertEquals(State.OBJECT_MODIFYING, stage.morphState(false));
        run(stage, r, 2f);
        assertEquals(State.OBJECT_COMPLETE, stage.morphState(false));
        // home
        assertTrue(stage.restoreBody(false));
        run(stage, r, 5f);
        assertEquals(State.BODY_INTACT, stage.morphState(false));
        assertArrayEquals("the canonical body is never changed", canonical, obi.mesh.pos, 0f);

        // interrupted half way: the particles turn round and the body is back
        ObjectModel table = ProceduralModelingEngine.build(ObjectRequestInterpreter.interpret("make a table", null).spec, 1, new ArrayList<Provenance>());
        assertTrue(stage.becomeObject(false, table));
        run(stage, r, 1.5f);
        assertTrue(stage.restoreBody(false));
        assertEquals(State.BODY_RECONSTRUCTING, stage.morphState(false));
        run(stage, r, 4f);
        assertEquals(State.BODY_INTACT, stage.morphState(false));

        // the download bar: the body's particles become a bar that fills, then the body returns
        stage.progress(false, 0.1f);
        run(stage, r, 4.5f);
        assertEquals(State.OBJECT_COMPLETE, stage.morphState(false));
        stage.progress(false, 0.6f);
        assertEquals(State.OBJECT_MODIFYING, stage.morphState(false));
        run(stage, r, 2f);
        stage.progress(false, 1f);
        run(stage, r, 2f);
        stage.progress(false, -1f);
        run(stage, r, 5f);
        assertEquals(State.BODY_INTACT, stage.morphState(false));
        assertEquals(0, r.glErrors);
        r.dispose();
        assertEquals(0, gl.live.size());
    }
}
