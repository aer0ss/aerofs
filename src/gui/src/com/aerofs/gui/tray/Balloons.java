package com.aerofs.gui.tray;

import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;

// this class queues balloons added by add() and display them one after another

public class Balloons {

    private final IBalloonsImpl _impl;

    Balloons(TrayIcon icon)
    {
        IBalloonsImpl impl = null;
        try {
            impl = OSUtil.isOSX() ? new BalloonsImplGrowl() :
                new BalloonsImplSWT(icon);
        } catch (Exception e) {
            Util.l(this).error("cannot create balloons: " + Util.e(e));
        }

        _impl = impl;
    }

    /**
     * All add* methods may be called by non-display threads
     *
     * @param msg
     *            the text to be shown in the balloon
     * @param icon
     *            can be one of SWT.ICON_ERROR, SWT.ICON_INFORMATION,
     *            and SWT.ICON_WARNING
     */
    public void add(MessageType mt, String title, String msg, Runnable onClick)
    {
        Util.l().warn("add BBLL " + _impl);

        if (_impl != null) _impl.add(mt, title, msg, onClick);
    }

    public boolean isAvailable()
    {
        return _impl != null;
    }

    public boolean hasVisibleBalloon()
    {
        assert UI.get().isUIThread();

        return _impl == null ? false : _impl.hasVisibleBalloon();
    }

    void dispose()
    {
        if (_impl != null) _impl.dispose();
    }
}
