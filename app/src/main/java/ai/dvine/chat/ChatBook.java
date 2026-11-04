package ai.dvine.chat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.dvine.brain.MiniJson;

/**
 * Chat sections on this phone. Each section has its own transcript and persona.
 * Notes and the latest line from every other section are one shared memory, so a
 * later chat can refer to an earlier one. This is not a cloud memory and not a
 * graph database.
 */
public final class ChatBook {
    public static final class Message {
        public final String role;
        public final String kind;
        public final String body;
        public final String persona;
        public final String language;

        public Message(String role, String kind, String body, String persona) {
            this(role, kind, body, persona, "");
        }

        public Message(String role, String kind, String body, String persona, String language) {
            this.role = role == null ? "assistant" : role;
            this.kind = kind == null ? "text" : kind;
            this.body = body == null ? "" : body;
            this.persona = persona == null ? "" : persona;
            this.language = language == null ? "" : language;
        }
    }

    public static final class Section {
        public final String id;
        public String title;
        public String persona;
        public boolean pinned;
        public boolean archived;
        /** The project this chat belongs to (its name), or null. */
        public String project;
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
        Section s = new Section("c" + next++, title == null || title.isEmpty() ? "Chat" : title, personaOf(persona));
        sections.add(s);
        currentId = s.id;
        save();
        return s;
    }

    /**
     * An empty chat for {@code persona} to start on (app opened, new chat): the latest one that has nothing in it yet,
     * or a new one, so empty chats don't pile up in the list.
     */
    public synchronized Section fresh(String persona) {
        String p = personaOf(persona);
        for (int i = sections.size() - 1; i >= 0; i--) {
            Section s = sections.get(i);
            if (!s.archived && s.messages.isEmpty() && p.equals(s.persona)) {
                currentId = s.id;
                save();
                return s;
            }
        }
        return open("New chat", p);
    }

    /**
     * "New chat": a clean start for every persona. {@code persona} gets an empty chat now; the others get theirs when
     * they are first switched to ({@link #forPersona}), so no empty chats pile up in the list.
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
     * The chat for this persona. Obi and Ivie keep a conversation. Ibiko keeps a coding chat.
     * A different persona is a different chat, not a rename of the one on screen.
     */
    public synchronized Section forPersona(String persona) {
        String p = personaOf(persona);
        Section cur = current();
        if (p.equals(cur.persona) && !cur.archived) {
            return cur;
        }
        for (int i = sections.size() - 1; i >= 0; i--) {
            Section s = sections.get(i);
            if (!s.archived && p.equals(s.persona) && (number(s.id) >= freshFrom || s.messages.isEmpty())) {
                currentId = s.id; // its chat since the last New chat (or an empty one)
                save();
                return s;
            }
        }
        String title = "ibiko".equals(p) ? "Ibiko code" : ("obi".equals(p) ? "Obi" : "Ivie");
        return open(title, p);
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

    public synchronized void add(String role, String kind, String body, String persona) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        Section s = current();
        if ("user".equals(role) && (s.title == null || s.title.equals("Chat") || s.title.equals("New chat"))
                && s.messages.isEmpty()) {
            String t = body.trim().replace('\n', ' ');
            s.title = t.length() > 48 ? t.substring(0, 48) : t;
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

    public synchronized void reply(String persona, String text) {
        boolean wrote = false;
        for (Message m : blocks("assistant", persona, text)) {
            if (m.body.trim().isEmpty()) {
                continue;
            }
            current().messages.add(m);
            wrote = true;
        }
        if (wrote) {
            save();
        }
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
     * What another section should be told: shared notes, then the latest user line from each other section.
     * The current section's own transcript is not repeated here.
     */
    public synchronized String memoryForPrompt(int limit) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = shared.size() - 1; i >= 0 && n < limit; i--) {
            b.append("- ").append(shared.get(i)).append('\n');
            n++;
        }
        String here = currentId;
        for (int i = sections.size() - 1; i >= 0 && n < limit; i--) {
            Section s = sections.get(i);
            if (s.id.equals(here)) {
                continue;
            }
            String last = lastUser(s);
            if (last == null) {
                continue;
            }
            b.append("- In ").append(s.title).append(" (").append(s.persona).append("): ").append(last).append('\n');
            n++;
        }
        if (b.length() == 0) {
            return "";
        }
        return "Shared across chats:\n" + b.toString().trim();
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
        for (Section s : sections) {
            if (s.id.equals(id)) {
                s.pinned = on;
                save();
                return true;
            }
        }
        return false;
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
     * Deletes a chat but keeps what was learned in it: the gist of what the user said there (its name and their
     * latest lines) becomes a shared note every chat can still see ({@link #memoryForPrompt}). Notes, learned
     * phrasings and corrections kept elsewhere are not touched. The last remaining chat is cleared instead.
     */
    public synchronized boolean delete(String id) {
        for (Section s : sections) {
            if (s.id.equals(id)) {
                String gist = gist(s);
                if (gist != null) {
                    remember(gist);
                }
                return remove(id);
            }
        }
        return false;
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
        for (Section s : sections) {
            if (s.id.equals(id)) {
                String p = project == null || project.trim().isEmpty() ? null : project.trim();
                if (p != null && !projects.contains(p)) {
                    projects.add(p);
                }
                s.project = p;
                save();
                return true;
            }
        }
        return false;
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
            save();
            return true;
        }
        sections.remove(found);
        if (found.id.equals(currentId)) {
            currentId = sections.get(sections.size() - 1).id;
        }
        save();
        return true;
    }

    public synchronized void reload() {
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
                                    MiniJson.str(mm.get("language"), "")));
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

    private void save() {
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
            List<Object> msgs = new ArrayList<Object>();
            for (Message msg : s.messages) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
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
            Files.write(file.toPath(), MiniJson.write(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the transcript stays in memory for this run
        }
    }
}
