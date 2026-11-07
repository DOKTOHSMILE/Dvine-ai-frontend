package ai.dvine.ops;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import ai.dvine.ops.task.DvineCommandGateway;
import ai.dvine.ops.task.TaskRecord;
import ai.dvine.ops.task.TaskState;

public class DvineCommandGatewayTest {
    @Test
    public void ordinaryDiscussionAndModelCommandsCannotControlTasks() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord task = running(core, "build android app");

        core.hear("What does the word stop mean?", 1);
        core.hear("What happens if I cancel this project?", 1);
        core.hear("Someone told me to stop the project.", 1);
        core.hear("// Stop the current process before continuing.", 1);
        core.hear("Cancel the current task.", 1);
        assertEquals(TaskState.RUNNING, task.state);

        DvineCommandGateway.Result model = core.commandGateway().rejectModelCommand(DvineCommandGateway.Action.CANCEL, task.id);
        assertFalse(model.accepted);
        assertEquals(TaskState.RUNNING, task.state);
    }

    @Test
    public void voiceControlRequiresExplicitModeConfirmationAndSpecificTarget() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord other = running(core, "website");
        TaskRecord selected = running(core, "android app");

        DvineCommandGateway.VoiceSession session = core.commandGateway().beginVoiceCommandMode();
        DvineCommandGateway.Result result = core.commandGateway().submitVoiceCommand(
                session, DvineCommandGateway.Action.CANCEL, selected.id, false);
        assertFalse(result.accepted);
        assertEquals(TaskState.RUNNING, selected.state);

        DvineCommandGateway.VoiceSession confirmedSession = core.commandGateway().beginVoiceCommandMode();
        result = core.commandGateway().submitVoiceCommand(
                confirmedSession, DvineCommandGateway.Action.CANCEL, selected.id, true);
        assertTrue(result.accepted);
        assertEquals(TaskState.CANCELLED, selected.state);
        assertEquals(TaskState.PAUSED, other.state);
    }

    @Test
    public void onlyExplicitVoiceModeCanPauseAndResumeTheSelectedTask() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord task = running(core, "current project");
        DvineCommandGateway.Result absent = core.commandGateway().submitVoiceCommand(
                null, DvineCommandGateway.Action.PAUSE, task.id, false);
        assertFalse(absent.accepted);
        assertEquals(TaskState.RUNNING, task.state);

        DvineCommandGateway.VoiceSession session = core.commandGateway().beginVoiceCommandMode();
        assertTrue(core.commandGateway().submitVoiceCommand(session,
                DvineCommandGateway.Action.PAUSE, task.id, false).accepted);
        assertEquals(TaskState.PAUSED, task.state);
        assertTrue(core.commandGateway().submitButtonCommand(
                DvineCommandGateway.Action.RESUME, task.id, false).accepted);
        assertEquals(TaskState.RUNNING, task.state);
    }

    @Test
    public void stopFailsClosedInsteadOfBeingAnAliasForCancel() throws Exception {
        AssistantCore core = AssistantCore.offline(OpsPipelineTest.dir());
        TaskRecord task = running(core, "build");
        DvineCommandGateway.Result result = core.commandGateway().submitButtonCommand(
                DvineCommandGateway.Action.STOP, task.id, true);
        assertFalse(result.accepted);
        assertEquals(TaskState.RUNNING, task.state);
    }

    private static TaskRecord running(AssistantCore core, String title) {
        TaskRecord task = core.tasks().create(title, title, "general", List.of("work"), false, System.currentTimeMillis());
        core.tasks().markRunning(task, System.currentTimeMillis() + 1);
        return task;
    }
}
