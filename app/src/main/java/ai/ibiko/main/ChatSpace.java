package ai.ibiko.main;

import java.io.File;

/**
 * Each chat's own folder, and nothing of another chat's: {@code files/ibiko/chats/<chat>/}. Inside it,
 * {@code repos/} holds what was pulled (cloned repositories), {@code memory/} holds what Ibiko keeps about this chat
 * (its project record, plans, task states), and {@code images/} the pictures made in it. The file tools, the agent and
 * the Linux commands of a chat see only this folder (in Linux it is {@code /workspace}); the workspace screen shows
 * only it; deleting the chat deletes it.
 */
public final class ChatSpace {
    /** Where the chats' folders are, under the coding workspace {@code files/ibiko}. */
    public static final String CHATS = "chats";
    /** Pulled data (cloned repositories), apart from memories. */
    public static final String REPOS = "repos";
    /** What Ibiko keeps about this chat. */
    public static final String MEMORY = "memory";
    public static final String IMAGES = "images";

    private ChatSpace() {
    }

    /** {@code files/ibiko}, the folder all chats' folders are in. */
    public static File root(File filesDir) {
        return new File(filesDir, "ibiko");
    }

    /** A chat's own folder (made with its parts when missing). {@code chat} is the conversation's id. */
    public static File of(File filesDir, String chat) {
        File d = new File(new File(root(filesDir), CHATS), safe(chat));
        new File(d, REPOS).mkdirs();
        new File(d, MEMORY).mkdirs();
        return d;
    }

    /** The chat a folder belongs to, or null when it is not inside a chat's folder. */
    public static File chatOf(File dir) {
        File cur = dir;
        while (cur != null && cur.getParentFile() != null) {
            File p = cur.getParentFile();
            if (CHATS.equals(p.getName()) && p.getParentFile() != null && "ibiko".equals(p.getParentFile().getName())) {
                return cur;
            }
            cur = p;
        }
        return null;
    }

    static String safe(String chat) {
        String s = chat == null ? "" : chat.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() || s.startsWith(".") ? "chat" : s;
    }

    /** Deletes a chat's folder and everything in it (its repositories, memories and pictures). */
    public static boolean delete(File filesDir, String chat) {
        File d = new File(new File(root(filesDir), CHATS), safe(chat));
        return !d.exists() || wipe(d);
    }

    private static boolean wipe(File f) {
        File[] kids = f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath()) ? f.listFiles() : null;
        if (kids != null) {
            for (File k : kids) {
                wipe(k);
            }
        }
        return f.delete() || !f.exists();
    }

    /**
     * Files from before chats had their own folders ({@code files/ibiko/memory/projects/<repo>} and the rest) are moved
     * once into {@code chat}'s folder: repositories (folders with {@code .git}) into {@code repos/}, records and task
     * states into {@code memory/}, anything else as it was. Returns how many were moved.
     */
    public static int adoptOld(File filesDir, String chat) {
        File ws = root(filesDir);
        File[] top = ws.listFiles();
        if (top == null) {
            return 0;
        }
        File mine = of(filesDir, chat);
        int moved = 0;
        File oldProjects = new File(ws, "memory/projects");
        File[] projects = oldProjects.listFiles();
        if (projects != null) {
            for (File p : projects) {
                File to = new File(new File(mine, new File(p, ".git").exists() ? REPOS : MEMORY + "/projects"), p.getName());
                to.getParentFile().mkdirs();
                if (!to.exists() && p.renameTo(to)) {
                    moved++;
                }
            }
        }
        for (File f : top) {
            if (CHATS.equals(f.getName())) {
                continue;
            }
            if (MEMORY.equals(f.getName()) && f.isDirectory()) {
                File[] kids = f.listFiles();
                for (File k : kids == null ? new File[0] : kids) {
                    if ("projects".equals(k.getName()) && (k.list() == null || k.list().length == 0)) {
                        k.delete();
                        continue;
                    }
                    File to = new File(new File(mine, MEMORY), k.getName());
                    if (!to.exists() && k.renameTo(to)) {
                        moved++;
                    }
                }
                f.delete();
                continue;
            }
            File to = new File(mine, f.getName());
            if (!to.exists() && f.renameTo(to)) {
                moved++;
            }
        }
        return moved;
    }
}
