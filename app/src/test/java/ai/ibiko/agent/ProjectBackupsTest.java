package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.junit.Test;

/** An agent on one project reads that project's backups only: never another project's notes, record or the task states. */
public class ProjectBackupsTest {
    @Test
    public void onlyThisProjectsNotesAndRecordMayBeRead() {
        String bp = ".blueprint/shop-app", rec = "memory/projects/section-shop";
        assertNull(CodeAgent.othersRecord(".blueprint/shop-app/MAP.md", bp, rec));
        assertNull(CodeAgent.othersRecord("memory/projects/section-shop/RECORD.md", bp, rec));
        assertNull("the project's own code", CodeAgent.othersRecord("shop-app/src/Main.java", bp, rec));
        assertNotNull(CodeAgent.othersRecord(".blueprint/bank-app/SURVEY.md", bp, rec));
        assertNotNull(CodeAgent.othersRecord("memory/projects/section-bank/RECORD.md", bp, rec));
        assertNotNull(CodeAgent.othersRecord("memory/tasks/42.json", bp, rec));
        assertNotNull("no record of its own: none may be read", CodeAgent.othersRecord("memory/projects/x/RECORD.md", bp, null));
    }

    private static File workspace() throws Exception {
        File ws = Files.createTempDirectory("ws").toFile();
        for (String p : new String[] {"shop-app/src/Main.java", ".blueprint/shop-app/MAP.md", ".blueprint/bank-app/MAP.md",
                "memory/projects/section-shop/RECORD.md", "memory/projects/section-bank/RECORD.md", "memory/tasks/1.json"}) {
            File f = new File(ws, p);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), ("secret of " + p + "\n").getBytes("UTF-8"));
        }
        return ws;
    }

    @Test
    public void anAgentOnOneProjectCannotOpenListOrSearchAnothersBackups() throws Exception {
        File ws = workspace();
        CodeAgent a = new CodeAgent(ws, (s, t) -> null, new CodeAgentTestHelper.Quiet(), "", 20_000)
                .ownRecord("memory/projects/section-shop/RECORD.md");
        java.lang.reflect.Field bp = CodeAgent.class.getDeclaredField("ownBlueprint");
        bp.setAccessible(true);
        bp.set(a, ".blueprint/shop-app");
        assertTrue(a.inside(".blueprint/shop-app/MAP.md").isFile());
        assertTrue(a.inside("memory/projects/section-shop/RECORD.md").isFile());
        for (String other : new String[] {".blueprint/bank-app/MAP.md", "memory/projects/section-bank/RECORD.md", "memory/tasks/1.json",
                "shop-app/../.blueprint/bank-app/MAP.md"}) {
            try {
                a.inside(other);
                fail("opened " + other);
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("project") || expected.getMessage().contains("task"));
            }
        }
        Method list = CodeAgent.class.getDeclaredMethod("listFiles", String.class);
        list.setAccessible(true);
        String listed = (String) list.invoke(a, ".");
        assertTrue(listed, listed.contains("shop-app") && listed.contains(".blueprint/shop-app") && listed.contains("section-shop"));
        assertTrue(listed, !listed.contains("bank-app") && !listed.contains("section-bank") && !listed.contains("1.json"));
        Method search = CodeAgent.class.getDeclaredMethod("search", String.class);
        search.setAccessible(true);
        String found = (String) search.invoke(a, "secret of");
        assertTrue(found, found.contains("shop-app") && !found.contains("bank") && !found.contains("tasks"));
        assertNotNull(a.othersRecordIn("cat .blueprint/bank-app/MAP.md"));
        assertNotNull(a.othersRecordIn("ls memory/tasks"));
        assertNull(a.othersRecordIn("cat .blueprint/shop-app/MAP.md && ls shop-app"));
        assertEquals(null, a.othersRecordIn("gradle test"));
    }
}
