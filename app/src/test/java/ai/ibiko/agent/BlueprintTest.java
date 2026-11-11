package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The planning process: understand, plan, check, build, verify; additions re-checked; no going in circles. */
public class BlueprintTest {
    static final String GOOD = "# Blueprint: dark mode\nPhase: CHECK\n## Goal\nA dark theme the user can switch on in Settings.\n"
            + "## Found\nSettingsActivity.java:toggle, Ui.java:colors, values-night/colors.xml\n"
            + "## Reuse\nSettingsActivity.toggle for the switch; Ui colors\n"
            + "## Steps\n- [ ] 1. Add the switch (files: SettingsActivity.java; check: SettingsTest)\n- [ ] 2. Night colors (files: values-night/colors.xml; check: read the file back)\n"
            + "## Risks\n- old screens keep light colors -> every screen reads Ui colors\n"
            + "## Check\ngradle testDebugUnitTest and open Settings\n## Changes\n";

    @Test
    public void realTasksFollowTheBlueprintQuickOnesDoNot() {
        assertTrue(Blueprint.applies("Add a dark mode to the settings"));
        assertTrue(Blueprint.applies("fix the login crash and upgrade the shell"));
        assertFalse(Blueprint.applies("what does this repo do"));
        assertFalse(Blueprint.applies("git status"));
        assertFalse("a quick job is done directly", Blueprint.applies("make a hello script"));
    }

    @Test
    public void nothingButThePlanChangesBeforeTheCheckPasses() {
        Blueprint b = new Blueprint();
        assertEquals(Blueprint.Phase.UNDERSTAND, b.phase());
        assertNull("reading is always allowed", b.gate("read_file", "app/Main.java"));
        assertNull("the plan itself can be written", b.gate("write_file", "app/PLAN.md"));
        String refused = b.gate("write_file", "app/Main.java");
        assertNotNull(refused);
        assertTrue(refused, refused.contains("PLAN.md is not written yet"));
        b.planned("app/PLAN.md", GOOD);
        assertEquals(Blueprint.Phase.CHECK, b.phase());
        assertNotNull(b.gate("edit_file", "app/Main.java"));
        String ok = b.planned("app/PLAN.md", GOOD.replace("Phase: CHECK", "Phase: BUILD"));
        assertTrue(ok, ok.startsWith("Blueprint checked"));
        assertEquals(Blueprint.Phase.BUILD, b.phase());
        assertNull(b.gate("edit_file", "app/Main.java"));
    }

    @Test
    public void theCheckFindsTheLoopholes() {
        assertTrue(Blueprint.check(GOOD).isEmpty());
        List<String> gaps = Blueprint.check("# Blueprint: x\nPhase: CHECK\n## Goal\nMake it faster everywhere.\n## Steps\n- [ ] 1. Do it\n"
                + "## Risks\n- might break\n");
        String all = String.join("|", gaps);
        assertTrue(all, all.contains("Found"));
        assertTrue(all, all.contains("Reuse"));
        assertTrue(all, all.contains("names no files"));
        assertTrue(all, all.contains("has no answer"));
        assertTrue(all, all.contains("Check"));
        assertTrue(String.join("|", Blueprint.check(GOOD + "TODO decide the color\n")).contains("open questions"));
        // a new file for something the code already has
        String dup = GOOD.replace("- [ ] 2. Night colors", "- [ ] 2. Create SettingsActivity.java again");
        assertTrue(String.join("|", Blueprint.check(dup)), String.join("|", Blueprint.check(dup)).contains("reuse or change it"));
    }

    @Test
    public void eachStepIsCheckedInOrderAndARepositoryHasItsFolder() {
        String noCheck = GOOD.replace("; check: SettingsTest", "");
        assertTrue(String.join("|", Blueprint.check(noCheck)), String.join("|", Blueprint.check(noCheck)).contains("'check:'"));
        String outOfOrder = GOOD.replace("- [ ] 2. Night", "- [ ] 3. Night");
        assertTrue(String.join("|", Blueprint.check(outOfOrder)).contains("not numbered in order"));
        String github = GOOD.replace("## Goal\n", "## Goal\nOn GitHub: ");
        assertTrue(String.join("|", Blueprint.check(github)).contains("'## Repository'"));
        assertTrue(Blueprint.check(github.replace("## Goal\n", "## Repository\nDOKTOHSMILE/app -> memory/projects/app\n## Goal\n")).isEmpty());
        assertTrue("a step is ticked only when its check passed", Blueprint.RULES.contains("only when the check passed"));
    }

    @Test
    public void buildWithGapsIsSentBackToTheCheck() {
        Blueprint b = new Blueprint();
        String said = b.planned("PLAN.md", "# Blueprint: x\nPhase: BUILD\n## Goal\nsomething big enough\n");
        assertTrue(said, said.startsWith("Blueprint check failed"));
        assertEquals(Blueprint.Phase.CHECK, b.phase());
    }

    @Test
    public void anAdditionReopensTheBlueprintAndUnfinishedStepsAreNotSkipped() {
        Blueprint b = new Blueprint();
        b.planned("PLAN.md", GOOD.replace("Phase: CHECK", "Phase: BUILD").replace("- [ ] 1.", "- [x] 1."));
        assertEquals("1 of 2", b.progress());
        assertTrue(b.unfinished(), b.unfinished().contains("Night colors"));
        b.reopen();
        assertEquals(Blueprint.Phase.CHECK, b.phase());
        assertTrue(b.unfinished().contains("addition"));
        assertNotNull("building waits for the re-check", b.gate("write_file", "app/Ui.java"));
        b.planned("PLAN.md", GOOD.replace("Phase: CHECK", "Phase: BUILD").replace("- [ ] ", "- [x] ")
                .replace("## Changes\n", "## Changes\n- the user asked for a schedule\n"));
        assertNull(b.unfinished());
    }

    @Test
    public void goingInCirclesIsStopped() {
        Blueprint b = new Blueprint();
        assertNull(b.circling("read_file", "a", "text"));
        assertNull(b.circling("read_file", "a", "text"));
        assertNotNull(b.circling("read_file", "a", "text"));
        assertNull(b.circling("run", "gradle", "error: cannot find symbol Foo\n[exit 1]"));
        String again = b.circling("run", "gradle build", "error: cannot find symbol Foo\n[exit 1]");
        assertTrue(again, again.contains("same failure"));
    }

    @Test
    public void thePhaseIsShownOncePerChange() {
        Blueprint b = new Blueprint();
        assertTrue(b.announce().startsWith("Understanding"));
        assertNull(b.announce());
        b.planned("PLAN.md", GOOD);
        assertEquals("Checking the blueprint for gaps", b.announce());
    }

    @Test
    public void fetchingARepositoryNeedsNoBlueprint() {
        assertFalse(Blueprint.applies("Clone https://github.com/me/app.git into the workspace and make it ready"));
        assertFalse(Blueprint.applies("Install the necessary files needed by Linux for this build"));
    }

    @Test
    public void aPlanIsAddedToNeverReplaced() {
        String old = "# Blueprint: notes\n## Steps\n- [x] 1. Make the list (files: a)\n- [ ] 2. Save notes (files: b)\n";
        assertEquals(null, Blueprint.kept(old, old + "- [ ] 3. Search notes (files: c)\n"));
        assertEquals("a priority may move up and steps renumber", null, Blueprint.kept(old,
                "## Steps\n- [x] 1. Make the list (files: a)\n- [ ] 2. Search notes (files: c)\n- [ ] 3. Save notes (files: b)\n"));
        assertEquals("ticking is the one edit", null, Blueprint.kept(old, old.replace("- [ ] 2.", "- [x] 2.")));
        String replaced = Blueprint.kept(old, "## Steps\n- [ ] 1. Build a chat app (files: z)\n");
        assertTrue(replaced, replaced != null && replaced.contains("Save notes") && replaced.contains("never replaced"));
        String unticked = Blueprint.kept(old, old.replace("- [x] 1.", "- [ ] 1."));
        assertTrue(unticked, unticked != null && unticked.contains("unticks finished work"));
    }

    @Test
    public void aShellCommandCannotWipeAPlanButMayAddToIt() {
        for (String bad : new String[] {"rm PLAN.md", "rm -f app/PLAN.md && git add .", "echo '# new' > PLAN.md",
                "mv draft.md PLAN.md", "truncate -s 0 todo.md", "sed -i 's/old//' Roadmap.md", "cat x | tee tasks.md"}) {
            assertTrue(bad, CodeAgent.wipesPlan(bad));
        }
        for (String fine : new String[] {"cat PLAN.md", "echo '- [ ] test' >> PLAN.md", "cat x | tee -a PLAN.md",
                "grep -n '\\[ \\]' PLAN.md", "cp PLAN.md backup/plan-copy.txt.bak", "rm build.log", "ls > files.txt"}) {
            assertFalse(fine, CodeAgent.wipesPlan(fine));
        }
    }

    @Test
    public void theWorkersSettingDecidesHowManyRunAtOnce() {
        assertEquals(8, ai.ibiko.chat.Tuning.workersAtOnce(8, true));
        assertEquals(16, ai.ibiko.chat.Tuning.workersAtOnce(16, true));
        assertEquals("the device's own model: two at once", 2, ai.ibiko.chat.Tuning.workersAtOnce(8, false));
        assertEquals(1, ai.ibiko.chat.Tuning.workersAtOnce(1, false));
        assertEquals(8, ai.ibiko.chat.Tuning.WORKERS.def);
        assertEquals(16, ai.ibiko.chat.Tuning.WORKERS.max);
        assertEquals(8, Squad.MOST_HANDS);
    }
}
