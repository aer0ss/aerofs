/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.notification.UploadNotifier.UploadThrottler;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestUploadNotifier extends AbstractTestNotifier
{
    UploadThrottler _throttler;
    UploadNotifier _uploadNotifier;

    @Override
    protected void teardownImpl()
    {
        _throttler.clear();
    }

    @Override
    protected void disableMetaFilter()
    {
        _uploadNotifier.filterMeta_(false);
    }

    @Override
    protected void performAction(TransferredItem key, TransferProgress value)
    {
        _uploadNotifier.onTransferStateChanged_(key, value);
    }

    @Override
    protected void verifyUntracked()
    {
        verify(_throttler, times(1)).untrack(any(TransferredItem.class));
    }

    @Override
    protected void setUpImpl()
    {
        _throttler = spy(new UploadThrottler(_factTimer));
        _uploadNotifier = new UploadNotifier(_directoryService, _userAndDeviceNames,
                _notificationServer, _throttler);
        _uploadNotifier.filterMeta_(true);
    }
}
