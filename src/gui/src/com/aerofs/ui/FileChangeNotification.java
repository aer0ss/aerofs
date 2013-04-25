package com.aerofs.ui;

import java.util.ArrayList;

import com.aerofs.base.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ICfgDatabaseListener;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.RitualNotificationClient.IListener;

/**
 * This is the class responsible for displaying Growl notifications every time a file gets updated
 */
public class FileChangeNotification
{
    private static final long SHOW_DELAY = 8 * C.SEC;
    private static final long MAX_LINES = 4;

    private final ArrayList<PBPath> _recents = new ArrayList<PBPath>();

    private final IListener _l = new IListener() {
        @Override
        public void onNotificationReceived(PBNotification pb)
        {
            if (pb.getType().equals(Type.DOWNLOAD)) received(pb.getDownload());
        }
    };

    public FileChangeNotification()
    {
        if (Cfg.db().getBoolean(Key.NOTIFY)) UI.rnc().addListener(_l);

        Cfg.db().addListener(new ICfgDatabaseListener() {
            @Override
            public void valueChanged_(Key key)
            {
                if (key != Key.NOTIFY) return;

                if (Cfg.db().getBoolean(Key.NOTIFY)) {
                    UI.rnc().addListener(_l);
                } else {
                    UI.rnc().removeListener(_l);
                }
            }
        });
    }

    private void received(final PBDownloadEvent ev)
    {
        if (ev.getSocid().getCid() == CID.META.getInt() ||
                !ev.hasOkay() || !ev.getOkay() || !ev.hasPath() ||
                UIUtil.isSystemFile(ev.getPath()) ||
                UIUtil.shallHide(ev.getPath())) return;

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
