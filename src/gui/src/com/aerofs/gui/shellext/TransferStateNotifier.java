package com.aerofs.gui.shellext;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import com.google.common.collect.Maps;
import java.util.Map;

/**
 * This class listens to upload / download notifications from the daemon and tells the shell
 * extension about them.
 */
public class TransferStateNotifier
{
    // these two fields should be accessed only by the notification thread
    private final Map<SOCKID, String> _dlMap = Maps.newHashMap(); // maps sockid -> file path
    private final Map<SOCKID, String> _ulMap = Maps.newHashMap(); // maps sockid -> file path

    private final ShellextService _service;

    TransferStateNotifier(ShellextService service)
    {
        _service = service;

        UI.rnc().addListener(new IListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType()) {
                case DOWNLOAD:
                    onDownloadNotification_(pb.getDownload());
                    break;
                case UPLOAD:
                    onUploadNotification_(pb.getUpload());
                    break;
                default:
                    // no-op
                }
            }
        });
    }

    private void onDownloadNotification_(PBDownloadEvent ev)
    {
        SOCKID k = new SOCKID(ev.getK());
        String path = Cfg.absRootAnchor() + FSIUtil.toString(ev.getPath());

        if (!_dlMap.containsKey(k)) {
            if (ev.getState() == PBDownloadEvent.State.ONGOING) {
                // This is a new download
                _dlMap.put(k, path);
                _service.notifyDownload(path, true); // no-op if path is empty
            }
        } else {
            String previousPath = _dlMap.get(k);
            if (!path.equals(previousPath)) {
                // The path has changed. We must clear the Downloading flag for the previous path
                _service.notifyDownload(previousPath, false);
                _dlMap.put(k, path);
                _service.notifyDownload(path, true);
            }
            if (ev.getState() == PBDownloadEvent.State.ENDED) {
                _service.notifyDownload(path, false);
                _dlMap.remove(k);
            }
        }
    }

    private void onUploadNotification_(PBUploadEvent ev)
    {
        SOCKID k = new SOCKID(ev.getK());
        String path = Cfg.absRootAnchor() + FSIUtil.toString(ev.getPath());

        if (!_ulMap.containsKey(k)) {
            if (ev.getTotal() > 0 && ev.getDone() > 0 && ev.getDone() != ev.getTotal()) {
                // This is a new upload
                _ulMap.put(k, path);
                _service.notifyUpload(path, true);
                return;
            }
        } else {
            String previousPath = _ulMap.get(k);
            if (!path.equals(previousPath)) {
                // The path has changed. We must clear the Uploading flag for the previous path
                _service.notifyUpload(previousPath, false);
                _ulMap.put(k, path);
                _service.notifyUpload(path, true);
            }
            if (ev.getDone() == ev.getTotal()) {
                // The upload finished
                _service.notifyUpload(path, false);
                _ulMap.remove(k);
            }
        }
    }
}
