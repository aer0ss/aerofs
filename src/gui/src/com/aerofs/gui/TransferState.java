package com.aerofs.gui;

import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/*
 * An aggregator of every single transfer events we've received from the daemon
 *
 * This class tracks the latest event from each upload / download and does no filtering.
 * On the other hand, the daemon can be configured to filter certain events.
 */
public class TransferState
{
    private final Table<SOCID, DID, PBTransferEvent> _uls = HashBasedTable.create();

    public TransferState()
    {
    }

    /**
     * Nop if the notification type is not download or upload
     */
    public synchronized void update(PBNotification pb)
    {
        if (pb.getType() == Type.TRANSFER) {
            updateTransferState_(pb.getTransfer());
        }
    }

    private void updateTransferState_(PBTransferEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());
        DID did = new DID(pb.getDeviceId());

        if (pb.getDone() == pb.getTotal()) {
            _uls.remove(socid, did);
        } else {
            _uls.put(socid, did, pb);
        }
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Table<SOCID, DID, PBTransferEvent> transfers_()
    {
        return _uls;
    }
}
