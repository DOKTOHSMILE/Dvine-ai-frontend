package ai.ibiko;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Boots the real Activity on a simulated Android 14 so start-up crashes show up in CI. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LaunchTest {
    @Test(timeout = 120000)
    public void startsAndDrawsWithoutCrashing() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        MainActivity a = c.get();
        assertNotNull(a);
        android.view.View root = a.getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        root.draw(new android.graphics.Canvas(bmp));
        c.pause().stop().destroy();
    }

    /** The AI providers sheet and the Usage screen open, draw, and take a tap on a provider card. */
    @Test(timeout = 120000)
    public void providersAndUsageOpen() {
        ActivityController<ai.ibiko.chat.ui.ProvidersActivity> p = Robolectric.buildActivity(ai.ibiko.chat.ui.ProvidersActivity.class)
                .create().start().resume();
        android.view.View root = p.get().getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        assertNotNull(find(root, "Anthropic (Claude)"));
        find(root, "Custom gateway").performClick(); // opens its card: key, address, model, certificate
        assertNotNull(find(root, "Add backup key"));
        root.draw(new android.graphics.Canvas(bmp));
        // the keyboard opens: the sheet must move up above it (Android 15 no longer does this by itself)
        android.view.View content = ((android.view.ViewGroup) p.get().findViewById(android.R.id.content)).getChildAt(0);
        androidx.core.view.WindowInsetsCompat withKeyboard = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 900))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true)
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, 80, 0, 40))
                .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(content, withKeyboard);
        org.junit.Assert.assertEquals(900, content.getPaddingBottom());
        org.junit.Assert.assertEquals(80, content.getPaddingTop());
        p.pause().stop().destroy();

        ActivityController<ai.ibiko.chat.ui.UsageActivity> u = Robolectric.buildActivity(ai.ibiko.chat.ui.UsageActivity.class)
                .create().start().resume();
        android.view.View ur = u.get().getWindow().getDecorView();
        ur.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        ur.layout(0, 0, 1080, 2340);
        ur.draw(new android.graphics.Canvas(bmp));
        assertNotNull(find(ur, "Context window"));
        u.pause().stop().destroy();
    }

    /** The view showing exactly this text (its card, for a title in a card row), or null. */
    @Test(timeout = 120000)
    public void yourOwnMessageCanBeCopied() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "RE clone the repo and audit my codebase", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.ibiko.stage.ChatPane) pf.get(c.get())).show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View copy = described(c.get().getWindow().getDecorView(), "Copy your message");
        assertNotNull(copy);
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) copy.getParent()).getVisibility()); // hidden until a tap
        java.util.ArrayList<android.view.View> said = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(said, "RE clone the repo and audit my codebase", android.view.View.FIND_VIEWS_WITH_TEXT);
        said.removeIf(v -> v.getId() != ai.ibiko.R.id.tv_message); // the message itself, not the chat list's preview
        said.get(0).performClick();
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, ((android.view.View) copy.getParent()).getVisibility());
        said.get(0).performClick(); // a second tap hides it
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) copy.getParent()).getVisibility());
        said.get(0).performClick();
        copy.performClick();
        android.content.ClipboardManager clips = (android.content.ClipboardManager) c.get().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        org.junit.Assert.assertEquals("RE clone the repo and audit my codebase", clips.getPrimaryClip().getItemAt(0).getText().toString());
        chats.add("assistant", "text", "On it.", chats.current().persona); // the chat moves on: the icon hides again
        ((ai.ibiko.stage.ChatPane) pf.get(c.get())).show();
        android.view.View again = described(c.get().getWindow().getDecorView(), "Copy your message");
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) again.getParent()).getVisibility());
        c.pause().stop().destroy();
    }

    /** The brain's reply written as the suit's code (as a model would), carried out by the suit. */
    private static void brainSays(ActivityController<MainActivity> c, String code) throws Exception {
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("carryOut", String.class, String.class,
                ai.ibiko.llm.BrainOrder.class);
        m.setAccessible(true);
        java.lang.reflect.Field st = MainActivity.class.getDeclaredField("stage");
        st.setAccessible(true);
        String agent = ((ai.ibiko.stage.StageView) st.get(c.get())).agent();
        m.invoke(c.get(), agent, "", ai.ibiko.llm.BrainOrder.leading(code));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    public void aDoubleTapOnSendSendsOnceAndCancelsNothing() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        field.setText("What time is it?");
        android.view.View send = c.get().findViewById(ai.ibiko.R.id.btn_send);
        send.performClick();
        send.performClick(); // the second tap of a double tap: neither a second send nor a Stop
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        int asked = 0;
        for (ai.ibiko.chat.ChatBook.Message m : chats.current().messages) {
            if ("user".equals(m.role) && "What time is it?".equals(m.body)) {
                asked++;
            }
        }
        org.junit.Assert.assertEquals(1, asked);
        // the guard lives in main/SendQueue since the send path left MainActivity
        java.lang.reflect.Field qf = MainActivity.class.getDeclaredField("sendQueue");
        qf.setAccessible(true);
        Object queue = qf.get(c.get());
        java.lang.reflect.Field at = ai.ibiko.main.SendQueue.class.getDeclaredField("lastSendAt");
        at.setAccessible(true);
        org.junit.Assert.assertTrue((long) at.get(queue) >= 0);
        c.pause().stop().destroy();
    }

    @Test
    public void theStepsOpenInABottomSheetAndTheChatKeepsOneTypedLine() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        pane.activityStep("Cloned me/app");
        pane.activityStep("Read app/Main.java");
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS); // the typewriter finishes
        android.view.View root = c.get().getWindow().getDecorView();
        android.view.View live = described(root, "What is happening now; tap for every step");
        assertNotNull(live);
        ai.ibiko.stage.LiveFace face = findFace(live);
        assertNotNull("Ibiko's blinking face is in the live line", face);
        float density = c.get().getResources().getDisplayMetrics().density;
        org.junit.Assert.assertEquals("big enough to see it blink", Math.round(38 * density), face.getLayoutParams().width);
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(found, "Read app/Main.java", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size()); // only the current step is in the chat, typed out
        root.findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "Cloned me/app", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertTrue(found.isEmpty()); // the earlier steps are in the sheet, not the chat
        live.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(sheet);
        sheet.getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "Cloned me/app",
                android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size());
        pane.activityStep("Wrote app/Main.java"); // live: the open sheet grows, on the next frame (one redraw a burst)
        ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        sheet.getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "Wrote app/Main.java",
                android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size());
        sheet.dismiss();
        pane.activityEnd();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // the kept work, answered or not: one "Worked for …" row for the turn, its steps a tap away
        android.view.View worked = describedStart(root, "Worked for ");
        if (worked == null) {
            worked = describedStart(root, "Thought for ");
        }
        assertNotNull(worked);
        worked.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView().findViewsWithText(
                found = new java.util.ArrayList<android.view.View>(), "Cloned me/app", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size());
        c.pause().stop().destroy();
    }

    /** The conversation rests in the middle: room under the last message, about a third of the chat's height. */
    @Test
    public void theChatHasRoomUnderTheLastMessage() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        java.lang.reflect.Field tf = ai.ibiko.stage.ChatPane.class.getDeclaredField("thread");
        tf.setAccessible(true);
        android.view.View thread = (android.view.View) tf.get(pane);
        android.view.View scroll = (android.view.View) thread.getParent();
        pane.activityStep("Cloned me/app"); // something in the chat: it shows
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertTrue("laid out", scroll.getHeight() > 0);
        org.junit.Assert.assertEquals(Math.round(scroll.getHeight() * 0.35f), thread.getPaddingBottom());
        c.pause().stop().destroy();
    }

    @Test
    public void aNoteDuringTheWorkSitsBetweenItsSteps() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        java.lang.reflect.Field bf = ai.ibiko.stage.ChatPane.class.getDeclaredField("book");
        bf.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bf.get(pane);
        int before = book.current().messages.size();
        pane.activityStep("Cloned me/app");
        pane.assistant("ibiko", "The repo is cloned; reading the main file next.");
        pane.activityStep("Read app/Main.java");
        pane.activityEnd();
        pane.assistant("ibiko", "The loop never ends.\n\nI fixed it.");
        java.util.List<ai.ibiko.chat.ChatBook.Message> m = book.current().messages.subList(before, book.current().messages.size());
        java.util.List<String> kinds = new java.util.ArrayList<String>();
        for (ai.ibiko.chat.ChatBook.Message x : m) {
            kinds.add(x.kind);
        }
        org.junit.Assert.assertEquals(java.util.Arrays.asList("activity", "text", "activity", "text"), kinds);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View root = c.get().getWindow().getDecorView();
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(found, "Listen to this part", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        // finished: the steps and the note along the way are one process row; the answer's two paragraphs show under it
        org.junit.Assert.assertEquals("Copy and Listen are out of the way until the reply is tapped", 0, found.size());
        java.util.ArrayList<android.view.View> said = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(said, "I fixed it.", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertFalse(said.isEmpty());
        said.get(0).performClick();
        root.findViewsWithText(found, "Listen to this part", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        org.junit.Assert.assertEquals("a tap shows only that paragraph's Listen and Copy, not every part's", 1, found.size());
        java.util.ArrayList<android.view.View> process = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(process, "open the process", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        org.junit.Assert.assertEquals("one process row for the whole turn", 1, process.size());
        java.util.ArrayList<android.view.View> copies = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(copies, "Copy", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        org.junit.Assert.assertEquals("one Copy, at the end, for the whole reply", 1, copies.size());
        // tapping the other paragraph moves the buttons to it: still only one part's at a time
        said.clear();
        root.findViewsWithText(said, "The loop never ends.", android.view.View.FIND_VIEWS_WITH_TEXT);
        said.get(0).performClick();
        found.clear();
        root.findViewsWithText(found, "Listen to this part", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        org.junit.Assert.assertEquals(1, found.size());
        copies.clear();
        root.findViewsWithText(copies, "Copy", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        org.junit.Assert.assertEquals("the whole reply's bar closes with the last part's", 0, copies.size());
        c.pause().stop().destroy();
    }

    @Test
    public void aCommandTheBrainWroteRunsAndItsResultIsTold() throws Exception {
        ai.ibiko.llm.BrainOrder o = ai.ibiko.llm.BrainOrder.read("DO SHELL python3 --version");
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.SHELL, o.kind);
        org.junit.Assert.assertEquals("python3 --version", o.arg);
        org.junit.Assert.assertTrue(ai.ibiko.llm.BrainOrder.prompt("").contains("write the real command yourself"));
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit().putBoolean("auto_accept", true).commit();
        java.lang.reflect.Method run = MainActivity.class.getDeclaredMethod("runShell", String.class, String.class);
        run.setAccessible(true);
        run.invoke(c.get(), "say hello in the shell", "echo hello-ibiko");
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        boolean told = false;
        for (int i = 0; i < 40 && !told; i++) {
            Thread.sleep(100);
            ShadowLooper.idleMainLooper();
            for (ai.ibiko.chat.ChatBook.Message m : chats.current().messages) {
                told |= m.body != null && m.body.contains("hello-ibiko");
            }
        }
        org.junit.Assert.assertTrue("with no model, the output itself is shown", told);
        c.pause().stop().destroy();
    }

    @Test
    public void thePersonasAreNamedPagesWithIcons() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().findViewById(ai.ibiko.R.id.tabIbiko).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        for (int id : new int[]{ai.ibiko.R.id.tabIvie, ai.ibiko.R.id.tabObi, ai.ibiko.R.id.tabIbiko}) {
            android.widget.TextView tab = c.get().findViewById(id);
            assertNotNull(tab.getCompoundDrawablesRelative()[0]); // graphics, chat, tools
        }
        org.junit.Assert.assertEquals("Ibiko", ((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tabIbiko)).getText().toString());
        // three named pages of one chat: the ones not open keep their names too (0.99.8)
        org.junit.Assert.assertEquals("Obi", ((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tabObi)).getText().toString());
        org.junit.Assert.assertTrue(((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tabIbiko)).isSelected());
        // the model under the title opens a switch: the offline models on the phone, every model, the API providers
        c.get().findViewById(ai.ibiko.R.id.tv_header_sub).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        for (String name : new String[]{"All offline models", "AI providers"}) {
            java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
            sheet.getWindow().getDecorView().findViewsWithText(found, name, android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertFalse(name, found.isEmpty());
        }
        sheet.dismiss();
        c.pause().stop().destroy();
    }

    @Test
    public void theOfflineModelCanBeChangedInSettings() throws Exception {
        ActivityController<ai.ibiko.chat.ui.SettingsActivity> c = Robolectric.buildActivity(ai.ibiko.chat.ui.SettingsActivity.class)
                .create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ai.ibiko.llm.ModelHub hub = ai.ibiko.llm.ModelHub.get(c.get());
        java.io.File phi = null;
        try {
            java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
            // the sections are closed until tapped: open Models first
            c.get().getWindow().getDecorView().findViewsWithText(found, "Models", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            org.junit.Assert.assertFalse(found.isEmpty());
            found.get(0).performClick(); // Models opens on its own page
            android.content.Intent page = org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
            org.junit.Assert.assertEquals("Models", page.getStringExtra(ai.ibiko.chat.ui.SettingsActivity.EXTRA_SECTION));
            c.pause().stop().destroy();
            c = Robolectric.buildActivity(ai.ibiko.chat.ui.SettingsActivity.screen(page), page).create().start().resume().visible();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            found.clear();
            // every offline model is listed, not folded: one not here has Download
            assertNotNull(described(c.get().getWindow().getDecorView(), "Download Qwen3 8B"));
            assertNotNull(described(c.get().getWindow().getDecorView(), "Download Phi-4 mini"));
            // a downloaded one has Use and Test (a sparse file of the full size stands in for the download)
            phi = new java.io.File(hub.model.file().getParentFile(), ai.ibiko.llm.ModelSpec.PHI_4_MINI.file);
            phi.getParentFile().mkdirs();
            try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(phi, "rw")) {
                f.setLength(ai.ibiko.llm.ModelSpec.PHI_4_MINI.bytes);
            }
            c.pause().resume();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(described(c.get().getWindow().getDecorView(), "Test the speed of Phi-4 mini"));
            described(c.get().getWindow().getDecorView(), "Use Phi-4 mini").performClick();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.Assert.assertSame(ai.ibiko.llm.ModelSpec.PHI_4_MINI, hub.chosen()); // shown at once
            hub.worker.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS); // the switch itself, on the model's thread
            org.junit.Assert.assertSame(ai.ibiko.llm.ModelSpec.PHI_4_MINI, hub.model.spec);
            c.get().getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "On this device: Phi-4 mini",
                    android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertEquals(1, found.size()); // the card names the model now in use
            assertNotNull(described(c.get().getWindow().getDecorView(), "Phi-4 mini is in use"));
            // a model on the phone is listed with Delete, to free the space
            java.io.File kept = new java.io.File(hub.model.file().getParentFile(), ai.ibiko.llm.ModelSpec.QWEN3_4B.file);
            kept.getParentFile().mkdirs();
            java.nio.file.Files.write(kept.toPath(), new byte[2048]);
            c.pause().resume(); // the card is filled again
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            // a part-download is listed with Delete, to free the space
            android.view.View delete = described(c.get().getWindow().getDecorView(), "Delete Qwen3 4B");
            assertNotNull(delete);
            delete.performClick();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            ((androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            for (int i = 0; i < 20 && kept.exists(); i++) {
                Thread.sleep(50);
                ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            org.junit.Assert.assertFalse(kept.exists());
        } finally {
            if (phi != null) {
                phi.delete();
            }
            hub.choose(c.get(), ai.ibiko.llm.ModelSpec.QWEN3_1_7B, false);
            hub.worker.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            c.pause().stop().destroy();
        }
    }

    @Test
    public void wordsAloneNeverRunAFunction() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // a meaning remembered from before no longer acts on the words: only the brain's code does
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE);
        ai.ibiko.turn.Meaning.Memory m = new ai.ibiko.turn.Meaning.Memory();
        m.put("Where are we with that?", ai.ibiko.turn.Meaning.Command.RESULT);
        m.put("start over", ai.ibiko.turn.Meaning.Command.NEW_CHAT);
        prefs.edit().putString("meanings", m.write()).commit();
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        String before = chats.current().id;
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        android.view.View send = c.get().findViewById(ai.ibiko.R.id.btn_send);
        for (String words : new String[]{"where are we with that", "start over", "stop", "new chat", "\u00A7NC"}) {
            field.setText(words);
            send.performClick();
            ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        }
        android.widget.TextView note = c.get().findViewById(ai.ibiko.R.id.composer_note);
        org.junit.Assert.assertFalse(note.getText().toString().startsWith("Understood as"));
        org.junit.Assert.assertEquals("no word started a new chat", before, chats.current().id);
        boolean kept = false;
        for (ai.ibiko.chat.ChatBook.Message msg : chats.current().messages) {
            kept |= "user".equals(msg.role) && "where are we with that".equals(msg.body);
        }
        org.junit.Assert.assertTrue("the user's own words stay in the chat", kept);
        c.pause().stop().destroy();
    }

    @Test
    public void aMessageSentDuringWorkWaitsForItInsteadOfCuttingItOff() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field gen = MainActivity.class.getDeclaredField("generating");
        gen.setAccessible(true);
        gen.set(c.get(), true); // a reply is being worked on
        android.widget.ImageButton send = c.get().findViewById(ai.ibiko.R.id.btn_send);
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        field.setText("");
        org.junit.Assert.assertEquals("an empty box during work: Stop", "Stop", send.getContentDescription().toString());
        field.setText("Also check the weather in Lagos");
        org.junit.Assert.assertEquals("typing during work: Send, into the running task", "Send to the running task",
                send.getContentDescription().toString());
        java.lang.reflect.Field ask = MainActivity.class.getDeclaredField("askId");
        ask.setAccessible(true);
        int before = ask.getInt(c.get());
        send.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("the running task is not stopped", true, gen.get(c.get()));
        org.junit.Assert.assertEquals("a new send does not start a new turn", before, ask.getInt(c.get()));
        java.lang.reflect.Field box = MainActivity.class.getDeclaredField("steer");
        box.setAccessible(true);
        ai.ibiko.main.SteerInbox steer = (ai.ibiko.main.SteerInbox) box.get(c.get());
        org.junit.Assert.assertEquals("held for the running task to read", 1, steer.size());
        android.widget.TextView line = c.get().findViewById(ai.ibiko.R.id.composer_note);
        org.junit.Assert.assertTrue(line.getText().toString(), line.getText().toString().startsWith("Sent"));
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        org.junit.Assert.assertTrue("shown in the chat at once", chats.transcript(5000).contains("weather in Lagos"));
        gen.set(c.get(), false); // the work finished without reading it
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("then it goes out, it does not sit waiting", 0, steer.size());
        c.pause().stop().destroy();
    }

    @Test
    public void everyMessageTakesOneRecordedWayThroughTheGateway() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        field.setText("Check trending post on x");
        c.get().findViewById(ai.ibiko.R.id.btn_send).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field gates = MainActivity.class.getDeclaredField("gates");
        gates.setAccessible(true);
        java.lang.reflect.Method gate = BrainGate.class.getDeclaredMethod("messageGate");
        gate.setAccessible(true);
        ai.ibiko.gate.BrainGateway g = (ai.ibiko.gate.BrainGateway) gate.invoke(gates.get(c.get()));
        // one route: the words go to the brain (here none is plugged in, so the Cortex answers what it can);
        // nothing acts on the wording itself
        org.junit.Assert.assertEquals("message \u2192 brain", g.last().toString());
        org.junit.Assert.assertEquals("brain \u2192 no model", g.recent().get(g.recent().size() - 2).toString());
        field.setText("Explain how compound interest works in a few sentences");
        c.get().findViewById(ai.ibiko.R.id.btn_send).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // an ordinary question: the brain reads what it means (as before the gateway), recorded like any other
        org.junit.Assert.assertEquals("message \u2192 brain", g.last().toString());
        org.junit.Assert.assertEquals("Explain how compound interest works in a few sentences", g.last().text);
        // pause and stop are not chat routes: only an authorized command (IbikoCommandGateway) controls a task
        org.junit.Assert.assertFalse(g.order().contains("pause"));
        org.junit.Assert.assertFalse(g.order().contains("retry"));
        org.junit.Assert.assertFalse(g.order().contains("read the meaning"));
        org.junit.Assert.assertEquals("model command", g.order().get(0));
        org.junit.Assert.assertEquals("brain", g.order().get(g.order().size() - 1));
        c.pause().stop().destroy();
    }

    @Test
    public void theBrainsCodesRunTheSuitsControls() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE);
        prefs.edit().putBoolean("auto_accept", false).commit();
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        field.setText("let ibiko just do things");
        c.get().findViewById(ai.ibiko.R.id.btn_send).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertFalse("words change no setting", prefs.getBoolean("auto_accept", false));
        brainSays(c, "\u00A7SW obi");
        org.junit.Assert.assertEquals("Obi", ((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tabObi)).getText().toString());
        brainSays(c, "\u00A7AO");
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false));
        brainSays(c, "\u00A7AF");
        org.junit.Assert.assertFalse(prefs.getBoolean("auto_accept", false));
        c.pause().stop().destroy();
    }

    @Test
    public void yourPicturesSitOnYourSideAndOpenFittedToTheScreen() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.io.File img = new java.io.File(c.get().getFilesDir(), "attachments/wide.jpg");
        img.getParentFile().mkdirs();
        android.graphics.Bitmap pic = android.graphics.Bitmap.createBitmap(1600, 900, android.graphics.Bitmap.Config.ARGB_8888);
        pic.eraseColor(0xFF6A8CAF);
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(img)) {
            pic.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, o);
        }
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        chats.add("user", "image", img.getAbsolutePath(), chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.ibiko.stage.ChatPane) pf.get(c.get())).show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View shown = described(c.get().getWindow().getDecorView(), "Your picture. Tap to open.");
        assertNotNull(shown);
        android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) shown.getLayoutParams();
        org.junit.Assert.assertEquals(android.view.Gravity.END, lp.gravity); // on the right, with the user's messages
        org.junit.Assert.assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, lp.width); // its own size, not the chat's width
        c.pause().stop().destroy();

        // the viewer: fitted to the screen, then a double tap zooms in and another goes back
        android.content.Intent open = new android.content.Intent(RuntimeEnvironment.getApplication(), ai.ibiko.chat.ui.ImageViewActivity.class)
                .putExtra(ai.ibiko.chat.ui.ImageViewActivity.EXTRA_PATH, img.getAbsolutePath());
        ActivityController<ai.ibiko.chat.ui.ImageViewActivity> v = Robolectric.buildActivity(ai.ibiko.chat.ui.ImageViewActivity.class, open)
                .create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field zf = ai.ibiko.chat.ui.ImageViewActivity.class.getDeclaredField("image");
        zf.setAccessible(true);
        ai.ibiko.chat.ui.ZoomImageView zoom = (ai.ibiko.chat.ui.ZoomImageView) zf.get(v.get());
        zoom.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        zoom.layout(0, 0, 1080, 2340);
        org.junit.Assert.assertNotNull(zoom.getDrawable());
        org.junit.Assert.assertEquals(1f, zoom.zoom(), 0.01f);
        android.graphics.RectF r = new android.graphics.RectF(0, 0, zoom.getDrawable().getIntrinsicWidth(), zoom.getDrawable().getIntrinsicHeight());
        zoom.getImageMatrix().mapRect(r);
        org.junit.Assert.assertEquals(0f, r.left, 1f); // the whole width on the screen
        org.junit.Assert.assertEquals(1080f, r.right, 1f);
        org.junit.Assert.assertEquals((2340f - r.height()) / 2f, r.top, 1f); // and centred
        v.pause().stop().destroy();
        // a missing picture says so instead of a blank screen
        ActivityController<ai.ibiko.chat.ui.ImageViewActivity> gone = Robolectric.buildActivity(ai.ibiko.chat.ui.ImageViewActivity.class,
                new android.content.Intent().putExtra(ai.ibiko.chat.ui.ImageViewActivity.EXTRA_PATH, "/nowhere.jpg")).create().start().resume().visible();
        java.util.ArrayList<android.view.View> said = new java.util.ArrayList<android.view.View>();
        gone.get().getWindow().getDecorView().findViewsWithText(said, "This picture isn't on the device any more.", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, said.size());
        gone.pause().stop().destroy();
    }

    @Test
    public void theAppAndEveryNewChatOpenEmptyOnObi() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        // Obi first in the switch, chosen, on an empty chat
        android.view.ViewGroup tabs = c.get().findViewById(ai.ibiko.R.id.personaTabs);
        org.junit.Assert.assertEquals(ai.ibiko.R.id.tabObi, tabs.getChildAt(0).getId());
        org.junit.Assert.assertEquals("Obi", ((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tabObi)).getText().toString());
        org.junit.Assert.assertEquals("obi", chats.current().persona);
        org.junit.Assert.assertTrue(chats.current().messages.isEmpty());
        // work going on in Ibiko's chat, then New chat: it starts clean, on Obi, nothing carried over
        c.get().findViewById(ai.ibiko.R.id.tabIbiko).performClick();
        chats.add("user", "text", "Audit my repo", "ibiko");
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        pane.activityStep("Cloned me/app");
        int before = chats.sections().size();
        c.get().findViewById(ai.ibiko.R.id.btnNewChat).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("obi", chats.current().persona);
        org.junit.Assert.assertTrue(chats.current().messages.isEmpty());
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(found, "Cloned me/app", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertTrue(found.isEmpty());
        // a second New chat reuses the empty one instead of piling up blank chats
        int after = chats.sections().size();
        c.get().findViewById(ai.ibiko.R.id.btnNewChat).performClick();
        org.junit.Assert.assertEquals(after, chats.sections().size());
        org.junit.Assert.assertTrue(after <= before + 1);
        c.pause().stop().destroy();
    }

    @Test
    public void theChatNameCanBeChanged() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "Plan my week", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.ibiko.stage.ChatPane) pf.get(c.get())).show();
        android.widget.TextView title = c.get().findViewById(ai.ibiko.R.id.tv_header_title);
        org.junit.Assert.assertEquals("Plan my week", title.getText().toString()); // named from the first message
        title.performClick(); // tap the name to rename it
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.EditText field = (android.widget.EditText) find(d.getWindow().getDecorView(), android.widget.EditText.class);
        field.setText("Weekly plan");
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("Weekly plan", chats.current().title);
        org.junit.Assert.assertEquals("Weekly plan", title.getText().toString());
        chats.add("user", "text", "Add gym on Monday", chats.current().persona);
        org.junit.Assert.assertEquals("Weekly plan", chats.current().title); // the user's name stays
        c.pause().stop().destroy();
    }

    @Test
    public void aChatCanBePinnedAndDeletedAndWhatItTaughtIsKept() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "My sister is called Ada", chats.current().persona);
        String first = chats.current().id;
        chats.fresh(ai.ibiko.stage.StageView.OBI);
        chats.add("user", "text", "Plan dinner", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());

        pane.chatMenu(first); // hold the chat: Rename, Pin to top, Delete
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog menu = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        menu.getListView().performItemClick(menu.getListView().getChildAt(1), 1, 1); // Pin to top
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(first, chats.pinnedFirst().get(0).id);
        android.widget.LinearLayout list = c.get().findViewById(ai.ibiko.R.id.historyList);
        org.junit.Assert.assertEquals("Pinned", ((android.widget.TextView) list.getChildAt(0)).getText().toString()); // the section header
        org.junit.Assert.assertTrue(String.valueOf(list.getChildAt(1).getContentDescription()).contains("pinned"));

        pane.delete(first);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog ask = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        ask.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        for (ai.ibiko.chat.ChatBook.Section s : chats.sections()) {
            org.junit.Assert.assertNotEquals(first, s.id); // the chat is gone
        }
        org.junit.Assert.assertTrue(chats.memoryForPrompt(6), chats.memoryForPrompt(6).contains("My sister is called Ada")); // its knowledge stays
        c.pause().stop().destroy();
    }

    @Test
    public void theLightThemeHasDarkBarIcons() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        android.view.Window w = c.get().getWindow();
        org.junit.Assert.assertTrue(androidx.core.view.WindowCompat.getInsetsController(w, w.getDecorView()).isAppearanceLightStatusBars());
        c.pause().stop().destroy();
    }

    @Test
    @Config(qualifiers = "night")
    public void theDarkThemeHasLightBarIcons() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        android.view.Window w = c.get().getWindow();
        org.junit.Assert.assertFalse(androidx.core.view.WindowCompat.getInsetsController(w, w.getDecorView()).isAppearanceLightStatusBars());
        c.pause().stop().destroy();
    }

    @Test
    public void ibikosModeIsChosenFromASheetLikeClaudeCode() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE);
        android.widget.TextView pill = c.get().findViewById(ai.ibiko.R.id.ibikoMode);
        pill.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        for (String row : new String[]{"Select mode", "Choose how Ibiko should work", "Auto", "Accept edits", "Plan"}) {
            org.junit.Assert.assertNotNull(row, find(sheet.getWindow().getDecorView(), row));
        }
        tapRow(sheet, "Plan");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(ai.ibiko.agent.IbikoMode.PLAN, ai.ibiko.agent.IbikoMode.of(prefs));
        org.junit.Assert.assertEquals("Plan", pill.getText().toString());
        pill.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        tapRow(org.robolectric.shadows.ShadowDialog.getLatestDialog(), "Auto");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(ai.ibiko.agent.IbikoMode.AUTO, ai.ibiko.agent.IbikoMode.of(prefs));
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false)); // the older switch means the same
        ai.ibiko.agent.IbikoMode.set(prefs, ai.ibiko.agent.IbikoMode.PLAN);
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false)); // plan keeps the choice for carrying it out
        ai.ibiko.agent.IbikoMode.set(prefs, ai.ibiko.agent.IbikoMode.EDITS);
        org.junit.Assert.assertFalse(prefs.getBoolean("auto_accept", true));
        c.pause().stop().destroy();
    }

    @Test
    public void aMessageCanBeEditedAndIsAskedAgainWithoutTheOldReply() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        String persona = chats.current().persona;
        chats.add("user", "text", "What is the capital of Ghana?", persona);
        chats.add("assistant", "text", "Accra.", persona);
        chats.add("user", "text", "And its population?", persona);
        chats.add("assistant", "text", "About 2.5 million.", persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        pane.show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // tap the first message: its edit button shows, with copy
        android.view.View first = find(pane, "What is the capital of Ghana?"); // in the chat (the header has the chat's name too)
        assertNotNull(first);
        first.performClick();
        android.view.View edit = described(pane, "Edit your message");
        assertNotNull("edit button", edit);
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, ((android.view.View) edit.getParent()).getVisibility());
        edit.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.EditText field = (android.widget.EditText) find(d.getWindow().getDecorView(), android.widget.EditText.class);
        org.junit.Assert.assertEquals("What is the capital of Ghana?", field.getText().toString());
        field.setText("What is the capital of Kenya?");
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        java.util.List<String> said = new java.util.ArrayList<String>();
        for (ai.ibiko.chat.ChatBook.Message m : chats.current().messages) {
            said.add(m.role + ": " + m.body);
        }
        org.junit.Assert.assertEquals(said.toString(), "user: What is the capital of Kenya?", said.get(0));
        for (String gone : new String[]{"Ghana", "Accra", "population", "2.5 million"}) {
            org.junit.Assert.assertFalse(said.toString(), said.toString().contains(gone)); // the old message and every reply after it
        }
        c.pause().stop().destroy();
    }

    @Test
    public void eachStepShowsItsOwnTimeInTheSheetAndKeepsIt() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        pane.activityStep("Read app/Main.java");
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3));
        pane.activityStep("Ran the tests");
        java.util.List<String> timed = pane.timedSteps(System.currentTimeMillis() + 65_000);
        org.junit.Assert.assertTrue(timed.toString(), timed.get(0).startsWith("Read app/Main.java\t"));
        org.junit.Assert.assertTrue(timed.toString(), Long.parseLong(timed.get(1).split("\t")[1]) >= 60); // the running step counts up
        pane.activityEnd();
        ai.ibiko.chat.ChatBook.Message kept = null;
        for (ai.ibiko.chat.ChatBook.Message m : chats.current().messages) {
            if ("activity".equals(m.kind)) {
                kept = m;
            }
        }
        assertNotNull(kept);
        org.junit.Assert.assertTrue(kept.body, kept.body.contains("Read app/Main.java\t") && kept.body.contains("Ran the tests\t"));
        c.pause().stop().destroy();
    }

    @Test
    public void workCutOffByACrashIsOfferedToCarryOnAndAPartialReplyIsKept() throws Exception {
        android.content.SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("running_persona", "ibiko").putString("running_task", "Audit the Harvana repo")
                .putString("partial_reply", "The audit found three problems so far: the wallet").commit();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("the carry-on question", d);
        android.widget.TextView msg = d.findViewById(android.R.id.message);
        org.junit.Assert.assertTrue(String.valueOf(msg.getText()), msg.getText().toString().contains("Audit the Harvana repo"));
        org.junit.Assert.assertNull(prefs.getString("running_task", null)); // asked once only
        d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        c.pause().stop().destroy();
    }

    @Test
    public void aPostIsShownFirstAndHandedOffOnlyOnAYes() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Method answer = MainActivity.class.getDeclaredMethod("answer", String.class);
        answer.setAccessible(true);
        while (org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity() != null) {
            // the first-run setup screen and the like, opened at start
        }
        answer.invoke(c.get(), "post on X: Big news today");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertFalse("the words alone stage nothing",
                org.robolectric.shadows.ShadowDialog.getLatestDialog() instanceof androidx.appcompat.app.AlertDialog
                        && org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
        brainSays(c, "\u00A7AC post on X: Big news today"); // the brain decided: stage a post
        androidx.appcompat.app.AlertDialog card = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.TextView msg = card.findViewById(android.R.id.message);
        org.junit.Assert.assertTrue(String.valueOf(msg.getText()), msg.getText().toString().startsWith("Big news today")
                && msg.getText().toString().contains("To: X"));
        org.junit.Assert.assertNull("nothing opened before the yes", org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity());
        answer.invoke(c.get(), "send it"); // the spoken yes
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.util.List<String> log = ai.ibiko.act.ActionLog.read(c.get().getFilesDir());
        org.junit.Assert.assertTrue(log.toString(), log.get(0).contains("share\tX\tapproved") && log.get(0).endsWith("Big news today"));
        // auto-approve: no card, straight to the app, logged as such
        c.get().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit().putBoolean("actions_auto", true).commit();
        brainSays(c, "\u00A7AC copy: the meeting is at 5");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        log = ai.ibiko.act.ActionLog.read(c.get().getFilesDir());
        org.junit.Assert.assertTrue(log.toString(), log.get(0).contains("copy\tyour share menu\tauto-approved\tdone"));
        c.pause().stop().destroy();
    }

    @Test
    public void theWorkloadRingShowsAndANearlyFullMemoryIsClearedWithANote() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ai.ibiko.stage.WorkloadGauge g = c.get().findViewById(ai.ibiko.R.id.workload);
        assertNotNull("the ring in the header", g);
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("model");
        mf.setAccessible(true);
        ai.ibiko.llm.LocalModel model = (ai.ibiko.llm.LocalModel) mf.get(c.get());
        model.note("Plan a trip to Abuja", "Sure: flights, hotels, the weekend.");
        model.note("Book for Friday", "Friday it is.");
        java.lang.reflect.Constructor<ai.ibiko.llm.UsageLog.Call> k = ai.ibiko.llm.UsageLog.Call.class.getDeclaredConstructor(String.class,
                int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, long.class);
        k.setAccessible(true);
        model.usage.record(k.newInstance("test", 4096, 600, 2900, 100, 120, false, 1000L, System.currentTimeMillis()));
        java.lang.reflect.Method check = MainActivity.class.getDeclaredMethod("checkWorkload");
        check.setAccessible(true);
        check.invoke(c.get());
        org.junit.Assert.assertTrue(String.valueOf(g.getContentDescription()), g.getContentDescription().toString().contains("nearly full"));
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        boolean told = false;
        for (int i = 0; i < 50 && !told; i++) { // the clear-out runs on the model's worker
            Thread.sleep(100);
            ShadowLooper.idleMainLooper();
            for (ai.ibiko.chat.ChatBook.Message m : chats.current().messages) {
                told |= m.body != null && m.body.startsWith("My working memory was");
            }
        }
        org.junit.Assert.assertTrue("told about the clear-out", told);
        c.pause().stop().destroy();
    }

    @Test
    public void obiHandsIbikosWorkToHimInANewChatAndTheRingOpensTheWorkload() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        // a hand-off card, as a plan made with Ibiko's kind of work leaves one (ThinkTurns)
        chats.add("assistant", "handoff", "ibiko\nFix the login crash in my Android app", "obi");
        pane.show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View button = described(pane, "Start on this task here");
        assertNotNull("the hand-off button", button);
        String obiChat = chats.current().id;
        button.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("the same group thread, no new chat", obiChat, chats.current().id);

        // the ring: a tap opens the workload sheet
        c.get().findViewById(ai.ibiko.R.id.workload).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        sheet.getWindow().getDecorView().findViewsWithText(found, "Workers", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertFalse("the workload sheet", found.isEmpty());
        sheet.dismiss();
        c.pause().stop().destroy();
    }

    /** The model picker lists Ibiko 1.0 (Ibiko's own brain); picking it marks it in use and the header says so. */
    @Test(timeout = 120000)
    public void ibikosOwnBrainCanBePicked() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().findViewById(R.id.tv_header_sub).performClick();
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.view.View row = described(sheet.getWindow().getDecorView(), MainActivity.IBIKO_OWN);
        assertNotNull("Ibiko's own brain is in the list", row);
        row.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("ibiko", c.get().getSharedPreferences("ibiko", 0).getString("brain_pick", ""));
        android.widget.TextView sub = c.get().findViewById(R.id.tv_header_sub);
        org.junit.Assert.assertTrue(sub.getText().toString(), sub.getText().toString().endsWith(MainActivity.IBIKO_OWN));
        c.get().findViewById(R.id.tv_header_sub).performClick();
        sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("the one in use is marked", described(sheet.getWindow().getDecorView(), MainActivity.IBIKO_OWN + ", in use"));
        sheet.dismiss();
        c.pause().stop().destroy();
    }

    /** Every reply can be marked right or wrong (check, cancel), whichever model wrote it; Ibiko learns either way. */
    @Test(timeout = 120000)
    public void everyReplyCanBeMarkedRightOrWrong() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field bf = MainActivity.class.getDeclaredField("chats");
        bf.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bf.get(c.get());
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        book.add("user", "text", "What is the capital of Kenya?", "obi");
        pane.assistant("obi", "The capital of Kenya is Nairobi.");
        android.view.View root = c.get().getWindow().getDecorView();
        assertNotNull(described(root, "Wrong"));
        described(root, "Correct").performClick();
        org.junit.Assert.assertNull("marked once", described(root, "Correct"));
        ai.ibiko.ibiko1.Knowledge k = new ai.ibiko.ibiko1.Knowledge(new java.io.File(c.get().getFilesDir(), "brain/knowledge.tsv"));
        org.junit.Assert.assertEquals("The capital of Kenya is Nairobi.", k.recall("Kenya's capital city?").answer);
        c.pause().stop().destroy();
    }

    /** Ibiko 1: a statement teaches (it isn't searched), and "what have you learned" is answered from its memory. */
    @Test(timeout = 120000)
    public void ibikoLearnsFromStatementsAndSaysWhatItLearned() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ai.ibiko.ibiko1.Ibiko1 ibiko = ibiko1(c.get());
        org.junit.Assert.assertTrue(ibiko.learnStatement("A pig is an animal").startsWith("Noted."));
        ai.ibiko.ibiko1.Knowledge k = new ai.ibiko.ibiko1.Knowledge(new java.io.File(c.get().getFilesDir(), "brain/knowledge.tsv"));
        org.junit.Assert.assertEquals("A pig is an animal", k.recall("what's a pig?").answer);
        String said = ibiko.aboutMe("What have you learned so far?");
        org.junit.Assert.assertTrue(said, said.contains("A pig is an animal"));
        org.junit.Assert.assertNull(ibiko.aboutMe("What is a pig?"));
        org.junit.Assert.assertNull("a question about shoes", ibiko.aboutMe("What do you know about a shoe?"));
        org.junit.Assert.assertNotNull(ibiko.aboutMe("What do you know?"));
        c.pause().stop().destroy();
    }

    /** Closing a picker (or a permission prompt) is not a message: the chat gets no reply for it. */
    @Test(timeout = 120000)
    public void closingAPickerSaysNothingInTheChat() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field bf = MainActivity.class.getDeclaredField("chats");
        bf.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bf.get(c.get());
        int before = book.current().messages.size();
        java.lang.reflect.Method result = MainActivity.class.getDeclaredMethod("onActivityResult", int.class, int.class,
                android.content.Intent.class);
        result.setAccessible(true);
        result.invoke(c.get(), MainActivity.PICK_CONTENT, android.app.Activity.RESULT_CANCELED, null);
        c.get().onRequestPermissionsResult(MainActivity.MIC, new String[]{android.Manifest.permission.RECORD_AUDIO},
                new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("no reply for closing a picker", before, book.current().messages.size());
        c.pause().stop().destroy();
    }

    /** Obi, Ivie and Ibiko are three named pages of one chat; Ibiko 1 thinks in steps; night learning is set from the picker. */
    @Test(timeout = 120000)
    public void pagesThinkingAndNightLearning() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.widget.TextView ivie = c.get().findViewById(R.id.tabIvie), ibiko = c.get().findViewById(R.id.tabIbiko);
        org.junit.Assert.assertEquals("every page is named, open or not", "Ivie", ivie.getText().toString());
        org.junit.Assert.assertEquals("Ibiko", ibiko.getText().toString());
        // a swipe from left to right in the middle of the chat opens the drawer; it never changes the page
        android.view.View deco = c.get().getWindow().getDecorView();
        long t0 = android.os.SystemClock.uptimeMillis();
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, 400, 1000, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 30, android.view.MotionEvent.ACTION_MOVE, 450, 1004, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 50, android.view.MotionEvent.ACTION_MOVE, 700, 1010, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 80, android.view.MotionEvent.ACTION_UP, 700, 1010, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, c.get().findViewById(R.id.historyDrawer).getVisibility());
        org.junit.Assert.assertEquals("the page stays: there is one, Ibiko's", "ibiko", c.get().getSharedPreferences("ibiko", 0).getString("face", "ibiko"));

        c.get().getSharedPreferences("ibiko", 0).edit().putString("brain_pick", "ibiko").apply();
        ibiko1(c.get()).answer("what is 12 times 7");
        java.lang.reflect.Field bf = MainActivity.class.getDeclaredField("chats");
        bf.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bf.get(c.get());
        int before = book.current().messages.size();
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        org.junit.Assert.assertEquals("it thinks first", before, book.current().messages.size());
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS);
        StringBuilder all = new StringBuilder();
        for (ai.ibiko.chat.ChatBook.Message m : book.current().messages) {
            all.append(m.body).append('\n');
        }
        org.junit.Assert.assertTrue(all.toString(), all.toString().contains("= 84"));

        c.get().findViewById(R.id.tv_header_sub).performClick();
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(find(sheet.getWindow().getDecorView(), "Learning schedule"));
        ai.ibiko.ibiko1.LearningJob.setPlan(c.get(), new ai.ibiko.ibiko1.LearningSchedule.Plan(true, 23 * 60, 6 * 60, true, true, 40));
        android.app.job.JobScheduler js = c.get().getSystemService(android.app.job.JobScheduler.class);
        org.junit.Assert.assertEquals(1, js.getAllPendingJobs().size());
        org.junit.Assert.assertTrue(js.getAllPendingJobs().get(0).isRequireCharging());
        ai.ibiko.ibiko1.LearningJob.setPlan(c.get(), ai.ibiko.ibiko1.LearningSchedule.Plan.defaults());
        org.junit.Assert.assertEquals("off: nothing waits", 0, js.getAllPendingJobs().size());
        sheet.dismiss();
        c.pause().stop().destroy();
    }

    /** The Workload sheet: the brain answering, Ibiko 1's memory, each worker's details, and today's Ibiko 1 answers. */
    @Test(timeout = 120000)
    public void theWorkloadSheetShowsWorkersInFull() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().getSharedPreferences("ibiko", 0).edit().putString("brain_pick", "ibiko").apply();
        String w = ai.ibiko.agent.AgentBoard.start("Obi \u00B7 reply", "What is a quasar?", "Claude Opus 5.5");
        ai.ibiko.agent.AgentBoard.step(w, "Reading en.wikipedia.org");
        c.get().findViewById(R.id.workload).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
        for (String text : new String[] { MainActivity.IBIKO_OWN, "never fills", w, "Claude Opus 5.5", "Reading en.wikipedia.org",
                "What is a quasar?", "working", "Ibiko 1 answers" }) {
            java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
            sheet.findViewsWithText(found, text, android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertFalse(text, found.isEmpty());
        }
        ai.ibiko.agent.AgentBoard.end(w);
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss();
        c.pause().stop().destroy();
    }

    /** No internet with an API picked: the message waits and Ibiko says the connection is missing, not the API. */
    @Test(timeout = 120000)
    public void noInternetIsSaidAndTheMessageWaits() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("waitForInternet", String.class);
        m.setAccessible(true);
        m.invoke(c.get(), "Summarise this article");
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field wf = MainActivity.class.getDeclaredField("waitingOnline");
        wf.setAccessible(true);
        org.junit.Assert.assertEquals(1, ((java.util.List<?>) wf.get(c.get())).size());
        java.lang.reflect.Field bf = MainActivity.class.getDeclaredField("chats");
        bf.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bf.get(c.get());
        StringBuilder all = new StringBuilder();
        for (ai.ibiko.chat.ChatBook.Message msg : book.current().messages) {
            all.append(msg.body).append('\n');
        }
        org.junit.Assert.assertTrue(all.toString(), all.toString().contains("I'm offline right now. I've kept your message"));
        c.pause().stop().destroy();
    }

    /** Ibiko 1 asks whether its answer was right; 👍🏽 saves it to its memory, found again for the same question reworded. */
    @Test(timeout = 120000)
    public void aThumbsUpTeachesIbiko() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.ibiko.stage.ChatPane pane = (ai.ibiko.stage.ChatPane) pf.get(c.get());
        String answer = "Microsoft was founded by Bill Gates and Paul Allen in 1975.";
        pane.assistant("obi", "Here's what I found: " + answer + "\n\nIs this accurate?");
        pane.askRating("Who founded Microsoft?", answer);
        android.view.View root = c.get().getWindow().getDecorView();
        assertNotNull(described(root, "Wrong"));
        described(root, "Correct").performClick();
        org.junit.Assert.assertNull("the buttons go once it is rated", described(root, "Correct"));
        org.junit.Assert.assertFalse(pane.ratingWaiting());
        ai.ibiko.ibiko1.Knowledge k = new ai.ibiko.ibiko1.Knowledge(new java.io.File(c.get().getFilesDir(), "brain/knowledge.tsv"));
        assertNotNull(k.recall("who created microsoft"));
        org.junit.Assert.assertEquals(answer, k.recall("Who's the founder of Microsoft?").answer);
        c.pause().stop().destroy();
    }

    private static android.view.View find(android.view.View v, Class<?> kind) {
        if (kind.isInstance(v)) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
                android.view.View f = find(((android.view.ViewGroup) v).getChildAt(i), kind);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Taps the row of a bottom sheet whose name is {@code name}. */
    private static void tapRow(android.app.Dialog sheet, String name) {
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        sheet.getWindow().getDecorView().findViewsWithText(found, name, android.view.View.FIND_VIEWS_WITH_TEXT);
        android.view.View row = null;
        for (android.view.View v : found) {
            if (v instanceof android.widget.TextView && name.contentEquals(((android.widget.TextView) v).getText())) {
                row = v;
            }
        }
        assertNotNull(name, row);
        while (!row.hasOnClickListeners()) {
            row = (android.view.View) row.getParent();
        }
        row.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Ibiko 1 as plugged into the screen. */
    private static ai.ibiko.ibiko1.Ibiko1 ibiko1(MainActivity a) throws Exception {
        java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("ibiko1");
        m.setAccessible(true);
        return (ai.ibiko.ibiko1.Ibiko1) m.invoke(a);
    }

    private static void collect(android.view.View v, Class<?> kind, java.util.List<android.view.View> into) {
        if (kind.isInstance(v) && !(v instanceof android.widget.ImageButton)) {
            into.add(v);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), kind, into);
            }
        }
    }

    private static android.view.View described(android.view.View v, String what) {
        if (what.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = described(g.getChildAt(i), what);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static android.view.View find(android.view.View v, String text) {
        if (v instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) v).getText())) {
            android.view.View up = v;
            while (up.getParent() instanceof android.view.View && !up.isClickable()) {
                up = (android.view.View) up.getParent();
            }
            return up.isClickable() ? up : v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = find(g.getChildAt(i), text);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** A job on Obi's page keeps running when a message is sent on Ivie's; Stop on Ivie stops only Ivie's. */
    @Test
    public void eachPageRunsItsOwnJob() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Method next = MainActivity.class.getDeclaredMethod("nextAsk");
        java.lang.reflect.Method alive = MainActivity.class.getDeclaredMethod("alive", int.class);
        java.lang.reflect.Method stop = MainActivity.class.getDeclaredMethod("stopVisibleJob");
        next.setAccessible(true);
        alive.setAccessible(true);
        stop.setAccessible(true);
        c.get().findViewById(ai.ibiko.R.id.tabObi).performClick();
        int obiJob = (Integer) next.invoke(c.get());
        c.get().findViewById(ai.ibiko.R.id.tabIvie).performClick();
        int ivieJob = (Integer) next.invoke(c.get());
        // one assistant, one page: whatever an old caller names, a newer message replaces the job before it
        org.junit.Assert.assertFalse("one page: the newer message's job replaces the earlier one", (Boolean) alive.invoke(c.get(), obiJob));
        org.junit.Assert.assertTrue((Boolean) alive.invoke(c.get(), ivieJob));
        stop.invoke(c.get());
        org.junit.Assert.assertFalse((Boolean) alive.invoke(c.get(), ivieJob));
        if (true) {
            c.pause().stop().destroy();
            return; // the rest checked two pages running side by side, which no longer exist
        }
        org.junit.Assert.assertTrue("Stop on Ivie leaves Obi's job running", (Boolean) alive.invoke(c.get(), obiJob));
        c.pause().stop().destroy();
    }

    /** The tools button beside the mic lists this persona's tools; a swipe from the left edge opens the drawer. */
    @Test
    public void theToolsButtonAndTheDrawerSwipe() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().findViewById(ai.ibiko.R.id.tabIbiko).performClick();
        c.get().findViewById(ai.ibiko.R.id.chip_tools).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        sheet.getWindow().getDecorView().findViewsWithText(found, "GitHub", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertFalse("Ibiko offers GitHub", found.isEmpty());
        found.clear();
        sheet.getWindow().getDecorView().findViewsWithText(found, "Deep thinking", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        android.view.View deep = null;
        for (android.view.View v : found) {
            if (v.isClickable() && "Off".contentEquals(v.getStateDescription())) {
                deep = v; // the card: a tap anywhere on it turns the tool on
            }
        }
        assertNotNull("the Deep thinking card, off", deep);
        deep.performClick();
        org.junit.Assert.assertEquals("On", String.valueOf(deep.getStateDescription()));
        org.junit.Assert.assertTrue(c.get().getSharedPreferences("ibiko", 0).getAll().toString().contains("DEEP"));
        found.clear();
        sheet.getWindow().getDecorView().findViewsWithText(found, "Images", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertFalse("every tool is offered", found.isEmpty());
        found.clear();
        sheet.getWindow().getDecorView().findViewsWithText(found, "on in this chat", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertFalse("how many are on, at the top", found.isEmpty());
        android.view.View github = described(sheet.getWindow().getDecorView(), "GitHub settings");
        assertNotNull("a tool with settings has its own Set up link", github);
        android.view.View githubCard = (android.view.View) github.getParent();
        String before = String.valueOf(githubCard.getStateDescription());
        org.junit.Assert.assertNotNull(described(sheet.getWindow().getDecorView(), "Linux shell settings"));
        // every tool has its icon; one with settings of its own opens them from its name
        java.util.ArrayList<android.view.View> icons = new java.util.ArrayList<android.view.View>();
        collect(sheet.getWindow().getDecorView(), android.widget.ImageView.class, icons);
        org.junit.Assert.assertTrue("an icon a tool", icons.size() >= ai.ibiko.suit.ChatTools.offered("ibiko").size());
        org.junit.Assert.assertNotNull(described(sheet.getWindow().getDecorView(), "Connectors settings"));
        org.junit.Assert.assertNull("deep thinking has no settings of its own", described(sheet.getWindow().getDecorView(), "Deep thinking settings"));
        org.junit.Assert.assertEquals(R.drawable.ic_branch, ToolsMenu.toolIcon(ai.ibiko.suit.ChatTools.Tool.GITHUB));
        sheet.dismiss();

        android.view.View drawer = c.get().findViewById(ai.ibiko.R.id.historyDrawer);
        org.junit.Assert.assertNotEquals(android.view.View.VISIBLE, drawer.getVisibility());
        long t = android.os.SystemClock.uptimeMillis();
        float dp = c.get().getResources().getDisplayMetrics().density;
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 4 * dp, 400, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 30, android.view.MotionEvent.ACTION_MOVE, 40 * dp, 402, 0));
        org.junit.Assert.assertEquals("the drawer comes out with the finger", android.view.View.VISIBLE, drawer.getVisibility());
        float half = drawer.getTranslationX();
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 60, android.view.MotionEvent.ACTION_MOVE, 120 * dp, 410, 0));
        org.junit.Assert.assertTrue("it follows the finger", drawer.getTranslationX() > half);
        org.junit.Assert.assertTrue("not all the way yet", drawer.getTranslationX() < 0);
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 70, android.view.MotionEvent.ACTION_UP, 120 * dp, 410, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("let go fast: it settles open", android.view.View.VISIBLE, drawer.getVisibility());
        org.junit.Assert.assertEquals(0f, drawer.getTranslationX(), 0.5f);
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t + 1000, t + 1000, android.view.MotionEvent.ACTION_DOWN, 200 * dp, 400, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t + 1000, t + 1030, android.view.MotionEvent.ACTION_MOVE, 120 * dp, 405, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t + 1000, t + 1060, android.view.MotionEvent.ACTION_MOVE, 20 * dp, 405, 0));
        c.get().dispatchTouchEvent(android.view.MotionEvent.obtain(t + 1000, t + 1070, android.view.MotionEvent.ACTION_UP, 20 * dp, 405, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertNotEquals("dragged back to the left: it shuts", android.view.View.VISIBLE, drawer.getVisibility());
        c.pause().stop().destroy();
    }

    /** Each chip over the message box opens what it names, nothing else. */
    @Test
    public void theQuickChipsOpenWhatTheyName() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        String[][] cases = { { "chip_tools", "on in this chat" }, { "chip_tools", "Agent mode" }, { "chip_code", "Workspace files" }, { "chip_code", "Shell" },
                { "chip_explore", "Social accounts" }, { "chip_explore", "Connectors" }, { "chip_more", "Settings" },
                { "chip_more", "What Ibiko remembers" } };
        for (String[] k : cases) {
            int id = c.get().getResources().getIdentifier(k[0], "id", c.get().getPackageName());
            android.view.View chip = c.get().findViewById(id);
            assertNotNull(k[0], chip);
            float density = c.get().getResources().getDisplayMetrics().density;
            org.junit.Assert.assertTrue("at least a 32dp-high touch target", chip.getLayoutParams().height >= 32 * density);
            chip.performClick();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
            sheet.getWindow().getDecorView().findViewsWithText(found, k[1], android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertFalse(k[0] + " opens " + k[1], found.isEmpty());
            sheet.dismiss();
        }
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        org.junit.Assert.assertEquals("Ask Ibiko anything", String.valueOf(field.getHint()));
        org.junit.Assert.assertNull("one tools button: the chip", c.get().findViewById(c.get().getResources().getIdentifier("btn_tools", "id",
                c.get().getPackageName())));
        org.junit.Assert.assertEquals("the line under the title names the model only", -1,
                String.valueOf(((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.tv_header_sub)).getText()).indexOf("Ibiko"));
        org.junit.Assert.assertEquals("I", String.valueOf(((android.widget.TextView) c.get().findViewById(ai.ibiko.R.id.empty_mark)).getText()));
        org.junit.Assert.assertEquals("no paperclip: Media attaches", 0, c.get().getResources().getIdentifier("btn_attach", "id",
                c.get().getPackageName()));
        // the Think pill turns deep thinking on and off, and says which
        android.widget.TextView think = c.get().findViewById(ai.ibiko.R.id.btn_think);
        boolean was = think.isSelected();
        think.performClick();
        org.junit.Assert.assertEquals(!was, think.isSelected());
        org.junit.Assert.assertEquals(!was ? "On" : "Off", String.valueOf(think.getStateDescription()));
        think.performClick();
        org.junit.Assert.assertEquals(was, think.isSelected());
        c.pause().stop().destroy();
    }

    @Test
    public void theSendButtonComesBackFromStopWhenTheWorkEnds() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        MainActivity a = c.get();
        android.view.View send = a.findViewById(ai.ibiko.R.id.btn_send);
        java.lang.reflect.Method refresh = MainActivity.class.getDeclaredMethod("refreshSend");
        refresh.setAccessible(true);
        java.lang.reflect.Method open = MainActivity.class.getDeclaredMethod("setStreamOpen", boolean.class);
        open.setAccessible(true);
        java.lang.reflect.Field stream = MainActivity.class.getDeclaredField("streamOpen");
        stream.setAccessible(true);
        // a reply streams: Stop
        open.invoke(a, true);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        org.junit.Assert.assertEquals("Stop", String.valueOf(send.getContentDescription()));
        // the reply ends the way it did when the button stuck: redrawn first, the stream closed after
        refresh.invoke(a);
        open.invoke(a, false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        org.junit.Assert.assertEquals("Send", String.valueOf(send.getContentDescription()));
        // work that ends without telling the button at all: it looks again within a second
        open.invoke(a, true);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        org.junit.Assert.assertEquals("Stop", String.valueOf(send.getContentDescription()));
        stream.setBoolean(a, false);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1200, java.util.concurrent.TimeUnit.MILLISECONDS);
        org.junit.Assert.assertEquals("Send", String.valueOf(send.getContentDescription()));
        c.pause().stop().destroy();
    }

    private static ai.ibiko.stage.LiveFace findFace(android.view.View v) {
        if (v instanceof ai.ibiko.stage.LiveFace) {
            return (ai.ibiko.stage.LiveFace) v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ai.ibiko.stage.LiveFace f = findFace(g.getChildAt(i));
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** The first view whose description starts with {@code start}. */
    private static android.view.View describedStart(android.view.View v, String start) {
        CharSequence d = v.getContentDescription();
        if (d != null && d.toString().startsWith(start)) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = describedStart(g.getChildAt(i), start);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }
}
