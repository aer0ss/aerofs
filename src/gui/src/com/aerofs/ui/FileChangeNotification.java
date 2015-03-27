package com.aerofs.ui;

import com.aerofs.base.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgKey;
import com.aerofs.lib.cfg.ICfgDatabaseListener;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.IUI.MessageType;

import java.util.ArrayList;

import static com.aerofs.lib.cfg.CfgDatabase.NOTIFY;

/**
 * This is the class responsible for displaying Growl notifications every time a file gets updated
 */
public class FileChangeNotification
{
    private static final long SHOW_DELAY = 8 * C.SEC;
    private static final long MAX_LINES = 4;

    private final ArrayList<PBPath> _recents = new ArrayList<PBPath>();

    private final IRitualNotificationListener _l = new IRitualNotificationListener() {
        @Override
        public void onNotificationReceived(PBNotification pb)
        {
            if (pb.getType().equals(Type.TRANSFER)) received(pb.getTransfer());
        }

        @Override
        public void onNotificationChannelBroken()
        {
            // noop
        }
    };

    public FileChangeNotification()
    {
        if (Cfg.db().getBoolean(NOTIFY)) UIGlobals.rnc().addListener(_l);

        Cfg.db().addListener(new ICfgDatabaseListener() {
            @Override
            public void valueChanged_(CfgKey key)
            {
                if (!key.keyString().equals(NOTIFY.keyString())) return;

                if (Cfg.db().getBoolean(NOTIFY)) {
                    UIGlobals.rnc().addListener(_l);
                } else {
                    UIGlobals.rnc().removeListener(_l);
                }
            }
        });
    }

    private void received(final PBTransferEvent ev)
    {
        if (ev.getUpload()
                || ev.getSocid().getCid() == CID.META.getInt()
                || !ev.hasPath() || ev.getDone() != ev.getTotal()
                || (ev.hasFailed() && ev.getFailed())
                || UIUtil.isSystemFile(ev.getPath())
                || UIUtil.shallHide(ev.getPath())) return;

        if (ev.getPath().getElemCount() == 0) return;

        UI.get().asyncExec(new Runnable() {
            @Override
            public void run()
            {
                _recents.add(_recents.size(), ev.getPath());

                if (_recents.size() == 1) {
                    if (!UI.get().hasVisibleNotifications()) {
                        show();
                    } else {
                        UI.get().timerExec(SHOW_DELAY, new Runnable() {
                            @Override
                            public void run()
                            {
                                show();
                            }
                        });
                    }
                } else {
                    // a timer is already set
                }
            }
        });
    }

    private void show()
    {
        assert UI.get().isUIThread();

        String title = UIUtil.prettyLabelWithCount(_recents.size(),
                "A file was " + S.MODIFIED, "files were " + S.MODIFIED);

        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (PBPath path : _recents) {
            String name = path.getElem(path.getElemCount() - 1);
            if (cnt++ == 0) {
                sb.append(name);
                if (UI.get().areNotificationsClickable()) {
                    sb.append(" (click to view)");
                }
            } else if (_recents.size() != MAX_LINES && cnt == MAX_LINES) {
                sb.append("\n");
                sb.append(_recents.size() - MAX_LINES + 1);
                sb.append(" more...");
                break;
            } else {
                sb.append("\n");
                sb.append(name);
            }
        }
        String msg = sb.toString();

        final String path = UIUtil.absPathNullable(Path.fromPB(_recents.get(0)));
        if (path != null) {
            UI.get().notify(MessageType.INFO, title, msg, new Runnable() {
                @Override
                public void run()
                {
                    OSUtil.get().showInFolder(path);
                }
            });
        }

        _recents.clear();
    }
}
