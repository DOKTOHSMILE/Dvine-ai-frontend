package ai.ibiko;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

/** From the pilot's screenshot: short messages one after another ("Hi", "A", "B", "AB") each get their own reply. */
@RunWith(RobolectricTestRunner.class)
public class EveryMessageAnsweredTest {
    @Test(timeout = 300000)
    public void eachMessageInARowGetsAReplyFromIbiko1() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("ibiko", 0).edit().putString("brain_pick", "ibiko").commit();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.ibiko.chat.ChatBook chats = (ai.ibiko.chat.ChatBook) cf.get(c.get());
        android.widget.EditText field = c.get().findViewById(ai.ibiko.R.id.et_message);
        android.view.View send = c.get().findViewById(ai.ibiko.R.id.btn_send);
        String[] said = {"Hi", "Hello", "What have you learned?", "Hi, do you need help?", "A", "B", "AB"};
        for (String w : said) {
            field.setText(w);
            send.performClick();
            for (int i = 0; i < 30; i++) {
                ShadowLooper.idleMainLooper(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                Thread.sleep(10);
            }
        }
        java.util.List<ai.ibiko.chat.ChatBook.Message> m = chats.current().messages;
        int asked = 0;
        for (int i = 0; i < m.size(); i++) {
            if (!"user".equals(m.get(i).role)) {
                continue;
            }
            asked++;
            boolean answered = false;
            for (int j = i + 1; j < m.size() && !"user".equals(m.get(j).role); j++) {
                answered |= "text".equals(m.get(j).kind) && !m.get(j).body.trim().isEmpty();
            }
            org.junit.Assert.assertTrue("no reply to “" + m.get(i).body + "”", answered);
        }
        org.junit.Assert.assertEquals(said.length, asked);
        c.pause().stop().destroy();
    }
}
