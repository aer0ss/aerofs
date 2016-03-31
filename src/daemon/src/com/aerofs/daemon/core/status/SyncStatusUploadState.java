package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.transfers.ITransferStateListener;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * maintains a set SOIDs corresponding to ongoing uploads to team servers.
 *
 * does not support tracking of simultaneous uploads to multiple team servers -
 * the SOID will be removed when the upload to the first team server completes.
 * This is desirable because at that point, a second ongoing team server upload
 * is irrelevant to the sync status classes, so there's no reason to add the
 * additional complexity required for tracking uploads to multiple team servers.
 */
public class SyncStatusUploadState implements ITransferStateListener
{
    private static final Logger logger = Loggers.getLogger(SyncStatusUploadState.class);

    private final UserAndDeviceNames _uadn;
    private final Set<SOID> _uploads;

    @Inject
    public SyncStatusUploadState(UserAndDeviceNames uadn, UploadState uploadState) {
        _uadn = uadn;
        _uploads = ConcurrentHashMap.newKeySet();
        uploadState.addListener_(this);
    }

    @Override
    public void onTransferStateChanged_(TransferredItem item, TransferProgress progress) {
        if (item._socid.cid().isMeta()) return;

        SOID soid = item._socid.soid();

        UserID deviceOwner = getDeviceOwner(item._ep.did());

        if (deviceOwner == null || !deviceOwner.isTeamServerID()) return;

        if (progress._done == progress._total) {
            logger.trace("removing {} from {}", item._ep.did(), soid);
            _uploads.remove(soid);
        } else {
            logger.trace("adding {}", soid);
            _uploads.add(soid);
        }
        logger.trace("current uploads: {}", _uploads.size());
    }

    private UserID getDeviceOwner(DID did) {
        UserID deviceOwner = null;
        try {
            deviceOwner = _uadn.getDeviceOwnerNullable_(did);
            if (deviceOwner == null && _uadn.updateLocalDeviceInfo_(Lists.newArrayList(did))) {
                deviceOwner = _uadn.getDeviceOwnerNullable_(did);
            }
        } catch (ExProtocolError | SQLException e) {
            logger.warn("error determining upload device owner.");
        }
        return deviceOwner;
    }

    public boolean contains(SOID soid) {
        return _uploads.contains(soid);
    }
}
