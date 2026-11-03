package ai.dvine.social;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The user's social accounts on this phone: which network, the name they gave it, and which one is in use on each
 * network (several accounts on one network take turns: the site's sign-in is swapped when switching). The sign-ins
 * themselves are kept apart, encrypted ({@code SocialWeb}). A text file; thread-safe; pure Java.
 */
public final class SocialAccounts {
    public static final class Account {
        public final String id, label;
        public final Platform platform;
        public final long added;
        public final boolean inUse, signedIn;

        Account(String id, Platform platform, String label, long added, boolean inUse, boolean signedIn) {
            this.id = id;
            this.platform = platform;
            this.label = label;
            this.added = added;
            this.inUse = inUse;
            this.signedIn = signedIn;
        }

        Account with(boolean inUse, boolean signedIn) {
            return new Account(id, platform, label, added, inUse, signedIn);
        }
    }

    private final File file;
    private final List<Account> list = new ArrayList<Account>();
    /** Networks switched off in Connectors: their accounts stay signed in, but DVINE doesn't use them. */
    private final java.util.Set<Platform> off = java.util.EnumSet.noneOf(Platform.class);

    public synchronized void setOn(Platform p, boolean on) {
        if (on) {
            off.remove(p);
        } else {
            off.add(p);
        }
    }

    public synchronized boolean on(Platform p, boolean ignored) {
        return !off.contains(p);
    }

    /** A new name for an account ("Work", "Shop page"); a blank one is ignored. */
    public synchronized void rename(String id, String label) {
        String name = label == null ? "" : label.trim().replace('\t', ' ').replace('\n', ' ');
        if (name.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Account a = list.get(i);
            if (a.id.equals(id)) {
                list.set(i, new Account(a.id, a.platform, name, a.added, a.inUse, a.signedIn));
            }
        }
        save();
    }

    public SocialAccounts(File file) {
        this.file = file;
        load();
    }

    public synchronized List<Account> all() {
        return new ArrayList<Account>(list);
    }

    public synchronized List<Account> on(Platform p) {
        List<Account> out = new ArrayList<Account>();
        for (Account a : list) {
            if (a.platform == p) {
                out.add(a);
            }
        }
        return out;
    }

    /** The account in use on a network, or null. */
    public synchronized Account inUse(Platform p) {
        for (Account a : list) {
            if (a.platform == p && a.inUse) {
                return a;
            }
        }
        return null;
    }

    public synchronized Account get(String id) {
        for (Account a : list) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    /** The networks with an account signed in, in the order searches use them. */
    public synchronized List<Platform> ready() {
        List<Platform> out = new ArrayList<Platform>();
        for (Platform p : new Platform[]{Platform.X, Platform.REDDIT, Platform.FACEBOOK, Platform.INSTAGRAM}) {
            Account a = inUse(p);
            if (a != null && a.signedIn && !off.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** A new account, in use on its network from now on; a blank name becomes "X 2" and so on. */
    public synchronized Account add(Platform p, String label, long now) {
        String name = label == null || label.trim().isEmpty() ? p.shown + " " + (on(p).size() + 1) : label.trim();
        name = name.replace('\t', ' ').replace('\n', ' ');
        String id = p.name().toLowerCase(java.util.Locale.ROOT) + "-" + Long.toString(now, 36)
                + Integer.toString((int) (Math.random() * 1296), 36);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).platform == p) {
                list.set(i, list.get(i).with(false, list.get(i).signedIn));
            }
        }
        Account a = new Account(id, p, name, now, true, true);
        list.add(a);
        save();
        return a;
    }

    public synchronized void use(String id) {
        Account a = get(id);
        if (a == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Account b = list.get(i);
            if (b.platform == a.platform) {
                list.set(i, b.with(b.id.equals(id), b.signedIn));
            }
        }
        save();
    }

    public synchronized void signedIn(String id, boolean in) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.set(i, list.get(i).with(list.get(i).inUse, in));
            }
        }
        save();
    }

    /** Gone from the list; another account on that network, if any, comes into use. */
    public synchronized void remove(String id) {
        Account a = get(id);
        if (a == null) {
            return;
        }
        list.remove(a);
        if (a.inUse) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).platform == a.platform) {
                    list.set(i, list.get(i).with(true, list.get(i).signedIn));
                    break;
                }
            }
        }
        save();
    }

    private void load() {
        list.clear();
        try {
            if (!file.isFile()) {
                return;
            }
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\t");
                if (f.length < 6) {
                    continue;
                }
                try {
                    list.add(new Account(f[0], Platform.valueOf(f[1]), f[2], Long.parseLong(f[3]), "1".equals(f[4]), "1".equals(f[5])));
                } catch (IllegalArgumentException damaged) {
                    // skipped
                }
            }
        } catch (IOException ignored) {
            // none
        }
    }

    private void save() {
        StringBuilder b = new StringBuilder();
        for (Account a : list) {
            b.append(a.id).append('\t').append(a.platform.name()).append('\t').append(a.label).append('\t').append(a.added)
                    .append('\t').append(a.inUse ? "1" : "0").append('\t').append(a.signedIn ? "1" : "0").append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (IOException ignored) {
            // kept in memory for now
        }
    }
}
