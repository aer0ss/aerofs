/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.shellext;

import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;

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

        UI.rnc().addListener(new IListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType()) {
                case PATH_STATUS:
                    onStatusNotification_(pb.getPathStatus());
                    break;
                case CLEAR_STATUS:
                    _service.notifyClearCache();
                    break;
                default:
                    // no-op
                }
            }
        });
    }

    private void onStatusNotification_(PBPathStatusEvent ev)
    {
        int n = ev.getPathCount();
        assert n == ev.getStatusCount();
        for (int i = 0; i < n; ++i) {
            String path = Path.fromPB(ev.getPath(i)).toAbsoluteString(Cfg.absDefaultRootAnchor());
            _service.notifyPathStatus(path, ev.getStatus(i));
        }
    }
}
