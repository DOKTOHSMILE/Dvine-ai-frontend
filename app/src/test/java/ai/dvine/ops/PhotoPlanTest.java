package ai.dvine.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ai.dvine.ops.mind.OpenSearch;
import ai.dvine.ops.pipe.MachinePlan;
import ai.dvine.render.field.PhotoFrame;
import ai.dvine.render.field.PhotoLookup;

public class PhotoPlanTest {
    @Test
    public void aDrawingIsAPhotographLookupAndABuildStaysAModel() {
        MachinePlan pen = MachinePlan.read("draw me a pen");
        assertEquals(MachinePlan.Op.LOOK_UP_PHOTOS, pen.op);
        assertEquals("pen", pen.subject);
        MachinePlan design = MachinePlan.read("design a red bicycle");
        assertEquals(MachinePlan.Op.LOOK_UP_PHOTOS, design.op);
        assertTrue(design.subject.contains("bicycle"));
        assertEquals(MachinePlan.Op.OTHER, MachinePlan.read("build a red motorcycle").op);
        assertEquals(MachinePlan.Op.LOOK_UP_PHOTOS, MachinePlan.read("form a dragon").op);
    }

    @Test
    public void commonsUrlsAreRealFilesAndTheCropIsTheMiddle() {
        String json = "{\"query\":{\"pages\":[{\"imageinfo\":[{\"thumburl\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Bike.jpg/640px-Bike.jpg\",\"url\":\"https://upload.wikimedia.org/wikipedia/commons/a/ab/Bike.jpg\"}]}]}}";
        List<String> urls = PhotoLookup.urls(json, 4);
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("upload.wikimedia.org"));
        assertTrue(PhotoLookup.commonsUrl("bicycle", 4).contains("commons.wikimedia.org"));
        assertEquals(0, PhotoLookup.urls("{\"url\":\"https://evil.example/a.jpg\"}", 4).size());
        int[] box = PhotoFrame.middle(100, 80, 0.72f);
        assertEquals(14, box[0]);
        assertEquals(11, box[1]);
        assertEquals(72, box[2]);
        assertEquals(58, box[3]);
    }

    @Test
    public void webAndSocialTextComeOnlyFromTheirOwnPages() {
        assertTrue(OpenSearch.duckUrl("capital of France").contains("duckduckgo.com"));
        assertEquals("Paris is the capital of France and has been for centuries.",
                OpenSearch.duckText("{\"AbstractText\":\"Paris is the capital of France and has been for centuries.\"}"));
        assertEquals(null, OpenSearch.duckText("{\"query\":{\"pages\":[{\"extract\":\"short\"}]}}"));
        String reddit = "{\"kind\":\"Listing\",\"data\":{\"children\":[{\"data\":{\"title\":\"Paris is the capital of France\"}}]}}";
        assertTrue(OpenSearch.redditText(reddit).contains("Paris"));
        assertEquals(null, OpenSearch.redditText("{\"query\":{\"pages\":[{\"title\":\"Counts\"}]}}"));
        assertFalse(OpenSearch.redditUrl("news").contains("google"));
    }
}
