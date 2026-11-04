package ai.dvine.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DevDocsTest {
    @Test
    public void aPageFileIsReadOnePageAtATime() throws Exception {
        String db = "{\"index\":\"<h1>JavaScript</h1>\",\"global_objects/array/map\":\"<h1>Array.prototype.map()</h1>"
                + "<p>Creates a new array with the results of calling a function.</p><pre>const doubled = [1, 2].map((x) => x * 2);\\n"
                + "// \\\"quoted\\\" \\u00e9</pre>\"}";
        final List<String> paths = new ArrayList<String>(), texts = new ArrayList<String>();
        int n = DocsStream.each(new StringReader(db), (p, h) -> {
            paths.add(p);
            texts.add(DocsStream.text(h));
        });
        assertEquals(2, n);
        assertEquals("global_objects/array/map", paths.get(1));
        assertTrue(texts.get(1), texts.get(1).startsWith("Array.prototype.map()\nCreates a new array"));
        assertTrue("code kept, escapes read", texts.get(1).contains("const doubled = [1, 2].map((x) => x * 2);\n// \"quoted\" \u00e9"));
        assertEquals("operators/addition", DocsStream.pageOf("operators/addition#syntax"));
        // the real JavaScript set, when it was downloaded beside the tests (DEVDOCS_JS=path/to/db.json)
        String real = System.getenv("DEVDOCS_JS");
        if (real != null) {
            final int[] pages = {0};
            try (java.io.Reader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(real), "UTF-8"))) {
                DocsStream.each(r, (p, h) -> pages[0]++);
            }
            System.out.println("real JavaScript set: " + pages[0] + " pages");
            assertTrue(pages[0] > 500);
        }
    }

    @Test
    public void theCatalogOffersTheNewestOfEachSet() {
        String json = "[{\"slug\":\"kotlin~2\",\"name\":\"Kotlin\",\"release\":\"2.4.20\",\"db_size\":81586953},"
                + "{\"slug\":\"kotlin~1.9\",\"name\":\"Kotlin\",\"release\":\"1.9.0\",\"db_size\":1},"
                + "{\"slug\":\"javascript\",\"name\":\"JavaScript\",\"db_size\":12370828},"
                + "{\"slug\":\"jquery\",\"name\":\"jQuery\",\"db_size\":5}]";
        List<DevDocs.Set> sets = DevDocs.parseCatalog(json);
        assertEquals("in the offered order, jQuery not offered", 2, sets.size());
        assertEquals("kotlin~2", sets.get(0).slug);
        assertEquals("Kotlin 2.4.20", sets.get(0).shown());
        assertEquals("javascript", sets.get(1).slug);
        assertEquals("map* array*", DevDocs.matchOf("map() array"));
    }

    @Test
    public void searchesAndReadsWithNoInternet() {
        DevDocs d = new DevDocs(RuntimeEnvironment.getApplication());
        d.put("python~3.14", "Python", "library/stdtypes", "list.sort()", "<h1>list.sort(key=None, reverse=False)</h1><p>Sorts the list in place.</p>");
        d.put("javascript", "JavaScript", "global_objects/array/sort", "Array.prototype.sort()",
                "<h1>Array.prototype.sort()</h1><p>Sorts the elements of an array in place.</p>");
        d.put("javascript", "JavaScript", "global_objects/array/map", "Array.prototype.map()", "<p>Creates a new array.</p>");
        assertEquals(2, d.installed().size());
        List<DevDocs.Hit> all = d.search("sort", 8);
        assertEquals(2, all.size());
        List<DevDocs.Hit> py = d.search("python: sort", 8);
        assertEquals("one set only", 1, py.size());
        assertEquals("library/stdtypes", py.get(0).path);
        String page = d.read("javascript", "global_objects/array/sort#syntax", 6000);
        assertNotNull(page);
        assertTrue(page, page.contains("Sorts the elements of an array in place."));
        assertNull(d.read("javascript", "nowhere", 6000));
        String said = d.forModel("array sort", 8);
        assertTrue(said, said.contains("[javascript global_objects/array/sort]"));
        d.remove("python~3.14");
        assertEquals(1, d.installed().size());
    }
}
