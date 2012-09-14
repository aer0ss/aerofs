package com.aerofs.daemon.core.net;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;

import java.util.Map;

/**
 * Use this interface to get notified when downloading of a component succeeds or fails.
 */
public interface IDownloadCompletionListener
{
    void okay_(SOCID socid, DID from);

    /**
     * Called when the download failed with errors recorded for each attempted device
     * @param remoteExceptions a map of attempted device to Exception describing the failure
     */
    void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> remoteExceptions);

    /**
     * Called when the download failed with a more serious error
     */
    void onGeneralError_(SOCID socid, Exception e);
}