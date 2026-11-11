package ai.ibiko.chat.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import ai.ibiko.R;

/**
 * The download bar on the chat screen, under the header: the model, a clone, or a package. The notification
 * bar still has its own copy. A percent below 0 is a moving bar (git prints nothing until it finishes).
 */
public final class DownloadStrip implements ai.ibiko.net.Transfers.Sink {
    private final Activity activity;
    private View box;
    private ProgressBar bar;
    private TextView titleView;
    private TextView detailView;
    private boolean modelOn;
    private String modelTitle = "";
    private String modelDetail = "";
    private int modelPercent;
    private boolean modelBusy;
    private final Map<String, String[]> transfers = new LinkedHashMap<String, String[]>();

    public DownloadStrip(Activity activity) {
        this.activity = activity;
    }

    public void bind() {
        box = activity.findViewById(R.id.downloadStrip);
        bar = activity.findViewById(R.id.downloadBar);
        titleView = activity.findViewById(R.id.downloadTitle);
        detailView = activity.findViewById(R.id.downloadDetail);
    }

    /** The model download. {@code visible} false hides it; a transfer still on the go stays up. */
    public void model(String title, int percent, boolean busy, String detail, boolean visible) {
        modelOn = visible;
        modelTitle = title == null ? "" : title;
        modelDetail = detail == null ? "" : detail;
        modelPercent = percent;
        modelBusy = busy;
        paint();
    }

    @Override
    public void progress(String key, String title, String line, int percent) {
        transfers.put(key, new String[] {title == null ? "Downloading" : title, line == null ? "" : line, Integer.toString(percent)});
        paint();
    }

    @Override
    public void end(String key, String title, String line, boolean ok) {
        transfers.remove(key);
        paint();
    }

    private void paint() {
        activity.runOnUiThread(() -> {
            if (box == null) {
                return;
            }
            String title;
            String detail;
            int percent;
            boolean busy;
            if (modelOn) {
                title = modelTitle;
                detail = modelDetail;
                percent = modelPercent;
                busy = modelBusy;
            } else if (!transfers.isEmpty()) {
                String[] last = null;
                for (String[] v : transfers.values()) {
                    last = v;
                }
                title = last[0];
                detail = last[1];
                try {
                    percent = Integer.parseInt(last[2]);
                } catch (NumberFormatException e) {
                    percent = -1;
                }
                busy = percent < 0;
            } else {
                box.setVisibility(View.GONE);
                return;
            }
            box.setVisibility(View.VISIBLE);
            if (titleView != null) {
                titleView.setText(percent >= 0 ? title + "  " + percent + "%" : title);
            }
            if (detailView != null) {
                detailView.setText(detail);
                detailView.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (bar != null) {
                if (percent < 0 || busy && percent == 0) {
                    bar.setIndeterminate(true);
                } else {
                    bar.setIndeterminate(false);
                    bar.setProgress(Math.max(0, Math.min(100, percent)));
                }
            }
        });
    }
}
