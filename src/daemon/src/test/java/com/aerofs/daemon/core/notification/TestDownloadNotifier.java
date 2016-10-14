/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.notification.DownloadNotifier.DownloadThrottler;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TestDownloadNotifier extends AbstractTestNotifier
{
    DownloadThrottler _throttler;
    DownloadNotifier _downloadNotifier;

    @Override
    protected void teardownImpl()
    {
        _throttler.clear();
    }

    @Override
    protected void disableMetaFilter()
    {
        _downloadNotifier.filterMeta_(false);
    }

    @Override
    protected void performAction(TransferredItem key, TransferProgress value)
    {
        _downloadNotifier.onTransferStateChanged_(key, value);
    }

    @Override
    protected void verifyUntracked()
    {
        verify(_throttler, times(1)).untrack(any(TransferredItem.class));
    }

    @Override
    protected void setUpImpl()
    {
        _throttler = spy(new DownloadThrottler(_factTimer));
        _downloadNotifier = new DownloadNotifier(_directoryService, _userAndDeviceNames,
                _notificationServer, mock(CoreScheduler.class), _throttler);
        _downloadNotifier.filterMeta_(true);
    }
}
