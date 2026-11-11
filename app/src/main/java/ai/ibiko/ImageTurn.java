package ai.ibiko;

import ai.ibiko.turn.LocalTurn;

/**
 * Makes a picture in the background (§IM) and pastes it into the chat when it is ready. The chat carries on
 * meanwhile: a card with a turning ring stands where the picture will go. Designs (icons, logos, diagrams) are drawn
 * in the app by the brain in use (design/SvgDraw); a photo-like picture goes to an image model, and when none is set
 * up or it fails, the brain draws it instead. Not the chat screen.
 */
final class ImageTurn {
    private final MainActivity a;

    ImageTurn(MainActivity a) {
        this.a = a;
    }

    void make(final String agent, final String what) {
        final String token = a.chatPane == null ? null : a.chatPane.makingImage(what);
        final String job = "image:" + (token == null ? System.nanoTime() : token);
        // carries on with the screen off or another app open, in the one "working" notification
        ai.ibiko.main.KeepAlive.status(a, job, "Making a picture", what, -1);
        final java.io.File out = new java.io.File(a.chatSpace(), ai.ibiko.main.ChatSpace.IMAGES); // deleted with its chat
        final java.util.List<ai.ibiko.llm.ImageMaker.Source> sources = ai.ibiko.llm.ImageMaker.sources(
                new ai.ibiko.llm.ProviderStore(a), a.prefs.getString("image_model", ""));
        final boolean design = ai.ibiko.design.SvgDraw.designish(what) || sources.isEmpty();
        a.hub.worker.execute(() -> {
            Made m = draw(what, sources, design, out);
            a.handler.post(() -> {
                ai.ibiko.main.KeepAlive.done(a, job);
                if (a.isDestroyed()) {
                    return;
                }
                if (a.chatPane != null && token != null) {
                    a.chatPane.madeImage(token, m.file == null ? null : m.file.getAbsolutePath());
                } else if (a.chatPane != null && m.file != null) {
                    a.chatPane.image(m.file.getAbsolutePath());
                }
                a.play(LocalTurn.speakAs(agent, m.file != null ? "Here it is, made by " + m.by + "." : m.error), null);
            });
        });
    }

    /** What came of it: the picture, who made it, or why not. */
    static final class Made {
        java.io.File file;
        String by, error;
    }

    /**
     * The brain's whole part: it imagines the picture (subject, composition, colours, light, style) in a short
     * paragraph. The generator then makes exactly that: an image model, or Ibiko's design board.
     */
    static final String IMAGINE = "You imagine pictures for an image generator. Describe the one picture asked for in a single"
            + " paragraph of at most 90 words: the subject, its pose or shape, the composition and framing, the colours, the"
            + " light, the style (photo, flat icon, illustration, 3D render, line art) and the background. Concrete and"
            + " visual, no story, no preamble, no quotes, no lists.";

    /** What the brain imagined, or the ask itself when no brain answered. */
    String imagine(String what) {
        String said = a.model.remoteTask(IMAGINE, what, 300);
        if (said == null) {
            said = a.model.task(IMAGINE, what, 220, null);
        }
        return imagined(what, said);
    }

    /** The brain's picture when it is a usable description; otherwise the pilot's own words. */
    static String imagined(String what, String said) {
        String t = said == null ? "" : said.replaceAll("(?s)```.*?```", "").replaceAll("[*_#>`]", "").trim();
        if (t.length() < 20 || t.length() > 1200 || t.contains("<svg")) {
            return what;
        }
        return t;
    }

    private Made draw(String what, java.util.List<ai.ibiko.llm.ImageMaker.Source> sources, boolean design, java.io.File out) {
        Made m = new Made();
        String seen = imagine(what); // the brain only imagines; the generator below makes the picture
        if (!design) {
            ai.ibiko.llm.ImageMaker.Result r = ai.ibiko.llm.ImageMaker.make(sources, seen, out, ai.ibiko.llm.ImageMaker.WEB);
            m.file = r.file;
            m.by = r.made;
            m.error = r.error;
        }
        if (m.file != null) {
            return m;
        }
        String sys = ai.ibiko.design.SvgDraw.SYSTEM;
        String ask = ai.ibiko.design.SvgDraw.brief(what) + (seen.equals(what) ? "" : "\nAs imagined: " + seen);
        String reply = a.model.remoteTask(sys, ask, 3000);
        boolean local = reply == null;
        if (local) {
            reply = a.model.task(sys, ask, 1800, null);
        }
        m.file = ai.ibiko.design.SvgDraw.save(reply, out, 1024);
        if (m.file != null) {
            m.by = "Ibiko's design board (drawn by " + (local ? "the on-device brain" : "the API brain") + ")";
        } else if (m.error == null) {
            m.error = "I couldn't draw that design: the brain didn't return a drawing I can read. Try again or describe it"
                    + " more simply.";
        }
        return m;
    }
}
