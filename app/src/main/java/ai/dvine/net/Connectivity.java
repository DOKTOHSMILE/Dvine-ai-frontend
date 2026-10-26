package ai.dvine.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

/** Reports real, validated internet. The badge and the route both read this. */
public final class Connectivity {
    public interface Listener {
        void onOnline(boolean online);
    }

    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean online;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            publish(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        }

        @Override
        public void onLost(Network network) {
            publish(false);
        }
    };
    private boolean registered;

    public Connectivity(Context context, Listener listener) {
        cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    public boolean isOnline() {
        return online;
    }

    public void start() {
        if (registered || cm == null) {
            return;
        }
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        listener.onOnline(online);
        cm.registerDefaultNetworkCallback(callback);
        registered = true;
    }

    public void stop() {
        if (registered && cm != null) {
            cm.unregisterNetworkCallback(callback);
            registered = false;
        }
    }

    private void publish(final boolean value) {
        if (value == online) {
            return;
        }
        online = value;
        main.post(() -> listener.onOnline(value));
    }
}
