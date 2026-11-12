package ai.ibiko.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ai.ibiko.ops.mind.OpenSearch;
import ai.ibiko.render.field.PhotoLookup;

public class PhotoPlanTest {
    @Test
    public void commonsUrlsAreRealFiles() {
        String json = "{\"query\":{\"pages\":[{\"imageinfo\":[{\"thumburl\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Bike.jpg/640px-Bike.jpg\",\"url\":\"https://upload.wikimedia.org/wikipedia/commons/a/ab/Bike.jpg\"}]}]}}";
        List<String> urls = PhotoLookup.urls(json, 4);
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("upload.wikimedia.org"));
        assertTrue(PhotoLookup.commonsUrl("bicycle", 4).contains("commons.wikimedia.org"));
        assertEquals(0, PhotoLookup.urls("{\"url\":\"https://evil.example/a.jpg\"}", 4).size());
    }

    @Test
    public void aBicycleFileBeatsAPersonOnABicycle() {
        String ranked = "{\"title\":\"File:Boy on a bicycle.jpg\",\"thumburl\":\"https://upload.wikimedia.org/a.jpg\"}"
                + "{\"title\":\"File:Bicycle.jpg\",\"thumburl\":\"https://upload.wikimedia.org/b.jpg\"}";
        java.util.List<PhotoLookup.Shot> shots = PhotoLookup.choose(ranked, "bicycle", 2);
        assertEquals("https://upload.wikimedia.org/b.jpg", shots.get(0).url);
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
