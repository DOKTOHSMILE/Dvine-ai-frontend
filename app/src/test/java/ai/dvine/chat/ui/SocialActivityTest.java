package ai.dvine.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import ai.dvine.social.Platform;
import ai.dvine.social.SocialWeb;

/** Social accounts: each network with a sign-in, accounts listed with today's reads, a paused one offers Resume. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SocialActivityTest {
    @Test
    public void listsNetworksAccountsAndLimits() {
        android.content.Context app = RuntimeEnvironment.getApplication();
        ai.dvine.social.SocialAccounts.Account a = SocialWeb.accounts(app).add(Platform.REDDIT, "Me", System.currentTimeMillis());
        SocialWeb.limits(app).pause(a.id, "the site said “security check”", System.currentTimeMillis() + 60_000);
        ActivityController<SocialActivity> c = Robolectric.buildActivity(SocialActivity.class).create().start().resume().visible();
        View root = c.get().getWindow().getDecorView();
        assertNotNull(find(root, "Sign in to X"));
        assertNotNull(find(root, "Sign in to Facebook"));
        assertNotNull(find(root, "Me"));
        assertNotNull("the account in use has the check icon", findDesc(root, "In use"));
        assertNotNull(find(root, "Resume Me"));
        assertNotNull(find(root, "Add account"));
        tapText(root, "Sign in to Instagram");
        Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals(SocialLoginActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("INSTAGRAM", next.getStringExtra(SocialLoginActivity.EXTRA_PLATFORM));
        SocialWeb.limits(app).resume(a.id);
        c.pause().resume();
        assertNull(find(c.get().getWindow().getDecorView(), "Resume Me"));
        c.pause().stop().destroy();
    }

    private static void tapText(View root, String s) {
        View t = find(root, s);
        assertNotNull(s, t);
        t.performClick();
    }

    private static View findDesc(View v, String s) {
        if (s.contentEquals(String.valueOf(v.getContentDescription()))) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View f = findDesc(((ViewGroup) v).getChildAt(i), s);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static View find(View v, String s) {
        if (v instanceof TextView && s.contentEquals(((TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View f = find(((ViewGroup) v).getChildAt(i), s);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }
}
