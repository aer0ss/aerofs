package com.aerofs.gui;

import java.util.Map;

import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent.State;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/*
 * An aggregator of every single transfer events we've received from the daemon
 *
 * This class tracks the latest event from each upload / download and does no filtering.
 * On the other hand, the daemon can be configured to filter certain events.
 */
public class TransferState
{
    // these two fields are protected by synchronized (this)
    private final Map<SOCID, PBDownloadEvent> _dls = Maps.newHashMap();
    private final Table<SOCID, DID, PBUploadEvent> _uls = HashBasedTable.create();

    public TransferState()
    {
    }

    /**
     * Nop if the notification type is not download or upload
     */
    public synchronized void update(PBNotification pb)
    {
        switch (pb.getType()) {
        case DOWNLOAD:
            updateDownloadState_(pb.getDownload());
            break;
        case UPLOAD:
            updateUploadState_(pb.getUpload());
            break;
        default:
            // no-op
        }
    }

    private void updateUploadState_(PBUploadEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());
        DID did = new DID(pb.getDeviceId());

        if (pb.getDone() == pb.getTotal()) _uls.remove(socid, did);
        else _uls.put(socid, did, pb);
    }

    private void updateDownloadState_(PBDownloadEvent pb)
    {
        SOCID socid = new SOCID(pb.getSocid());

        if (pb.getState() == State.ENDED) {
            _dls.remove(socid);
        } else {
            _dls.put(socid, pb);
        }
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Map<SOCID, PBDownloadEvent> downloads_()
    {
        return _dls;
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Table<SOCID, DID, PBUploadEvent> uploads_()
    {
        return _uls;
    }
}
