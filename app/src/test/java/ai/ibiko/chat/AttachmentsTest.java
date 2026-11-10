package ai.ibiko.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Test;

/** A file's words reach a model that cannot see, and the chat's short form still names the file. */
public class AttachmentsTest {
    @Test
    public void theDeviceModelGetsTheExtractedWords() throws Exception {
        java.io.File doc = java.io.File.createTempFile("sales", ".xlsx");
        Files.write(new java.io.File(doc.getPath() + ".txt").toPath(), "Jan\t120".getBytes(StandardCharsets.UTF_8));
        String msg = "Total this\n\n" + Attachments.file(doc.getPath(), "application/vnd.ms-excel", "sales.xlsx", Collections.<String>emptyList());
        String device = Attachments.forDevice(msg);
        assertTrue(device, device.contains("Jan\t120") && device.contains("sales.xlsx"));
        assertFalse(device, device.contains("[[file:"));
        String memory = Attachments.forMemory(msg);
        assertTrue(memory, memory.contains("Jan\t120"));
        assertTrue(Attachments.withoutImages(msg).contains("[a document: sales.xlsx]"));
    }
}
