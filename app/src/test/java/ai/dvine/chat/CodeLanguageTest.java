package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Code lands in the code viewer with its language named, even when the model fenced it badly or not at all. */
public class CodeLanguageTest {
    @Test
    public void theLanguageIsReadFromTheCode() {
        assertEquals("xml", CodeLanguage.detect("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:id=\"@+id/root\"\n    android:layout_width=\"match_parent\">\n</LinearLayout>"));
        assertEquals("java", CodeLanguage.detect("public class Main {\n    @Override\n    public String toString() {\n        return \"x\";\n    }\n}"));
        assertEquals("kotlin", CodeLanguage.detect("data class User(val name: String)\nfun greet(u: User) {\n    val text = \"Hi ${u.name}\"\n}"));
        assertEquals("python", CodeLanguage.detect("import os\n\ndef main():\n    print(os.getcwd())\n\nif __name__ == \"__main__\":\n    main()"));
        assertEquals("javascript", CodeLanguage.detect("const x = 5;\nfunction add(a, b) {\n  return a + b;\n}\nconsole.log(add(x, 2));"));
        assertEquals("json", CodeLanguage.detect("{\n  \"name\": \"dvine\",\n  \"version\": 3\n}"));
        assertEquals("sql", CodeLanguage.detect("SELECT name FROM users WHERE id = 1;\nCREATE TABLE t (id INT);"));
        assertEquals("bash", CodeLanguage.detect("#!/bin/bash\ncd app\ngit pull\necho $HOME"));
        assertEquals("html", CodeLanguage.detect("<!DOCTYPE html>\n<html><body><div class=\"a\">Hi</div></body></html>"));
        assertEquals("", CodeLanguage.detect("Just a sentence about the sea."));
        assertEquals("python", CodeLanguage.of("", "def f():\n    return 1\nprint(f())"));
        assertEquals("kotlin", CodeLanguage.of("kt", "fun main() {}")); // a known name is kept
    }

    @Test
    public void badFencesAndNoFencesStillGiveACodeBlock() {
        // the screenshot: one backtick before the language
        String reply = "Here's what it says:\n\n`xml\n<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout\n    android:id=\"@+id/chatScreenRoot\"\n    android:orientation=\"vertical\">\n</LinearLayout>\n`\nThat's the layout.";
        List<MessageParts.Part> parts = MessageParts.split(ReplyText.prepare(reply));
        assertEquals(3, parts.size());
        assertEquals("text", parts.get(0).kind);
        assertEquals("code", parts.get(1).kind);
        assertEquals("xml", parts.get(1).language);
        assertTrue(parts.get(1).body, parts.get(1).body.contains("\n    android:id=")); // the indentation kept
        assertEquals("That's the layout.", parts.get(2).body);
        // no fence at all
        List<MessageParts.Part> bare = MessageParts.split("Use this:\npublic class A {\n    int x;\n}\nThen build.");
        assertEquals("code", bare.get(1).kind);
        assertEquals("java", bare.get(1).language);
        // a fence with no language: read from the code
        List<MessageParts.Part> plain = MessageParts.split("```\nimport os\ndef f():\n    print(1)\n```");
        assertEquals("python", plain.get(0).language);
        // ordinary prose stays prose
        assertEquals(1, MessageParts.split("The sea is wide. It is blue; it is deep.").size());
    }

    @Test
    public void aBareSayIsNotTheAnswer() {
        org.junit.Assert.assertNull(ai.dvine.llm.BrainOrder.read("SAY"));
        org.junit.Assert.assertNull(ai.dvine.llm.BrainOrder.read("SAY:"));
        assertEquals("Hello there.", ai.dvine.llm.BrainOrder.read("SAY Hello there.").speech);
        assertEquals("Hello there.", ai.dvine.llm.BrainOrder.read("SAY: Hello there.").speech);
    }

    @Test
    public void oneWordIsNotAPhotoSearch() {
        // the screenshot: "Retry" came back as a photograph of a retry screen
        for (String word : new String[]{"Retry", "car", "anything", "Explain"}) {
            ai.dvine.llm.BrainOrder o = ai.dvine.llm.BrainOrder.decide(word, null);
            org.junit.Assert.assertTrue(word, o == null || o.kind != ai.dvine.llm.BrainOrder.Kind.PHOTO);
        }
        // asking for a picture still finds one
        assertEquals(ai.dvine.llm.BrainOrder.Kind.PHOTO, ai.dvine.llm.BrainOrder.decide("show me a red car", null).kind);
        assertEquals(ai.dvine.llm.BrainOrder.Kind.PHOTO, ai.dvine.llm.BrainOrder.decide("a photo of a lion", null).kind);
    }
}
