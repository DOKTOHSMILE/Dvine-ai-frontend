package ai.dvine.ui;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import ai.dvine.MainActivity;

/**
 * Renders the chat screens with real pixels (a phone 1080 × 2340 at 440 dpi) into build/screens, so the layout can
 * be looked at without a phone: the empty chat, a conversation, the drawer, the providers sheet and the usage screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {
    private static final File OUT = new File(System.getProperty("user.dir"), "build/screens");

    @Test(timeout = 180000)
    public void rendersTheChatScreens() throws Exception {
        OUT.mkdirs();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        MainActivity a = c.get();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(a, "1_chat_empty");
        java.lang.reflect.Field chatsF = MainActivity.class.getDeclaredField("chats");
        chatsF.setAccessible(true);
        ai.dvine.chat.ChatBook book = (ai.dvine.chat.ChatBook) chatsF.get(a);
        if (book != null) {
            book.add("user", "text", "Write a Java function that reverses a string", "ibiko");
            book.reply("ibiko", "Here's a simple one:\n\n```java\nstatic String reverse(String s) {\n    return new StringBuilder(s).reverse().toString();\n}\n```\n\nIt works for any string, including an empty one.");
            book.add("user", "text", "What's the capital of Nigeria?", "ivie");
            book.reply("ivie", "Abuja. It became the capital in 1991, taking over from Lagos.");
            java.lang.reflect.Field paneF = MainActivity.class.getDeclaredField("chatPane");
            paneF.setAccessible(true);
            Object pane = paneF.get(a);
            if (pane != null) {
                pane.getClass().getMethod("show").invoke(pane);
            }
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            shoot(a, "2_chat_conversation");
        }
        java.lang.reflect.Method toggle = MainActivity.class.getDeclaredMethod("toggleDrawer");
        toggle.setAccessible(true);
        toggle.invoke(a);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(a, "3_drawer");
        c.pause().stop().destroy();

        ActivityController<ai.dvine.chat.ui.ProvidersActivity> p = Robolectric.buildActivity(ai.dvine.chat.ui.ProvidersActivity.class)
                .create().start().resume().visible();
        shoot(p.get(), "4_providers");
        p.pause().stop().destroy();
        ActivityController<ai.dvine.chat.ui.SetupActivity> st = Robolectric.buildActivity(ai.dvine.chat.ui.SetupActivity.class)
                .create().start().resume().visible();
        shoot(st.get(), "0_setup");
        st.pause().stop().destroy();
        ActivityController<ai.dvine.chat.ui.UsageActivity> us = Robolectric.buildActivity(ai.dvine.chat.ui.UsageActivity.class)
                .create().start().resume().visible();
        shoot(us.get(), "5_usage");
        us.pause().stop().destroy();
        assertTrue(new File(OUT, "1_chat_empty.png").length() > 0);
    }

    /** The same chat and drawer at night. */
    @Test(timeout = 180000)
    @Config(sdk = 35, qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void rendersTheNightTheme() throws Exception {
        OUT.mkdirs();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(c.get(), "6_chat_night");
        java.lang.reflect.Method toggle = MainActivity.class.getDeclaredMethod("toggleDrawer");
        toggle.setAccessible(true);
        toggle.invoke(c.get());
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(c.get(), "7_drawer_night");
        c.pause().stop().destroy();
        ActivityController<ai.dvine.chat.ui.ConnectorsActivity> k = Robolectric.buildActivity(ai.dvine.chat.ui.ConnectorsActivity.class)
                .create().start().resume().visible();
        shoot(k.get(), "8_connectors_night");
        k.pause().stop().destroy();
    }

    @Test(timeout = 180000)
    @Config(sdk = 35, qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void rendersSettingsTheAddSheetAndTheWorkingSteps() throws Exception {
        OUT.mkdirs();
        ActivityController<ai.dvine.chat.ui.SettingsActivity> st = Robolectric.buildActivity(ai.dvine.chat.ui.SettingsActivity.class)
                .create().start().resume().visible();
        shoot(st.get(), "9_settings_night");
        st.pause().stop().destroy();

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("chatPane");
        f.setAccessible(true);
        ai.dvine.stage.ChatPane pane = (ai.dvine.stage.ChatPane) f.get(c.get());
        pane.setThinking("Writing the answer");
        pane.activityStep("Reading your message");
        pane.activityStep("Searching the web for \u201cLagos population 2026\u201d");
        pane.activityStep("Read a source: Wikipedia, Lagos");
        pane.activityStep("Asking OpenAI (gpt-5-mini), the fast model");
        pane.activityStep("Writing the answer");
        String diffId = ai.dvine.agent.StepDetail.save(c.get().getFilesDir(), "app/Main.java", java.util.Arrays.asList("@ line 1",
                " class Main {", "-    void old() {}", "+    void hello() {", "+        System.out.println(\"hi\");", "+    }", " }"), 3, 1);
        pane.activityStep("Edited app/Main.java" + ai.dvine.agent.StepDetail.mark(diffId));
        pane.setLive("Lagos has about 16 million people in its metropolitan area, which makes it");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(c.get(), "10_working_night");
        // a step opened: the changes in green and red
        ai.dvine.chat.ui.DetailSheet.show(c.get(), "Edited app/Main.java", ai.dvine.agent.StepDetail.load(c.get().getFilesDir(), diffId));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog opened = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.view.View ov = opened.getWindow().getDecorView();
        ov.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY));
        ov.layout(0, 0, 1080, 1200);
        android.graphics.Bitmap ob = android.graphics.Bitmap.createBitmap(1080, 1200, android.graphics.Bitmap.Config.ARGB_8888);
        ov.draw(new android.graphics.Canvas(ob));
        try (FileOutputStream o = new FileOutputStream(new File(OUT, "16_step_diff_night.png"))) {
            ob.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
        opened.dismiss();
        pane.activityEnd();
        pane.setLive(null);
        pane.assistant("obi", "Lagos has about 16 million people in its metropolitan area.");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(c.get(), "11_worked_night");

        // a photo and a long paste waiting in the box for the instructions
        java.io.File img = new java.io.File(c.get().getFilesDir(), "attachments/shot.jpg");
        img.getParentFile().mkdirs();
        android.graphics.Bitmap pic = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888);
        pic.eraseColor(0xFF6A8CAF);
        try (FileOutputStream o = new FileOutputStream(img)) {
            pic.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, o);
        }
        java.lang.reflect.Field af = MainActivity.class.getDeclaredField("attachments");
        af.setAccessible(true);
        ai.dvine.chat.ui.ComposerAttachments box = (ai.dvine.chat.ui.ComposerAttachments) af.get(c.get());
        box.add(new ai.dvine.chat.ui.ComposerAttachments.Item("image", "bike.jpg", img.getAbsolutePath(), null));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            longText.append("line ").append(i).append('\n');
        }
        box.add(new ai.dvine.chat.ui.ComposerAttachments.Item("text", "Pasted text", null, longText.toString()));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(c.get(), "13_attachments_night");
        box.clear();


        // Ibiko's tab with a changed clone, a pull request and an artifact: the Diff, Pull requests and Artifacts chips
        ai.dvine.connect.GitHubGitTest.FakeHub hub = new ai.dvine.connect.GitHubGitTest.FakeHub();
        java.util.Map<String, String> files = new java.util.TreeMap<String, String>();
        files.put("app/Main.java", "class Main {\n}\n");
        hub.refs.put("main", hub.commit(files, null, "first"));
        java.io.File repo = new java.io.File(c.get().getFilesDir(), "ibiko/app");
        new ai.dvine.connect.GitHubGit(hub, "tok").clone("me/app", "main", repo);
        java.nio.file.Files.write(new java.io.File(repo, "app/Main.java").toPath(),
                "class Main {\n    void hello() {}\n}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new ai.dvine.chat.WorkLog(c.get()).pullRequest("7", "Say hello", "https://github.com/me/app/pull/7", "me/app");
        new ai.dvine.chat.WorkLog(c.get()).artifact("car.glb", "content://x/car.glb", "3D model \u00B7 GLB", "model/gltf-binary");
        java.lang.reflect.Method persona = MainActivity.class.getDeclaredMethod("selectPersona", String.class);
        persona.setAccessible(true);
        persona.invoke(c.get(), "ibiko");
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100); // the diff is worked out off the main thread
            ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        // the reply that did the work carries its own pills
        java.lang.reflect.Field chatsField = MainActivity.class.getDeclaredField("chats");
        chatsField.setAccessible(true);
        ai.dvine.chat.ChatBook book = (ai.dvine.chat.ChatBook) chatsField.get(c.get());
        book.add("user", "text", "Add a hello to Main and open a pull request", "ibiko");
        book.add("assistant", "text", "Added hello() to Main.java and opened pull request #7.", "ibiko");
        java.lang.reflect.Method attachWork = MainActivity.class.getDeclaredMethod("attachWork", java.util.List.class, long.class);
        attachWork.setAccessible(true);
        attachWork.invoke(c.get(), java.util.Collections.singletonList("app/app/Main.java"), 0L);
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100); // the work is gathered off the main thread
            ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        java.util.ArrayList<android.view.View> rows = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(rows, "Pull requests \u00B7 1", android.view.View.FIND_VIEWS_WITH_TEXT);
        org.junit.Assert.assertEquals(1, rows.size());
        android.widget.LinearLayout chips = (android.widget.LinearLayout) rows.get(0).getParent().getParent();
        org.junit.Assert.assertEquals(ai.dvine.R.id.reply_work_row, chips.getId());
        org.junit.Assert.assertEquals(3, chips.getChildCount()); // Diff, Pull requests, Artifacts
        // the persona tabs sit in the composer row beside the attach button; the Auto pill sits in the header
        org.junit.Assert.assertSame(c.get().findViewById(ai.dvine.R.id.btn_attach).getParent(),
                c.get().findViewById(ai.dvine.R.id.tabIbiko).getParent().getParent());
        org.junit.Assert.assertSame(c.get().findViewById(ai.dvine.R.id.chatHeader), c.get().findViewById(ai.dvine.R.id.ibikoMode).getParent());
        org.junit.Assert.assertEquals(android.view.View.VISIBLE, c.get().findViewById(ai.dvine.R.id.ibikoMode).getVisibility());
        shoot(c.get(), "15_work_chips_night");
        persona.invoke(c.get(), "ivie");

        java.lang.reflect.Method attach = MainActivity.class.getDeclaredMethod("showAttachMenu", android.view.View.class);
        attach.setAccessible(true);
        attach.invoke(c.get(), (android.view.View) null);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        org.junit.Assert.assertNotNull(sheet);
        android.view.View sv = sheet.getWindow().getDecorView();
        int w = 1080;
        sv.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.AT_MOST));
        sv.layout(0, 0, w, sv.getMeasuredHeight());
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, Math.max(1, sv.getMeasuredHeight()),
                android.graphics.Bitmap.Config.ARGB_8888);
        sv.draw(new android.graphics.Canvas(bmp));
        try (FileOutputStream o = new FileOutputStream(new File(OUT, "12_add_sheet_night.png"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
        sheet.dismiss();
        c.pause().stop().destroy();
    }

    private static void shoot(android.app.Activity a, String name) throws Exception {
        android.view.View root = a.getWindow().getDecorView();
        int w = 1080, h = 2340;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF000000);
        root.draw(new android.graphics.Canvas(bmp));
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name + ".png"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
    }
}
