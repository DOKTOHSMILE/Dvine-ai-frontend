package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Commands;
import org.junit.Test;

/** Ibiko 1 runs code tasks itself (as one approved shell command) and works every control of the app. */
public class CodeTasksTest {
    private static String code(String m) {
        return Commands.code(m, c -> true);
    }

    @Test
    public void commandsTypedAsTheyAre() {
        assertEquals("§SH git status", code("git status"));
        assertEquals("§SH ls -la /workspace", code("ls -la /workspace"));
        assertEquals("§SH python3 app.py", code("python3 app.py"));
        assertEquals("§SH npm test", code("run `npm test`"));
        assertEquals("§SH uname -a", code("run the command uname -a"));
        assertEquals("§SH pwd", code("$ pwd"));
        assertNull("a sentence, not a command", code("cat videos are funny"));
        assertNull(code("top 10 movies"));
        assertNull(code("go home"));
    }

    @Test
    public void codeTasksInPlainWords() {
        assertEquals("§SH ls -la /workspace", code("list my files"));
        assertEquals("§SH ls -la '/workspace/App'", code("show the files in /workspace/App"));
        assertEquals("§SH cat -- 'notes.txt'", code("read the file notes.txt"));
        assertEquals("§SH cd /workspace && git clone 'https://github.com/DOKTOHSMILE/Dvine-ai-backend'",
                code("clone DOKTOHSMILE/Dvine-ai-backend"));
        assertEquals("§SH pkg install nodejs", code("install nodejs"));
        assertEquals("§SH mkdir -p -- 'demo'", code("make a folder called demo"));
        assertEquals("§SH python3 --version", code("is python installed?"));
        assertEquals("§SH df -h", code("how much space is left"));
        assertEquals("§SH pwd", code("where am i"));
    }

    @Test
    public void pastedCodeIsWrittenThenRun() {
        String sh = code("run this\n```python\nprint('hi')\nfor i in range(2):\n    print(i)\n```");
        assertTrue(sh, sh.startsWith("§SH mkdir -p /work && printf '%s\\n' 'print('\\''hi'\\'')' 'for i in range(2):' '    print(i)' > /work/ibiko_run.py && "));
        assertTrue(sh, sh.endsWith("{ command -v python3 >/dev/null 2>&1 || apk add -q python3; } && python3 /work/ibiko_run.py"));
        assertTrue("one line, as a function code is", !sh.contains("\n"));
        assertTrue(code("execute:\n```js\nconsole.log(1)\n```").endsWith("node /work/ibiko_run.js"));
        assertNull("code shown, not asked to run", code("what does this do?\n```python\nprint(1)\n```"));
    }

    @Test
    public void realWorkGoesToTheAgents() {
        assertEquals("§WK", code("fix the login bug in my app"));
        assertEquals("§WK", code("write a python script that renames my photos"));
        assertEquals("§WK", code("build a website for my shop"));
        assertEquals("a picture to make goes to the image tool", "§IM Generate a call icon", code("Generate a call icon"));
        assertEquals("§WP", code("write a story about a lion"));
    }

    @Test
    public void theAppsControls() {
        assertEquals("§YS", code("go ahead"));
        assertEquals("§CX", code("cancel that"));
        assertEquals("§PM", code("plan mode on"));
        assertEquals("§AO", code("auto mode on"));
        assertEquals("§AF", code("turn off auto mode"));
        assertEquals("§CO", code("carry on with the task"));
        assertEquals("§CA 17 * 23", code("calculate 17 * 23"));
        assertEquals("§UZ", code("unzip the file"));
        assertEquals("§FZ", code("find my zip files"));
        assertEquals("§RF", code("read the workspace files"));
        assertEquals("§TT", code("think together about moving to Lagos"));
        assertEquals("§OS", code("open settings"));
        assertNull("a tool that is off is never used", Commands.code("git status", c -> !"SH".equals(c)));
        assertNull("questions stay questions", code("what is the capital of Nigeria"));
    }
}
