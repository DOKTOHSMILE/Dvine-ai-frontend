package ai.ibiko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** The two build files agree: app/build.gradle (Gradle) and app/module.toml (CodeAssist on the device). */
public class BuildFilesTest {
    private static String read(String name) throws Exception {
        File f = new File(name);
        if (!f.isFile()) {
            f = new File("app/" + name);
        }
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    @Test
    public void versionsAndSdksMatch() throws Exception {
        String gradle = read("build.gradle"), toml = read("module.toml");
        assertNotNull(find(gradle, "versionCode (\\d+)"));
        assertEquals("versionCode", find(gradle, "versionCode (\\d+)"), find(toml, "versionCode = (\\d+)"));
        assertEquals("versionName", find(gradle, "versionName \"([^\"]+)\""), find(toml, "versionName = \"([^\"]+)\""));
        assertEquals("minSdk", find(gradle, "minSdk (\\d+)"), find(toml, "minSdk = (\\d+)"));
        assertEquals("targetSdk", find(gradle, "targetSdk (\\d+)"), find(toml, "targetSdk = (\\d+)"));
        assertEquals("compileSdk", find(gradle, "compileSdk (\\d+)"), find(toml, "compileSdk = (\\d+)"));
        for (String dep : new String[] {"androidx.appcompat:appcompat:", "androidx.core:core:"}) {
            assertEquals(dep, find(gradle, Pattern.quote(dep) + "([\\d.]+)"), find(toml, Pattern.quote(dep) + "([\\d.]+)"));
        }
    }

    /** CodeAssist builds one module: Ibiko 1's code and knowledge are listed in it, and its job is in the app's manifest. */
    @org.junit.Test
    public void codeAssistBuildsIbiko1InTheApp() throws Exception {
        String toml = read("module.toml");
        org.junit.Assert.assertTrue(toml.contains("\"../ibiko1/src/main/java\""));
        org.junit.Assert.assertTrue(toml.contains("\"../ibiko1/src/main/assets\""));
        org.junit.Assert.assertTrue(read("src/main/AndroidManifest.xml").contains("ai.ibiko.ibiko1.LearningJob"));
    }
}
