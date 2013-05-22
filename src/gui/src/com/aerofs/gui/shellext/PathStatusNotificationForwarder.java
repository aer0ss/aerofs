/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.shellext;

import com.aerofs.lib.Path;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;

/**
 * This class listens to path status notifications from the daemon and forwards them to the shell
 * extension
 */
public class PathStatusNotificationForwarder
{
    private final ShellextService _service;

    PathStatusNotificationForwarder(ShellextService service)
    {
        _service = service;

        UI.rnc().addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType()) {
                case PATH_STATUS:
                    onStatusNotification(pb.getPathStatus());
                    break;
                case PATH_STATUS_OUT_OF_DATE:
                    _service.notifyClearCache();
                    break;
                default:
                    // no-op
                }
            }

            @Override
            public void onNotificationChannelBroken()
            {
                // noop
            }
        });
    }

    private void onStatusNotification(PBPathStatusEvent ev)
    {
        int n = ev.getPathCount();
        assert n == ev.getStatusCount();
        for (int i = 0; i < n; ++i) {
            String path = UIUtil.absPathNullable(Path.fromPB(ev.getPath(i)));
            if (path != null) _service.notifyPathStatus(path, ev.getStatus(i));
        }
    }
}
