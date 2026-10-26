package ai.dvine.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the engine is holding in mind right now: a few items, each with a
 * priority and an expiry. When full, the least important item goes. Never an
 * archive: long-term memory is the journal and the server's episode store.
 */
public final class WorkingMemory {
    public static final int CAPACITY = 7;

    private static final class Item {
        final String value;
        final int priority;
        final long expires;

        Item(String value, int priority, long expires) {
            this.value = value;
            this.priority = priority;
            this.expires = expires;
        }
    }

    private final LinkedHashMap<String, Item> items = new LinkedHashMap<String, Item>();

    public void put(String key, String value, int priority, long ttlMs, long now) {
        expire(now);
        items.remove(key);
        if (items.size() >= CAPACITY) {
            String weakest = null;
            int low = Integer.MAX_VALUE;
            for (Map.Entry<String, Item> e : items.entrySet()) {
                if (e.getValue().priority < low) {
                    low = e.getValue().priority;
                    weakest = e.getKey();
                }
            }
            if (low > priority) {
                return; // everything held matters more
            }
            items.remove(weakest);
        }
        items.put(key, new Item(value, priority, now + ttlMs));
    }

    public String get(String key, long now) {
        expire(now);
        Item i = items.get(key);
        return i == null ? null : i.value;
    }

    public int size(long now) {
        expire(now);
        return items.size();
    }

    /** A copy of what is held, oldest first. */
    public Map<String, String> snapshot(long now) {
        expire(now);
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Item> e : items.entrySet()) {
            out.put(e.getKey(), e.getValue().value);
        }
        return out;
    }

    private void expire(long now) {
        items.values().removeIf(i -> now >= i.expires);
    }
}
