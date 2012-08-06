package com.aerofs.gui.tray;

import com.aerofs.ui.IUI.MessageType;

public interface IBalloonsImpl {

    // all the methods must be called from within the UI thread

    void add(MessageType mt, String title, String msg, Runnable onClick);

    boolean hasVisibleBalloon();

    void dispose();

}
