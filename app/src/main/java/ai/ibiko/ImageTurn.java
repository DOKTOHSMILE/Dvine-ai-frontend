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
        final java.io.File out = new java.io.File(a.getFilesDir(), "chat-images");
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

    private Made draw(String what, java.util.List<ai.ibiko.llm.ImageMaker.Source> sources, boolean design, java.io.File out) {
        Made m = new Made();
        if (!design) {
            ai.ibiko.llm.ImageMaker.Result r = ai.ibiko.llm.ImageMaker.make(sources, what, out, ai.ibiko.llm.ImageMaker.WEB);
            m.file = r.file;
            m.by = r.made;
            m.error = r.error;
        }
        if (m.file != null) {
            return m;
        }
        String sys = ai.ibiko.design.SvgDraw.SYSTEM;
        String ask = ai.ibiko.design.SvgDraw.brief(what);
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
