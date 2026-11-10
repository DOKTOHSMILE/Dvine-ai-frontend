package ai.ibiko.main;

/**
 * The sizing of the type bar and the chat pane. The XML chat screen owns the sizing of everything
 * else; these two views fill their dedicated hosts.
 */
public final class ChatFit {

    /** The activity as the screen: the two views being fitted, and the resources they are measured in. */
    public interface Host {
        /** The message box at the bottom, where the words go. */
        android.view.View typeBar();

        /** The pane of earlier chats. */
        android.view.View chatPane();

        android.content.res.Resources getResources();
    }

    private final Host host;

    public ChatFit(Host host) {
        this.host = host;
    }

    /** The message box fills the bar it is in. */
    public void fitTypeBar() {
        android.view.View typeBar = host.typeBar();
        if (typeBar == null || typeBar.getParent() == null)
            return;
        android.util.DisplayMetrics dm = host.getResources().getDisplayMetrics();
        int margin = Math.round(4 * dm.density);
        android.view.ViewGroup.LayoutParams raw = typeBar.getLayoutParams();
        if (raw instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) raw;
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.setMargins(margin, 0, margin, 0);
            typeBar.setLayoutParams(lp);
        }
    }

    /** The pane of earlier chats fills the screen. */
    public void fitChatPane() {
        android.view.View chatPane = host.chatPane();
        if (chatPane == null || chatPane.getParent() == null)
            return;
        android.view.ViewGroup.LayoutParams raw = chatPane.getLayoutParams();
        raw.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        raw.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        chatPane.setLayoutParams(raw);
    }
}