package com.aerofs.gui.tray;
import com.aerofs.base.Loggers;
import  com.aerofs.growl.*;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.aerofs.labeling.L;
import com.aerofs.lib.Param;
import org.slf4j.Logger;
import com.aerofs.gui.Images;
import com.aerofs.base.C;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Util;
import com.aerofs.ui.IUI.MessageType;

public class BalloonsImplGrowl implements IBalloonsImpl {
    private static final Logger l = Loggers.getLogger(Balloons.class);
    private final GrowlApplicationBridge _growl = new GrowlApplicationBridge(L.product());
    private final NotificationType _gInfo = new NotificationType(L.product() + " Notifications");
    private final NotificationType _gWarn = new NotificationType(L.product() + " Errors");

    BalloonsImplGrowl() throws IOException
    {
        RenderedImage aeroIcon = ImageIO.read(new File(AppRoot.abs() + Param.ICONS_DIR + Images.ICON_LOGO32));
        RenderedImage aeroIconError = ImageIO.read(new File(AppRoot.abs() + Param.ICONS_DIR + Images.ICON_LOGO32_ERROR));

        _growl.setDefaultIcon(aeroIcon);
        _gInfo.setDefaultIcon(aeroIcon);
        _gWarn.setDefaultIcon(aeroIconError);

        try {
            _growl.registerNotifications(_gInfo, _gWarn);
        } catch (UnsatisfiedLinkError e) {
            l.warn("Failed to load the Growl library: " + Util.e(e));
        }
    }

    @Override
    public void add(MessageType mt, String title, String msg, Runnable onClick)
    {
        l.info("adding balloon \"" + title + ": " + msg + "\"");

        Notification n = new Notification(mt == MessageType.INFO ? _gInfo : _gWarn)
                .setTitle(title)
                .setMessage(msg)
                .setClickedCallback(onClick);

        _growl.notify(n);
    }

    @Override
    public boolean hasVisibleBalloon()
    {
        return _growl.timeSinceLastNotification() < 5 * C.SEC;
    }

    @Override
    public void dispose()
    {
        _growl.release();
    }
}
