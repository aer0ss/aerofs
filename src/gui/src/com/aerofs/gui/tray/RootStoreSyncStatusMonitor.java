/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.syncstatus.SyncStatusModel;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatusEntry;
import com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBPathStatusEvent;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.swt.widgets.Widget;
import org.slf4j.Logger;

import java.util.Collection;

import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.IN_SYNC;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.OUT_OF_SYNC;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.UNKNOWN;
import static com.google.common.base.Preconditions.checkState;

/**
 * This component doesn't support TeamServer because it doesn't support multi-root.
 */
public class RootStoreSyncStatusMonitor implements IRitualNotificationListener
{
    private static final Logger l = Loggers.getLogger(RootStoreSyncStatusMonitor.class);

    private final Widget _widget;
    private final RitualNotificationClient _rnc;
    private final CfgRootSID _rootSID;
    private final SyncStatusModel _model;

    private RootStoreSyncStatusListener _listener;

    public RootStoreSyncStatusMonitor(Widget widget, RitualNotificationClient rnc,
            IRitualClientProvider provider, CfgLocalUser localUser, CfgRootSID rootSID)
    {
        checkState(!L.isMultiuser());

        _widget = widget;
        _rnc = rnc;
        _rootSID = rootSID;
        _model = new SyncStatusModel(localUser, provider);
    }

    public void start()
    {
        checkState(GUI.get().isUIThread());
        _rnc.addListener(this);
    }

    public void stop()
    {
        checkState(GUI.get().isUIThread());
        _rnc.removeListener(this);
    }

    public void setListener(RootStoreSyncStatusListener listener)
    {
        checkState(GUI.get().isUIThread());
        _listener = listener;
    }

    private void updateStatus(RootStoreSyncStatus status)
    {
        l.debug("updating status to {}", status.name());
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onSyncStatusChanged(status);
    }

    private void scheduleUpdateStatus(final RootStoreSyncStatus status)
    {
        new GUIExecutor(_widget).execute(new Runnable()
        {
            @Override
            public void run()
            {
                l.debug("scheduling update status to {}", status.name());
                updateStatus(status);
            }
        });
    }

    private void getSyncStatus()
    {
        l.debug("calling getSyncStatus()");
        _model.getSyncStatusEntries(Path.root(_rootSID.get()),
                new GUIExecutor(_widget),
                new FutureCallback<Collection<SyncStatusEntry>>()
                {
                    @Override
                    public void onSuccess(Collection<SyncStatusEntry> entries)
                    {
                        l.debug("getSyncStatus() succeeded");
                        updateStatus(getStatus(entries));
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.debug("getSyncStatus() failed");
                        // this is the expected behaviour when server is not available.
                        updateStatus(UNKNOWN);
                    }

                    private RootStoreSyncStatus getStatus(Collection<SyncStatusEntry> entries)
                    {
                        // consider it synced when there's no one else to sync to.
                        if (entries.size() == 0) return IN_SYNC;

                        for (SyncStatusEntry entry : entries) {
                            if (entry._status == SyncStatusModel.SyncStatus.IN_SYNC) return IN_SYNC;
                        }

                        return OUT_OF_SYNC;
                    }
                });
    }

    public interface RootStoreSyncStatusListener
    {
        void onSyncStatusChanged(RootStoreSyncStatus status);
    }

    @Override
    public void onNotificationReceived(PBNotification notification)
    {
        switch (notification.getType()) {
        case PATH_STATUS:
            l.debug("received path status");
            onPathStatusNotification(notification.getPathStatus());
            break;
        case PATH_STATUS_OUT_OF_DATE:
            l.debug("path status out of date");
            getSyncStatus();
            break;
        case ONLINE_STATUS_CHANGED:
            // query sync status when device comes online.
            // because otherwise, we have no idea what the initial sync status is.
            if (notification.getOnlineStatus()) {
                l.debug("device came online");
                getSyncStatus();
            }
            break;
        default:
            // noop
        }
    }

    @Override
    public void onNotificationChannelBroken()
    {
        // noop
    }

    private void onPathStatusNotification(PBPathStatusEvent pbEvent)
    {
        Path rootPath = Path.root(_rootSID.get());

        for (int i = 0; i < pbEvent.getPathCount(); i++) {
            Path path = Path.fromPB(pbEvent.getPath(i));

            if (rootPath.equals(path)) {
                l.debug("root anchor path status updated");
                PBPathStatus pbPathStatus = pbEvent.getStatus(i);
                switch (pbPathStatus.getSync()) {
                case UNKNOWN:
                    scheduleUpdateStatus(UNKNOWN);
                    break;
                case OUT_SYNC:
                    scheduleUpdateStatus(OUT_OF_SYNC);
                    break;
                case PARTIAL_SYNC:
                case IN_SYNC:
                    scheduleUpdateStatus(IN_SYNC);
                    break;
                }
            }
        }
    }
}
