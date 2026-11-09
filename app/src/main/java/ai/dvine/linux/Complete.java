package ai.dvine.linux;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tab completion against the Linux files on disk. No command is run. */
public final class Complete {
    private static final String[] BINS = {"/bin", "/usr/bin", "/sbin", "/usr/sbin", "/usr/local/bin"};

    private Complete() {
    }

    public static final class Hit {
        public final String line;
        public final int cursor;
        /** Names to print when there is more than one match. Empty when there is nothing to print. */
        public final String shown;

        Hit(String line, int cursor, String shown) {
            this.line = line;
            this.cursor = cursor;
            this.shown = shown;
        }
    }

    public static Hit tab(File rootfs, String cwd, String line, int cursor) {
        String text = line == null ? "" : line;
        int at = cursor < 0 || cursor > text.length() ? text.length() : cursor;
        int start = at;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        String token = text.substring(start, at);
        boolean command = text.substring(0, start).trim().isEmpty();
        List<String> names = new ArrayList<String>();
        String base = token;
        int slash = token.lastIndexOf('/');
        if (slash >= 0 || !command) {
            String dirToken = slash < 0 ? "" : token.substring(0, slash + 1);
            base = slash < 0 ? token : token.substring(slash + 1);
            files(directory(rootfs, cwd, dirToken), base, names);
        } else {
            files(directory(rootfs, cwd, ""), token, names);
            for (String bin : BINS) {
                files(new File(rootfs, bin.substring(1)), token, names);
            }
        }
        Collections.sort(names);
        unique(names);
        if (names.isEmpty()) {
            return new Hit(text, at, "");
        }
        if (names.size() == 1) {
            String put = token.substring(0, token.length() - base.length()) + names.get(0);
            if (!put.endsWith("/")) {
                put = put + " ";
            }
            String next = text.substring(0, start) + put + text.substring(at);
            return new Hit(next, start + put.length(), "");
        }
        String shared = common(names);
        String next = text;
        int caret = at;
        if (shared.length() > base.length()) {
            String put = token.substring(0, token.length() - base.length()) + shared;
            next = text.substring(0, start) + put + text.substring(at);
            caret = start + put.length();
        }
        StringBuilder show = new StringBuilder();
        int n = Math.min(names.size(), 80);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                show.append("  ");
            }
            show.append(names.get(i));
        }
        return new Hit(next, caret, show.toString());
    }

    private static void unique(List<String> names) {
        int i = 1;
        while (i < names.size()) {
            if (names.get(i).equals(names.get(i - 1))) {
                names.remove(i);
            } else {
                i++;
            }
        }
    }

    private static String common(List<String> names) {
        String shared = names.get(0);
        for (int i = 1; i < names.size(); i++) {
            String name = names.get(i);
            while (!name.startsWith(shared) && shared.length() > 0) {
                shared = shared.substring(0, shared.length() - 1);
            }
        }
        return shared;
    }

    private static File directory(File rootfs, String cwd, String dirToken) {
        String guest = cwd == null || cwd.isEmpty() ? "/root" : cwd;
        if (dirToken.startsWith("/")) {
            guest = dirToken;
        } else if (dirToken.startsWith("~/")) {
            guest = "/root/" + dirToken.substring(2);
        } else if (!dirToken.isEmpty()) {
            guest = guest.endsWith("/") ? guest + dirToken : guest + "/" + dirToken;
        }
        while (guest.endsWith("/") && guest.length() > 1) {
            guest = guest.substring(0, guest.length() - 1);
        }
        return new File(rootfs, guest.startsWith("/") ? guest.substring(1) : guest);
    }

    private static void files(File dir, String prefix, List<String> out) {
        File[] list = dir == null ? null : dir.listFiles();
        if (list == null) {
            return;
        }
        Arrays.sort(list);
        for (File file : list) {
            String name = file.getName();
            if (prefix.isEmpty() || name.startsWith(prefix)) {
                out.add(file.isDirectory() ? name + "/" : name);
            }
        }
    }
}
