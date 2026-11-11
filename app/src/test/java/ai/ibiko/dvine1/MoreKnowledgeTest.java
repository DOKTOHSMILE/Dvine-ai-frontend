package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import org.junit.Test;

/** The further built-in knowledge: countries' codes and regions, the elements in full, Nigeria's states, the planets. */
public class MoreKnowledgeTest {
    private static BuiltIn all() throws Exception {
        String k = "src/main/assets/knowledge/";
        return new BuiltIn(new FileInputStream(k + "world_facts.tsv"), new FileInputStream(k + "country_more.tsv"),
                new FileInputStream(k + "elements_more.tsv"), new FileInputStream(k + "local_facts.tsv"));
    }

    @Test
    public void countriesCodesAndRegions() throws Exception {
        BuiltIn b = all();
        assertEquals("The calling code of Nigeria is +234.", b.answer("What is Nigeria's calling code?"));
        assertEquals("The internet domain of Ghana is .gh.", b.answer("internet domain for ghana"));
        assertEquals("Nigeria is in Western Africa.", b.answer("which region is Nigeria in"));
        assertEquals("Nigeria is a member of the United Nations.", b.answer("is nigeria a member of the united nations"));
        assertEquals("The capital of Nigeria is Abuja.", b.answer("capital of Nigeria"));
        assertEquals("Abuja stays Nigeria's capital in the reverse question", "Abuja is the capital of Nigeria.",
                b.answer("Abuja is the capital of which country?"));
    }

    @Test
    public void theElementsInFull() throws Exception {
        BuiltIn b = all();
        assertEquals("The atomic mass of iron is 55.85.", b.answer("what is the atomic mass of iron"));
        assertEquals("Iron is a transition metal.", b.answer("what kind of element is iron"));
        assertTrue(b.answer("what category is sodium"), b.answer("what category is sodium").startsWith("Sodium is an alkali metal"));
        assertEquals("Iron melts at 1538 °C (1811 K).", b.answer("melting point of iron"));
        assertEquals("Iron has been known since 5000 BC.", b.answer("who discovered iron"));
        assertEquals("The electron configuration of iron is [Ar] 3d6 4s2.", b.answer("electron configuration of iron"));
        assertEquals("Mercury is a liquid at room temperature.", b.answer("is mercury solid liquid or gas"));
    }

    @Test
    public void nigeriasStatesAndThePlanets() throws Exception {
        BuiltIn b = all();
        assertEquals("The capital of Lagos State is Ikeja.", b.answer("What is the capital of Lagos State?"));
        assertEquals("The capital of Rivers State is Port Harcourt.", b.answer("capital of rivers state"));
        assertEquals("not the country Niger", "The capital of Niger State is Minna.", b.answer("capital of Niger state"));
        assertEquals("The capital of Niger is Niamey.", b.answer("capital of Niger"));
        assertEquals("Earth is the third planet from the Sun.", b.answer("which planet from the sun is earth"));
        assertEquals("Jupiter is about 139,820 kilometres across.", b.answer("how big is jupiter"));
        assertEquals("a country's size is still its area", true, b.answer("how big is Nigeria").contains("square kilometres"));
        assertEquals("A year on Mars lasts about 687 Earth days.", b.answer("how long is a year on mars"));
        assertEquals("the planet, not the element", "Mercury is about 4,879 kilometres across.", b.answer("diameter of mercury"));
        assertEquals("Neptune is an ice giant.", b.answer("what kind of planet is neptune"));
        assertTrue("thousands of facts: " + b.size(), b.size() > 4800);
    }
}
