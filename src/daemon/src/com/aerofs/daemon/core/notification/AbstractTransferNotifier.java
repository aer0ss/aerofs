/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.transfers.ITransferStateListener;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.proto.RitualNotifications.PBTransportMethod;
import com.aerofs.ritual_notification.RitualNotificationServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collections;

import static com.aerofs.proto.RitualNotifications.PBNotification.Type.TRANSFER;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Base class for transfer state listeners emitting Ritual notifications
 */
abstract class AbstractTransferNotifier implements ITransferStateListener
{
    protected final Logger l = Loggers.getLogger(getClass());

    private final DirectoryService _ds;
    private final UserAndDeviceNames _nr;
    private final RitualNotificationServer _rns;
    private final CoreScheduler _sched;

    private boolean _filterMeta = true;

    protected AbstractTransferNotifier(DirectoryService ds, UserAndDeviceNames nr,
                                       RitualNotificationServer rns, CoreScheduler sched)
    {
        _ds = ds;
        _nr = nr;
        _rns = rns;
        _sched = sched;
    }

    public final void filterMeta_(boolean enable)
    {
        _filterMeta = enable;
    }

    /**
     *
     * @param item component being transferred
     * @param progress how complete the transfer is
     * @param forceNotificationGeneration
     * @return null if no notification should be sent, a valid {@link PBNotification} message if a notification should be sent
     */
    protected abstract @Nullable PBNotification createTransferNotification_(TransferredItem item, TransferProgress progress, boolean forceNotificationGeneration);

    @Override
    public void onTransferStateChanged_(TransferredItem item, TransferProgress progress)
    {
        if (_filterMeta && item._socid.cid().isMeta()) return;

        PBNotification notification = createTransferNotification_(item, progress, false);
        if (notification != null) _rns.getRitualNotifier().sendNotification(notification);
    }

    /**
     * Always send a transfer notification for a given tuple (item, progress)
     * @param item {@link TransferredItem} for which the notification should be sent
     * @param progress {@link TransferProgress} progress for this item
     */
    void sendTransferNotification_(TransferredItem item, TransferProgress progress)
    {
        PBNotification notification = createTransferNotification_(item, progress, true);
        _rns.getRitualNotifier().sendNotification(checkNotNull(notification));
    }

    private static PBTransportMethod formatTransportMethod(ITransport transport)
    {
        if (transport instanceof TCP) {
            return PBTransportMethod.TCP;
        } else if (transport instanceof Zephyr) {
            return PBTransportMethod.ZEPHYR;
        } else if (transport == null) {
            return PBTransportMethod.NOT_AVAILABLE;
        } else {
            return PBTransportMethod.UNKNOWN;
        }
    }

    /**
     * Resolve the DID into either username or device name depending on if the owner
     *   is the local user. It will make SP calls to update local database if necessary,
     *   and it will return an user friends default label if it's unable to resolve the DID
     *   to a name.
     *
     * N.B. S.LBL_UNKNOWN_USER and S.LBL_UNKNOWN_DEVICE should have already included
     *   custom prefix/suffix so we should not format them again.
     *
     * @return an user-friendly display name based on:
     *   - the device name if the owner of the device is the local user.
     *   - the username of the owner if the owner of the device is not hte local user.
     *   - a default display name if we are unable to resolve the owner or the device.
     */
    private String formatDisplayName_(DID did)
    {
        try {
            UserID owner = _nr.getDeviceOwnerNullable_(did);

            if (owner == null) {
                scheduleUpdateDeviceInfo_(did);
                return S.LBL_UNKNOWN_USER;
            } else if (_nr.isLocalUser(owner)) {
                String devicename = _nr.getDeviceNameNullable_(did);

                if (devicename == null) scheduleUpdateDeviceInfo_(did);

                return "My " + (devicename != null ? devicename : "computer");
            } else {
                FullName username = _nr.getUserNameNullable_(owner);
                if (username == null) scheduleUpdateDeviceInfo_(did);

                return (username == null ? owner.getString() : username.getString())
                        + "'s computer";
            }
        } catch (Exception ex) {
            l.warn("Failed to lookup display name for {}", did, ex);
            return S.LBL_UNKNOWN_USER;
        }
    }

    private void scheduleUpdateDeviceInfo_(DID did) {
        _sched.schedule_(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                try {
                    // NB: this release the core lock around a call to SP
                    // hence it must be scheduled as a core event or notification reordering may
                    // happen, which could, for instance, cause stale transfers to linger in the
                    // transfer dialog forever.
                    _nr.updateLocalDeviceInfo_(Collections.singletonList(did));
                } catch (Exception e) {}
            }
        });
    }

    private PBTransferEvent newTransferEvent_(TransferredItem item, TransferProgress progress, boolean isUpload)
    {
        SOCID socid = item._socid;
        DID did = item._ep.did();

        PBSOCID pbsocid = PBSOCID
                .newBuilder()
                .setSidx(socid.sidx().getInt())
                .setOid(BaseUtil.toPB(socid.oid()))
                .setCid(socid.cid().getInt())
                .build();

        PBTransferEvent.Builder transferEventBuilder = PBTransferEvent
                .newBuilder()
                .setUpload(isUpload)
                .setSocid(pbsocid)
                .setDeviceId(BaseUtil.toPB(did))
                .setDisplayName(formatDisplayName_(did))
                .setDone(progress._done)
                .setTotal(progress._total)
                .setFailed(progress._failed)
                .setTransport(formatTransportMethod(item._ep.tp()));

        try {
            Path path = _ds.resolveNullable_(socid.soid());
            if (path != null) transferEventBuilder.setPath(path.toPB());
        } catch (SQLException e) {
            l.warn("err resolve path for transfer state socid:{} err:{}", socid, e);
        }

        return transferEventBuilder.build();
    }

    protected final PBNotification newTransferNotification_(TransferredItem item, TransferProgress progress, boolean isUpload)
    {
        l.debug("transfer notif: {} {} {} {}/{}",
                item._socid, isUpload ? "to" : "from", item._ep, progress._done, progress._total);
        return PBNotification
                .newBuilder()
                .setType(TRANSFER)
                .setTransfer(newTransferEvent_(item, progress, isUpload))
                .build();
    }
}
