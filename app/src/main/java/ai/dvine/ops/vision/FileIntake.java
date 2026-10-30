package ai.dvine.ops.vision;

import java.util.Locale;

/**
 * What a selected file actually is, from its name, type, and bytes.
 * A journal note is not model training. A header is not an object detection.
 */
public final class FileIntake {
    public static final int HEAD = 262144;

    public static final class Report {
        public final String kind;
        public final String speech;
        public final String note;
        public final boolean showImage;
        public final int width;
        public final int height;

        public Report(String kind, String speech, String note, boolean showImage, int width, int height) {
            this.kind = kind;
            this.speech = speech;
            this.note = note;
            this.showImage = showImage;
            this.width = width;
            this.height = height;
        }
    }

    public Report inspect(String name, String mime, byte[] data, long size, boolean learn) {
        String label = name == null || name.isBlank() ? "that file" : name.trim();
        if (size <= 0 || data == null || data.length == 0) {
            return new Report("empty", label + " is empty. I didn't read anything from it.", null, false, 0, 0);
        }
        String type = mime == null ? "" : mime.toLowerCase(Locale.US);
        String lower = label.toLowerCase(Locale.US);
        ImageMetadataReader.Meta image = new ImageMetadataReader().read(data);
        if (image.supported || type.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            if (!image.supported) {
                return new Report("image", "I opened " + label + ", but it is not a PNG, JPEG, or WebP I can read. I didn't invent a description.", null, false, 0, 0);
            }
            String sizeBit = image.width > 0 ? " It is " + image.width + " by " + image.height + " pixels." : "";
            String speech = "I opened " + label + ", a " + image.format + " image." + sizeBit
                    + " I'm showing it. No object detector is loaded, so I won't name parts I didn't measure.";
            String note = learn ? image.format + " image " + label + sizeBit : null;
            return new Report("image", withLearn(speech, learn, note), note, true, image.width, image.height);
        }
        if (type.equals("application/pdf") || lower.endsWith(".pdf") || starts(data, "%PDF")) {
            String speech = "I opened " + label + ", a PDF. This build has no text extractor, so I have not read the words. I'll try the first page as a picture.";
            return new Report("pdf", withLearn(speech, learn, null), null, true, 0, 0);
        }
        if (type.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
            String speech = "I opened " + label + ", a video of about " + kb(size) + ". I'll try one frame if the phone can decode it. I have not watched every frame.";
            return new Report("video", withLearn(speech, learn, null), null, true, 0, 0);
        }
        if (type.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav")) {
            return new Report("audio", "I opened " + label + ", audio of about " + kb(size) + ". This build does not transcribe audio.", null, false, 0, 0);
        }
        if (looksText(type, lower, data)) {
            String text = textOf(data);
            if (text.isBlank()) {
                return new Report("empty", label + " has no readable text.", null, false, 0, 0);
            }
            String excerpt = safeExcerpt(text);
            if (excerpt.isEmpty()) {
                return new Report("text", "I read " + label + ", but the text looked like instructions to me, so I didn't repeat it or save it.", null, false, 0, 0);
            }
            String speech = "I read " + label + ". It starts: " + excerpt;
            String note = learn ? excerpt : null;
            return new Report("text", withLearn(speech, learn, note), note, false, 0, 0);
        }
        return new Report("unknown", "I opened " + label + " (" + kb(size) + ") but I don't have a reader for that type. I didn't guess its contents.", null, false, 0, 0);
    }

    private static String withLearn(String speech, boolean learn, String note) {
        if (!learn) {
            return speech;
        }
        if (note == null) {
            return speech + " I didn't save it. There was no text I could store, and saving a file is not retraining.";
        }
        return speech + " I saved that note in the learning journal. That is a stored note, not a change to the model's weights.";
    }

    /** Drops wording that tells the model to ignore its rules or run a command. That text is not read aloud. */
    static String safeExcerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.replaceAll("(?i)ignore (all |the )?(previous |prior )?(instructions|rules|prompts)", " ")
                .replaceAll("(?i)you are now", " ")
                .replaceAll("(?i)disregard (all |the )?(previous |prior )?[^.\\n]*", " ")
                .replaceAll("(?i)rm\\s+-rf\\S*", " ")
                .replaceAll("(?i)\\bsudo\\s+\\S+", " ")
                .replaceAll("(?i)\\bsystem\\s*:", " ");
        String excerpt = cleaned.replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 280) {
            return excerpt.substring(0, 277).trim() + "...";
        }
        return excerpt;
    }

    private static boolean looksText(String mime, String name, byte[] data) {
        if (mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv") || name.endsWith(".json")) {
            return true;
        }
        int n = Math.min(data.length, 512);
        for (int i = 0; i < n; i++) {
            int c = data[i] & 0xFF;
            if (c == 0) {
                return false;
            }
        }
        return mime.isEmpty() && n > 0 && (data[0] >= 32 || data[0] == '\n' || data[0] == '\r' || data[0] == '\t');
    }

    private static boolean starts(byte[] data, String magic) {
        if (data.length < magic.length()) {
            return false;
        }
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != (byte) magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String textOf(byte[] data) {
        int n = data.length;
        String s = new String(data, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        return s.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String kb(long size) {
        if (size < 1024) {
            return size + " bytes";
        }
        return (size / 1024) + " KB";
    }
}
