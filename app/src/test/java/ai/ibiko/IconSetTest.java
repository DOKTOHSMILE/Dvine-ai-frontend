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
 * canvas (an icon whose points fall outside its viewport shows as nothing), and every one one of the two sizes
 * ({@link IconSize}): 24 dp on its own, 18 dp beside words.
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
            Matcher w = Pattern.compile("android:width=\"(\\d+)dp\" android:height=\"(\\d+)dp\"").matcher(x);
            assertTrue(f.getName(), w.find());
            int size0 = Integer.parseInt(w.group(1));
            assertTrue(f.getName() + " is " + size0 + " dp, not one of the two sizes",
                    w.group(1).equals(w.group(2)) && (size0 == IconSize.STANDARD || size0 == IconSize.INLINE));
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

    /** No icon in the code is sized by a loose number: every size comes from IconSize. */
    @Test
    public void codeSizesIconsFromTheScale() throws Exception {
        File src = new File("src/main/java").isDirectory() ? new File("src/main/java") : new File("app/src/main/java");
        java.util.List<File> files = new java.util.ArrayList<File>();
        java.util.ArrayDeque<File> todo = new java.util.ArrayDeque<File>();
        todo.add(src);
        while (!todo.isEmpty()) {
            File d = todo.pop();
            for (File f : d.listFiles()) {
                if (f.isDirectory()) {
                    todo.add(f);
                } else if (f.getName().endsWith(".java")) {
                    files.add(f);
                }
            }
        }
        Pattern loose = Pattern.compile("Ui\\.(?:glyph|icon)\\([^;]*, \\d+\\)");
        for (File f : files) {
            String x = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher m = loose.matcher(x);
            assertFalse(f.getName() + ": " + (m.find() ? m.group() : ""), loose.matcher(x).find());
        }
    }
}
