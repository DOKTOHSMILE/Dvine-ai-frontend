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

    /** CodeAssist builds one module: Ibiko 1 is in it (its own folder in the app's sources), its job in the manifest. */
    @org.junit.Test
    public void codeAssistBuildsIbiko1InTheApp() throws Exception {
        org.junit.Assert.assertTrue(new File(new File("src/main/java/ai/ibiko/ibiko1").isDirectory() ? "src/main/java/ai/ibiko/ibiko1"
                : "app/src/main/java/ai/ibiko/ibiko1", "Ibiko1Model.java").isFile());
        org.junit.Assert.assertFalse("no source folder outside the module", read("module.toml").contains("../"));
        org.junit.Assert.assertTrue(read("src/main/AndroidManifest.xml").contains("ai.ibiko.ibiko1.LearningJob"));
    }
}
