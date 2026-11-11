package ai.ibiko;

import ai.ibiko.stage.StageView;

/** The chat header, the drawer rows and the composer. The activity only opens them. */
final class ChatChrome {
    private final MainActivity a;

    ChatChrome(MainActivity a) {
        this.a = a;
    }

    void bindHeader() {

        android.view.View ivie = a.findViewById(R.id.tabIvie);
        android.view.View obi = a.findViewById(R.id.tabObi);
        android.view.View ibiko = a.findViewById(R.id.tabIbiko);
        if (ivie != null)
            ivie.setOnClickListener(v -> a.selectPersona("ivie"));
        if (obi != null)
            obi.setOnClickListener(v -> a.selectPersona("obi"));
        if (ibiko != null)
            ibiko.setOnClickListener(v -> a.selectPersona("ibiko", true));

        android.view.View menu = a.findViewById(R.id.btnMenu);
        if (menu != null)
            menu.setOnClickListener(v -> a.toggleDrawer());

        android.view.View fresh = a.findViewById(R.id.btnNewChat);
        if (fresh != null)
            fresh.setOnClickListener(v -> a.startNewChatSafely());
        android.view.View drawerNew = a.findViewById(R.id.btnDrawerNew);
        if (drawerNew != null)
            drawerNew.setOnClickListener(v -> a.startNewChatSafely());
        android.view.View workload = a.findViewById(R.id.workload);
        if (workload != null)
            workload.setOnClickListener(v -> {
                if (a.model != null) {
                    ai.ibiko.chat.ui.WorkloadSheet.show(a, a.model, a.brainLabel(), a.ibikoOwn()); // the context window, the agents at work, today
                }
            });
        android.view.View projects = a.findViewById(R.id.btnDrawerProjects);
        if (projects != null)
            projects.setOnClickListener(v -> {
                if (a.chatPane != null) {
                    a.chatPane.openProjects();
                }
            });
        android.view.View workspaceRow = a.findViewById(R.id.btnDrawerWorkspace);
        if (workspaceRow != null)
            workspaceRow.setOnClickListener(v -> {
                a.closeDrawer();
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.WorkspaceActivity.class));
            });
        android.view.View socialRow = a.findViewById(R.id.btnDrawerSocial);
        if (socialRow != null)
            socialRow.setOnClickListener(v -> {
                a.closeDrawer();
                a.startActivityForResult(new android.content.Intent(a, ai.ibiko.chat.ui.SocialActivity.class), a.SOCIAL);
            });
        android.view.View providers = a.findViewById(R.id.btnProviders);
        if (providers != null)
            providers.setOnClickListener(v -> {
                a.closeDrawer();
                a.openProviders();
            });
        android.view.View ibikoMode = a.findViewById(R.id.ibikoMode);
        if (ibikoMode != null)
            ibikoMode.setOnClickListener(v -> a.chooseIbikoMode());
        android.view.View connectors = a.findViewById(R.id.btnConnectors);
        if (connectors != null)
            connectors.setOnClickListener(v -> {
                a.closeDrawer();
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.ConnectorsActivity.class));
            });
        android.view.View settings = a.findViewById(R.id.btnTheme);
        if (settings != null)
            settings.setOnClickListener(v -> {
                a.closeDrawer();
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SettingsActivity.class));
            });
        android.view.View usage = a.findViewById(R.id.btnUsage);
        if (usage != null)
            usage.setOnClickListener(v -> {
                a.closeDrawer();
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.UsageActivity.class));
            });
        android.view.View header = a.findViewById(R.id.tv_header_sub);
        if (header != null)
            header.setOnClickListener(v -> a.modelMenu.showModelSwitch());
        android.view.View manage = a.findViewById(R.id.btnManageChats);
        if (manage != null)
            manage.setOnClickListener(v -> a.openHistory());
        android.view.View scrim = a.findViewById(R.id.drawerScrim);
        if (scrim != null)
            scrim.setOnClickListener(v -> a.closeDrawer());
        // the app opens on an empty chat with Obi (the chats so far are in the menu), not on the last conversation
        if (a.chats != null) {
            a.chats.fresh(StageView.OBI);
        }
        a.selectPersona(StageView.OBI);
        a.bindComposer();
        a.refreshSend();
        a.crash.bind();
        }

    void bindComposer() {

        android.widget.EditText field = a.findViewById(R.id.et_message);
        if (field != null) {
            field.setOnFocusChangeListener((v, focused) -> {
                if (focused && a.drawerOpen()) {
                    a.closeDrawer(); // typing: the drawer folds away, the keyboard has the screen
                }
            });
            field.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!a.dictating() && count > 0 && a.attachments != null
                            && ai.ibiko.chat.Attachments.longPaste(s.subSequence(start, start + count), ai.ibiko.chat.Tuning.PASTE_CHARS.get(a.prefs))) {
                        // a long paste becomes an attachment, as in Claude, instead of filling the box
                        final String pasted = s.subSequence(start, start + count).toString();
                        final int from = start, to = start + count;
                        a.handler.post(() -> {
                            android.text.Editable e = field.getText();
                            if (e != null && to <= e.length()) {
                                e.delete(from, to);
                            }
                            a.attachments.add(new ai.ibiko.chat.ui.ComposerAttachments.Item("text", "Pasted text", null, pasted));
                        });
                    }
                    a.refreshSend();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
        }
        android.widget.HorizontalScrollView attachScroll = a.findViewById(R.id.attach_scroll);
        android.widget.LinearLayout attachRow = a.findViewById(R.id.attach_row);
        if (attachScroll != null && attachRow != null) {
            a.attachments = new ai.ibiko.chat.ui.ComposerAttachments(a, attachScroll, attachRow, a::refreshSend);
        }
        android.view.View send = a.findViewById(R.id.btn_send);
        android.view.View voice = a.findViewById(R.id.btn_voice);
        a.bindQuickRow();
        a.bindThink(); // Tools lives in the chip row now: one tools button, not two
        if (field != null) {
            field.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    a.sendComposer();
                    return true;
                }
                return false;
            });
        }
        if (send != null) {
            send.setOnClickListener(v -> a.sendComposer());
        }
        if (voice != null) {
            voice.setOnClickListener(v -> a.toggleDictation());
            voice.setOnLongClickListener(v -> {
                if (a.assistant == null) {
                    a.note("Command mode is not ready yet.");
                    return true;
                }
                a.commandSession = a.assistant.commandGateway().beginVoiceCommandMode();
                a.note("Command mode is on for the next message. It can pause, resume or cancel one task.");
                return true;
            });
        }

        a.wireStarter(R.id.chip_explain, "Explain this in plain words");
        a.wireStarter(R.id.chip_draw, "Draw me a bicycle");
        a.wireStarter(R.id.chip_crash, "What can you do from this chat?");
        }
}
