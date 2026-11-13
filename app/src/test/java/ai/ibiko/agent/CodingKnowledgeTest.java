package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Ibiko's coding knowledge: reading errors, the git playbook, the official docs, and lessons from its own fixes. */
public class CodingKnowledgeTest {
    private static ErrorReader.Problem first(String output) {
        List<ErrorReader.Problem> ps = ErrorReader.read(output);
        assertFalse("an error is found in: " + output, ps.isEmpty());
        return ps.get(0);
    }

    @Test
    public void javacAndGradleErrorsAreFoundUnderTheLog() {
        String log = "> Task :app:compileDebugJavaWithJavac\n"
                + "/home/u/app/src/main/java/a/Main.java:42: error: cannot find symbol\n"
                + "        Foo f = new Foo();\n"
                + "        ^\n"
                + "  symbol:   class Foo\n"
                + "  location: class Main\n"
                + "1 error\n\nFAILURE: Build failed with an exception.\n[exit 1]";
        ErrorReader.Problem p = first(log);
        assertEquals(ErrorReader.Kind.SYMBOL, p.kind);
        assertTrue(p.file, p.file.endsWith("Main.java"));
        assertEquals(42, p.line);
        assertTrue(p.message, p.message.contains("class Foo"));
        String said = ErrorReader.summary(log);
        assertTrue(said, said.startsWith("Error read: /home/u/app/src/main/java/a/Main.java:42: cannot find symbol"));
        assertTrue(said, said.contains("Read /home/u/app/src/main/java/a/Main.java around line 42 first."));
    }

    @Test
    public void aPythonTracebackGivesTheLastFrameAndTheException() {
        String tb = "Traceback (most recent call last):\n"
                + "  File \"/w/app.py\", line 3, in <module>\n"
                + "    import helpers\n"
                + "  File \"/w/helpers.py\", line 1, in <module>\n"
                + "    import requests\n"
                + "ModuleNotFoundError: No module named 'requests'\n[exit 1]";
        ErrorReader.Problem p = first(tb);
        assertEquals(ErrorReader.Kind.IMPORT, p.kind);
        assertEquals("/w/helpers.py", p.file);
        assertEquals(1, p.line);
        assertTrue(ErrorReader.hint(p).contains("dependency file"));
    }

    @Test
    public void otherToolchainsAreReadToo() {
        assertEquals(ErrorReader.Kind.TYPE, first("src/a.ts(7,3): error TS2322: Type 'string' is not assignable to type 'number'.").kind);
        ErrorReader.Problem rust = first("error[E0425]: cannot find value `x` in this scope\n --> src/main.rs:4:13\n  |\n");
        assertEquals("src/main.rs", rust.file);
        assertEquals(4, rust.line);
        assertEquals(ErrorReader.Kind.SYMBOL, rust.kind);
        ErrorReader.Problem c = first("main.c:9:5: error: expected ';' before 'return'");
        assertEquals(ErrorReader.Kind.SYNTAX, c.kind);
        assertEquals(9, c.line);
        ErrorReader.Problem node = first("/w/index.js:12\nTypeError: Cannot read properties of undefined (reading 'map')\n"
                + "    at render (/w/index.js:12:18)\n");
        assertEquals(ErrorReader.Kind.NULL, node.kind);
        ErrorReader.Problem java = first("Exception in thread \"main\" java.lang.NullPointerException: name is null\n"
                + "\tat a.Main.run(Main.java:17)\n");
        assertEquals(ErrorReader.Kind.NULL, java.kind);
        assertEquals("Main.java", java.file);
        assertEquals(17, java.line);
        assertEquals(ErrorReader.Kind.TEST, first("org.junit.ComparisonFailure: expected:<4> but was:<5>").kind);
        assertEquals(ErrorReader.Kind.COMMAND, first("sh: cargo: not found\n[exit 127]").kind);
        assertTrue(ErrorReader.read("All 12 tests passed.\n[exit 0]").isEmpty());
    }

    @Test
    public void ibikosOwnGitMessagesAreRead() {
        assertEquals(ErrorReader.Kind.GIT_BEHIND, first("Error: main has newer commits on GitHub; pull first").kind);
        assertEquals(ErrorReader.Kind.GIT_CONFLICT, first("Merged main.\nConflicts (your version is under <<<<<<< yours, theirs under >>>>>>> main; ...):\napp.py").kind);
        assertEquals(ErrorReader.Kind.GIT_AUTH, first("Error: GitHub said 403 (the token may lack access to this repository)").kind);
        assertTrue(ErrorReader.hint(first("Error: main has newer commits on GitHub; pull first")).contains("sync"));
        assertTrue(GitPlaybook.RULES.contains("never clone it again"));
        assertTrue(GitPlaybook.RULES.contains("<<<<<<< yours"));
    }

    @Test
    public void theSameErrorHasTheSameSignatureAndAMissingModuleKeepsItsName() {
        ErrorReader.Problem a = first("/w/a.py:3\nTraceback (most recent call last):\n  File \"/w/a.py\", line 3\nModuleNotFoundError: No module named 'requests'");
        ErrorReader.Problem b = first("Traceback (most recent call last):\n  File \"/other/b.py\", line 90\nModuleNotFoundError: No module named 'requests'");
        ErrorReader.Problem c = first("Traceback (most recent call last):\n  File \"/w/a.py\", line 3\nModuleNotFoundError: No module named 'flask'");
        assertEquals(ErrorReader.signature(a), ErrorReader.signature(b));
        assertNotEquals("another module is another fix", ErrorReader.signature(a), ErrorReader.signature(c));
        ErrorReader.Problem t1 = first("src/a.ts(7,3): error TS2322: Type 'string' is not assignable to type 'number'.");
        ErrorReader.Problem t2 = first("src/b.ts(99,1): error TS2322: Type 'boolean' is not assignable to type 'Date'.");
        assertEquals("the same kind of type error, other types", ErrorReader.signature(t1), ErrorReader.signature(t2));
    }

    @Test
    public void lessonsAreKeptAndCounted() throws Exception {
        File f = new File(Files.createTempDirectory("lessons").toFile(), "coding_lessons.tsv");
        CodingLessons l = new CodingLessons(f);
        assertNull(l.recall("IMPORT:no module named 'requests'"));
        l.learn("IMPORT:no module named 'requests'", "ran pip install requests");
        l.learn("IMPORT:no module named 'requests'", "ran pip install requests");
        CodingLessons again = new CodingLessons(f);
        assertEquals("ran pip install requests (it worked 2 times)", again.recall("IMPORT:no module named 'requests'"));
    }

    private static final String INDEX = "{\"entries\":[{\"name\":\"json.loads()\",\"path\":\"library/json#json.loads\",\"type\":\"x\"},"
            + "{\"name\":\"json.load()\",\"path\":\"library/json#json.load\",\"type\":\"x\"},"
            + "{\"name\":\"str.split()\",\"path\":\"library/stdtypes#str.split\",\"type\":\"x\"}],\"types\":[]}";
    private static final String PAGE = "<html><body><h1>json</h1><dl><dt id=\"json.load\">json.load(fp)</dt><dd>Read a file.</dd>"
            + "<dt id=\"json.loads\">json.loads(<em>s</em>, *, cls=None)</dt><dd><p>Deserialize <em>s</em> (a str) to a Python"
            + " object &amp; return it.</p></dd></dl></body></html>";

    @Test
    public void theDocsGiveTheEntryAndAreKeptForNextTime() throws Exception {
        File cache = Files.createTempDirectory("docs").toFile();
        final List<String> asked = new ArrayList<String>();
        DocsLookup.Fetch web = (url, max) -> {
            asked.add(url);
            return url.endsWith("index.json") ? INDEX : url.endsWith("library/json.html") ? PAGE : null;
        };
        String got = DocsLookup.lookup("python", "json.loads", cache, web);
        assertTrue(got, got.startsWith("Python 3.13 reference, json.loads() (library/json):"));
        assertTrue(got, got.contains("json.loads(s, *, cls=None)") && got.contains("to a Python object & return it."));
        assertTrue(got, got.contains("Also in the docs: json.load()"));
        assertEquals(Arrays.asList("https://documents.devdocs.io/python~3.13/index.json",
                "https://documents.devdocs.io/python~3.13/library/json.html"), asked);
        asked.clear();
        assertTrue(DocsLookup.lookup("py", "json.loads", cache, web).contains("Deserialize"));
        assertTrue("the second time it is read from the phone", asked.isEmpty());
        assertTrue(DocsLookup.lookup("klingon", "x", cache, web).startsWith("No documentation set"));
        assertTrue(DocsLookup.lookup("python", "zzzz", cache, web).startsWith("Nothing named"));
    }

    @Test
    public void aFailedDownloadIsNotKept() throws Exception {
        File cache = Files.createTempDirectory("docs").toFile();
        assertTrue(DocsLookup.lookup("go", "fmt.Println", cache, (url, max) -> "The page couldn't be fetched.").contains("couldn't be read"));
        assertFalse(new File(cache, "go/index.json").exists());
    }

    /** A scripted model and a phone whose shell answers from a list. */
    private static final class Script implements CodeAgent.Brain {
        final List<String> replies, seen = new ArrayList<String>(), systems = new ArrayList<String>();

        Script(String... r) {
            replies = new ArrayList<String>(Arrays.asList(r));
        }

        @Override
        public String ask(String system, String transcript) {
            systems.add(system);
            seen.add(transcript);
            return replies.isEmpty() ? null : replies.remove(0);
        }
    }

    private static class Phone implements CodeAgent.Host {
        final List<String> outputs;

        Phone(String... o) {
            outputs = new ArrayList<String>(Arrays.asList(o));
        }

        @Override public boolean allowRun(String command) { return true; }
        @Override public String run(String command, File workspace) { return outputs.isEmpty() ? "[exit 0]" : outputs.remove(0); }
        @Override public String fetch(String url) { return null; }
        @Override public String connector(String call) { return ""; }
        @Override public void step(String line) { }
        @Override public boolean stopped() { return false; }
    }

    @Test
    public void aFixIsLearnedAndOfferedTheNextTimeTheSameErrorComesUp() throws Exception {
        File lessons = new File(Files.createTempDirectory("lessons").toFile(), "coding_lessons.tsv");
        String failing = "Traceback (most recent call last):\n  File \"/w/calc.py\", line 1, in <module>\nNameError: name 'mth' is not defined\n[exit 1]";
        File ws = Files.createTempDirectory("ibiko").toFile();
        Files.write(new File(ws, "calc.py").toPath(), "import math\nprint(mth.pi)\n".getBytes());
        Script brain = new Script(
                "<tool name=\"run\" command=\"python3 calc.py\"/>",
                "<tool name=\"edit_file\" path=\"calc.py\"><old>print(mth.pi)</old><new>print(math.pi)</new></tool>",
                "<tool name=\"run\" command=\"python3 calc.py\"/>",
                "Done: calc.py runs.");
        new CodeAgent(ws, brain, new Phone(failing, "3.14159\n[exit 0]"), "", 20_000).lessons(lessons).run("run calc", "");
        assertTrue("the error is read for the model: " + brain.seen.get(1), brain.seen.get(1).contains("Error read: /w/calc.py:1: NameError"));
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("Likely: a name that doesn't exist"));

        // another day, another project: the same error
        File ws2 = Files.createTempDirectory("ibiko").toFile();
        Script later = new Script("<tool name=\"run\" command=\"python3 calc.py\"/>", "Done.");
        new CodeAgent(ws2, later, new Phone(failing), "", 20_000).lessons(lessons).run("run calc", "");
        assertTrue(later.seen.get(1), later.seen.get(1).contains("Seen before, and fixed by: in calc.py, `print(mth.pi)` → `print(math.pi)`"));
        assertTrue("the docs tool is offered", later.systems.get(0).contains("<tool name=\"docs\""));
    }

    @Test
    public void aCodingQuestionGetsTheOfficialEntryForTheModelWithoutTools() throws Exception {
        File cache = Files.createTempDirectory("docs").toFile();
        DocsLookup.Fetch web = (url, max) -> url.endsWith("index.json") ? INDEX : url.endsWith("library/json.html") ? PAGE : null;
        String brief = DocsLookup.brief("How do I use json.loads in Python to read a config.json file?", cache, web, 900);
        assertTrue(brief, brief.startsWith("From the official documentation"));
        assertTrue(brief, brief.contains("json.loads(s, *, cls=None)"));
        assertEquals("no language named: nothing looked up", "", DocsLookup.brief("how do I use json.loads", cache, web, 900));
        assertEquals("no API named: nothing", "", DocsLookup.brief("is Python a good first language?", cache, web, 900));
        // offline afterwards: what was read is still there
        assertTrue(DocsLookup.brief("python json.loads example", cache, (u, m) -> null, 900).contains("Deserialize"));
        assertEquals("python", DocsLookup.languageOfFile("/w/calc.py"));
        assertEquals("kotlin", DocsLookup.languageOfText("my Kotlin app crashes"));
    }

    @Test
    public void theDevicesModelGetsTheDocsUnderAnErrorWithoutAsking() throws Exception {
        File docs = Files.createTempDirectory("docs").toFile();
        File ws = Files.createTempDirectory("ibiko").toFile();
        String failing = "Traceback (most recent call last):\n  File \"/w/app.py\", line 2, in <module>\n"
                + "TypeError: json.loads() missing 1 required positional argument: 's'\n[exit 1]";
        Script brain = new Script("<tool name=\"run\" command=\"python3 app.py\"/>", "Done.");
        Phone phone = new Phone(failing) {
            @Override
            public String fetchRaw(String url, int maxBytes) {
                return url.endsWith("index.json") ? INDEX : url.endsWith("library/json.html") ? PAGE : null;
            }
        };
        new CodeAgent(ws, brain, phone, "", 20_000).compact(true).docsCache(docs).run("run app", "");
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("From the docs: Python 3.13 reference, json.loads()"));
    }
}
