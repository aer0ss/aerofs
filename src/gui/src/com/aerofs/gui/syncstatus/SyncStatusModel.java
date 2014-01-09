/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Ritual.GetSyncStatusReply;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.aerofs.proto.Ritual.PBSyncStatus.Status;
import com.aerofs.ritual.IRitualClientProvider;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

public class SyncStatusModel
{
    private final CfgLocalUser _localUser;
    private final IRitualClientProvider _provider;

    public SyncStatusModel(CfgLocalUser localUser, IRitualClientProvider provider)
    {
        _localUser = localUser;
        _provider = provider;
    }

    /**
     * throw ExServerUnavailable if the sync status server or the daemon is unavailable.
     */
    public void getSyncStatusEntries(Path path, Executor executor,
            final FutureCallback<Collection<SyncStatusEntry>> callback)
    {
        Futures.addCallback(_provider.getNonBlockingClient().getSyncStatus(path.toPB()),
                new FutureCallback<GetSyncStatusReply>()
                {
                    @Override
                    public void onSuccess(GetSyncStatusReply reply)
                    {
                        if (!reply.getIsServerUp()) {
                            onFailure(new ExServerUnavialable());
                            return;
                        }

                        List<SyncStatusEntry> entries = newArrayListWithCapacity(
                                reply.getStatusCount());

                        for (PBSyncStatus pbStatus : reply.getStatusList()) {
                            entries.add(createEntryFromPB(pbStatus));
                        }

                        callback.onSuccess(entries);
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        callback.onFailure(throwable);
                    }
                }, executor);
    }

    private SyncStatusEntry createEntryFromPB(PBSyncStatus pb)
    {
        return new SyncStatusEntry(pb.getUserName(),
                pb.hasDeviceName() ? pb.getDeviceName() : "Unknown Device",
                createSyncStatusFromPB(pb.getStatus()));
    }

    private SyncStatus createSyncStatusFromPB(Status status)
    {
        switch (status) {
        case IN_SYNC:       return SyncStatus.IN_SYNC;
        case IN_PROGRESS:   return SyncStatus.IN_PROGRESS;
        case OFFLINE:       return SyncStatus.OFFLINE;
        }

        throw new IllegalArgumentException("Unsupported sync status value.");
    }

    public enum SyncStatus
    {
        IN_SYNC, IN_PROGRESS, OFFLINE
    }

    public class SyncStatusEntry
    {
        private final String        _username;
        private final String        _deviceName;
        public  final SyncStatus    _status;

        private SyncStatusEntry(String username, String deviceName, SyncStatus status)
        {
            _username = username;
            _deviceName = deviceName;
            _status = status;
        }

        public boolean isLocalUser()
        {
            return _localUser.get().getString().equals(_username);
        }

        public String getDisplayName()
        {
            return isLocalUser() ? _deviceName : _username;
        }
    }

    public static class ExServerUnavialable extends Exception
    {
        private static final long serialVersionUID = 0L;
    }
}
