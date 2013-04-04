/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.TransferState;
import com.aerofs.gui.transfers.DlgTransfers;
import com.aerofs.lib.DelayedRunner;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent.State;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.ui.UIParam;
import com.google.common.collect.Maps;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.MenuItem;

import java.util.Map;

public class TransferTrayMenuSection
{
    private MenuItem _transferStats1;    // menu item used to display information about
                                         // ongoing transfers - line 1
    private MenuItem _transferStats2;    // menu item used to display information about
                                         // ongoing transfers - line 2
    private Object _transferProgress;    // non-null if transfer is in progress

    private DlgTransfers _transferDialog;
    private AbstractListener _listener = new AbstractListener(null)
    {
        @Override
        protected void handleEventImpl(Event event)
        {
            if (_transferDialog == null) _transferDialog = new DlgTransfers(GUI.get().sh());

            _transferDialog.showSOCID(Util.test(event.stateMask, SWT.SHIFT));

            if (_transferDialog.isDisposed()) {
                _transferDialog.openDialog();
            } else {
                _transferDialog.forceActive();
            }
        }
    };

    private final TrayMenuPopulator _trayMenuPopulator;

    private final Map<Integer, Image> _pieChartCache = Maps.newHashMap();

    // TODO: consolidate the code to use the same TransferState as CompTransfersTable
    private final TransferState _ts = new TransferState();

    public TransferTrayMenuSection(TrayMenuPopulator trayMenuPopulator)
    {
        _trayMenuPopulator = trayMenuPopulator;
    }

    public void populate()
    {
        _transferStats1 = _trayMenuPopulator.addMenuItem("", _listener);
        _transferStats2 = null;
        updateTransferMenus();
    }

    public void updateTransferMenus()
    {
        // Gather the stats about the current downloads and uploads

        int dlCount = 0, ulCount = 0;
        long dlBytesDone = 0, ulBytesDone = 0;
        long dlBytesTotal = 0, ulBytesTotal = 0;

        synchronized (_ts) {
            for (PBDownloadEvent dl : _ts.downloads_().values()) {
                // only aggregate stats from ongoing downloads
                if (dl.getState() == State.ONGOING) {
                    dlCount++;
                    dlBytesDone += dl.getDone();
                    dlBytesTotal += dl.getTotal();
                }
            }

            for (PBUploadEvent ul : _ts.uploads_().values()) {
                // only aggregate stats from started and unfinished uploads
                if (ul.getDone() > 0 && ul.getDone() < ul.getTotal()) {
                    ulCount++;
                    ulBytesDone += ul.getDone();
                    ulBytesTotal += ul.getTotal();
                }
            }
        }
        // If there are both downloads and uploads, create a second MenuItem to display uploads

        MenuItem menuItem;
        if (dlCount > 0 && ulCount > 0) {
            if (_transferStats2 == null) {
                _transferStats2 = _trayMenuPopulator.addMenuItemAfterItem(
                        "", _transferStats1, _listener);
            }
            menuItem = _transferStats2;
        } else {
            if (_transferStats2 != null) {
                _transferStats2.dispose();
                _transferStats2 = null;
            }
            menuItem = _transferStats1;
        }

        // Display the appropriate status in the menu items

        boolean transferring = dlCount != 0 || ulCount != 0;

        if (transferring) {
            showStats(_transferStats1, S.LBL_DOWNLOADING, dlCount, dlBytesDone, dlBytesTotal);
            showStats(menuItem, S.LBL_UPLOADING, ulCount, ulBytesDone, ulBytesTotal);
        } else {
            _transferStats1.setText(S.LBL_NO_ACTIVE_TRANSFERS);
            _transferStats1.setImage(null);
        }

        // Display the progress on the menu icon

        if (transferring) {
            if (_transferProgress == null) {
                _transferProgress = GUI.get().addProgress(S.LBL_TRANSFERRING, false);
            }
        } else {
            if (_transferProgress != null) {
                GUI.get().removeProgress(_transferProgress);
                _transferProgress = null;
            }
        }
    }

    private void showStats(MenuItem menuItem, String action, int count, long done, long total)
    {
        if (count > 0) {
            menuItem.setText(String.format("%s %s file%s... (%s)",
                    action, count, count > 1 ? "s" : "",
                    Util.formatProgress(done, total)));

            if (total > 0) {
                Color bg = Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND);
                Color fg = Display.getCurrent().getSystemColor(SWT.COLOR_TITLE_INACTIVE_FOREGROUND);
                // Swap bg and fg on Windows XP
                if (OSUtil.isWindowsXP()) {
                    Color tmp = bg; bg = fg; fg = tmp;
                }
                menuItem.setImage(Images.getPieChart(
                        done, total, 16, bg, fg, null, _pieChartCache));
            }
        }
    }

    // Member variables related to the ongoing transfer status
    private final DelayedRunner _dr = new DelayedRunner("update-transfers-menu",
            UIParam.SLOW_REFRESH_DELAY, new Runnable() {
        @Override
        public void run()
        {
            if (_transferStats1 != null) {
                GUI.get().safeExec(_transferStats1, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        updateTransferMenus();
                    }
                });
            }
        }
    });

    public void dispose()
    {
        for (Image img : _pieChartCache.values()) {
            img.dispose();
        }
        _pieChartCache.clear();
    }

    public void update(PBNotification pb)
    {
        _ts.update(pb);
        _dr.schedule();
    }
}
