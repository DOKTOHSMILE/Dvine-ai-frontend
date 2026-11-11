package ai.ibiko;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Ibiko's own icon set: every icon two-tone in the app's colour (no grey left), and every one drawn inside its own
 * canvas (an icon whose points fall outside its viewport shows as nothing).
 */
public class IconSetTest {
    @Test
    public void everyIconIsTheAppsOwnAndVisible() throws Exception {
        File dir = new File("src/main/res/drawable").isDirectory() ? new File("src/main/res/drawable") : new File("app/src/main/res/drawable");
        File[] icons = dir.listFiles((d, n) -> n.startsWith("ic_") && n.endsWith(".xml"));
        int ours = 0;
        for (File f : icons) {
            String x = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (!x.contains("Ibiko icon")) {
                continue; // the app icon and the social networks' own marks
            }
            ours++;
            assertFalse(f.getName() + " is grey", x.contains("@color/icon\"") || x.contains("@color/text_secondary"));
            Matcher vp = Pattern.compile("viewportWidth=\"([\\d.]+)\"").matcher(x);
            assertTrue(vp.find());
            double size = Double.parseDouble(vp.group(1));
            Matcher path = Pattern.compile("pathData=\"([^\"]+)\"").matcher(x);
            double most = 0;
            while (path.find()) {
                Matcher n = Pattern.compile("[\\d.]+").matcher(path.group(1));
                while (n.find()) {
                    most = Math.max(most, Double.parseDouble(n.group()));
                }
            }
            assertTrue(f.getName() + " is drawn outside its canvas", most <= size + 0.5);
            assertTrue(f.getName() + " is drawn too small to see", most > size / 2);
        }
        assertTrue(ours >= 70);
    }
}
