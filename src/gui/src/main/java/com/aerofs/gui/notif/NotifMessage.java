package com.aerofs.gui.notif;

public class NotifMessage {
    public final static String INSTALL_SHELLEXT = "INSTALL_SHELLEXT";
    public final static String LAUNCH = "LAUNCH";
    public final static String OPEN = "OPEN";

    private final String _type;
    private final String _payload;

    public NotifMessage(String type) {
        this(type, "");
    }

    public NotifMessage(String type, String payload) {
        _type = type;
        _payload = payload;
    }

    public String getType() {
        return _type;
    }

    public String getPayload() {
        return _payload;
    }
}
