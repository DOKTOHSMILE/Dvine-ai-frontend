package ai.ibiko.gate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class BrainGatewayTest {
    @Test
    public void theFirstRouteThatTakesItHandlesItAndItIsRecorded() {
        final List<String> ran = new ArrayList<String>();
        BrainGateway front = new BrainGateway("message");
        BrainGateway brain = new BrainGateway("brain", front);
        front.route("pause", t -> t.equals("pause") && ran.add("pause"))
                .route("social search", t -> t.startsWith("search X") && ran.add("social"))
                .route("brain", t -> brain.handle(t, 2) != null);
        brain.route("think together", t -> t.contains("should I") && ran.add("council"))
                .route("the model decides", t -> ran.add("model"));
        assertEquals(Arrays.asList("pause", "social search", "brain"), front.order());

        assertEquals("social search", front.handle("search X for fuel", 1));
        assertEquals("brain", front.handle("should I open a second shop?", 2));
        assertEquals(Arrays.asList("social", "council"), ran);
        // one record shows the message's whole way: the brain stage inside the message stage
        List<BrainGateway.Decision> d = front.recent();
        assertEquals("message → social search", d.get(0).toString());
        assertEquals("brain → think together", d.get(1).toString());
        assertEquals("message → brain", d.get(2).toString());
        assertEquals("should I open a second shop?", front.last().text);
    }

    @Test
    public void noneTakesItAndNamesAreUnique() {
        BrainGateway g = new BrainGateway("message").route("pause", t -> false);
        assertNull(g.handle("hello", 1));
        assertTrue(g.recent().isEmpty());
        try {
            g.route("pause", t -> true);
            org.junit.Assert.fail("two routes with one name");
        } catch (IllegalArgumentException expected) {
            // each route is named once
        }
    }

    @Test
    public void theRecordKeepsTheLatest() {
        BrainGateway g = new BrainGateway("message").route("all", t -> true);
        for (int i = 0; i < BrainGateway.KEEP + 5; i++) {
            g.handle("m" + i, i);
        }
        assertEquals(BrainGateway.KEEP, g.recent().size());
        assertEquals("m5", g.recent().get(0).text);
    }
}
