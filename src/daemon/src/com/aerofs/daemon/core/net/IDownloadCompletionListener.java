package com.aerofs.daemon.core.net;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;

/**
 * Use this interface to get notified when downloading of a component succeeds or fails.
 */
public interface IDownloadCompletionListener
{
    /**
     * This method is called only after the downloaded version has dominated the known version,
     * i.e., no KML version is left behind.
     */
    void okay_(SOCID socid, DID from);

    void error_(SOCID socid, Exception e);
}