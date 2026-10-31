package ai.dvine.llm;

import java.util.concurrent.atomic.AtomicInteger;

/** One number per reply, so an old timer cannot stop the reply that replaced it. */
public final class ReplyLease {
    private final AtomicInteger generation = new AtomicInteger();

    public int open() {
        return generation.incrementAndGet();
    }

    public boolean mine(int id) {
        return generation.get() == id;
    }
}
