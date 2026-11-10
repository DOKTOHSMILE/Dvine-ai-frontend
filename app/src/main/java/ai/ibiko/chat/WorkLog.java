package ai.ibiko.chat;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.ibiko.brain.MiniJson;

/**
 * What DVINE's work has produced, kept on the phone for the chips above the message box: the pull requests Ibiko
 * opened (number, title, address, repository) and the artifacts made (Ivie's exported models, files saved for the
 * user), newest first, at most 50 of each.
 */
public final class WorkLog {
    /** One pull request or artifact. */
    public static final class Entry {
        public final String title, address, detail, mime;
        public final long at;

        public Entry(String title, String address, String detail, String mime, long at) {
            this.title = title;
            this.address = address;
            this.detail = detail;
            this.mime = mime;
            this.at = at;
        }
    }

    private final SharedPreferences prefs;

    public WorkLog(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("dvine_work", Context.MODE_PRIVATE);
    }

    public void pullRequest(String number, String title, String url, String repo) {
        add("prs", "#" + number + " " + title, url, repo, "", System.currentTimeMillis());
    }

    public void artifact(String name, String address, String what, String mime) {
        add("artifacts", name, address, what, mime, System.currentTimeMillis());
    }

    public List<Entry> pullRequests() {
        return read("prs");
    }

    public List<Entry> artifacts() {
        return read("artifacts");
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private synchronized void add(String list, String title, String address, String detail, String mime, long at) {
        List<Object> all = MiniJson.list(parse(prefs.getString(list, "[]")));
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("title", title);
        e.put("address", address);
        e.put("detail", detail);
        e.put("mime", mime);
        e.put("at", (double) at);
        all.add(0, e);
        while (all.size() > 50) {
            all.remove(all.size() - 1);
        }
        prefs.edit().putString(list, MiniJson.write(all)).apply();
    }

    private List<Entry> read(String list) {
        List<Entry> out = new ArrayList<Entry>();
        for (Object o : MiniJson.list(parse(prefs.getString(list, "[]")))) {
            Map<String, Object> m = MiniJson.obj(o);
            out.add(new Entry(MiniJson.str(m.get("title"), ""), MiniJson.str(m.get("address"), ""), MiniJson.str(m.get("detail"), ""),
                    MiniJson.str(m.get("mime"), ""), (long) MiniJson.num(m.get("at"), 0)));
        }
        return out;
    }

    private static Object parse(String s) {
        try {
            return MiniJson.parse(s);
        } catch (RuntimeException e) {
            return new ArrayList<Object>();
        }
    }
}
