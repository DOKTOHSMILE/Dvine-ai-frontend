package ai.dvine.dvine1;

/**
 * DVINE 1's endpoint: the one door the app talks to the model through, as it does with an API model or the offline
 * engine. It runs inside the app (no network port is opened), at {@link #ADDRESS}; a message goes in with
 * {@link #chat}, and the answer comes back through the {@link Dvine1.Host} the app plugged in: steps while it
 * thinks, then text, or a function code for the app to carry out.
 */
public final class Endpoint {
    /** The model's address inside the app, shown where a model's endpoint is shown. */
    public static final String ADDRESS = "dvine://dvine1/v1/chat";
    public static final String MODEL = "dvine-1-0.1";

    private final Dvine1 model;

    Endpoint(Dvine1 model) {
        this.model = model;
    }

    /** One user message for the model to answer. */
    public void chat(String message) {
        model.answer(message);
    }

    /** The pilot marked an answer right or wrong. */
    public void feedback(String question, String answer, boolean right) {
        model.rate(question, answer, right);
    }
}
