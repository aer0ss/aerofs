/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.daemon.core.notification.DownloadNotifier.DownloadThrottler;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ElapsedTimer.class)
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestDownloadNotifier extends AbstractTestNotifier
{
    @Spy DownloadThrottler _throttler = new DownloadThrottler();
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
        _downloadNotifier = new DownloadNotifier(_directoryService, _userAndDeviceNames,
                _notificationServer, _throttler);
        _downloadNotifier.filterMeta_(true);
    }
}
