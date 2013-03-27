package com.aerofs.gui.singleuser.tray;

import com.aerofs.base.Loggers;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.misc.DlgInviteToSignUp;
import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.gui.singleuser.IndexingPoller;
import com.aerofs.gui.singleuser.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.gui.singleuser.IndexingTrayMenuSection;
import com.aerofs.gui.singleuser.preferences.SingleuserDlgPreferences;
import com.aerofs.gui.tray.ITrayMenu;
import com.aerofs.gui.tray.PauseOrResumeSyncing;
import com.aerofs.gui.tray.TransferTrayMenuSection;
import com.aerofs.gui.tray.TrayIcon;
import com.aerofs.gui.tray.TrayIcon.NotificationReason;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.labeling.L;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UIUtil;
import org.slf4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.activitylog.DlgActivityLog;
import com.aerofs.gui.diagnosis.DlgDiagnosis;
import com.aerofs.gui.sharing.DlgManageSharedFolder;
import com.aerofs.gui.sharing.folders.DlgFolders;
import com.aerofs.base.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import static com.aerofs.proto.Sv.PBSVEvent.Type.*;

public class SingleuserTrayMenu implements ITrayMenu
{
    static final Logger l = Loggers.getLogger(SingleuserTrayMenu.class);

    private volatile int _conflictCount = 0;

    private final Menu _menu;
    private final TrayIcon _icon;

    private final IndexingPoller _indexingPoller;

    private boolean _enabled;
    public final TrayMenuPopulator _trayMenuPopulator;

    private final IndexingTrayMenuSection _indexingTrayMenuSection;
    private final TransferTrayMenuSection _transferTrayMenuSection;

    private final PauseOrResumeSyncing _prs = new PauseOrResumeSyncing();

    private final IListener _l = new IListener() {
        @Override
        public void onNotificationReceived(PBNotification pb) {
            switch (pb.getType().getNumber()) {
            case Type.DOWNLOAD_VALUE:
            case Type.UPLOAD_VALUE:
                _transferTrayMenuSection.update(pb);
                break;
            case Type.CONFLICT_COUNT_VALUE:
                assert pb.hasConflictCount();
                _conflictCount = pb.getConflictCount();
                _icon.showNotification(NotificationReason.CONFLICT, pb.getConflictCount() > 0);
                // TODO: schedule GUI update in case the menu is currently visible?
                break;
            case Type.SHARED_FOLDER_JOIN_VALUE:
                final Path p = new Path(pb.getPath());
                UI.get().notify(MessageType.INFO,
                        "You have joined \"" + p.last() + "\"", new Runnable() {
                    @Override
                    public void run()
                    {
                        GUIUtil.launch(p.toAbsoluteString(Cfg.absRootAnchor()));
                    }
                });
                break;
            case Type.SHARED_FOLDER_KICKOUT_VALUE:
                UI.get().notify(MessageType.INFO,
                        "You have left \"" + new Path(pb.getPath()) + "\"");
                break;
            default: break;
            }
        }
    };

    SingleuserTrayMenu(TrayIcon icon)
    {
        _icon = icon;

        _indexingPoller = new IndexingPoller(UI.scheduler());

        // delay start of the shellext service to avoid spamming
        // daemon with status requests while it is busy indexing...
        _indexingPoller.addListener(new IIndexingCompletionListener() {
            @Override
            public void onIndexingDone()
            {
                try {
                    ShellextService.get().start_();
                } catch (Exception e) {
                    SVClient.logSendDefectAsync(true, "cant start shellext worker", e);
                }
            }
        });

        _menu = new Menu(GUI.get().sh(), SWT.POP_UP);

        _trayMenuPopulator = new TrayMenuPopulator(_menu);

        _indexingTrayMenuSection = new IndexingTrayMenuSection(_menu, _trayMenuPopulator,
                _indexingPoller);
        _transferTrayMenuSection = new TransferTrayMenuSection(_trayMenuPopulator);

        _menu.addMenuListener(new MenuListener()
        {
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
        } else if (!_indexingPoller.isIndexingDone()) {
            _indexingTrayMenuSection.populate();
            _trayMenuPopulator.addMenuSeparator();
        } else {
            createSharedFoldersMenu();
            createRecentActivitesMenu();
            addVersionHistoryMenuItem();

            _trayMenuPopulator.addMenuSeparator();

            _transferTrayMenuSection.populate();
            addPauseOrResumeSyncingMenuItem();

            _trayMenuPopulator.addMenuSeparator();

            addPreferencesMenuItem();
        }

        createHelpMenu();
        addInviteToSignUpMenuItem();

        _trayMenuPopulator.addMenuSeparator();

        _trayMenuPopulator.addExitMenuItem(L.PRODUCT);
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
        _trayMenuPopulator.addMenuItem("Open " + L.PRODUCT + " Folder",
                new AbstractListener(CLICKED_TASKBAR_OPEN_AEROFS)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        GUIUtil.launch(Cfg.absRootAnchor());
                    }
                });
    }

    private void createSharedFoldersMenu()
    {
        MenuItem mi = new MenuItem(_menu, SWT.CASCADE);
        mi.setText("Shared Folders");
        final Menu menuManage = new Menu(_menu.getShell(), SWT.DROP_DOWN);
        mi.setMenu(menuManage);
        final TrayMenuPopulator populater = new TrayMenuPopulator(menuManage);
        menuManage.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuShown(MenuEvent event)
            {
                populater.clearAllMenuItems();

                populater.addMenuItem("Share New Folder...",
                        new AbstractListener(CLICKED_TASKBAR_SHARE_FOLDER)
                        {
                            @Override
                            protected void handleEventImpl(Event event)
                            {
                                new DlgFolders(GUI.get().sh()).openDialog();
                            }
                        });

                populater.addMenuSeparator();

                addSharedFoldersSubmenu(menuManage, new ISharedFolderMenuExecutor()
                {
                    @Override
                    public void run(Path path)
                    {
                        new DlgManageSharedFolder(GUI.get().sh(), path).openDialog();
                    }
                });
            }
        });
    }

    private void addSharedFoldersSubmenu(Menu submenu, final ISharedFolderMenuExecutor lme)
    {
        final TrayMenuPopulator sharedTrayMenuPopulator = new TrayMenuPopulator(submenu);

        final MenuItem loading = sharedTrayMenuPopulator.addMenuItem(S.GUI_LOADING, null);
        loading.setEnabled(false);

        // asynchronously fetch results, as GetActivities call may be slow. (see ritual.proto)
        final RitualClient ritual = RitualClientFactory.newClient();
        Futures.addCallback(ritual.listSharedFolders(),
                new FutureCallback<ListSharedFoldersReply>()
                {
                    @Override
                    public void onFailure(Throwable e)
                    {
                        loading.dispose();
                        sharedTrayMenuPopulator.addErrorMenuItem("Couldn't list shared folders");
                        l.warn(Util.e(e));
                        ritual.close();
                    }

                    @Override
                    public void onSuccess(ListSharedFoldersReply reply)
                    {
                        loading.dispose();
                        for (PBPath pbpath : reply.getPathList()) {
                            addSharedFolderEntry(new Path(pbpath));
                        }
                        if (reply.getPathCount() == 0) {
                            sharedTrayMenuPopulator.addMenuItem("No shared folder", null)
                                    .setEnabled(false);
                        }
                        ritual.close();
                    }

                    private void addSharedFolderEntry(final Path path)
                    {
                        sharedTrayMenuPopulator.addMenuItem(path.last(),
                                new GUIUtil.AbstractListener(CLICKED_TASKBAR_MANAGER_SHARED_FOLDER)
                                {
                                    @Override
                                    protected void handleEventImpl(Event event)
                                    {
                                        try {
                                            lme.run(path);
                                        } catch (Exception e) {
                                            l.warn("menu handler: " + Util.e(e));
                                        }
                                    }
                                });
                    }
                }, new GUIExecutor(submenu));
    }

    private void createRecentActivitesMenu()
    {
        MenuItem mi;
        mi = new MenuItem(_menu, SWT.CASCADE);
        mi.setText("Recent Activities");
        final Menu menuActivities = new Menu(_menu.getShell(), SWT.DROP_DOWN);
        mi.setMenu(menuActivities);
        menuActivities.addMenuListener(new MenuAdapter()
        {
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
        Futures.addCallback(ritual.getActivities(true, 5, null),
                new FutureCallback<GetActivitiesReply>()
                {

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
                            activitiesTrayMenuPopulator.addMenuItem(a.getMessage(),
                                    new AbstractListener(null)
                                    {
                                        @Override
                                        protected void handleEventImpl(Event event)
                                        {
                                            if (a.hasPath()) {
                                                String path = new Path(
                                                        a.getPath()).toAbsoluteString(
                                                        Cfg.absRootAnchor());
                                                OSUtil.get().showInFolder(path);
                                            } else {
                                                new DlgActivityLog(GUI.get().sh(),
                                                        idx).openDialog();
                                            }
                                        }
                                    });
                            added = true;
                        }

                        if (added && reply.getHasUnresolvedDevices()) {
                            MenuItem mi = activitiesTrayMenuPopulator.addMenuItem(
                                    S.FAILED_FOR_ACCURACY, null);
                            mi.setEnabled(false);
                            mi.setImage(Images.get(Images.ICON_WARNING));
                        }

                        if (!added) {
                            MenuItem mi = activitiesTrayMenuPopulator.addMenuItem(
                                    "No recent activity", null);
                            mi.setEnabled(false);
                        }

                        done();
                    }

                    private void done()
                    {
                        activitiesTrayMenuPopulator.addMenuSeparator();

                        activitiesTrayMenuPopulator.addMenuItem("Show More...",
                                new AbstractListener(null)
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

    private void addInviteToSignUpMenuItem()
    {
        MenuItem mi = _trayMenuPopulator.addMenuItem(
                (OSUtil.isLinux() ? "\u2665 " : "") + "Invite a Friend to AeroFS...",
                new AbstractListener(CLICKED_TASKBAR_INVITE_TO_SIGNUP)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgInviteToSignUp(GUI.get().sh()).openDialog();
                    }
                });

        if (!OSUtil.isLinux()) mi.setImage(Images.get(Images.ICON_HEART));
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

    private SingleuserDlgPreferences _dlgPref;

    public void openDlgPreferences()
    {
        if (_dlgPref == null || _dlgPref.isDisposed()) {
            _dlgPref = new SingleuserDlgPreferences(GUI.get().sh());
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
                        openDlgPreferences();
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

        _transferTrayMenuSection.dispose();
    }
}
