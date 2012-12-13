package com.aerofs.gui.singleuser.tray;

import java.util.Map;

import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.singleuser.preferences.DlgPreferences;
import com.aerofs.gui.tray.ITrayMenu;
import com.aerofs.gui.tray.PauseOrResumeSyncing;
import com.aerofs.gui.tray.TrayIcon;
import com.aerofs.gui.tray.TrayIcon.NotificationReason;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.TransferState;
import com.aerofs.gui.activitylog.DlgActivityLog;
import com.aerofs.gui.diagnosis.DlgDiagnosis;
import com.aerofs.gui.misc.DlgFolderlessInvite;
import com.aerofs.gui.sharing.DlgJoinSharedFolder;
import com.aerofs.gui.sharing.DlgManageSharedFolder;
import com.aerofs.gui.sharing.folders.DlgFolders;
import com.aerofs.lib.C;
import com.aerofs.lib.DelayedRunner;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent.State;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIParam;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import static com.aerofs.proto.Sv.PBSVEvent.Type.*;

public class TrayMenu implements ITrayMenu
{
    static final Logger l = Util.l(TrayMenu.class);


    private volatile int _conflictCount = 0;

    private MenuItem _transferStats1;    // menu item used to display information about ongoing transfers - line 1
    private MenuItem _transferStats2;    // menu item used to display information about ongoing transfers - line 2
    private Object _transferProgress;    // non-null if transfer is in progress
    private final Map<Integer, Image> _pieChartCache = Maps.newHashMap();
    private final TransferState _ts = new TransferState(true);

    private final Menu _menu;
    private final TrayIcon _icon;

    private boolean _enabled;
    public final TrayMenuPopulator _trayMenuPopulator;

    private final PauseOrResumeSyncing _prs = new PauseOrResumeSyncing();

    private final IListener _l = new IListener() {
        @Override
        public void onNotificationReceived(PBNotification pb) {
            switch (pb.getType().getNumber()) {
            case Type.DOWNLOAD_VALUE:
            case Type.UPLOAD_VALUE:
                _ts.update(pb);
                _dr.schedule();
                break;
            case Type.CONFLICT_COUNT_VALUE:
                assert pb.hasConflictCount();
                _conflictCount = pb.getConflictCount();
                _icon.showNotification(NotificationReason.CONFLICT, pb.getConflictCount() > 0);
                // TODO: schedule GUI update in case the menu is currently visible?
                break;
            default: break;
            }
        }
    };

    TrayMenu(TrayIcon icon)
    {
        _icon = icon;

        _menu = new Menu(GUI.get().sh(), SWT.POP_UP);

        _trayMenuPopulator = new TrayMenuPopulator(_menu);

        _menu.addMenuListener(new MenuListener() {
            @Override
            public void menuShown(MenuEvent event)
            {
                _trayMenuPopulator.clearAllMenuItems();
                loadMenu();
            }

            @Override
            public void menuHidden(MenuEvent arg0)
            {
                _icon.clearNotifications();
            }
        });
        UI.rnc().addListener(_l);
    }

    public void loadMenu()
    {
        boolean hasWarnings = false;

        if (UI.updater().getUpdateStatus() == Status.APPLY) {
            _trayMenuPopulator.addApplyUpdateMenuItem();
            hasWarnings = true;
        }

        if (_conflictCount > 0) {
            addConflictsMenuItem(_conflictCount);
            hasWarnings = true;
        }

        if (hasWarnings) _trayMenuPopulator.addMenuSeparator();

        addOpenFolderMenuItem();

        _trayMenuPopulator.addMenuSeparator();

        if (!_enabled) {
            _trayMenuPopulator.addLaunchingMenuItem();
            _trayMenuPopulator.addMenuSeparator();
        } else {

            addShareFolderMenuItem();
            addAcceptInvitationMenuitem();
            createManageSharedFoldersMenu();

            _trayMenuPopulator.addMenuSeparator();

            createRecentActivitesMenu();
            addVersionHistoryMenuItem();

            _trayMenuPopulator.addMenuSeparator();

            addTransfersMenuItem();
            addPauseOrResumeSyncingMenuItem();

            _trayMenuPopulator.addMenuSeparator();

            addPreferencesMenuItem();
        }

        createHelpMenu();
        addInviteFriendMenuItem();

        _trayMenuPopulator.addMenuSeparator();

        _trayMenuPopulator.addExitMenuItem(S.PRODUCT);
    }

    private void addConflictsMenuItem(int conflictCount)
    {
        String label = UIUtil.prettyLabelWithCount(conflictCount, "A Conflict Was Found",
                "Conflicts Were Found");

        _trayMenuPopulator.addWarningMenuItem(label,
                new AbstractListener(CLICKED_TASKBAR_RESOLVE_CONFLICTS)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        // TODO: split conflict resolution from diagnosis dialog
                        boolean showSysFiles = (event.stateMask & SWT.SHIFT) != 0;
                        new DlgDiagnosis(GUI.get().sh(), showSysFiles).openDialog();
                    }
                });
    }

    private void addOpenFolderMenuItem()
    {
        _trayMenuPopulator.addMenuItem("Open " + S.PRODUCT + " Folder",
                new AbstractListener(CLICKED_TASKBAR_OPEN_AEROFS)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        GUIUtil.launch(Cfg.absRootAnchor());
                    }
                });
    }

    private void addShareFolderMenuItem()
    {
        _trayMenuPopulator.addMenuItem("Share Folder...",
                new AbstractListener(CLICKED_TASKBAR_SHARE_FOLDER)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgFolders(GUI.get().sh()).openDialog();
                    }
                });
    }

    private void addAcceptInvitationMenuitem()
    {
        _trayMenuPopulator.addMenuItem("Accept Invitation...",
                new AbstractListener(CLICKED_TASKBAR_ACCEPT_INVITE)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgJoinSharedFolder(GUI.get().sh()).openDialog();
                    }
                });
    }

    private void createManageSharedFoldersMenu()
    {
        MenuItem mi = new MenuItem(_menu, SWT.CASCADE);
        mi.setText("Manage Shared Folders");
        final Menu menuManage = new Menu(_menu.getShell(), SWT.DROP_DOWN);
        mi.setMenu(menuManage);
        final TrayMenuPopulator manageTrayMenuPopulator = new TrayMenuPopulator(menuManage);
        menuManage.addMenuListener(new MenuAdapter() {
            @Override
            public void menuShown(MenuEvent event)
            {
                manageTrayMenuPopulator.clearAllMenuItems();

                boolean added = addSharedFoldersSubmenu(menuManage,
                        new ISharedFolderMenuExecutor() {
                            @Override
                            public void run(Path path)
                            {
                                new DlgManageSharedFolder(GUI.get().sh(), path).openDialog();
                            }
                        });

                if (!added) {
                    manageTrayMenuPopulator.addMenuItem("No shared folder", null).setEnabled(false);
                }
            }
        });
    }

    /**
     * @return true if one or more menu item is added
     */
    private boolean addSharedFoldersSubmenu(Menu submenu, final ISharedFolderMenuExecutor lme)
    {
        boolean added = false;
        TrayMenuPopulator sharedTrayMenuPopulator = new TrayMenuPopulator(submenu);
        try {
            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                for (PBPath pbpath : ritual.listSharedFolders().getPathList()) {
                    final Path path = new Path(pbpath);
                    sharedTrayMenuPopulator.addMenuItem(path.last(),
                            new GUIUtil.AbstractListener(CLICKED_TASKBAR_MANAGER_SHARED_FOLDER)
                            {
                                @Override
                                protected void handleEventImpl(Event event)
                                {
                                    try {
                                        lme.run(path);
                                    } catch (Exception e) {
                                        Util.l(this).warn("menu handler: " + Util.e(e));
                                    }
                                }
                            });

                    added = true;
                }
            } finally {
                ritual.close();
            }

        } catch (Exception e) {
            sharedTrayMenuPopulator.addErrorMenuItem("Couldn't list shared folders");
            added = true;
        }

        return added;
    }

    private void createRecentActivitesMenu()
    {
        MenuItem mi;
        mi = new MenuItem(_menu, SWT.CASCADE);
        mi.setText("Recent Activities");
        final Menu menuActivities = new Menu(_menu.getShell(), SWT.DROP_DOWN);
        mi.setMenu(menuActivities);
        menuActivities.addMenuListener(new MenuAdapter() {
            @Override
            public void menuShown(MenuEvent event)
            {
                populateActivitiesMenu(menuActivities);
            }
        });
    }

    private void populateActivitiesMenu(final Menu menu)
    {
        final TrayMenuPopulator activitiesTrayMenuPopulator = new TrayMenuPopulator(menu);
        activitiesTrayMenuPopulator.clearAllMenuItems();

        activitiesTrayMenuPopulator.addMenuItem(S.GUI_LOADING, null).setEnabled(false);

        // asynchronously fetch results, as GetActivities call may be slow. (see ritual.proto)
        final RitualClient ritual = RitualClientFactory.newClient();
        Futures.addCallback(ritual.getActivities(true, 5, null), new FutureCallback<GetActivitiesReply>() {

            @Override
            public void onFailure(Throwable e)
            {
                activitiesTrayMenuPopulator.clearAllMenuItems();
                activitiesTrayMenuPopulator.addErrorMenuItem(S.COULDNT_LIST_ACTIVITIES);

                l.warn(Util.e(e));
                done();
            }

            @Override
            public void onSuccess(GetActivitiesReply reply)
            {
                activitiesTrayMenuPopulator.clearAllMenuItems();

                boolean added = false;
                for (int i = 0; i < reply.getActivityCount(); i++) {
                    final PBActivity a = reply.getActivity(i);
                    final int idx = i;
                    activitiesTrayMenuPopulator.addMenuItem(a.getMessage(), new AbstractListener(null)
                    {
                        @Override
                        protected void handleEventImpl(Event event)
                        {
                            if (a.hasPath()) {
                                String path = new Path(a.getPath()).toAbsoluteString(
                                        Cfg.absRootAnchor());
                                OSUtil.get().showInFolder(path);
                            } else {
                                new DlgActivityLog(GUI.get().sh(), idx).openDialog();
                            }
                        }
                    });
                    added = true;
                }

                if (added && reply.getHasUnresolvedDevices()) {
                    MenuItem mi = activitiesTrayMenuPopulator.addMenuItem(S.FAILED_FOR_ACCURACY, null);
                    mi.setEnabled(false);
                    mi.setImage(Images.get(Images.ICON_WARNING));
                }

                if (!added) {
                    MenuItem mi = activitiesTrayMenuPopulator.addMenuItem("No recent activity", null);
                    mi.setEnabled(false);
                }

                done();
            }

            private void done()
            {
                activitiesTrayMenuPopulator.addMenuSeparator();

                activitiesTrayMenuPopulator.addMenuItem("Show More...", new AbstractListener(null)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgActivityLog(GUI.get().sh(), null).openDialog();
                    }
                });

                ritual.close();
            }
        }, new GUIExecutor(menu));
    }

    private void addVersionHistoryMenuItem()
    {
        _trayMenuPopulator.addMenuItem("Version History...", new AbstractListener(null)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgHistory(GUI.get().sh()).openDialog();
            }
        });
    }

    private void addTransfersMenuItem()
    {
        _transferStats1 = _trayMenuPopulator.addMenuItem("", null);
        _transferStats1.setEnabled(false);

        _transferStats2 = null;
        updateTransferMenus();
    }

    /**
     * Update the menu items _transferStats1 and _transferStats2 with stats about the current uploads and downloads.
     * Must be called from the GUI thread
     */
    private void updateTransferMenus()
    {
        // Gather the stats about the current downloads and uploads

        int dlCount = 0, ulCount = 0;
        long dlBytesDone = 0, ulBytesDone = 0;
        long dlBytesTotal = 0, ulBytesTotal = 0;

        synchronized (_ts) {
            for (PBDownloadEvent dl : _ts.downloads_().values()) {
                // guaranteed by updateDownloadState
                assert dl.getState() == State.ONGOING;
                dlCount++;
                dlBytesDone += dl.getDone();
                dlBytesTotal += dl.getTotal();
            }

            for (PBUploadEvent ul : _ts.uploads_().values()) {
                // guaranteed by updateUploadState
                assert ul.getDone() != ul.getTotal();
                if (ul.getDone() > 0 && ul.getDone() != ul.getTotal()) {
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
                _transferStats2 = _trayMenuPopulator.addMenuItem( "",
                        _menu.indexOf(_transferStats1) + 1, null);
                _transferStats2.setEnabled(false);
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
            showStats(_transferStats1, "Downloading", dlCount, dlBytesDone, dlBytesTotal);
            showStats(menuItem, "Uploading", ulCount, ulBytesDone, ulBytesTotal);
        } else {
            _transferStats1.setText("No active transfers");
            _transferStats1.setImage(null);
        }

        // Display the progress on the menu icon

        if (transferring) {
            if (_transferProgress == null) {
                _transferProgress = GUI.get().addProgress("transferring files", false);
            }
        } else {
            if (_transferProgress != null) {
                GUI.get().removeProgress(_transferProgress);
                _transferProgress = null;
            }
        }
    }

    /**
     * Sets menuItem's text to something like "Downloading 2 files (12.1/29.5 MB)"
     * and menuItem's image to a pie chart representing the progress.
     * No-op if count is 0 (no files are being transfered)
     *
     * @param menuItem: MenuItem to set the text and image
     * @param action:   verb displayed in the menu: "Downloading" or "Uploading"
     * @param count:    number of files being transferred. If count is 0, this method does nothing.
     * @param done:     bytes transferred
     * @param total:    amount of bytes to transfer
     */
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
                menuItem.setImage(Images.getPieChart(done, total, 16, bg, fg, null, _pieChartCache));
            }
        }
    }

    private void addPauseOrResumeSyncingMenuItem()
    {
        final boolean paused = _prs.isPaused();
        String strPauseOrResume = paused ? "Resume Syncing" : "Pause syncing for an hour";
        _trayMenuPopulator.addMenuItem(strPauseOrResume,
                new AbstractListener(CLICKED_TASKBAR_PAUSE_SYNCING)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        try {
                            if (paused) _prs.resume();
                            else _prs.pause(1 * C.HOUR);
                        } catch (Exception e) {
                            UI.get().show(MessageType.ERROR, "Couldn't " +
                                    (paused ? "resume" : "pause") + " syncing. " +
                                    S.TRY_AGAIN_LATER);
                        }
                    }
                });
    }

    private void addInviteFriendMenuItem()
    {
        int quota = Cfg.db().getInt(Key.FOLDERLESS_INVITES);
        if (quota > 0) {
            String text = (OSUtil.isLinux() ? "\u2665 " : "") + "Invite " +
                    (quota > 1 ? String.valueOf(quota) +
                    " Friends" : "a Friend") + "...";
            MenuItem mi = _trayMenuPopulator.addMenuItem(text,
                    new AbstractListener(CLICKED_TASKBAR_INVITE_FOLDERLESS)
                    {
                        @Override
                        protected void handleEventImpl(Event event)
                        {
                            new DlgFolderlessInvite(GUI.get().sh()).openDialog();
                        }
                    });

            if (!OSUtil.isLinux()) mi.setImage(Images.get(Images.ICON_HEART));
        }
    }



    static interface ISharedFolderMenuExecutor
    {
        void run(Path path);
    }

    public void createHelpMenu()
    {
        Menu helpMenu = _trayMenuPopulator.createHelpMenu();
        TrayMenuPopulator helpTrayMenuPopulator = new TrayMenuPopulator(helpMenu);
        if (_enabled) {
            helpTrayMenuPopulator.addMenuItem(S.WHY_ARENT_MY_FILES_SYNCED,
                    new AbstractListener(CLICKED_TASKBAR_WHY_NOT_SYNCED)
                    {
                        @Override
                        protected void handleEventImpl(Event event)
                        {
                            boolean showSysFiles = (event.stateMask & SWT.SHIFT) != 0;
                            new DlgDiagnosis(GUI.get().sh(), showSysFiles).openDialog();
                        }
                    });

            helpTrayMenuPopulator.addMenuSeparator();
        }
        helpTrayMenuPopulator.addHelpMenuItems();
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

    private DlgPreferences _dlgPref;

    public void openDlgPreferences(boolean showTransfers)
    {
        if (_dlgPref == null || _dlgPref.isDisposed()) {
            _dlgPref = new DlgPreferences(GUI.get().sh(), showTransfers);
            _dlgPref.openDialog();
        } else {
            _dlgPref.forceActive();
        }
    }

    public void addPreferencesMenuItem()
    {
        _trayMenuPopulator.addPreferencesMenuItem(
                new AbstractListener(CLICKED_TASKBAR_PREFERENCES)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        boolean shift = (event.stateMask & SWT.SHIFT) != 0;
                        openDlgPreferences(shift);
                    }
                });
    }

    @Override
    public void setVisible(boolean b)
    {
        _menu.setVisible(b);
    }

    @Override
    public void enable()
    {
        _enabled = true;
    }

    @Override
    public void dispose()
    {
        // TODO remove listeners
        _trayMenuPopulator.dispose();

        for (Image img : _pieChartCache.values()) {
            img.dispose();
        }
        _pieChartCache.clear();
    }
}
