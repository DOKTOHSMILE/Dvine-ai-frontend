package ai.ibiko.llm;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;

/** One reading of the phone for {@link DeviceGuard}: Android's thermal status, the battery's temperature, level and charging. */
public final class DeviceReadings {
    private DeviceReadings() {
    }

    /** -1 where Android doesn't say. Cheap: the battery's last broadcast is kept by the system and only read here. */
    public static DeviceGuard.Reading read(Context c) {
        int status = -1;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                status = pm.getCurrentThermalStatus();
            }
        } catch (RuntimeException ignored) {
            // no thermal service on this phone: battery temperature alone
        }
        int tenths = -1, pct = -1;
        boolean charging = false;
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                tenths = t == Integer.MIN_VALUE || t <= 0 ? -1 : t;
                int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                pct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
                int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        || st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (RuntimeException ignored) {
            // no battery broadcast: heat from the thermal status alone
        }
        return new DeviceGuard.Reading(System.currentTimeMillis(), status, tenths, pct, charging);
    }
}
