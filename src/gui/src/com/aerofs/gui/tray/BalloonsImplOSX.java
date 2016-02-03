package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.gui.notif.NotifMessage;
import com.aerofs.labeling.L;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import com.aerofs.ui.IUI.MessageType;
import org.slf4j.Logger;

public class BalloonsImplOSX implements IBalloonsImpl
{
    private static final Logger l = Loggers.getLogger(BalloonsImplOSX.class);

    BalloonsImplOSX()
    {}

    @Override
    public void add(MessageType mt, String title, String msg, NotifMessage onClick)
    {
        String onClickMessage = null;
        if (onClick != null) {
            onClickMessage = onClick.getType() + ":" + onClick.getPayload();
        }

        Driver.scheduleNotification(null, L.product(), title.equals(L.product()) ? "" : title,
                                    msg, 0.0, onClickMessage);
    }

    @Override
    public boolean hasVisibleBalloon()
    {
        return false;
    }

    @Override
    public void dispose()
    {}
}
