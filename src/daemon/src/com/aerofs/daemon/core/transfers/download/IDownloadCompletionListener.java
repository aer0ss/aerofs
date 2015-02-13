package com.aerofs.daemon.core.transfers.download;

import com.aerofs.ids.DID;
import com.aerofs.lib.id.SOCID;

import java.util.Map;

/**
 * Use this interface to get notified when downloading of a component succeeds or fails.
 */
public interface IDownloadCompletionListener
{
    /**
     * Called following the successful download of socid from didFrom
     * (but this does not indicate all available DIDs have been queried)
     */
    void onPartialDownloadSuccess_(SOCID socid, DID didFrom);

    void onDownloadSuccess_(SOCID socid, DID from);

    /**
     * Called when the download failed with errors recorded for each attempted device
     * @param did2e a map of attempted device to Exception describing the failure
     */
    void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e);

    /**
     * Called when the download failed with a more serious error
     */
    void onGeneralError_(SOCID socid, Exception e);
}
