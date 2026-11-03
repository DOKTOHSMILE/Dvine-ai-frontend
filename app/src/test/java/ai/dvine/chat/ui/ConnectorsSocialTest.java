package ai.dvine.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Intent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/** Connectors lists the social networks: signed-out ones sign in on a tap, signed-in ones say as whom. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConnectorsSocialTest {
    @Test
    public void socialNetworksAreConnectors() {
        ai.dvine.social.SocialWeb.accounts(RuntimeEnvironment.getApplication())
                .add(ai.dvine.social.Platform.REDDIT, "Me", System.currentTimeMillis());
        ActivityController<ConnectorsActivity> c = Robolectric.buildActivity(ConnectorsActivity.class).create().start().resume().visible();
        View root = c.get().getWindow().getDecorView();
        ArrayList<View> found = new ArrayList<View>();
        root.findViewsWithText(found, "Reddit, Connected as Me", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertFalse(found.isEmpty());
        found.clear();
        root.findViewsWithText(found, "X, Not signed in: tap to sign in on X", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertFalse(found.isEmpty());
        found.get(0).performClick();
        Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals(SocialLoginActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("X", next.getStringExtra(SocialLoginActivity.EXTRA_PLATFORM));
        c.pause().stop().destroy();
    }
}
