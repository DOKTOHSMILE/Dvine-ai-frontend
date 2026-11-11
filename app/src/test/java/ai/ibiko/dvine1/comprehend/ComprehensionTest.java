package ai.ibiko.dvine1.comprehend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

/** The blueprint's acceptance examples, read against the real WordNet and ConceptNet files. */
public class ComprehensionTest {
    static final File K = new File("src/main/assets/knowledge");

    static Dictionary dict() {
        return new Dictionary(new SortedTable(new File(K, "lexicon.tsv")));
    }

    static Concepts concepts() {
        return new Concepts(new SortedTable(new File(K, "concepts.tsv")));
    }

    static Comprehension ucl() {
        return new Comprehension(dict(), concepts());
    }

    @Test
    public void theTablesAreFoundByBinarySearch() {
        Dictionary d = dict();
        assertTrue(d.present());
        assertEquals("noun", d.senses("dog").get(0).pos);
        assertTrue(d.senses("dog").get(0).definition.contains("Canis"));
        assertEquals("plural to its base", "knife", d.senses("knives").get(0).word);
        assertTrue(d.senses("happy", "adjective").get(0).opposites.contains("unhappy"));
        assertTrue(d.senses("zzzqqq").isEmpty());
        assertTrue("the first and last words in the file are found too", !d.exact(firstKey()).isEmpty());
        Concepts c = concepts();
        assertTrue(c.of("knife", "UsedFor", 12).toString(), c.of("knife", "UsedFor", 12).stream().anyMatch(s -> s.contains("cut")));
        assertTrue("a tyre is part of a car", c.partOf("tire", "car") || c.partOf("wheel", "car"));
    }

    private static String firstKey() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(new File(K, "lexicon.tsv")))) {
            return r.readLine().split("\t")[0];
        } catch (java.io.IOException e) {
            return "";
        }
    }

    @Test
    public void takeTheRedTyreOffTheCar() {
        Comprehension.Meaning m = ucl().read("Take the red tyre off the car.");
        assertEquals(Comprehension.Intent.DELETION, m.intent);
        Comprehension.Operation o = m.first();
        assertEquals("remove", o.action);
        assertEquals("tyre", o.target);
        assertEquals("red", o.attributes.get("colour"));
        assertEquals("car", o.parent);
        assertTrue(m.toJson(), m.toJson().contains("\"action\":\"remove\"") && m.toJson().contains("\"source\":\"car\""));
    }

    @Test
    public void theTwoBanks() {
        Comprehension u = ucl();
        String river = u.read("The bank is beside the river.").senses.get("bank");
        String loan = u.read("The bank approved my loan.").senses.get("bank");
        assertNotNull(river);
        assertNotNull(loan);
        assertTrue("two meanings: " + river + " / " + loan, !river.equals(loan));
        assertTrue(river, river.contains("slop") || river.contains("land") || river.contains("water"));
        assertTrue(loan, loan.contains("financial") || loan.contains("deposit") || loan.contains("money"));
    }

    @Test
    public void itAndItsFollowTheConversation() {
        Comprehension u = ucl();
        u.read("I bought a new car.");
        assertEquals("car", u.read("It is red.").references.get("it"));
        Comprehension.Meaning m = u.read("Remove its tyres.");
        assertEquals("car", m.references.get("its"));
        assertEquals("remove", m.first().action);
        assertEquals("tyres", m.first().target);
        assertEquals("car", m.first().parent);
    }

    @Test
    public void theLeftDoorOfTheCar() {
        Comprehension.Meaning m = ucl().read("Can you remove the left door of the car?");
        assertEquals(Comprehension.Intent.DELETION, m.intent);
        assertEquals("door", m.first().target);
        assertEquals("left", m.first().position);
        assertEquals("car", m.first().parent);
        assertEquals(Boolean.TRUE, m.first().partKnown);
    }

    @Test
    public void twoStepsAndPronouns() {
        Comprehension u = ucl();
        u.read("Draw a house.");
        Comprehension.Meaning m = u.read("Make it bigger and remove the small part on its right");
        assertEquals(2, m.operations.size());
        assertEquals("resize", m.operations.get(0).action);
        assertEquals("increase", m.operations.get(0).direction);
        assertEquals("house", m.operations.get(0).target);
        assertEquals("remove", m.operations.get(1).action);
        assertEquals("part", m.operations.get(1).target);
        assertEquals("small", m.operations.get(1).attributes.get("size"));
        assertEquals("right", m.operations.get(1).position);
        assertNull(m.clarify);
    }

    @Test
    public void negationAndAskingFirst() {
        Comprehension.Meaning m = ucl().read("Don't delete the file");
        assertTrue(m.negated);
        assertEquals("remove", m.first().action);
        Comprehension.Meaning lost = ucl().read("Remove it");
        assertNotNull("nothing to point to: ask first", lost.clarify);
        assertTrue(lost.brief(), lost.brief().contains("ask first"));
    }

    @Test
    public void theTenIntents() {
        Comprehension u = ucl();
        assertEquals(Comprehension.Intent.QUESTION, u.read("What is a computer?").intent);
        assertEquals(Comprehension.Intent.EXPLANATION, u.read("Explain electricity.").intent);
        assertEquals(Comprehension.Intent.CREATION, u.read("Design a logo.").intent);
        assertEquals(Comprehension.Intent.MODIFICATION, u.read("Change its colour.").intent);
        assertEquals(Comprehension.Intent.DELETION, u.read("Remove the background.").intent);
        assertEquals(Comprehension.Intent.NAVIGATION, u.read("Open settings.").intent);
        assertEquals(Comprehension.Intent.CALCULATION, u.read("Calculate 25% of 500.").intent);
        assertEquals(Comprehension.Intent.RESEARCH, u.read("Find information about AI.").intent);
        assertEquals(Comprehension.Intent.CONVERSATION, u.read("I am happy today.").intent);
        assertEquals(Comprehension.Intent.INSTRUCTION, u.read("Save this document.").intent);
        assertFalse(u.read("Save this document.").negated);
    }
}
