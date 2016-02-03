package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.gui.notif.NotifMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.slf4j.Logger;

// this class queues balloons added by add() and display them one after another

public class Balloons
{
    private static final Logger l = Loggers.getLogger(Balloons.class);

    private final IBalloonsImpl _impl;

    Balloons(TrayIcon icon)
    {
        IBalloonsImpl impl = null;
        try {
            impl = OSUtil.isOSXMountainLionOrNewer() ? new BalloonsImplOSX() :
                new BalloonsImplSWT(icon);
        } catch (Exception e) {
            l.error("cannot create balloons: ", e);
        }

        _impl = impl;
    }

    /**
     * All add* methods may be called by non-display threads
     *
     * @param msg
     *            the text to be shown in the balloon
     */
    public void add(MessageType mt, String title, String msg, NotifMessage onClick)
    {
        if (_impl != null) _impl.add(mt, title, msg, onClick);
    }

    public boolean hasVisibleBalloon()
    {
        assert UI.get().isUIThread();
        return _impl != null && _impl.hasVisibleBalloon();
    }

    void dispose()
    {
        if (_impl != null) _impl.dispose();
    }
}
