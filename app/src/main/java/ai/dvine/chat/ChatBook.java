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

        public Message(String role, String kind, String body, String persona) {
            this.role = role == null ? "assistant" : role;
            this.kind = kind == null ? "text" : kind;
            this.body = body == null ? "" : body;
            this.persona = persona == null ? "" : persona;
        }
    }

    public static final class Section {
        public final String id;
        public String title;
        public String persona;
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
    private String currentId;
    private int next = 1;

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

    public synchronized void add(String role, String kind, String body, String persona) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        Section s = current();
        if ("user".equals(role) && (s.title == null || s.title.equals("Chat") || s.title.equals("New chat"))
                && s.messages.isEmpty()) {
            String t = body.trim().replace('\n', ' ');
            s.title = t.length() > 28 ? t.substring(0, 28) : t;
        }
        if ("code".equals(kind) || "image".equals(kind) || "video".equals(kind) || "text".equals(kind)) {
            s.messages.add(new Message(role, kind, body, persona));
        } else {
            s.messages.add(new Message(role, "text", body, persona));
        }
        save();
    }

    /** Splits a reply on fenced code blocks. Ordinary speech stays one text message. */
    public static List<Message> blocks(String role, String persona, String text) {
        List<Message> out = new ArrayList<Message>();
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
        int i = 0;
        while (i < text.length()) {
            int fence = text.indexOf("```", i);
            if (fence < 0) {
                String rest = text.substring(i).trim();
                if (!rest.isEmpty()) {
                    out.add(new Message(role, "text", rest, persona));
                }
                break;
            }
            String before = text.substring(i, fence).trim();
            if (!before.isEmpty()) {
                out.add(new Message(role, "text", before, persona));
            }
            int close = text.indexOf("```", fence + 3);
            if (close < 0) {
                String code = text.substring(fence + 3).replaceFirst("^[A-Za-z0-9_+-]*\\n?", "").trim();
                if (!code.isEmpty()) {
                    out.add(new Message(role, "code", code, persona));
                }
                break;
            }
            String code = text.substring(fence + 3, close).replaceFirst("^[A-Za-z0-9_+-]*\\n?", "").trim();
            if (!code.isEmpty()) {
                out.add(new Message(role, "code", code, persona));
            }
            i = close + 3;
        }
        return out;
    }

    public synchronized void reply(String persona, String text) {
        for (Message m : blocks("assistant", persona, text)) {
            add(m.role, m.kind, m.body, m.persona);
        }
    }

    /** A fact every section can see. Empty and duplicate notes are ignored. */
    public synchronized boolean remember(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || shared.contains(t)) {
            return false;
        }
        shared.add(t);
        if (shared.size() > 40) {
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
            Object list = root.get("sections");
            if (list instanceof List) {
                for (Object item : (List<?>) list) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> m = (Map<?, ?>) item;
                    Section s = new Section(MiniJson.str(m.get("id"), "c" + next),
                            MiniJson.str(m.get("title"), "Chat"), personaOf(MiniJson.str(m.get("persona"), "ivie")));
                    Object msgs = m.get("messages");
                    if (msgs instanceof List) {
                        for (Object row : (List<?>) msgs) {
                            if (!(row instanceof Map)) {
                                continue;
                            }
                            Map<?, ?> mm = (Map<?, ?>) row;
                            s.messages.add(new Message(MiniJson.str(mm.get("role"), "assistant"),
                                    MiniJson.str(mm.get("kind"), "text"), MiniJson.str(mm.get("body"), ""),
                                    MiniJson.str(mm.get("persona"), s.persona)));
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
        } catch (RuntimeException | IOException ignored) {
            sections.clear();
            shared.clear();
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
        root.put("shared", new ArrayList<String>(shared));
        List<Object> list = new ArrayList<Object>();
        for (Section s : sections) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", s.id);
            m.put("title", s.title);
            m.put("persona", s.persona);
            List<Object> msgs = new ArrayList<Object>();
            for (Message msg : s.messages) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("role", msg.role);
                row.put("kind", msg.kind);
                row.put("body", msg.body);
                row.put("persona", msg.persona);
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
