package ai.dvine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.dvine.turn.TurnLine;

/** The online client against a fake server: rooms, retries, and which events become lines. */
public class DeskClientTest {
    static final String BASE = "https://desk.example";
    static final String EVENTS = "[{\"eventId\":1,\"state\":\"RECEIVED\",\"agent\":null,\"cue\":\"STATUS\",\"message\":\"Received\"},"
            + "{\"eventId\":7,\"state\":\"EXECUTING\",\"agent\":\"obi\",\"cue\":\"THINK\",\"message\":\"Weigh the cost first.\"},"
            + "{\"eventId\":8,\"state\":\"EXECUTING\",\"agent\":\"ivie\",\"cue\":\"SAY\",\"message\":\"Start small, then grow.\"},"
            + "{\"eventId\":9,\"state\":\"EXECUTING\",\"agent\":\"obi\",\"cue\":\"SAY\",\"message\":\"Agreed.\"},"
            + "{\"eventId\":10,\"state\":\"COMPLETED\",\"agent\":null,\"cue\":\"STATUS\",\"message\":\"Completed\"}]";

    /** Answers like the real server, with switches to fail in particular ways. */
    static final class FakeServer implements DeskClient.Http {
        final List<String> calls = new ArrayList<String>();
        final List<String> tokens = new ArrayList<String>();
        String knownRoom;
        int rooms;
        int refuseTokens; // answer 401 this many times
        boolean down;

        @Override
        public SessionClient.Reply send(String method, String url, String token, String json) throws IOException {
            if (down) {
                throw new IOException("unreachable");
            }
            calls.add(method + " " + url.replace(BASE, ""));
            tokens.add(token);
            if (url.endsWith("/api/v1/desk-rooms") && method.equals("POST")) {
                rooms++;
                knownRoom = "room-" + rooms + "-abcdef";
                return new SessionClient.Reply(201, "{\"deskRoomId\":\"" + knownRoom + "\"}");
            }
            if (url.contains("/commands")) {
                if (refuseTokens > 0) {
                    refuseTokens--;
                    return new SessionClient.Reply(401, "");
                }
                if (!url.contains("/desk-rooms/" + knownRoom + "/")) {
                    return new SessionClient.Reply(404, "");
                }
                assertTrue(json.contains("\"requestId\":\"ph-"));
                return new SessionClient.Reply(201, "{\"taskId\":\"task-1\",\"state\":\"COMPLETED\"}");
            }
            if (url.contains("/api/v1/tasks/task-1/events?after=0")) {
                return new SessionClient.Reply(200, EVENTS);
            }
            return new SessionClient.Reply(500, "");
        }
    }

    static final class Rooms implements DeskClient.RoomStore {
        String room;

        public String get() {
            return room;
        }

        public void put(String r) {
            room = r;
        }
    }

    @Test
    public void asksTheDeskAndPlaysOnlyThinkAndSayLines() {
        FakeServer server = new FakeServer();
        Rooms rooms = new Rooms();
        DeskClient c = new DeskClient(() -> BASE, () -> "tok", () -> { }, rooms, server);
        List<TurnLine> lines = c.ask("Should I start a farm?", "ivie");
        assertNotNull(lines);
        assertEquals(3, lines.size());
        assertEquals("THINK", lines.get(0).cue);
        assertEquals("obi", lines.get(0).agent);
        assertEquals("Start small, then grow.", lines.get(1).text);
        assertEquals("room-1-abcdef", rooms.room);
        assertEquals("tok", server.tokens.get(0));
        // the room is reused
        c.ask("And then?", "obi");
        assertEquals(1, server.rooms);
    }

    @Test
    public void signsInAgainOnceAndRemakesALostRoom() {
        FakeServer server = new FakeServer();
        Rooms rooms = new Rooms();
        int[] invalidated = {0};
        DeskClient c = new DeskClient(() -> BASE, () -> "tok", () -> invalidated[0]++, rooms, server);
        server.refuseTokens = 1;
        assertNotNull(c.ask("hello there", "obi"));
        assertEquals(1, invalidated[0]);
        // the server forgot the room: the client makes a new one and tries again
        rooms.room = "room-gone-123456";
        assertNotNull(c.ask("hello again", "obi"));
        assertEquals("room-2-abcdef", rooms.room);
        // a server that keeps refusing gives up after one retry
        server.refuseTokens = 5;
        assertNull(c.ask("still there?", "obi"));
    }

    @Test
    public void noServerNoTokenOrNoNetworkMeansTheOfflineAnswer() {
        FakeServer server = new FakeServer();
        assertNull(new DeskClient(() -> null, () -> "tok", () -> { }, new Rooms(), server).ask("hi", "obi"));
        assertNull(new DeskClient(() -> BASE, () -> null, () -> { }, new Rooms(), server).ask("hi", "obi"));
        server.down = true;
        assertNull(new DeskClient(() -> BASE, () -> "tok", () -> { }, new Rooms(), server).ask("hi", "obi"));
        assertTrue(DeskClient.lines("[]").isEmpty());
        // the server's "Obi is thinking" placeholder becomes an empty thought (the bubble's dots)
        List<TurnLine> l = DeskClient.lines("[{\"agent\":\"obi\",\"cue\":\"THINK\",\"message\":\"Obi is thinking\"}]");
        assertEquals(1, l.size());
        assertEquals("", l.get(0).text);
    }

    @Test(expected = IOException.class)
    public void plainHttpIsRefused() throws IOException {
        DeskClient.https("GET", "http://desk.example/api/v1/desk-rooms", "tok", null);
    }
}
