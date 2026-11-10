package ai.ibiko.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import ai.ibiko.ops.mind.LanguageInferenceOptimizer.InferenceScheduler;
import ai.ibiko.ops.mind.ModelRuntimeManager;
import ai.ibiko.ops.pipe.CapabilityRegistry;
import ai.ibiko.ops.pipe.RequestGateway;
import ai.ibiko.ops.pipe.SemanticGraph;
import ai.ibiko.ops.pipe.StructuredActionPlan;
import ai.ibiko.ops.recon.EditableScene;
import ai.ibiko.ops.recon.MultiViewReconstructionManager;
import ai.ibiko.ops.task.TaskControl;
import ai.ibiko.ops.task.TaskRecord;
import ai.ibiko.ops.task.TaskState;

public class BrainPipelineTest {
    @Test
    public void theSameRequestIsNotASecondTask() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord first = core.tasks().create("inspect the server", "Server inspection", "ssh", List.of("connect"), false, 1, "req-7");
        TaskRecord retry = core.tasks().create("inspect the server again", "Server inspection", "ssh", List.of("connect"), false, 2, "req-7");
        assertEquals(first.id, retry.id);
        assertEquals("req-7:ssh:1", first.idempotencyKey);
        assertFalse(first.idempotencyKey.equals(Integer.toHexString("inspect the server|ssh".hashCode())));
        TaskRecord other = core.tasks().create("inspect the server", "Server inspection", "ssh", List.of("connect"), false, 3, null);
        assertFalse(first.id.equals(other.id));
    }

    @Test
    public void safetyBeatsCodingAndAQuestionIsNotAYes() {
        ai.ibiko.ops.pipe.BrainCoordinator.Decision safe = new ai.ibiko.ops.pipe.BrainCoordinator().decide("is ssh safe", null);
        assertEquals("obi", safe.persona);
        ai.ibiko.ops.pipe.BrainCoordinator.Decision code = new ai.ibiko.ops.pipe.BrainCoordinator().decide("fix this bug in the login", null);
        assertEquals("ibiko", code.persona);
        ai.ibiko.ops.pipe.BrainCoordinator.Decision stopped = new ai.ibiko.ops.pipe.BrainCoordinator().decide("don't compile this", null);
        assertEquals("", stopped.persona);
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        Outcome asked = core.hear("delete the file", 1);
        assertTrue(asked.handled);
        assertTrue(asked.speech.toLowerCase().contains("not deleted") || asked.speech.toLowerCase().contains("confirm"));
        Outcome next = core.hear("what is a triangle", 1);
        assertFalse(next.speech.toLowerCase().contains("left it"));
    }

    @Test
    public void aBusyStepIsNotReportedCancelledUntilItStops() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord task = core.tasks().create("build", "Motorcycle reconstruction", "reconstruct", List.of("build"), false, 1);
        core.tasks().markRunning(task, 2);
        TaskControl control = core.tasks().attach(task);
        control.working();
        core.tasks().cancel(task, 3);
        assertEquals(TaskState.CANCEL_REQUESTED, OpsPipelineTest.reloaded(core, task.id).state);
        assertTrue(control.isCancelled());
        control.stopped();
        core.tasks().noteStopped(task, 4);
        assertEquals(TaskState.CANCELLED, OpsPipelineTest.reloaded(core, task.id).state);
    }

    @Test
    public void theSchedulerRefusesWorkWhenItIsFull() throws Exception {
        InferenceScheduler scheduler = new InferenceScheduler(1);
        try {
            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            assertTrue(scheduler.submit(() -> {
                started.countDown();
                try {
                    hold.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertFalse(scheduler.submit(() -> {
            }));
            assertTrue(scheduler.rejected() >= 1);
            hold.countDown();
            scheduler.awaitIdle(2000);
            assertEquals(1, scheduler.ran());
        } finally {
            scheduler.close();
        }
    }

    @Test
    public void theModelIsNotReadyUntilTheRuntimeSaysSo() {
        ModelRuntimeManager runtime = new ModelRuntimeManager();
        runtime.load("qwen3-1.7b-q4km.gguf");
        runtime.onMicrophone();
        runtime.load("qwen3-1.7b-q4km.gguf");
        assertEquals(1, runtime.loads());
        assertEquals(ModelRuntimeManager.Phase.LOADING, runtime.phase());
        assertFalse(runtime.ready());
        runtime.confirmReady("other");
        assertFalse(runtime.ready());
        runtime.confirmReady("qwen3-1.7b-q4km.gguf");
        assertTrue(runtime.loaded());
    }

    @Test
    public void aCorrectionDoesNotRewriteGeneralKnowledge() {
        SemanticGraph graph = new SemanticGraph();
        graph.approveGeneral("tyre", "a rubber ring");
        graph.observe("motorcycle-1", "front_tyre", SemanticGraph.Relation.PART_OF, "front_wheel", "scene");
        graph.correct("motorcycle", "tyre", "front_tyre");
        assertEquals("a rubber ring", graph.general("tyre"));
        assertEquals("front_tyre", graph.correction("motorcycle", "tyre"));
        assertEquals("front_wheel", graph.related("motorcycle-1", "front_tyre", SemanticGraph.Relation.PART_OF).get(0).object);
        graph.forgetCorrection("motorcycle", "tyre");
        assertNull(graph.correction("motorcycle", "tyre"));
        assertEquals("a rubber ring", graph.general("tyre"));
    }

    @Test
    public void extraPhotosAreNotCalledAReconstruction() {
        MultiViewReconstructionManager views = new MultiViewReconstructionManager();
        MultiViewReconstructionManager.Report one = views.assess(1);
        MultiViewReconstructionManager.Report many = views.assess(4);
        assertEquals(MultiViewReconstructionManager.Quality.SINGLE_IMAGE_APPROXIMATION, one.quality);
        assertEquals(MultiViewReconstructionManager.Quality.MULTI_VIEW_UNSUPPORTED, many.quality);
        assertFalse(one.measured);
        assertFalse(many.measured);
        assertTrue(many.note.contains("not measured"));
        assertTrue(new CapabilityRegistry().get("photogrammetry").available);
        assertTrue(new CapabilityRegistry().get("photogrammetry").honest.toLowerCase().contains("not a dense"));
        assertFalse(new CapabilityRegistry().get("ocr").available);
        assertTrue(new CapabilityRegistry().get("image-generation").available);
    }

    @Test
    public void aFullQueueDoesNotRunTheRejectedJob() throws Exception {
        AtomicInteger ran = new AtomicInteger();
        InferenceScheduler scheduler = new InferenceScheduler(1);
        try {
            CountDownLatch hold = new CountDownLatch(1);
            assertTrue(scheduler.submit(() -> {
                try {
                    hold.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ran.incrementAndGet();
            }));
            Thread.sleep(30);
            assertFalse(scheduler.submit(ran::incrementAndGet));
            hold.countDown();
            scheduler.awaitIdle(2000);
            assertEquals(1, ran.get());
        } finally {
            scheduler.close();
        }
    }
}
