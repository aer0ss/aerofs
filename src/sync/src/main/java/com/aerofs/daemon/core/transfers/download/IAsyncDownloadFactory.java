package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.tc.Token;
import com.aerofs.ids.DID;
import com.aerofs.lib.id.SOID;

import javax.annotation.Nonnull;
import java.util.Set;

public interface IAsyncDownloadFactory
{
    public IAsyncDownload create_(SOID soid, Set<DID> dids, IDownloadCompletionListener listener,
            @Nonnull Token tk);
}
