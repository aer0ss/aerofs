/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.TransferState;
import com.aerofs.gui.TransferState.ITransferStateChangedListener;
import com.aerofs.gui.transfers.DlgTransfers;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.google.common.collect.Maps;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import java.util.Map;

public class TransferTrayMenuSection extends DynamicTrayMenuComponent
        implements ITransferStateChangedListener
{
    private MenuItem _transferStats1;    // menu item used to display information about
                                         // ongoing transfers - line 1
    private MenuItem _transferStats2;    // menu item used to display information about
                                         // ongoing transfers - line 2
    private String _transferProgress;    // non-null if transfer is in progress
    private Menu _lastMenu;

    private AbstractListener _handleClick = new AbstractListener()
    {
        private DlgTransfers dialog;

        @Override
        protected void handleEventImpl(Event event)
        {
            if (dialog == null) {
                dialog = new DlgTransfers(GUI.get().sh());
                dialog.setTransferState(_ts);
            }

            boolean enableDeveloperMode = Util.test(event.stateMask, SWT.SHIFT);

            dialog.showSOCID(enableDeveloperMode);
            dialog.showDID(enableDeveloperMode);

            if (dialog.isDisposed()) {
                dialog.openDialog();
            } else {
                dialog.forceActive();
            }
        }
    };

    private final Map<Integer, Image> _pieChartCache = Maps.newHashMap();

    private final TransferState _ts;

    public TransferTrayMenuSection(TransferState ts)
    {
        _ts = ts;
        _ts.addListener(this);
    }

    @Override
    public void populateMenu(Menu menu)
    {
        _lastMenu = menu;
        TrayMenuPopulator p = new TrayMenuPopulator(_lastMenu);
        _transferStats1 = p.addMenuItem("Transfers...", _handleClick);
        _transferStats2 = null;
        updateTransferMenus(p);
    }

    @Override
    public void updateInPlace()
    {
        if (_lastMenu != null) {
            updateTransferMenus(new TrayMenuPopulator(_lastMenu));
        }
    }

    private class TransferStats {
        int ulCount;
        long ulBytesDone;
        long ulBytesTotal;
        int dlCount;
        long dlBytesDone;
        long dlBytesTotal;
        public boolean transferring() {
            return dlCount != 0 || ulCount != 0;
        }
    }

    public void updateTransferMenus(TrayMenuPopulator p)
    {
        // Gather the stats about the current downloads and uploads
        TransferStats stats = getStats();

        // If there are both downloads and uploads, create a second MenuItem to display uploads
        MenuItem menuItem;
        if (stats.dlCount > 0 && stats.ulCount > 0) {
            if (_transferStats2 == null) {
                _transferStats2 = p.addMenuItemAfterItem(
                        "", _transferStats1, _handleClick);
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

        boolean transferring = stats.transferring();

        if (transferring) {
            showStats(_transferStats1, S.LBL_DOWNLOADING,
                    stats.dlCount, stats.dlBytesDone, stats.dlBytesTotal);
            showStats(menuItem, S.LBL_UPLOADING,
                    stats.ulCount, stats.ulBytesDone, stats.ulBytesTotal);
        } else {
            _transferStats1.setText(S.LBL_NO_ACTIVE_TRANSFERS);
            _transferStats1.setImage(null);
        }

        // Display the progress on the menu icon

        if (transferring) {
            if (_transferProgress == null) {
                _transferProgress = S.LBL_TRANSFERRING;
                GUI.get().addProgress(_transferProgress, false);
            }
        } else {
            if (_transferProgress != null) {
                GUI.get().removeProgress(_transferProgress);
                _transferProgress = null;
            }
        }
    }

    private TransferStats getStats()
    {
        TransferStats stats = new TransferStats();
        synchronized (_ts) {
            for (PBTransferEvent ts : _ts.transfers_()) {
                long done = ts.getDone();
                long total = ts .getTotal();
                if (done > 0 && done < total) {
                    if (ts.getUpload()) {
                        stats.ulCount++;
                        stats.ulBytesDone += done;
                        stats.ulBytesTotal += total;
                    } else {
                        stats.dlCount++;
                        stats.dlBytesDone += done;
                        stats.dlBytesTotal += total;
                    }
                }
            }
        }
        return stats;
    }

    private void showStats(MenuItem menuItem, String action, int count, long done, long total)
    {
        if (count > 0) {
            menuItem.setText(String.format("%s %s file%s (%s)",
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

    public void dispose()
    {
        for (Image img : _pieChartCache.values()) {
            img.dispose();
        }
        _pieChartCache.clear();

        _ts.removeListener(this);
    }

    @Override
    public void onTransferStateChanged(TransferState state)
    {
        scheduleUpdate();
    }
}
