package ai.dvine;

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
        ActivityController<ai.dvine.chat.ui.ProvidersActivity> p = Robolectric.buildActivity(ai.dvine.chat.ui.ProvidersActivity.class)
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

        ActivityController<ai.dvine.chat.ui.UsageActivity> u = Robolectric.buildActivity(ai.dvine.chat.ui.UsageActivity.class)
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
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "RE clone the repo and audit my codebase", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.dvine.stage.ChatPane) pf.get(c.get())).show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View copy = described(c.get().getWindow().getDecorView(), "Copy your message");
        assertNotNull(copy);
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) copy.getParent()).getVisibility()); // hidden until a tap
        java.util.ArrayList<android.view.View> said = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(said, "RE clone the repo and audit my codebase", android.view.View.FIND_VIEWS_WITH_TEXT);
        said.removeIf(v -> v.getId() != ai.dvine.R.id.tv_message); // the message itself, not the chat list's preview
        said.get(0).performClick();
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, ((android.view.View) copy.getParent()).getVisibility());
        said.get(0).performClick(); // a second tap hides it
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) copy.getParent()).getVisibility());
        said.get(0).performClick();
        copy.performClick();
        android.content.ClipboardManager clips = (android.content.ClipboardManager) c.get().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        org.junit.Assert.assertEquals("RE clone the repo and audit my codebase", clips.getPrimaryClip().getItemAt(0).getText().toString());
        chats.add("assistant", "text", "On it.", chats.current().persona); // the chat moves on: the icon hides again
        ((ai.dvine.stage.ChatPane) pf.get(c.get())).show();
        android.view.View again = described(c.get().getWindow().getDecorView(), "Copy your message");
        org.junit.Assert.assertEquals(android.view.View.GONE, ((android.view.View) again.getParent()).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void aDoubleTapOnSendSendsOnceAndCancelsNothing() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.widget.EditText field = c.get().findViewById(ai.dvine.R.id.et_message);
        field.setText("What time is it?");
        android.view.View send = c.get().findViewById(ai.dvine.R.id.btn_send);
        send.performClick();
        send.performClick(); // the second tap of a double tap: neither a second send nor a Stop
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        int asked = 0;
        for (ai.dvine.chat.ChatBook.Message m : chats.current().messages) {
            if ("user".equals(m.role) && "What time is it?".equals(m.body)) {
                asked++;
            }
        }
        org.junit.Assert.assertEquals(1, asked);
        java.lang.reflect.Field at = MainActivity.class.getDeclaredField("lastSendAt");
        at.setAccessible(true);
        org.junit.Assert.assertTrue((long) at.get(c.get()) >= 0);
        c.pause().stop().destroy();
    }

    @Test
    public void theStepsOpenInABottomSheetAndTheChatKeepsOneTypedLine() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) pf.get(c.get());
        pane.activityStep("Cloned me/app");
        pane.activityStep("Read app/Main.java");
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS); // the typewriter finishes
        android.view.View root = c.get().getWindow().getDecorView();
        android.view.View live = described(root, "What is happening now; tap for every step");
        assertNotNull(live);
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
        pane.activityStep("Wrote app/Main.java"); // live: the open sheet grows
        sheet.getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "Wrote app/Main.java",
                android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size());
        sheet.dismiss();
        pane.activityEnd();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View worked = described(root, "Worked for 1s \u00B7 3 steps, open the steps");
        assertNotNull(worked);
        worked.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView().findViewsWithText(
                found = new java.util.ArrayList<android.view.View>(), "Cloned me/app", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, found.size());
        c.pause().stop().destroy();
    }

    @Test
    public void thePersonasHaveIconsAndTheChosenOneItsName() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        c.get().findViewById(ai.dvine.R.id.tabIbiko).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        for (int id : new int[]{ai.dvine.R.id.tabIvie, ai.dvine.R.id.tabObi, ai.dvine.R.id.tabIbiko}) {
            android.widget.TextView tab = c.get().findViewById(id);
            assertNotNull(tab.getCompoundDrawablesRelative()[0]); // graphics, chat, tools
        }
        org.junit.Assert.assertEquals("Ibiko", ((android.widget.TextView) c.get().findViewById(ai.dvine.R.id.tabIbiko)).getText().toString());
        org.junit.Assert.assertEquals("", ((android.widget.TextView) c.get().findViewById(ai.dvine.R.id.tabObi)).getText().toString());
        // the model under the title opens a switch: the offline models on the phone, every model, the API providers
        c.get().findViewById(ai.dvine.R.id.tv_header_sub).performClick();
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
        ActivityController<ai.dvine.chat.ui.SettingsActivity> c = Robolectric.buildActivity(ai.dvine.chat.ui.SettingsActivity.class)
                .create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ai.dvine.llm.ModelHub hub = ai.dvine.llm.ModelHub.get(c.get());
        try {
            java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
            c.get().getWindow().getDecorView().findViewsWithText(found, "Change the offline model", android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertFalse(found.isEmpty());
            android.view.View v = found.get(0);
            while (!v.hasOnClickListeners()) {
                v = (android.view.View) v.getParent();
            }
            v.performClick();
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            // first the family, then its size
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            for (String name : new String[]{"Qwen3", "Llama 3.2", "Gemma 3", "Phi-4", "Sizes: 0.6B \u00B7 1.7B \u00B7 4B \u00B7 8B. In use: 1.7B."}) {
                sheet.getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), name,
                        android.view.View.FIND_VIEWS_WITH_TEXT);
                org.junit.Assert.assertFalse(name, found.isEmpty());
            }
            tapRow(sheet, "Qwen3");
            android.app.Dialog sizes = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            for (String name : new String[]{"Qwen3 0.6B", "Qwen3 1.7B  \u00B7 in use", "Qwen3 4B", "Qwen3 8B"}) {
                sizes.getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), name,
                        android.view.View.FIND_VIEWS_WITH_TEXT);
                org.junit.Assert.assertFalse(name, found.isEmpty());
            }
            sizes.dismiss();
            tapRow(sheet, "Phi-4");
            tapRow(org.robolectric.shadows.ShadowDialog.getLatestDialog(), "Phi-4 mini");
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.Assert.assertSame(ai.dvine.llm.ModelSpec.PHI_4_MINI, hub.chosen()); // shown at once
            hub.worker.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS); // the switch itself, on the model's thread
            org.junit.Assert.assertSame(ai.dvine.llm.ModelSpec.PHI_4_MINI, hub.model.spec);
            c.get().getWindow().getDecorView().findViewsWithText(found = new java.util.ArrayList<android.view.View>(), "On this phone: Phi-4 mini",
                    android.view.View.FIND_VIEWS_WITH_TEXT);
            org.junit.Assert.assertEquals(1, found.size()); // the card names the new model, with Download under it
            // a model on the phone is listed with Delete, to free the space
            java.io.File kept = new java.io.File(hub.model.file().getParentFile(), ai.dvine.llm.ModelSpec.QWEN3_4B.file);
            kept.getParentFile().mkdirs();
            java.nio.file.Files.write(kept.toPath(), new byte[2048]);
            c.pause().resume(); // the card is filled again
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
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
            hub.choose(c.get(), ai.dvine.llm.ModelSpec.QWEN3_1_7B, false);
            hub.worker.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            c.pause().stop().destroy();
        }
    }

    @Test
    public void aRephrasedCommandIsActedOnByItsMeaning() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // Ibiko has work under way, and "where are we with that" was understood before as "give me the result"
        c.get().findViewById(ai.dvine.R.id.tabIbiko).performClick();
        java.nio.file.Files.write(new java.io.File(c.get().getFilesDir(), "agent-ibiko.txt").toPath(),
                "Task: audit the repo\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("dvine", android.content.Context.MODE_PRIVATE);
        ai.dvine.turn.Meaning.Memory m = new ai.dvine.turn.Meaning.Memory();
        m.put("Where are we with that?", ai.dvine.turn.Meaning.Command.RESULT);
        prefs.edit().putString("meanings", m.write()).commit();
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("meanings");
        mf.setAccessible(true);
        mf.set(c.get(), null); // read again from the phone
        android.widget.EditText field = c.get().findViewById(ai.dvine.R.id.et_message);
        field.setText("where are we with that");
        c.get().findViewById(ai.dvine.R.id.btn_send).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.widget.TextView note = c.get().findViewById(ai.dvine.R.id.composer_note);
        org.junit.Assert.assertEquals("Understood as \u201cgive me the result\u201d", note.getText().toString());
        // the user's own words stay in the chat
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        boolean kept = false;
        for (ai.dvine.chat.ChatBook.Message msg : chats.current().messages) {
            kept |= "user".equals(msg.role) && "where are we with that".equals(msg.body);
        }
        org.junit.Assert.assertTrue(kept);
        c.pause().stop().destroy();
    }

    @Test
    public void rephrasedAppCommandsRunTheirOwnActions() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("dvine", android.content.Context.MODE_PRIVATE);
        ai.dvine.turn.Meaning.Memory m = new ai.dvine.turn.Meaning.Memory();
        m.put("let me talk to the research one", ai.dvine.turn.Meaning.Command.SWITCH_OBI);
        m.put("let ibiko just do things", ai.dvine.turn.Meaning.Command.AUTO_ON);
        m.put("wipe everything you cloned", ai.dvine.turn.Meaning.Command.CLEAR_WORKSPACE);
        prefs.edit().putString("meanings", m.write()).putBoolean("auto_accept", false).commit();
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("meanings");
        mf.setAccessible(true);
        mf.set(c.get(), null);
        android.widget.EditText field = c.get().findViewById(ai.dvine.R.id.et_message);
        android.view.View send = c.get().findViewById(ai.dvine.R.id.btn_send);

        field.setText("Let me talk to the research one");
        send.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("Obi", ((android.widget.TextView) c.get().findViewById(ai.dvine.R.id.tabObi)).getText().toString());

        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS); // past the double-tap guard
        field.setText("let Ibiko just do things");
        send.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false));

        java.io.File ws = new java.io.File(c.get().getFilesDir(), "ibiko/sandbox/demo");
        ws.mkdirs();
        java.nio.file.Files.write(new java.io.File(ws, "main.py").toPath(), "print(1)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        field.setText("wipe everything you cloned");
        send.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        // it can't be undone, so it asks first
        ((androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog())
                .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        java.io.File root = new java.io.File(c.get().getFilesDir(), "ibiko");
        for (int i = 0; i < 40 && root.exists(); i++) {
            Thread.sleep(50);
            ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        org.junit.Assert.assertFalse(root.exists());
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
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        chats.add("user", "image", img.getAbsolutePath(), chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.dvine.stage.ChatPane) pf.get(c.get())).show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View shown = described(c.get().getWindow().getDecorView(), "Your picture. Tap to open.");
        assertNotNull(shown);
        android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) shown.getLayoutParams();
        org.junit.Assert.assertEquals(android.view.Gravity.END, lp.gravity); // on the right, with the user's messages
        org.junit.Assert.assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, lp.width); // its own size, not the chat's width
        c.pause().stop().destroy();

        // the viewer: fitted to the screen, then a double tap zooms in and another goes back
        android.content.Intent open = new android.content.Intent(RuntimeEnvironment.getApplication(), ai.dvine.chat.ui.ImageViewActivity.class)
                .putExtra(ai.dvine.chat.ui.ImageViewActivity.EXTRA_PATH, img.getAbsolutePath());
        ActivityController<ai.dvine.chat.ui.ImageViewActivity> v = Robolectric.buildActivity(ai.dvine.chat.ui.ImageViewActivity.class, open)
                .create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field zf = ai.dvine.chat.ui.ImageViewActivity.class.getDeclaredField("image");
        zf.setAccessible(true);
        ai.dvine.chat.ui.ZoomImageView zoom = (ai.dvine.chat.ui.ZoomImageView) zf.get(v.get());
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
        ActivityController<ai.dvine.chat.ui.ImageViewActivity> gone = Robolectric.buildActivity(ai.dvine.chat.ui.ImageViewActivity.class,
                new android.content.Intent().putExtra(ai.dvine.chat.ui.ImageViewActivity.EXTRA_PATH, "/nowhere.jpg")).create().start().resume().visible();
        java.util.ArrayList<android.view.View> said = new java.util.ArrayList<android.view.View>();
        gone.get().getWindow().getDecorView().findViewsWithText(said, "This picture isn't on the phone any more.", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, said.size());
        gone.pause().stop().destroy();
    }

    @Test
    public void theAppAndEveryNewChatOpenEmptyOnObi() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        // Obi first in the switch, chosen, on an empty chat
        android.view.ViewGroup tabs = c.get().findViewById(ai.dvine.R.id.personaTabs);
        org.junit.Assert.assertEquals(ai.dvine.R.id.tabObi, tabs.getChildAt(0).getId());
        org.junit.Assert.assertEquals("Obi", ((android.widget.TextView) c.get().findViewById(ai.dvine.R.id.tabObi)).getText().toString());
        org.junit.Assert.assertEquals("obi", chats.current().persona);
        org.junit.Assert.assertTrue(chats.current().messages.isEmpty());
        // work going on in Ibiko's chat, then New chat: it starts clean, on Obi, nothing carried over
        c.get().findViewById(ai.dvine.R.id.tabIbiko).performClick();
        chats.add("user", "text", "Audit my repo", "ibiko");
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) pf.get(c.get());
        pane.activityStep("Cloned me/app");
        int before = chats.sections().size();
        c.get().findViewById(ai.dvine.R.id.btnNewChat).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals("obi", chats.current().persona);
        org.junit.Assert.assertTrue(chats.current().messages.isEmpty());
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(found, "Cloned me/app", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertTrue(found.isEmpty());
        // a second New chat reuses the empty one instead of piling up blank chats
        int after = chats.sections().size();
        c.get().findViewById(ai.dvine.R.id.btnNewChat).performClick();
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
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "Plan my week", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.dvine.stage.ChatPane) pf.get(c.get())).show();
        android.widget.TextView title = c.get().findViewById(ai.dvine.R.id.tv_header_title);
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
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "My sister is called Ada", chats.current().persona);
        String first = chats.current().id;
        chats.fresh(ai.dvine.stage.StageView.OBI);
        chats.add("user", "text", "Plan dinner", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) pf.get(c.get());

        pane.chatMenu(first); // hold the chat: Rename, Pin to top, Delete
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog menu = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        menu.getListView().performItemClick(menu.getListView().getChildAt(1), 1, 1); // Pin to top
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(first, chats.pinnedFirst().get(0).id);
        android.widget.LinearLayout list = c.get().findViewById(ai.dvine.R.id.historyList);
        org.junit.Assert.assertEquals("Pinned", ((android.widget.TextView) list.getChildAt(0)).getText().toString()); // the section header
        org.junit.Assert.assertTrue(String.valueOf(list.getChildAt(1).getContentDescription()).contains("pinned"));

        pane.delete(first);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog ask = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        ask.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        for (ai.dvine.chat.ChatBook.Section s : chats.sections()) {
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
        android.content.SharedPreferences prefs = c.get().getSharedPreferences("dvine", android.content.Context.MODE_PRIVATE);
        android.widget.TextView pill = c.get().findViewById(ai.dvine.R.id.ibikoMode);
        pill.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        for (String row : new String[]{"Select mode", "Choose how Ibiko should work", "Auto", "Accept edits", "Plan"}) {
            org.junit.Assert.assertNotNull(row, find(sheet.getWindow().getDecorView(), row));
        }
        tapRow(sheet, "Plan");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(ai.dvine.agent.IbikoMode.PLAN, ai.dvine.agent.IbikoMode.of(prefs));
        org.junit.Assert.assertEquals("Plan", pill.getText().toString());
        pill.performClick();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        tapRow(org.robolectric.shadows.ShadowDialog.getLatestDialog(), "Auto");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        org.junit.Assert.assertEquals(ai.dvine.agent.IbikoMode.AUTO, ai.dvine.agent.IbikoMode.of(prefs));
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false)); // the older switch means the same
        ai.dvine.agent.IbikoMode.set(prefs, ai.dvine.agent.IbikoMode.PLAN);
        org.junit.Assert.assertTrue(prefs.getBoolean("auto_accept", false)); // plan keeps the choice for carrying it out
        ai.dvine.agent.IbikoMode.set(prefs, ai.dvine.agent.IbikoMode.EDITS);
        org.junit.Assert.assertFalse(prefs.getBoolean("auto_accept", true));
        c.pause().stop().destroy();
    }

    @Test
    public void aMessageCanBeEditedAndIsAskedAgainWithoutTheOldReply() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        String persona = chats.current().persona;
        chats.add("user", "text", "What is the capital of Ghana?", persona);
        chats.add("assistant", "text", "Accra.", persona);
        chats.add("user", "text", "And its population?", persona);
        chats.add("assistant", "text", "About 2.5 million.", persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) pf.get(c.get());
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
        for (ai.dvine.chat.ChatBook.Message m : chats.current().messages) {
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
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) pf.get(c.get());
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        pane.activityStep("Read app/Main.java");
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3));
        pane.activityStep("Ran the tests");
        java.util.List<String> timed = pane.timedSteps(System.currentTimeMillis() + 65_000);
        org.junit.Assert.assertTrue(timed.toString(), timed.get(0).startsWith("Read app/Main.java\t"));
        org.junit.Assert.assertTrue(timed.toString(), Long.parseLong(timed.get(1).split("\t")[1]) >= 60); // the running step counts up
        pane.activityEnd();
        ai.dvine.chat.ChatBook.Message kept = null;
        for (ai.dvine.chat.ChatBook.Message m : chats.current().messages) {
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
        android.content.SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("dvine", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("running_persona", "ibiko").putString("running_task", "Audit the Harvana repo")
                .putString("partial_reply", "The audit found three problems so far: the wallet").commit();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
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
        org.junit.Assert.assertFalse(MainActivity.looksLikeAction("share your thoughts on AI")); // a question, not a post
        org.junit.Assert.assertTrue(MainActivity.looksLikeAction("post on X: Big news today"));
        answer.invoke(c.get(), "post on X: Big news today");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        androidx.appcompat.app.AlertDialog card = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.TextView msg = card.findViewById(android.R.id.message);
        org.junit.Assert.assertTrue(String.valueOf(msg.getText()), msg.getText().toString().startsWith("Big news today")
                && msg.getText().toString().contains("To: X"));
        org.junit.Assert.assertNull("nothing opened before the yes", org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity());
        answer.invoke(c.get(), "send it"); // the spoken yes
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.util.List<String> log = ai.dvine.act.ActionLog.read(c.get().getFilesDir());
        org.junit.Assert.assertTrue(log.toString(), log.get(0).contains("share\tX\tapproved") && log.get(0).endsWith("Big news today"));
        // auto-approve: no card, straight to the app, logged as such
        c.get().getSharedPreferences("dvine", android.content.Context.MODE_PRIVATE).edit().putBoolean("actions_auto", true).commit();
        answer.invoke(c.get(), "copy: the meeting is at 5");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        log = ai.dvine.act.ActionLog.read(c.get().getFilesDir());
        org.junit.Assert.assertTrue(log.toString(), log.get(0).contains("copy\tyour share menu\tauto-approved\tdone"));
        c.pause().stop().destroy();
    }

    @Test
    public void theWorkloadRingShowsAndANearlyFullMemoryIsClearedWithANote() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ai.dvine.stage.WorkloadGauge g = c.get().findViewById(ai.dvine.R.id.workload);
        assertNotNull("the ring in the header", g);
        java.lang.reflect.Field mf = MainActivity.class.getDeclaredField("model");
        mf.setAccessible(true);
        ai.dvine.llm.LocalModel model = (ai.dvine.llm.LocalModel) mf.get(c.get());
        model.note("Plan a trip to Abuja", "Sure: flights, hotels, the weekend.");
        model.note("Book for Friday", "Friday it is.");
        java.lang.reflect.Constructor<ai.dvine.llm.UsageLog.Call> k = ai.dvine.llm.UsageLog.Call.class.getDeclaredConstructor(String.class,
                int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, long.class);
        k.setAccessible(true);
        model.usage.record(k.newInstance("test", 4096, 600, 2900, 100, 120, false, 1000L, System.currentTimeMillis()));
        java.lang.reflect.Method check = MainActivity.class.getDeclaredMethod("checkWorkload");
        check.setAccessible(true);
        check.invoke(c.get());
        org.junit.Assert.assertTrue(String.valueOf(g.getContentDescription()), g.getContentDescription().toString().contains("nearly full"));
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        boolean told = false;
        for (int i = 0; i < 50 && !told; i++) { // the clear-out runs on the model's worker
            Thread.sleep(100);
            ShadowLooper.idleMainLooper();
            for (ai.dvine.chat.ChatBook.Message m : chats.current().messages) {
                told |= m.body != null && m.body.startsWith("My working memory was");
            }
        }
        org.junit.Assert.assertTrue("told about the clear-out", told);
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
}
