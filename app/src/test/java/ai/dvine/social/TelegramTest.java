package ai.dvine.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TelegramTest {
    @Test
    public void telegramIsANetworkOfTheUsersOwn() {
        assertEquals(Platform.TELEGRAM, Platform.named("telegram"));
        assertEquals(Platform.TELEGRAM, Platform.named("TG"));
        assertEquals("a channel by its name", "https://web.telegram.org/k/#@lagosnews", Platform.TELEGRAM.searchUrl("@lagosnews"));
        assertEquals("https://web.telegram.org/k/#@lagosnews", Platform.TELEGRAM.searchUrl("t.me/lagosnews"));
        assertEquals("other words: the chat list", Platform.TELEGRAM.home, Platform.TELEGRAM.searchUrl("fuel price"));
        assertEquals(Platform.TELEGRAM.home, Platform.TELEGRAM.trendingUrl());
        assertTrue(Platform.TELEGRAM.owns("https://web.telegram.org/k/#@lagosnews"));
        assertFalse("no cookie says Telegram is signed in", Platform.TELEGRAM.signedIn("stel_ssid=1"));
        assertTrue(SocialWeb.telegramSignedOut("Log in to Telegram by QR Code\n1. Open Telegram on your phone"));
        assertFalse(SocialWeb.telegramSignedOut("Chats\nLagos News\nFuel is N1,050 today"));
        SocialAsk a = SocialAsk.parse("search telegram for fuel price");
        assertTrue(String.valueOf(a), a != null);
    }
}
