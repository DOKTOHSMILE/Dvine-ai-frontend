package ai.ibiko;

import ai.ibiko.turn.LocalTurn;

/** What the pilot says about the device's own model: download it, remove it, turn it on or off, ask its status. */
final class ModelWords {
    private final MainActivity a;

    ModelWords(MainActivity a) {
        this.a = a;
    }

    /** The words about the device's own model: download, remove, turn on or off, its status. */
    boolean handle(String said) {
        if (a.model == null) {
            return false;
        }
        boolean yes = said
                .matches("^(yes|yeah|yep|ok|okay|sure|go ahead|start|do it|please do)( please)?$");
        boolean no = said.matches("^(no|nope|not now|cancel|don't|do not|leave it)( please)?$");
        if (a.confirmModelDownload && (yes || no)) {
            a.confirmModelDownload = false;
            if (yes) {
                a.startModelDownload();
            } else {
                a.play(LocalTurn.speakAs(a.stage.agent(),
                        "Okay, not now. You can download it from AI providers in the menu whenever you're ready."), null);
            }
            return true;
        }
        if (a.confirmModelRemove && (yes || no)) {
            a.confirmModelRemove = false;
            if (!yes) {
                a.play(LocalTurn.speakAs(a.stage.agent(), "Okay, I'll keep it."), null);
                return true;
            }
            final String agent = a.stage.agent();
            a.hub.worker.execute(() -> { // the model's own thread: removing it may wait for a reply to stop
                final boolean gone = a.model.uninstall();
                a.handler.post(() -> a.play(LocalTurn.speakAs(agent, gone ? "Done. The model is removed; I'm on my offline notes again."
                        : "I couldn't delete the model file."), null));
            });
            return true;
        }
        a.confirmModelDownload = false;
        a.confirmModelRemove = false;
        String thing = "(your |the |a |my )?(brain|language model|llm|ai model|offline model|model)";
        boolean ivie = a.stage.isIvie();
        if (said.matches(
                "^(please )?(download|install|get|add|set up)( me)? " + thing + "( please)?$")
                || said.matches("^(upgrade your brain|make yourself smarter|get smarter)$")) {
            if (a.model.state() == ai.ibiko.llm.LocalModel.State.UNSUPPORTED) {
                a.play(LocalTurn.speakAs(ivie,
                        "The model can't run on this device: " + a.model.problem() + "."), null);
            } else if (a.model.installed()) {
                a.play(LocalTurn.speakAs(ivie,
                        a.model.localReady() ? "The model is already installed and running."
                                : "It's already installed. Starting it now."),
                        null);
                if (!a.model.localReady()) {
                    a.loadModel(true);
                }
            } else if (a.model.state() == ai.ibiko.llm.LocalModel.State.DOWNLOADING) {
                a.play(LocalTurn.speakAs(ivie,
                        "It's downloading now. I'll tell you when it's ready."), null);
            } else {
                // a button, not a spoken yes: the setup screen has the download and its progress
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SetupActivity.class));
            }
            return true;
        }
        if (said.matches("^(cancel|stop|pause) (the |that |my )?download$")) {
            return false; // naming the download is not authorization to stop it
        }
        if (said.matches("^(continue|resume|restart|carry on with) (the |my )?download$")) {
            if (a.model.installed()) {
                a.play(LocalTurn.speakAs(ivie, "It's already downloaded."), null);
            } else {
                a.startModelDownload();
            }
            return true;
        }
        String dl = "(the |my |your )?(download|downloading)( progress| bar| status)?";
        if (said.matches("^(show|display|open)( me)? " + dl + "$")
                || said.matches("^(show|display)( me)? (the )?progress( bar)?$")) {
            a.downloadBarHidden = false;
            ai.ibiko.llm.DownloadStatus st = a.hub.status;
            a.stage.setDownload(true, "Downloading the model",
                    a.model.installed() ? 100 : st.percent(),
                    st.phase() == ai.ibiko.llm.DownloadStatus.Phase.DOWNLOADING,
                    a.model.installed() ? "installed"
                            : st.phase() == ai.ibiko.llm.DownloadStatus.Phase.IDLE
                                    ? "not started: AI providers in the menu"
                                    : st.detail());
            if (a.downloadStrip != null) {
                a.downloadStrip.model("Downloading the model", a.model.installed() ? 100 : st.percent(),
                        st.phase() == ai.ibiko.llm.DownloadStatus.Phase.DOWNLOADING, st.detail(), true);
            }
            a.play(LocalTurn.speakAs(ivie,
                    a.model.installed() ? "The model is already downloaded." : st.spoken()),
                    null);
            return true;
        }
        if (said.matches("^(hide|close|remove)( the)? " + dl + "$")) {
            a.downloadBarHidden = true;
            a.stage.setDownload(false, "", 0, false, "");
            if (a.downloadStrip != null) {
                a.downloadStrip.model("", 0, false, "", false);
            }
            a.play(LocalTurn.speakAs(ivie,
                    "Hidden. It keeps downloading; ask me how far it is any time."), null);
            return true;
        }
        if (said.matches(
                "^(how('s| is) (the |my )?download( going)?|how far (is|along is) (the |my )?download|(what('s| is) the )?download (progress|status)|is (the |my )?download (done|finished|complete))$")) {
            a.play(LocalTurn.speakAs(ivie,
                    a.model.installed() ? "The download is finished. The model is installed."
                            : a.hub.status.spoken()),
                    null);
            return true;
        }
        if (said.matches("^(delete|remove|uninstall) " + thing + "$")) {
            if (!a.model.installed()) {
                a.play(LocalTurn.speakAs(ivie, "The model isn't installed."), null);
            } else {
                a.confirmModelRemove = true;
                a.play(LocalTurn.speakAs(ivie, "Remove the model and free "
                        + a.model.spec.sizeWords() + "? Say yes or no."), null);
            }
            return true;
        }
        if (said.matches("^(turn off|disable|stop using|switch off) " + thing + "$")) {
            a.prefs.edit().putBoolean("llm_on", false).apply();
            a.play(LocalTurn.speakAs(ivie, "Okay. I'll answer from my offline notes only."), null);
            return true;
        }
        if (said.matches("^(turn on|enable|use|switch on|start) " + thing + "( again)?$")) {
            a.prefs.edit().putBoolean("llm_on", true).apply();
            if (a.model.installed() && !a.model.localReady()) {
                a.loadModel(true);
            } else {
                a.play(LocalTurn
                        .speakAs(ivie,
                                a.model.localReady() || a.model.remoteOn() ? "My model is on."
                                        : "It isn't installed yet. Download it from AI providers in the menu."),
                        null);
            }
            return true;
        }
        if (said.matches("^(is|are) " + thing
                + " (installed|ready|on|working)$|^(language model|model|brain) status$")) {
            String line;
            switch (a.model.state()) {
                case READY:
                    line = "The model is running on this device, offline.";
                    break;
                case LOADING:
                    line = "It's starting up now.";
                    break;
                case DOWNLOADING:
                    line = "It's downloading.";
                    break;
                case INSTALLED:
                    line = "It's installed but switched off. Say turn on the model.";
                    break;
                case UNSUPPORTED:
                    line = "This device can't run it: " + a.model.problem() + ".";
                    break;
                case FAILED:
                    line = "It's installed but didn't start: " + a.model.problem() + ".";
                    break;
                default:
                    line = "Not installed yet: download it from AI providers in the menu.";
            }
            a.play(LocalTurn.speakAs(ivie, line), null);
            return true;
        }
        return false;
    }
}
