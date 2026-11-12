package ai.ibiko.chat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.ibiko.brain.MiniJson;

/**
 * Chat sections on this phone. A section is one conversation, with its own clean page for each persona
 * (Obi, Ivie, Ibiko). Those pages do not see another section's messages. A note the user asked to remember
 * is the only thing shared. This is not a cloud memory and not a graph database.
 */
public final class ChatBook {
    public static final class Message {
        public final String role;
        public final String kind;
        public final String body;
        public final String persona;
        public final String language;
        /**
         * The message's own id, kept with it for good: what belongs to this message (its voice, kept on the device) is
         * filed under it, and goes when the message's chat is deleted.
         */
        public final String id;

        public Message(String role, String kind, String body, String persona) {
            this(role, kind, body, persona, "");
        }

        public Message(String role, String kind, String body, String persona, String language) {
            this(role, kind, body, persona, language, null);
        }

        public Message(String role, String kind, String body, String persona, String language, String id) {
            this.id = id == null || id.isEmpty() ? newId() : id;
            this.role = role == null ? "assistant" : role;
            this.kind = kind == null ? "text" : kind;
            this.body = body == null ? "" : body;
            this.persona = persona == null ? "" : persona;
            this.language = language == null ? "" : language;
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();

    /** A new message id: the time it was made and a count, so ids never repeat on this device. */
    public static String newMessageId() {
        return newId();
    }

    static String newId() {
        return "m" + Long.toString(System.currentTimeMillis(), 36) + Long.toString(IDS.incrementAndGet() & 0xFFFF, 36);
    }

    public static final class Section {
        public final String id;
        public String title;
        public String persona;
        public boolean pinned;
        public boolean archived;
        /** The project this chat belongs to (its name), or null. */
        public String project;
        /** Pages for Obi, Ivie and Ibiko that belong to the same conversation share this. */
        public String group;
        public final List<Message> messages = new ArrayList<Message>();

        Section(String id, String title, String persona) {
            this.id = id;
            this.title = title;
            this.persona = persona;
        }
    }

    private final File file;
    private final List<Section> sections = new ArrayList<Section>();
    private final List<String> shared = new ArrayList<String>();
    /** The user's projects (names), in the order made; a project may have no chats yet. */
    private final List<String> projects = new ArrayList<String>();
    private String currentId;
    private int next = 1;
    /**
     * Chats numbered below this were before the last "New chat": a persona's chat is looked for only from here on,
     * so after New chat every persona starts clean (0: no new chat yet).
     */
    private int freshFrom;

    private static final java.util.Map<String, ChatBook> OPEN = new java.util.HashMap<String, ChatBook>();

    /**
     * The one book for this file in this process: the chat screen and the memory screen share it, so a forget on one
     * is not written back by the other.
     */
    public static synchronized ChatBook open(File file) {
        String k = file.getAbsolutePath();
        ChatBook b = OPEN.get(k);
        if (b == null) {
            b = new ChatBook(file);
            OPEN.put(k, b);
        }
        return b;
    }

    /** The shared notes every chat sees (taught with "remember …", or kept from a deleted chat), oldest first. */
    public synchronized List<String> notes() {
        return new ArrayList<String>(shared);
    }

    /** Forgets one shared note; false when it wasn't there. */
    public synchronized boolean forgetNote(String note) {
        boolean gone = shared.remove(note);
        if (gone) {
            save();
        }
        return gone;
    }

    public synchronized void forgetAllNotes() {
        if (!shared.isEmpty()) {
            shared.clear();
            save();
        }
    }

    public ChatBook(File file) {
        this.file = file;
        load();
        if (sections.isEmpty()) {
            open("Chat", "ivie");
        }
    }

    public synchronized List<Section> sections() {
        return new ArrayList<Section>(sections);
    }

    public synchronized Section current() {
        for (Section s : sections) {
            if (s.id.equals(currentId)) {
                return s;
            }
        }
        return sections.get(0);
    }

    public synchronized Section open(String title, String persona) {
        return openIn(title, persona, null);
    }

    private Section openIn(String title, String persona, String group) {
        String id = "c" + next++;
        Section s = new Section(id, title == null || title.isEmpty() ? "Chat" : title, personaOf(persona));
        s.group = group == null || group.isEmpty() ? "g" + id.substring(1) : group;
        sections.add(s);
        currentId = s.id;
        save();
        return s;
    }

    /**
     * An empty chat for {@code persona}. A conversation that already has messages is left alone: this starts
     * a new one, so the three persona pages begin empty. A conversation that is still empty is reused.
     */
    public synchronized Section fresh(String persona) {
        String p = personaOf(persona);
        for (int i = sections.size() - 1; i >= 0; i--) {
            Section s = sections.get(i);
            if (s.archived || !p.equals(s.persona) || !s.messages.isEmpty() || !groupQuiet(s.group)) {
                continue;
            }
            currentId = s.id;
            save();
            return s;
        }
        return open("New chat", p);
    }

    /**
     * "New chat": a new conversation. {@code persona} gets an empty page now. Ivie and Ibiko get an empty page
     * of this same conversation when they are first opened, not an older chat of theirs.
     */
    public synchronized Section freshForAll(String persona) {
        freshFrom = next;
        return fresh(persona);
    }

    public synchronized boolean use(String id) {
        for (Section s : sections) {
            if (s.id.equals(id)) {
                currentId = id;
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized void persona(String persona) {
        current().persona = personaOf(persona);
        save();
    }

    /**
     * The page for this persona inside the open conversation. A different persona is a different page of the
     * same conversation, never another conversation's transcript.
     */
    public synchronized Section forPersona(String persona) {
        String p = personaOf(persona);
        Section cur = current();
        if (p.equals(cur.persona) && !cur.archived) {
            return cur;
        }
        String group = cur.group;
        for (int i = sections.size() - 1; i >= 0; i--) {
            Section s = sections.get(i);
            if (!s.archived && p.equals(s.persona) && group.equals(s.group)) {
                currentId = s.id;
                save();
                return s;
            }
        }
        String title = cur.title != null && !cur.title.matches("(?i)^(chat|new chat|obi|ivie|ibiko|ibiko code)$")
                ? cur.title : "Chat";
        return openIn(title, p, group);
    }

    /** The persona pages of a conversation that have messages, in page order (Obi, Ivie, Ibiko). */
    public synchronized List<String> pagesOf(String group) {
        List<String> out = new ArrayList<String>();
        for (String p : new String[] { "obi", "ivie", "ibiko" }) {
            for (Section s : sections) {
                if (!s.archived && p.equals(s.persona) && group != null && group.equals(s.group) && !s.messages.isEmpty()) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /** True when no page of this conversation has a message yet. */
    private boolean groupQuiet(String group) {
        if (group == null) {
            return true;
        }
        for (Section s : sections) {
            if (group.equals(s.group) && !s.messages.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Startup lines that should not sit in the chat. */
    public synchronized void dropGreetings() {
        boolean changed = false;
        for (Section s : sections) {
            for (int i = s.messages.size() - 1; i >= 0; i--) {
                if (greeting(s.messages.get(i).body)) {
                    s.messages.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    private static boolean greeting(String body) {
        if (body == null) {
            return false;
        }
        String t = body.trim();
        return t.startsWith("I closed unexpectedly last time") || t.startsWith("Welcome back,");
    }

    /**
     * Where each persona's work writes: the page it was asked on. A job keeps writing there when the user opens
     * another page or another chat meanwhile, so one conversation's replies, notes and steps never land in another.
     */
    private final java.util.Map<String, String> workPage = new java.util.HashMap<String, String>();

    /** {@code persona}'s work from now on belongs to the page on screen. */
    public synchronized void workOn(String persona) {
        Section cur = current();
        workPage.put(personaOf(persona), cur.id);
    }

    /** The page {@code persona}'s work writes to: the page it was asked on, else the one on screen. */
    public synchronized Section pageFor(String persona) {
        String id = workPage.get(personaOf(persona));
        if (id != null) {
            for (Section s : sections) {
                if (s.id.equals(id) && !s.archived) {
                    return s;
                }
            }
        }
        return current();
    }

    /** A line from Ibiko on the page on screen, whatever work is bound elsewhere ("your message is waiting"). */
    public synchronized void noteHere(String persona, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        current().messages.add(new Message("assistant", "text", text.trim(), persona));
        save();
    }

    /** True when {@code persona}'s work belongs to the page on screen. */
    public synchronized boolean onScreen(String persona) {
        return pageFor(persona) == current();
    }

    public synchronized void add(String role, String kind, String body, String persona) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        // the user writes on the page on screen; an answer, a step or a file goes to the page its work was asked on
        Section s = "user".equals(role) ? current() : pageFor(persona);
        if ("user".equals(role) && (s.title == null || s.title.equals("Chat") || s.title.equals("New chat")
                || s.title.equals("Obi") || s.title.equals("Ivie") || s.title.equals("Ibiko code") || s.title.equals("Chat"))
                && s.messages.isEmpty()) {
            String t = body.trim().replace('\n', ' ');
            s.title = t.length() > 48 ? t.substring(0, 48) : t;
            for (Section other : sections) {
                if (s.group.equals(other.group)) {
                    other.title = s.title;
                }
            }
        }
        if (!"user".equals(role) && "text".equals(kind) && ReplyText.repeats(lastAnswer(s), body)) {
            return; // the same answer twice in one turn (a progress note, then the result): shown once
        }
        if ("text".equals(kind) && file != null) {
            String chatId = s.group != null ? s.group : s.id;
            // identified and saved as ids too (learn/Identifier): every sentence its id, every word its id
            Message m = new Message(role, kind, body, persona);
            s.messages.add(m);
            ai.ibiko.learn.Identifier.save(file.getParentFile(), chatId, role, m.id, body);
            save();
            return;
        }
        if ("code".equals(kind) || "image".equals(kind) || "video".equals(kind) || "audio".equals(kind)
                || "html".equals(kind) || "text".equals(kind) || "activity".equals(kind) || "step".equals(kind) || "file".equals(kind)
                || "work".equals(kind) || "handoff".equals(kind)) {
            s.messages.add(new Message(role, kind, body, persona));
        } else {
            s.messages.add(new Message(role, "text", body, persona));
        }
        save();
    }

    /** Splits a reply on fenced code blocks. A fenced HTML page is its own message. */
    public static List<Message> blocks(String role, String persona, String text) {
        List<Message> out = new ArrayList<Message>();
        for (MessageParts.Part part : MessageParts.split(text)) {
            out.add(new Message(role, part.kind, part.body, persona, part.language));
        }
        return out;
    }

    /** The last answer in this page's current turn (after the latest question), or null. */
    private static String lastAnswer(Section s) {
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            Message m = s.messages.get(i);
            if ("user".equals(m.role)) {
                return null;
            }
            if ("text".equals(m.kind)) {
                return m.body;
            }
        }
        return null;
    }

    public synchronized void reply(String persona, String text) {
        reply(persona, text, null);
    }

    /**
     * As {@link #reply(String, String)}. {@code firstId}, when set, is the id of the first block: the voice of this
     * reply was filed under it while the words were still being written, and Listen looks it up by that same id.
     * True when a block was saved.
     */
    public synchronized boolean reply(String persona, String text, String firstId) {
        Section page = pageFor(persona);
        if (ReplyText.repeats(lastAnswer(page), text)) {
            return false; // said already in this turn: a second copy is not shown
        }
        if (addReply(page, persona, text, firstId)) {
            save();
            return true;
        }
        return false;
    }

    /**
     * A reply for one persona's page of the open conversation, written there even when another page is on screen
     * (a job that finished in the background). The open page stays open.
     */
    public synchronized void replyToPage(String persona, String text) {
        replyToPage(persona, text, null);
    }

    /** As {@link #replyToPage(String, String)}, with the first block keeping {@code firstId} (its voice was filed there). */
    public synchronized void replyToPage(String persona, String text, String firstId) {
        String p = personaOf(persona);
        Section cur = current();
        Section bound = workPage.containsKey(p) ? pageFor(p) : null;
        Section page = bound != null ? bound : p.equals(cur.persona) ? cur : null;
        for (int i = sections.size() - 1; page == null && i >= 0; i--) {
            Section s = sections.get(i);
            if (!s.archived && p.equals(s.persona) && cur.group.equals(s.group)) {
                page = s;
            }
        }
        if (page == null) {
            page = new Section("c" + next++, cur.title, p);
            page.group = cur.group;
            sections.add(page);
        }
        boolean wrote = addReply(page, p, text, firstId);
        if (wrote) {
            save();
        }
    }

    /** Adds a reply's blocks to {@code page}. The first block keeps {@code firstId} when one was reserved for its voice. */
    private boolean addReply(Section page, String persona, String text, String firstId) {
        boolean wrote = false;
        boolean stamp = firstId != null && !firstId.isEmpty();
        for (Message m : blocks("assistant", persona, text)) {
            if (m.body.trim().isEmpty()) {
                continue;
            }
            if (stamp) {
                m = new Message(m.role, m.kind, m.body, m.persona, m.language, firstId);
                stamp = false;
            }
            page.messages.add(m);
            if ("text".equals(m.kind) && file != null) {
                ai.ibiko.learn.Identifier.save(file.getParentFile(), page.group != null ? page.group : page.id, "assistant", m.id, m.body);
            }
            wrote = true;
        }
        return wrote;
    }

    /** A fact every section can see. Empty and duplicate notes are ignored. */
    public synchronized boolean remember(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || shared.contains(t)) {
            return false;
        }
        shared.add(t);
        if (shared.size() > 80) {
            shared.remove(0);
        }
        save();
        return true;
    }

    /**
     * Notes the user asked to remember. Another conversation's messages are not included: each conversation
     * starts clean.
     */
    public synchronized String memoryForPrompt(int limit) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = shared.size() - 1; i >= 0 && n < limit; i--) {
            b.append("- ").append(shared.get(i)).append('\n');
            n++;
        }
        if (b.length() == 0) {
            return "";
        }
        return "Remembered:\n" + b.toString().trim();
    }

    /** The current section only, newest tail, so the brain can follow this chat and not another one. */
    public synchronized String transcript(int maxChars) {
        return transcript(maxChars, 220);
    }

    /** As above, each message kept up to {@code perMessage} characters (an agent needs the earlier findings whole). */
    public synchronized String transcript(int maxChars, int perMessage) {
        Section s = current();
        StringBuilder b = new StringBuilder();
        List<Integer> starts = new ArrayList<Integer>(), owners = new ArrayList<Integer>(); // where each line starts, whose
        for (int mi = 0; mi < s.messages.size(); mi++) {
            Message m = s.messages.get(mi);
            if ("activity".equals(m.kind) || "step".equals(m.kind) || "work".equals(m.kind)) {
                continue; // how a reply was worked out isn't part of the conversation the model sees
            }
            String who = "user".equals(m.role) ? "User" : (m.persona == null || m.persona.isEmpty() ? "Assistant" : m.persona);
            String line;
            if ("image".equals(m.kind)) {
                line = who + ": [a photograph file is in this chat]";
            } else if ("file".equals(m.kind)) {
                line = who + ": [attached " + m.body.split("\n")[0] + "]";
            } else if ("video".equals(m.kind)) {
                line = who + ": [a video file is in this chat]";
            } else if ("code".equals(m.kind) || "html".equals(m.kind)) {
                String code = m.body.replace('\n', ' ').trim();
                line = who + " code: " + (code.length() > 180 ? code.substring(0, 180) : code);
            } else {
                String t = m.body.replace('\n', ' ').trim();
                line = who + ": " + (t.length() > perMessage ? t.substring(0, perMessage) : t);
            }
            starts.add(b.length());
            owners.add(mi);
            b.append(line).append('\n');
        }
        String all = b.toString().trim();
        if (all.isEmpty()) {
            return "";
        }
        if (maxChars > 0 && all.length() > maxChars) {
            // the start no longer fits: the notes record keeps what was asked and said there, in short, so a model
            // with a short memory (the device's) still knows what happened earlier in this chat
            int notesRoom = Math.min(NOTES_MAX, maxChars * 2 / 5);
            int from = all.length() - (maxChars - notesRoom);
            int line = 0;
            while (line < starts.size() && starts.get(line) < from) {
                line++; // the first whole line that fits
            }
            if (line >= starts.size()) {
                line = starts.size() - 1;
            }
            String recent = all.substring(Math.min(all.length(), starts.get(line)));
            String notes = notes(s, owners.get(line), notesRoom);
            return "This chat (" + s.persona + "):\n" + (notes.isEmpty() ? "" : notes + "\nThe latest messages:\n") + recent;
        }
        return "This chat (" + s.persona + "):\n" + all;
    }

    /**
     * The notes record for what the model no longer holds: every exchange before the last {@code turns} of this chat
     * (a model remembers only its last few), in at most {@code room} characters; "" when there is nothing older.
     */
    public synchronized String olderNotes(int turns, int room) {
        Section s = current();
        int seen = 0;
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            Message m = s.messages.get(i);
            if ("user".equals(m.role) && "text".equals(m.kind) && ++seen > turns) {
                return notes(s, i + 1, room);
            }
        }
        return "";
    }

    /** Each page's memory codes (its sentences filed under ids and tags), built as the chat grows. */
    private final Map<String, ai.ibiko.learn.Codes> codes = new java.util.HashMap<String, ai.ibiko.learn.Codes>();
    private final Map<String, Integer> coded = new java.util.HashMap<String, Integer>();

    /**
     * What this chat said earlier about {@code question}, by memory code: the question's tags pull the ids of the
     * sentences about it (older than the last {@code turns} exchanges, which the brain already has), and only those
     * sentences are read back, each with its code, in the order they were said. "" when nothing earlier is about it.
     */
    public synchronized String recall(String question, int turns, int room) {
        Section s = current();
        int until = s.messages.size(), seen = 0;
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            Message m = s.messages.get(i);
            if ("user".equals(m.role) && "text".equals(m.kind) && ++seen > turns) {
                until = i + 1;
                break;
            }
        }
        if (seen <= turns) {
            return ""; // the whole chat is still in the brain's own memory
        }
        ai.ibiko.learn.Codes c = codes.get(s.id);
        Integer done = coded.get(s.id);
        if (c == null || done == null || done > until) {
            c = new ai.ibiko.learn.Codes(); // the chat was cut back: filed again
            done = 0;
            codes.put(s.id, c);
        }
        for (int i = done; i < until; i++) {
            Message m = s.messages.get(i);
            if ("text".equals(m.kind)) {
                c.addMessage(m.id, ("user".equals(m.role) ? "The user: " : "") + m.body);
            }
        }
        coded.put(s.id, until);
        List<String> ids = c.recall(question, 8, 2);
        if (ids.isEmpty()) {
            ids = c.recall(question, 4, 1);
        }
        return ids.isEmpty() ? "" : c.readBack(ids, room);
    }

    /** The most characters the notes record may take in front of the latest messages. */
    static final int NOTES_MAX = 1800;
    /** The chat's first notes are always kept: what it is about, and who the user is. */
    static final int KEEP_FIRST = 2;

    /**
     * The notes record: one short line per earlier exchange (what the user asked, the first sentence of the answer),
     * for the messages before {@code until}, the part of the chat that no longer fits. Built from the chat itself,
     * so it costs no model call; the newest notes are kept when they don't all fit {@code room}.
     */
    static String notes(Section s, int until, int room) {
        List<String> lines = new ArrayList<String>();
        String asked = null;
        for (int i = 0; i < until && i < s.messages.size(); i++) {
            Message m = s.messages.get(i);
            if (!"text".equals(m.kind) || m.body == null || m.body.trim().isEmpty()) {
                continue;
            }
            String t = m.body.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if ("user".equals(m.role)) {
                if (asked != null) {
                    lines.add("- The user said: " + clip(asked, 110));
                }
                asked = t;
            } else if (asked != null) {
                lines.add("- The user asked: " + clip(asked, 110) + " \u2192 " + (m.persona == null ? "the answer" : m.persona) + ": "
                        + clip(firstSentence(t), 150));
                asked = null;
            }
        }
        if (asked != null) {
            lines.add("- The user said: " + clip(asked, 110));
        }
        // the chat's start (what it is about, who the user is) and then the newest notes, as many as fit
        int budget = room - 60;
        StringBuilder head = new StringBuilder();
        int first = 0;
        for (; first < Math.min(KEEP_FIRST, lines.size()); first++) {
            String l = lines.get(first);
            if (head.length() + l.length() + 1 > budget / 2) {
                break;
            }
            head.append(l).append('\n');
        }
        StringBuilder tail = new StringBuilder();
        int used = head.length();
        int i = lines.size() - 1;
        for (; i >= first; i--) {
            String l = lines.get(i);
            if (used + l.length() + 3 > budget) {
                break;
            }
            tail.insert(0, l + "\n");
            used += l.length() + 1;
        }
        String gap = i >= first ? "- \u2026\n" : "";
        String out = (head.toString() + gap + tail).trim();
        return out.isEmpty() ? "" : "Notes from earlier in this chat (oldest first):\n" + out;
    }

    private static String firstSentence(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.{20,}?[.!?])(\\s|$)").matcher(t);
        return m.find() ? m.group(1) : t;
    }

    private static String clip(String t, int most) {
        return t.length() <= most ? t : t.substring(0, most - 1).trim() + "\u2026";
    }

    private static String lastUser(Section s) {
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            Message m = s.messages.get(i);
            if ("user".equals(m.role) && "text".equals(m.kind)) {
                String t = m.body.replace('\n', ' ').trim();
                return t.length() > 160 ? t.substring(0, 160) : t;
            }
        }
        return null;
    }

    public synchronized boolean rename(String id, String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (Section s : sections) {
            if (s.id.equals(id)) {
                s.title = t.length() > 48 ? t.substring(0, 48) : t;
                for (Section other : sections) {
                    if (s.group.equals(other.group)) {
                        other.title = s.title;
                    }
                }
                save();
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the message at {@code index} of a chat and everything after it (an edited message is sent again in its
     * place, and the replies to the old one go); false when there is no such message.
     */
    public synchronized boolean cutFrom(String id, int index) {
        for (Section s : sections) {
            if (s.id.equals(id)) {
                if (index < 0 || index >= s.messages.size()) {
                    return false;
                }
                while (s.messages.size() > index) {
                    s.messages.remove(s.messages.size() - 1);
                }
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean pin(String id, boolean on) {
        Section found = null;
        for (Section s : sections) {
            if (s.id.equals(id)) {
                found = s;
                break;
            }
        }
        if (found == null) {
            return false;
        }
        for (Section s : sections) {
            if (found.group.equals(s.group)) {
                s.pinned = on;
            }
        }
        save();
        return true;
    }

    public synchronized boolean archive(String id, boolean on) {
        for (Section s : sections) {
            if (s.id.equals(id)) {
                s.archived = on;
                save();
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes a conversation (every persona page in it) but keeps what was learned: the gist of what the user
     * said becomes a remembered note. The last remaining conversation is cleared instead.
     */
    public synchronized boolean delete(String id) {
        Section found = null;
        for (Section s : sections) {
            if (s.id.equals(id)) {
                found = s;
                break;
            }
        }
        if (found == null) {
            return false;
        }
        String gist = gist(found);
        if (gist != null) {
            remember(gist);
        }
        int groups = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (Section s : sections) {
            if (seen.add(s.group)) {
                groups++;
            }
        }
        if (groups <= 1) {
            for (Section s : sections) {
                s.messages.clear();
                s.title = "Chat";
                s.pinned = false;
            }
            forgetVoice(found.group);
            save();
            return true;
        }
        String group = found.group;
        forgetVoice(group); // the voice kept for its messages goes with the chat
        for (int i = sections.size() - 1; i >= 0; i--) {
            if (group.equals(sections.get(i).group)) {
                sections.remove(i);
            }
        }
        boolean still = false;
        for (Section s : sections) {
            if (s.id.equals(currentId)) {
                still = true;
            }
        }
        if (!still) {
            currentId = sections.get(0).id;
        }
        save();
        return true;
    }

    /** Deletes the voice kept for a chat's messages ({@code files/voices/said/<chat>}). */
    private void forgetVoice(String group) {
        if (file == null || group == null) {
            return;
        }
        ai.ibiko.voice.SpeechCache.forgetChat(file.getParentFile(), group);
    }

    /** "From the chat “Trip plans”: book a flight to Lagos; …", or null when the user said nothing there. */
    static String gist(Section s) {
        List<String> said = new ArrayList<String>();
        for (int i = s.messages.size() - 1; i >= 0 && said.size() < 4; i--) {
            Message m = s.messages.get(i);
            if ("user".equals(m.role) && "text".equals(m.kind) && m.body != null && !m.body.trim().isEmpty()) {
                String t = m.body.replace('\n', ' ').trim();
                said.add(0, t.length() > 120 ? t.substring(0, 120) + "…" : t);
            }
        }
        if (said.isEmpty()) {
            return null;
        }
        String name = s.title == null || s.title.trim().isEmpty() || s.title.matches("(?i)^(chat|new chat)$") ? "" : " “" + s.title.trim() + "”";
        return "From the deleted chat" + name + ", the user said: " + String.join("; ", said);
    }

    public synchronized List<String> projects() {
        return new ArrayList<String>(projects);
    }

    /** Makes a project (a name of at most 40 characters); false when the name is empty or taken. */
    public synchronized boolean addProject(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 40) {
            n = n.substring(0, 40).trim();
        }
        if (n.isEmpty() || projects.contains(n)) {
            return false;
        }
        projects.add(n);
        save();
        return true;
    }

    /** Puts a chat in a project (null: out of any project); a project named here is made if needed. */
    public synchronized boolean moveTo(String id, String project) {
        Section found = null;
        for (Section s : sections) {
            if (s.id.equals(id)) {
                found = s;
                break;
            }
        }
        if (found == null) {
            return false;
        }
        String p = project == null || project.trim().isEmpty() ? null : project.trim();
        if (p != null && !projects.contains(p)) {
            projects.add(p);
        }
        for (Section s : sections) {
            if (found.group.equals(s.group)) {
                s.project = p;
            }
        }
        save();
        return true;
    }

    /** Deletes a project; its chats stay, outside any project. */
    public synchronized boolean removeProject(String name) {
        if (!projects.remove(name)) {
            return false;
        }
        for (Section s : sections) {
            if (name.equals(s.project)) {
                s.project = null;
            }
        }
        save();
        return true;
    }

    /** The chats in a project, newest last. */
    public synchronized List<Section> inProject(String name) {
        List<Section> out = new ArrayList<Section>();
        for (Section s : sections) {
            if (name != null && name.equals(s.project)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Pinned chats first, the rest after, each group in its own order. */
    public synchronized List<Section> pinnedFirst() {
        List<Section> out = new ArrayList<Section>();
        for (Section s : sections) {
            if (s.pinned) {
                out.add(s);
            }
        }
        for (Section s : sections) {
            if (!s.pinned) {
                out.add(s);
            }
        }
        return out;
    }

    /** One row per conversation: the page with a real title, or the one that has messages. */
    public synchronized List<Section> groups() {
        Map<String, Section> pick = new LinkedHashMap<String, Section>();
        for (Section s : pinnedFirst()) {
            Section have = pick.get(s.group);
            if (have == null || groupScore(s) > groupScore(have)) {
                pick.put(s.group, s);
            }
        }
        List<Section> out = new ArrayList<Section>(pick.values());
        // newest first, oldest last (pinned ones stay on top): a chat's number grows with each new chat
        java.util.Collections.sort(out, (x, y) -> x.pinned != y.pinned ? (x.pinned ? -1 : 1)
                : Integer.compare(number(y.id), number(x.id)));
        return out;
    }

    /** The projects, newest first. */
    public synchronized List<String> projectsNewestFirst() {
        List<String> out = new ArrayList<String>(projects);
        java.util.Collections.reverse(out);
        return out;
    }

    private static int groupScore(Section s) {
        int score = s.messages.isEmpty() ? 0 : 2;
        if (s.title != null && !s.title.matches("(?i)^(chat|new chat|obi|ivie|ibiko|ibiko code)$")) {
            score += 1;
        }
        return score;
    }

    /** Removes a chat. The last remaining chat is cleared instead, so the book is never empty. */
    public synchronized boolean remove(String id) {
        Section found = null;
        for (Section s : sections) {
            if (s.id.equals(id)) {
                found = s;
                break;
            }
        }
        if (found == null) {
            return false;
        }
        if (sections.size() == 1) {
            found.messages.clear();
            found.title = "Chat";
            found.pinned = false;
            found.archived = false;
            forgetVoice(found.group);
            save();
            return true;
        }
        sections.remove(found);
        boolean groupLeft = false;
        for (Section s : sections) {
            groupLeft |= found.group != null && found.group.equals(s.group);
        }
        if (!groupLeft) {
            forgetVoice(found.group); // the last page of this chat: its kept voice and its folder go
            final File files = file == null ? null : file.getParentFile();
            final String group = found.group;
            if (files != null && group != null) {
                new Thread(() -> ai.ibiko.main.ChatSpace.delete(files, group), "ibiko-forget-chat").start();
            }
        }
        if (found.id.equals(currentId)) {
            currentId = sections.get(sections.size() - 1).id;
        }
        save();
        return true;
    }

    public synchronized void reload() {
        if (writeWaiting) {
            writeWaiting = false;
            writeNow(); // lines not written yet would be lost by reading the file back
        }
        sections.clear();
        shared.clear();
        currentId = null;
        next = 1;
        load();
        if (sections.isEmpty()) {
            open("Chat", "ivie");
        }
    }

    private static String personaOf(String persona) {
        if ("obi".equals(persona) || "ibiko".equals(persona)) {
            return persona;
        }
        return "ivie";
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            Object parsed = MiniJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map)) {
                return;
            }
            Map<?, ?> root = (Map<?, ?>) parsed;
            Object notes = root.get("shared");
            if (notes instanceof List) {
                for (Object n : (List<?>) notes) {
                    if (n != null && !String.valueOf(n).isEmpty()) {
                        shared.add(String.valueOf(n));
                    }
                }
            }
            Object projs = root.get("projects");
            if (projs instanceof List) {
                for (Object n : (List<?>) projs) {
                    if (n != null && !String.valueOf(n).trim().isEmpty() && !projects.contains(String.valueOf(n).trim())) {
                        projects.add(String.valueOf(n).trim());
                    }
                }
            }
            Object list = root.get("sections");
            if (list instanceof List) {
                for (Object item : (List<?>) list) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> m = (Map<?, ?>) item;
                    Section s = new Section(MiniJson.str(m.get("id"), "c" + next),
                            MiniJson.str(m.get("title"), "Chat"), personaOf(MiniJson.str(m.get("persona"), "ivie")));
                    s.pinned = Boolean.TRUE.equals(m.get("pinned"));
                    s.archived = Boolean.TRUE.equals(m.get("archived"));
                    String proj = MiniJson.str(m.get("project"), "");
                    s.project = proj.isEmpty() ? null : proj;
                    String group = MiniJson.str(m.get("group"), "");
                    s.group = group.isEmpty() ? "g" + s.id.replaceFirst("^c", "") : group;
                    Object msgs = m.get("messages");
                    if (msgs instanceof List) {
                        for (Object row : (List<?>) msgs) {
                            if (!(row instanceof Map)) {
                                continue;
                            }
                            Map<?, ?> mm = (Map<?, ?>) row;
                            s.messages.add(new Message(MiniJson.str(mm.get("role"), "assistant"),
                                    MiniJson.str(mm.get("kind"), "text"), MiniJson.str(mm.get("body"), ""),
                                    MiniJson.str(mm.get("persona"), s.persona),
                                    MiniJson.str(mm.get("language"), ""), MiniJson.str(mm.get("id"), "")));
                        }
                    }
                    sections.add(s);
                    int num = number(s.id);
                    if (num >= next) {
                        next = num + 1;
                    }
                }
            }
            currentId = MiniJson.str(root.get("current"), sections.isEmpty() ? null : sections.get(0).id);
            freshFrom = (int) MiniJson.num(root.get("freshFrom"), 0);
        } catch (RuntimeException | IOException ignored) {
            // an unreadable file is kept aside before anything is written over it, so no chat is lost for good
            try {
                Files.copy(file.toPath(), new File(file.getPath() + ".unreadable").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException kept) {
                // nothing more to do
            }
            sections.clear();
            shared.clear();
            projects.clear();
        }
    }

    private static int number(String id) {
        if (id == null || !id.startsWith("c")) {
            return 0;
        }
        try {
            return Integer.parseInt(id.substring(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** While long work adds a line every few seconds, the whole book is written at most about once a second. */
    private static final java.util.concurrent.ScheduledExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ibiko-chats-save");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile boolean coalesce, writeWaiting;

    /**
     * The chat screen turns this on: saves are gathered and written off the screen's thread, at most about once a
     * second (every chat is one file, so writing it on each line of a long task kept the processor and storage busy
     * and warmed the device). {@link #flush} writes at once (when the app leaves the screen).
     */
    public void coalesce(boolean on) {
        coalesce = on;
    }

    /** Writes now whatever is waiting. */
    public synchronized void flush() {
        if (writeWaiting) {
            writeWaiting = false;
            writeNow();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        if (!coalesce) {
            writeNow();
            return;
        }
        if (writeWaiting) {
            return; // one write is already on its way and will carry this change
        }
        writeWaiting = true;
        WRITER.schedule(this::flush, 900, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void writeNow() {
        if (file == null) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("current", currentId);
        root.put("freshFrom", Integer.valueOf(freshFrom));
        root.put("shared", new ArrayList<String>(shared));
        root.put("projects", new ArrayList<String>(projects));
        List<Object> list = new ArrayList<Object>();
        for (Section s : sections) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", s.id);
            m.put("title", s.title);
            m.put("persona", s.persona);
            m.put("pinned", Boolean.valueOf(s.pinned));
            m.put("archived", Boolean.valueOf(s.archived));
            if (s.project != null) {
                m.put("project", s.project);
            }
            if (s.group != null) {
                m.put("group", s.group);
            }
            List<Object> msgs = new ArrayList<Object>();
            for (Message msg : s.messages) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", msg.id);
                row.put("role", msg.role);
                row.put("kind", msg.kind);
                row.put("body", msg.body);
                row.put("persona", msg.persona);
                if (msg.language != null && !msg.language.isEmpty()) {
                    row.put("language", msg.language);
                }
                msgs.add(row);
            }
            m.put("messages", msgs);
            list.add(m);
        }
        root.put("sections", list);
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            // written beside, then moved over in one step: a crash or a reader mid-write never sees half a file
            // (a half file read back as no chats, and the next save then wrote that emptiness over everything)
            File part = new File(file.getPath() + ".part");
            Files.write(part.toPath(), MiniJson.write(root).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(part.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(part.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // the transcript stays in memory for this run
        }
    }
}
