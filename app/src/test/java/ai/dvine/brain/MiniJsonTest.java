package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MiniJsonTest {
    @Test
    public void roundTrips() {
        String src = "{\"a\":1,\"b\":[true,false,null,\"x\\n\\\"y\\\"\"],\"c\":{\"d\":-2.5}}";
        Object v = MiniJson.parse(src);
        assertEquals(src, MiniJson.write(v));
        Map<String, Object> m = MiniJson.obj(v);
        List<Object> b = MiniJson.list(m.get("b"));
        assertEquals("x\n\"y\"", b.get(3));
        assertEquals(-2.5, MiniJson.num(MiniJson.obj(m.get("c")).get("d"), 0), 0);
    }

    @Test
    public void rejectsGarbage() {
        String[] bad = {"", "{", "[1,]", "{\"a\" 1}", "nul", "{} x"};
        for (String s : bad) {
            try {
                MiniJson.parse(s);
                throw new AssertionError("accepted " + s);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("json"));
            }
        }
    }
}
