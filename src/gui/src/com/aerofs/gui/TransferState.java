package com.aerofs.gui;

import java.util.Map;

import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.google.common.collect.Maps;

public class TransferState
{
    // these two fields are protected by synchronized (this)
    private final Map<SOCKID, PBDownloadEvent> _dls = Maps.newHashMap();
    private final Map<SOCKID, PBUploadEvent> _uls = Maps.newHashMap();

    private final boolean _onlyTrackOngoingDownloads;

    public TransferState(boolean onlyTrackOngoingDownloads)
    {
        _onlyTrackOngoingDownloads = onlyTrackOngoingDownloads;
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
        SOCKID k = new SOCKID(pb.getK());
        if (pb.getDone() == pb.getTotal()) _uls.remove(k);
        else _uls.put(k, pb);
    }

    @SuppressWarnings("fallthrough")
    private void updateDownloadState_(PBDownloadEvent pb)
    {
        switch (pb.getState()) {
        default:
            if (_onlyTrackOngoingDownloads) break;
            // fall through
        case ONGOING:
            _dls.put(new SOCKID(pb.getK()), pb);
            break;
        case ENDED:
            _dls.remove(new SOCKID(pb.getK()));
            break;
        }
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Map<SOCKID, PBDownloadEvent> downloads_()
    {
        return _dls;
    }

    /**
     * N.B. access to the return value must be protected by synchronized (this)
     */
    public Map<SOCKID, PBUploadEvent> uploads_()
    {
        return _uls;
    }
}
