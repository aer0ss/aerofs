package com.aerofs.daemon.core.transfers.upload;

// see ITransferListener for valid state transitions

import com.aerofs.daemon.core.transfers.BaseTransferState;

/**
 * TransferState for uploads (use subclass for DI)
 */
public class UploadState extends BaseTransferState
{
    public UploadState() { }
}
