package ai.dvine.ops.termux;

/** Whether a verified Termux bridge is present. The app does not guess executable paths. */
public final class TermuxCapabilityDetector {
    public interface Probe {
        boolean installed();

        boolean permissionGranted();

        boolean resultBridgeConnected();
    }

    private final Probe probe;

    public TermuxCapabilityDetector(Probe probe) {
        this.probe = probe;
    }

    public boolean installed() {
        return probe != null && probe.installed();
    }

    public boolean canExecuteAndRead() {
        return installed() && probe.permissionGranted() && probe.resultBridgeConnected();
    }

    public String setupAdvice() {
        if (probe == null || !probe.installed()) {
            return "Termux is not installed, so I can't run a terminal command through it.";
        }
        if (!probe.permissionGranted()) {
            return "Termux is installed, but this app is not allowed to run commands in it. Enable the Run command permission in Termux.";
        }
        if (!probe.resultBridgeConnected()) {
            return "Termux can be asked to run a command, but nothing returns its output to me yet, so I won't treat a command as done.";
        }
        return null;
    }
}
