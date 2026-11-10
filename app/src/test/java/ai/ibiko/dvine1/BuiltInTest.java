package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class BuiltInTest {
    private static BuiltIn small() {
        String tsv = "Nigeria\tcapital\tAbuja\nNigeria\tpopulation\t223804632\nNigeria\tcurrency\tNigerian naira\n"
                + "Nigeria\tcontinent\tAfrica\nNigeria\tofficial language\tEnglish\nSudan\tcapital\tKhartoum\n"
                + "South Sudan\tcapital\tJuba\nSouth Africa\tofficial language\tZulu\nSouth Africa\tofficial language\tXhosa\n"
                + "gold\tsymbol\tAu\ngold\tatomic number\t79\nbad line\n";
        return new BuiltIn(new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void answeredHoweverItIsWorded() {
        BuiltIn b = small();
        assertEquals("The capital of Nigeria is Abuja.", b.answer("What is the capital of Nigeria?"));
        assertEquals("The capital of Nigeria is Abuja.", b.answer("nigeria's capital city pls"));
        assertEquals("Abuja is the capital of Nigeria.", b.answer("Abuja is the capital of which country?"));
        assertEquals("Nigeria has a population of about 223,804,632.", b.answer("How many people live in Nigeria"));
        assertEquals("The currency of Nigeria is the Nigerian naira.", b.answer("what money do they use in nigeria"));
        assertEquals("The capital of South Sudan is Juba.", b.answer("capital of south sudan"));
        assertEquals("The official languages of South Africa are Zulu and Xhosa.", b.answer("what languages are spoken in South Africa"));
        assertEquals("The chemical symbol of gold is Au.", b.answer("chemical symbol for gold?"));
        assertEquals("The atomic number of gold is 79.", b.answer("what's the atomic number of gold"));
    }

    @Test
    public void whatItDoesNotHaveItDoesNotInvent() {
        BuiltIn b = small();
        assertNull(b.answer("What is the capital of Atlantis?"));
        assertNull(b.answer("Tell me about Nigeria"));
        assertNull(b.answer("what is the area of Nigeria"));
        assertNull(new BuiltIn(null).answer("capital of Nigeria"));
        assertEquals(11, b.size());
    }

    @Test
    public void theFileThatShipsIsReadable() throws Exception {
        File f = new File("src/main/assets/knowledge/world_facts.tsv");
        if (!f.isFile()) {
            return; // not built yet in this checkout
        }
        BuiltIn b = new BuiltIn(new FileInputStream(f));
        assertTrue(b.size() > 2000);
        assertEquals("The capital of Nigeria is Abuja.", b.answer("What is the capital of Nigeria?"));
        assertEquals("The capital of Kenya is Nairobi.", b.answer("capital of kenya"));
    }

    @Test
    public void theShippedFileKnowsNeighboursPeopleAndCoasts() throws Exception {
        File f = new File("src/main/assets/knowledge/world_facts.tsv");
        BuiltIn b = new BuiltIn(new FileInputStream(f));
        assertEquals("Someone from Ghana is Ghanaian.", b.answer("what are people from Ghana called"));
        assertEquals("Nigeria borders Benin, Cameroon, Chad and Niger.", b.answer("Which countries border Nigeria?"));
        assertEquals("Uganda is landlocked: it has no coastline.", b.answer("is Uganda landlocked"));
        assertEquals("The chemical symbol of oganesson is Og.", b.answer("chemical symbol of oganesson"));
        assertEquals("The atomic number of iron is 26.", b.answer("atomic number of iron"));
        String pop = b.answer("How many people live in Nigeria?");
        org.junit.Assert.assertTrue(pop, pop.startsWith("Nigeria has a population of about ") && pop.endsWith(" estimate)."));
    }
}
