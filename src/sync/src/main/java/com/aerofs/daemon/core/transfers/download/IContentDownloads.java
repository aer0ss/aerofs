package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SOID;

import java.util.Set;

public interface IContentDownloads {
    boolean downloadAsync_(SOID soid, Set<DID> dids,
                           ITokenReclamationListener continuationCallback,
                           IDownloadCompletionListener completionListener);
}
