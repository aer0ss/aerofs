package com.aerofs.gui.singleuser.tray;

import com.aerofs.base.C;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.conflicts.DlgConflicts;
import com.aerofs.gui.singleuser.preferences.SingleuserDlgPreferences;
import com.aerofs.gui.tray.AbstractTrayMenu;
import com.aerofs.gui.tray.ITrayMenu;
import com.aerofs.gui.tray.PauseOrResumeSyncing;
import com.aerofs.gui.tray.RebuildDisposition;
import com.aerofs.gui.tray.TrayIcon;
import com.aerofs.gui.tray.TrayIcon.NotificationReason;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.gui.unsyncablefiles.DlgUnsyncableFiles;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.update.Updater.Status;
import com.google.common.base.Preconditions;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.google.common.base.Preconditions.checkArgument;

public class SingleuserTrayMenu extends AbstractTrayMenu implements IRitualNotificationListener, ITrayMenu
{
    private static final ClickEvent RESOLVE_CONFLICTS
            = new ClickEvent(Action.RESOLVE_CONFLICTS, Source.TASKBAR);
    private static final ClickEvent RESOLVE_UNSYNCABLE_FILES
            = new ClickEvent(Action.RESOLVE_UNSYNCABLE_FILES, Source.TASKBAR);
    private static final ClickEvent PAUSE_SYNCING
            = new ClickEvent(Action.PAUSE_SYNCING, Source.TASKBAR);
    private static final ClickEvent RESUME_SYNCING
            = new ClickEvent(Action.RESUME_SYNCING, Source.TASKBAR);

    private volatile int _conflictCount = 0;
    private volatile int _unsyncableFilesCount = 0;

    private final PauseOrResumeSyncing _prs = new PauseOrResumeSyncing();

    SingleuserTrayMenu(TrayIcon icon, RebuildDisposition rebuildDisposition)
    {
        super(icon, rebuildDisposition);
        Preconditions.checkState(_indexingPoller != null);

        // Delay start of the shellext service to avoid spamming
        // daemon with status requests while it is busy indexing...
        _indexingPoller.addListener(() -> GUI.get().asyncExec(() -> {
            try {
                UIGlobals.shellext().start_();
            } catch (Exception e) {
                newDefectWithLogs("shellext")
                        .setMessage("can't start shellext worker")
                        .setException(e)
                        .sendAsync();
            }
        }));

        UIGlobals.rnc().addListener(this);
        // It's critical that we rebuild the menu here, since otherwise Ubuntu won't
        // have a menu to display until one of the menu items needs to be updated.
        rebuildMenu();
    }

    @Override
    protected void updateLastMenuInPlace()
    {
        Preconditions.checkState(_indexingTrayMenuSection != null);
        _indexingTrayMenuSection.updateInPlace();
        _transferTrayMenuSection.updateInPlace();
    }

    @Override
    public void populateMenu(Menu menu)
    {
        TrayMenuPopulator trayMenuPopulator = new TrayMenuPopulator(menu);
        // Updates and/or conflicts
        boolean hasWarnings = false;
        if (UIGlobals.updater().getUpdateStatus() == Status.APPLY) {
            trayMenuPopulator.addApplyUpdateMenuItem();
            hasWarnings = true;
        }
        if (_conflictCount > 0) {
            addConflictsMenuItem(trayMenuPopulator, _conflictCount);
            hasWarnings = true;
        }
        if (_unsyncableFilesCount > 0) {
            addUnsyncableFileMenuItem(trayMenuPopulator, _unsyncableFilesCount);
            hasWarnings = true;
        }
        if (hasWarnings) trayMenuPopulator.addMenuSeparator();

        // Open AeroFS folder
        addOpenFolderMenuItem(trayMenuPopulator);
        trayMenuPopulator.addMenuSeparator();

        trayMenuPopulator.addInviteCoworkerMenuItem();
        trayMenuPopulator.addMenuSeparator();

        if (!_enabled) {
            // Launching
            trayMenuPopulator.addLaunchingMenuItem();
            trayMenuPopulator.addMenuSeparator();
        } else if (!_indexingPoller.isIndexingDone()) {
            // Indexing
            _indexingTrayMenuSection.populateMenu(menu);
            trayMenuPopulator.addMenuSeparator();
        } else {
            // Shared folders, activities, version history
            createSharedFoldersMenu(trayMenuPopulator);
            createRecentActivitesMenu(menu);
            addVersionHistoryMenuItem(trayMenuPopulator);
            trayMenuPopulator.addMenuSeparator();

            // Transfers, pause/resume sync
            _transferTrayMenuSection.populateMenu(menu);
            addPauseOrResumeSyncingMenuItem(trayMenuPopulator);
            trayMenuPopulator.addMenuSeparator();

            // Preferences
            addPreferencesMenuItem(trayMenuPopulator);
        }

        createHelpMenu(trayMenuPopulator);

        trayMenuPopulator.addMenuSeparator();

        trayMenuPopulator.addExitMenuItem(L.product());
    }

    @Override
    protected AeroFSDialog preferencesDialog()
    {
        return new SingleuserDlgPreferences(GUI.get().sh());
    }

    @Override
    public void onNotificationReceived(PBNotification pb) {
        switch (pb.getType().getNumber()) {
        case Type.CONFLICT_COUNT_VALUE:
            checkArgument(pb.hasCount());
            _conflictCount = pb.getCount();
            _icon.showNotification(NotificationReason.CONFLICT, _conflictCount > 0);
            break;
        case Type.NRO_COUNT_VALUE:
            checkArgument(pb.hasCount());
            _unsyncableFilesCount = pb.getCount();
            _icon.showNotification(NotificationReason.UNSYNCABLE_FILE, _unsyncableFilesCount > 0);
            break;
        case Type.SHARED_FOLDER_JOIN_VALUE:
            final Path p = Path.fromPB(pb.getPath());
            final String absPath = UIUtil.absPathNullable(p);
            if (absPath != null) {
                UI.get().notify(MessageType.INFO,
                        "You have joined the shared folder \"" + UIUtil.sharedFolderName(p, null) + "\"",
                        () -> GUIUtil.launch(absPath));
            }
            break;
        case Type.SHARED_FOLDER_KICKOUT_VALUE:
            UI.get().notify(MessageType.INFO,
                    "You have left \"" + Path.fromPB(pb.getPath()).toStringRelative() + "\"");
            break;
        default: break;
        }
    }

    @Override
    public void onNotificationChannelBroken()
    {
        // noop
    }

    private void addConflictsMenuItem(TrayMenuPopulator trayMenuPopulator, int conflictCount)
    {
        String label = UIUtil.prettyLabelWithCount(conflictCount, "A conflict was found",
                "conflicts were found");

        trayMenuPopulator.addWarningMenuItem(label,
                new AbstractListener(RESOLVE_CONFLICTS)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgConflicts(GUI.get().sh()).openDialog();
                    }
                });
    }

    private void addUnsyncableFileMenuItem(TrayMenuPopulator trayMenuPopulator, int nroCount)
    {
        String label = UIUtil.prettyLabelWithCount(nroCount, "An unsyncable file was found",
                "unsyncable files were found");

        trayMenuPopulator.addWarningMenuItem(label,
                new AbstractListener(RESOLVE_UNSYNCABLE_FILES)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgUnsyncableFiles(GUI.get().sh()).openDialog();
                    }
                });
    }

    private void addPauseOrResumeSyncingMenuItem(TrayMenuPopulator trayMenuPopulator)
    {
        final boolean paused = _prs.isPaused();
        String strPauseOrResume = paused ? "Resume Syncing" : "Pause syncing for an hour";
        trayMenuPopulator.addMenuItem(strPauseOrResume,
                new AbstractListener(paused ? RESUME_SYNCING : PAUSE_SYNCING)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        UI.get().asyncExec(() -> {
                            try {
                                if (paused) _prs.resume();
                                else _prs.pause(1 * C.HOUR);
                            } catch (Exception e) {
                                UI.get().show(MessageType.ERROR, "Couldn't " +
                                        (paused ? "resume" : "pause") + " syncing. " +
                                        S.TRY_AGAIN_LATER);
                            }
                        });
                    }
                });
    }

    public void createHelpMenu(TrayMenuPopulator trayMenuPopulator)
    {
        Menu helpMenu = trayMenuPopulator.createHelpMenu();
        TrayMenuPopulator helpTrayMenuPopulator = new TrayMenuPopulator(helpMenu);
        helpTrayMenuPopulator.addHelpMenuItems();
    }
}
