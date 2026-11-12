package ai.ibiko;

import ai.ibiko.turn.LocalTurn;

/** Looks up photographs for what was just drawn. Not the chat screen. */
final class PhotoLookup {
    private final MainActivity a;

    PhotoLookup(MainActivity a) {
        this.a = a;
    }

    void show(final String subject) {

        final int id = a.nextAsk();
        final boolean online = a.connectivity != null && a.connectivity.isOnline()
                && a.prefs.getBoolean("fresh_facts", true);
        a.stopPlay();
        a.stage.activity("PHOTO", subject);
        if (a.lastDrawn == null || !a.lastDrawn.equalsIgnoreCase(subject)) {
            a.shownPhotos.clear();
        }
        a.lastDrawn = subject;
        if (!online) {
            a.play(LocalTurn.speakAs(a.stage.agent(),
                    "I won't invent a shape and call it " + ai.ibiko.llm.DrawPrompt.article(subject)
                            + ". I need the network to fetch a real photograph."),
                    null);
            return;
        }
        a.armThink("Looking up photographs", "Looking up photographs");
        a.hub.worker.execute(() -> {
            byte[] bytes = null;
            String used = null;
            int found = 0;
            try {
                String[] tries = new String[] { subject,
                        ai.ibiko.render.field.PhotoLookup.wider(subject) };
                for (String term : tries) {
                    if (bytes != null || term == null || term.isEmpty()) {
                        break;
                    }
                    byte[] listing = ai.ibiko.net.WebGet.get(
                            ai.ibiko.render.field.PhotoLookup.commonsUrl(term, 12), 12000,
                            512 * 1024);
                    java.util.List<ai.ibiko.render.field.PhotoLookup.Shot> shots = ai.ibiko.render.field.PhotoLookup
                            .choose(listing == null ? null : new String(listing, "UTF-8"), subject,
                                    8);
                    found = Math.max(found, shots.size());
                    for (ai.ibiko.render.field.PhotoLookup.Shot shot : shots) {
                        if (!a.alive(id)) {
                            return;
                        }
                        if (a.shownPhotos.contains(shot.url)) {
                            continue;
                        }
                        bytes = ai.ibiko.net.WebGet.get(shot.url, 12000, 3 * 1024 * 1024);
                        if (bytes != null && bytes.length > 32) {
                            used = shot.url;
                            break;
                        }
                        bytes = null;
                    }
                }
                if (bytes == null) {
                    byte[] listing = ai.ibiko.net.WebGet.get(
                            ai.ibiko.render.field.PhotoLookup.plainUrl(subject, 8), 12000, 512 * 1024);
                    String json = listing == null ? null : new String(listing, "UTF-8");
                    java.util.List<String> loose = ai.ibiko.render.field.PhotoLookup.urls(json, 4);
                    found = Math.max(found, loose.size());
                    for (String url : loose) {
                        if (!a.alive(id) || a.shownPhotos.contains(url)) {
                            continue;
                        }
                        bytes = ai.ibiko.net.WebGet.get(url, 12000, 3 * 1024 * 1024);
                        if (bytes != null && bytes.length > 32) {
                            used = url;
                            break;
                        }
                        bytes = null;
                    }
                }
                if (bytes == null) {
                    bytes = ai.ibiko.render.field.PictureFinder.find(subject);
                    if (bytes != null) {
                        found = Math.max(found, 1);
                    }
                }
            } catch (java.io.UnsupportedEncodingException ignored) {
                bytes = null;
            }
            final byte[] got = bytes;
            final int count = found;
            final String chosen = used;
            a.handler.post(() -> {
                if (!a.alive(id) || a.isFinishing()) {
                    return;
                }
                a.endThink();
                android.graphics.Bitmap bm = got == null ? null
                        : ai.ibiko.render.field.Raster.decode(got);
                if (bm == null) {
                    a.play(LocalTurn.speakAs(a.stage.agent(),
                            a.shownPhotos.isEmpty() ? "I looked for photographs of "
                                    + ai.ibiko.llm.DrawPrompt.article(subject)
                                    + " and didn't get one I can show. I didn't draw a stand-in."
                                    : "I've already shown the photographs I found of "
                                            + ai.ibiko.llm.DrawPrompt.article(subject) + "."),
                            null);
                    return;
                }
                if (chosen != null) {
                    a.shownPhotos.add(chosen);
                }
                java.io.File shot = new java.io.File(a.getFilesDir(),
                        "chat-images/" + System.currentTimeMillis() + ".png");
                java.io.File parent = shot.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(shot)) {
                    bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out);
                } catch (java.io.IOException ignored) {
                    shot = null;
                }
                ai.ibiko.render.field.Shape picture = ai.ibiko.render.field.Raster.image(bm,
                        ai.ibiko.render.field.ParticleDisplay.SKIN_POINTS, 2f);
                bm.recycle();
                if (picture == null || picture.count < 80) {
                    a.play(LocalTurn.speakAs(a.stage.agent(), "I fetched a photograph of "
                            + ai.ibiko.llm.DrawPrompt.article(subject)
                            + ", but it didn't decode into something I can show. I didn't invent a model."),
                            null);
                    return;
                }
                a.currentSolid = null;
                a.stage.showPhotograph(picture, subject);
                if (a.chatPane != null && shot != null && shot.isFile()) {
                    a.chatPane.image(shot.getAbsolutePath());
                }
                String line = "Here's a photograph of " + ai.ibiko.llm.DrawPrompt.article(subject)
                        + (count > 1 ? ". I found " + count + " and this is one of them." : ".")
                        + " It is a real picture, not a measured 3D model.";
                a.play(LocalTurn.speakAs(a.stage.agent(), line), null);
            });
        });
        }
}
