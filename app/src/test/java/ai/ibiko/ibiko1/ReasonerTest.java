package ai.ibiko.ibiko1;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Ibiko 1 reasons: facts are put together step by step (compare, rank, count, list, chain, add up), every answer says the
 * values it rests on, a false premise is corrected, and what one fact answers is left to the plain lookup.
 */
public class ReasonerTest {
    private static Reasoner r;

    @BeforeClass
    public static void load() throws Exception {
        String d = "../ibiko1/src/main/assets/knowledge/";
        r = new Reasoner(new BuiltIn(new FileInputStream(d + "world_facts.tsv"), new FileInputStream(d + "country_more.tsv"),
                new FileInputStream(d + "elements_more.tsv"), new FileInputStream(d + "local_facts.tsv")));
    }

    private static String a(String q) {
        Reasoner.Result res = r.answer(q);
        assertTrue("no reasoning for: " + q, res != null);
        assertTrue("its steps are shown: " + q, res.steps.size() >= 2);
        return res.text;
    }

    private static void has(String q, String... parts) {
        String t = a(q);
        for (String p : parts) {
            assertTrue(q + " → " + t, t.contains(p));
        }
    }

    @Test
    public void comparesWithTheValuesItRestsOn() {
        has("Which is bigger, Nigeria or Ghana?", "Nigeria is bigger than Ghana", "923,768", "238,533");
        has("Does Nigeria have more people than Ghana?", "Yes.", "times as many");
        has("Which is heavier, gold or silver?", "Gold is heavier than silver");
        has("Which has a higher melting point, iron or copper?", "Iron has a higher melting point");
        has("Is Mars further from the sun than Earth?", "Yes.");
        has("Which has more people, China or India?", "India has more people than China");
        has("What is the difference in population between Nigeria and Ghana?", "a difference of about");
        has("Which of Mercury, Venus and Earth is the biggest planet?", "Earth is the biggest of these");
    }

    @Test
    public void aFalsePremiseIsCorrectedNotExplained() {
        has("Is Ghana bigger than Nigeria?", "No.", "Nigeria is bigger than Ghana");
        has("Why is Ghana bigger than Nigeria?", "It isn't.", "Nigeria is bigger");
        has("Is Ghana the biggest country in Africa?", "No.", "Algeria is the biggest");
        has("What is the capital of Europe?", "no capital of its own");
    }

    @Test
    public void ranksCountsAndListsASet() {
        has("What is the most populous country in Africa?", "Nigeria has the most people", "54 countries");
        has("What is the smallest country in Europe?", "Vatican City is the smallest", "0.44");
        has("Which is the biggest planet?", "Jupiter");
        has("Which planet is closest to the sun?", "Mercury is the closest");
        has("How many countries are in Africa?", "54 are in Africa");
        has("How many countries border Nigeria?", "4 border Nigeria", "Benin", "Cameroon", "Chad", "Niger");
        has("Which countries in Western Africa are landlocked?", "Burkina Faso, Mali and Niger");
        has("Which countries speak Portuguese?", "Brazil", "Portugal", "Angola");
        has("What is the heaviest element?", "oganesson", "tied with tennessine");
    }

    @Test
    public void chainsAddsAndAnswersEachPart() {
        has("What is the currency of the country whose capital is Accra?", "is Ghana", "Ghanaian cedi");
        has("Which country has the capital Abuja and what is its currency?", "is Nigeria", "Nigerian naira");
        has("What are the capitals of Ghana and Togo?", "Accra", "Lomé");
        has("What is the capital and currency of Ghana?", "Accra", "cedi");
        has("What is the combined population of Nigeria and Ghana?", "Together");
        has("What is the population density of Nigeria?", "people per square kilometre");
        has("What is the population of Africa?", "54 countries together have about");
        has("What currency is used in Europe?", "Euro");
    }

    @Test
    public void oneFactOrNothingKnownIsLeftAlone() {
        assertNull("one fact: the plain lookup answers", r.answer("What is the capital of Ghana?"));
        assertNull("nothing known about these: no invented comparison", r.answer("Which is older, Rome or Paris?"));
        assertNull(r.answer("Make the text bigger"));
        assertNull(r.answer("Is iron more useful than gold?"));
        assertNull(r.answer("Hello"));
    }
}
