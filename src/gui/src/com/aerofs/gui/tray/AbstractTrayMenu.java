/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.activitylog.DlgActivityLog;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.sharing.DlgManageSharedFolders;
import com.aerofs.gui.tray.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Ritual.GetActivitiesReply;
import com.aerofs.proto.Ritual.GetActivitiesReply.PBActivity;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.UbuntuTrayItem;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractTrayMenu implements ITrayMenu, ITrayMenuComponentListener
{
    protected static Logger l = Loggers.getLogger(AbstractTrayMenu.class);

    private static final ClickEvent OPEN_AEROFS_FOLDER
            = new ClickEvent(Action.OPEN_AEROFS_FOLDER, Source.TASKBAR);
    private static final ClickEvent PREFERENCES
            = new ClickEvent(Action.PREFERENCES, Source.TASKBAR);
    // this event is used by both subclasses
    protected static final ClickEvent MANAGE_SHARED_FOLDER
            = new ClickEvent(Action.MANAGE_SHARED_FOLDER, Source.TASKBAR);

    protected final TrayIcon _icon;

    protected final @Nullable IndexingPoller _indexingPoller;
    protected final @Nullable IndexingTrayMenuSection _indexingTrayMenuSection;

    protected final TransferTrayMenuSection _transferTrayMenuSection;
    protected final RebuildDisposition _rebuildDisposition;
    private boolean rebuilding = false;
    protected boolean _enabled;
    protected final List<ITrayMenuListener> _trayMenuListeners;
    protected Menu _lastMenu;

    private AeroFSDialog _dlgPreferences;

    public AbstractTrayMenu(TrayIcon icon, RebuildDisposition rebuildDisposition)
    {
        _icon = icon;
        _rebuildDisposition = rebuildDisposition;
        _trayMenuListeners = Lists.newArrayList();

        _lastMenu = new Menu(GUI.get().sh(), SWT.POP_UP);

        _transferTrayMenuSection = new TransferTrayMenuSection(UIGlobals.ts());
        _transferTrayMenuSection.addListener(this);

        _lastMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuShown(MenuEvent event)
            {
                // Note: this will never be called by the libappindicator backend.
                Preconditions.checkState(!UbuntuTrayItem.supported());
                TrayMenuPopulator p = new TrayMenuPopulator(_lastMenu);
                p.clearAllMenuItems();
                populateMenu(_lastMenu);
            }

            @Override
            public void menuHidden(MenuEvent arg0)
            {
                _icon.clearNotifications();
            }
        });

        if (Cfg.storageType() == StorageType.LINKED) {
            _indexingPoller = new IndexingPoller(UIGlobals.scheduler());
            _indexingTrayMenuSection = new IndexingTrayMenuSection(_indexingPoller);
            _indexingPoller.addListener(new IIndexingCompletionListener()
            {
                @Override
                public void onIndexingDone()
                {
                    GUI.get().asyncExec(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            rebuildMenu();
                        }
                    });
                }
            });
            _indexingTrayMenuSection.addListener(this);
        } else {
            _indexingPoller = null;
            _indexingTrayMenuSection = null;
        }

        UIGlobals.rnc().addListener(new IRitualNotificationListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType().getNumber()) {
                case Type.ROOTS_CHANGED_VALUE:
                    UIGlobals.shellext().notifyRootAnchor();
                    break;
                default:
                    break;
                }
            }

            @Override
            public void onNotificationChannelBroken()
            {
                // noop
            }
        });
    }

    // See RebuildDisposition.java for why we need to support updating the menu in place as
    // well as building whole new menus.
    protected abstract void populateMenu(Menu menu);
    protected abstract void updateLastMenuInPlace();
    protected abstract AeroFSDialog preferencesDialog();

    protected void rebuildMenu()
    {
        if (!rebuilding) {
            rebuilding = true;
            // The rebuild has to happen on the GUI thread.
            GUI.get().disp().syncExec(new Runnable() {
                @Override
                public void run()
                {
                    switch (_rebuildDisposition) {
                    case REBUILD:
                        unsafeRebuildMenu();
                        break;
                    case REUSE:
                        unsafeRepopulateExistingMenu();
                        break;
                    }
                    rebuilding = false;
                }
            });
        }
    }

    private void unsafeRebuildMenu()
    {
        // This function should only be called on the GUI thread.
        Preconditions.checkState(GUI.get().isUIThread(),
                "tried to rebuild menu from non-GUI thread");

        // note that we can totally dispose the old menu first because no other events will be
        // processed, including repaint and relayout, while we are holding the event lock.
        boolean enabled = _lastMenu.isEnabled();
        boolean visible = _lastMenu.isVisible();
        new TrayMenuPopulator(_lastMenu).clearAllMenuItems();
        _lastMenu.dispose();

        _lastMenu = populateNewMenu();
        _lastMenu.setEnabled(enabled);
        _lastMenu.setVisible(visible);

        // note that notifying the listener will cause the only listener, TrayIcon, to call
        // setMenu() with the new menu.
        //
        // For reasons unknown, this will cause the old menu to lose references to all of its
        // children and make it harder to clean up unused references.
        notifyListeners(_lastMenu);
    }

    private void unsafeRepopulateExistingMenu()
    {
        Preconditions.checkState(GUI.get().isUIThread(),
                "tried to repopulate menu from non-GUI thread");
        TrayMenuPopulator trayMenuPopulator = new TrayMenuPopulator(_lastMenu);
        trayMenuPopulator.clearAllMenuItems();
        populateMenu(_lastMenu);
    }

    private Menu populateNewMenu()
    {
        Preconditions.checkState(GUI.get().isUIThread(),
                "tried to rebuild menu from non-GUI thread");
        Menu menu = new Menu(GUI.get().sh(), SWT.POP_UP);
        menu.addMenuListener(new MenuListener()
        {
            @Override
            public void menuShown(MenuEvent event)
            {
                rebuildMenu();
            }

            @Override
            public void menuHidden(MenuEvent arg0)
            {
                _icon.clearNotifications();
            }
        });
        populateMenu(menu);
        return menu;
    }


    protected void addOpenFolderMenuItem(TrayMenuPopulator trayMenuPopulator)
    {
        trayMenuPopulator.addMenuItem("Open " + L.brand() + " Folder",
                new AbstractListener(OPEN_AEROFS_FOLDER) {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch(Cfg.absDefaultRootAnchor());
            }
        });
    }

    protected void createSharedFoldersMenu(TrayMenuPopulator trayMenuPopulator)
    {
        trayMenuPopulator.addMenuItem("Manage Shared Folders...",
                new AbstractListener(MANAGE_SHARED_FOLDER) {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        new DlgManageSharedFolders(GUI.get().sh()).openDialog();
                    }
                });
    }

    protected void createRecentActivitesMenu(Menu menu)
    {
        MenuItem mi;
        mi = new MenuItem(menu, SWT.CASCADE);
        mi.setText("Recent Activities");
        final Menu menuActivities = new Menu(menu.getShell(), SWT.DROP_DOWN);
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
        final TrayMenuPopulator activitiesPopulator = new TrayMenuPopulator(menu);
        activitiesPopulator.clearAllMenuItems();

        activitiesPopulator.addMenuItem(S.GUI_LOADING, null).setEnabled(false);

        // asynchronously fetch results, as GetActivities call may be slow. (see ritual.proto)
        Futures.addCallback(UIGlobals.ritualNonBlocking().getActivities(true, 5, null),
                new FutureCallback<GetActivitiesReply>()
                {
                    @Override
                    public void onFailure(Throwable e)
                    {
                        activitiesPopulator.clearAllMenuItems();
                        activitiesPopulator.addErrorMenuItem(S.COULDNT_LIST_ACTIVITIES);
                        l.warn(Util.e(e));
                        done();
                    }

                    @Override
                    public void onSuccess(GetActivitiesReply reply)
                    {
                        activitiesPopulator.clearAllMenuItems();

                        boolean added = false;
                        for (int i = 0; i < reply.getActivityCount(); i++) {
                            addBriefActivityEntry(reply.getActivity(i), i, activitiesPopulator);
                            added = true;
                        }

                        if (added && reply.getHasUnresolvedDevices()) {
                            MenuItem mi = activitiesPopulator.addMenuItem(
                                    S.FAILED_FOR_ACCURACY, null);
                            mi.setEnabled(false);
                            mi.setImage(Images.get(Images.ICON_WARNING));
                        }

                        if (!added) {
                            MenuItem mi = activitiesPopulator.addMenuItem(
                                    "No recent activity", null);
                            mi.setEnabled(false);
                        }

                        done();
                    }

                    private void done()
                    {
                        activitiesPopulator.addMenuSeparator();

                        activitiesPopulator.addMenuItem("Show More...", new AbstractListener(null) {
                            @Override
                            protected void handleEventImpl(Event event)
                            {
                                new DlgActivityLog(GUI.get().sh(), null).openDialog();
                            }
                        });
                    }
                }, new GUIExecutor(menu));
    }

    protected void addBriefActivityEntry(final PBActivity a, final int idx,
            TrayMenuPopulator populator)
    {
        populator.addMenuItem(a.getMessage(), new AbstractListener(null) {
            @Override
            protected void handleEventImpl(Event event)
            {
                if (a.hasPath()) {
                    String path = UIUtil.absPathNullable(Path.fromPB(a.getPath()));
                    if (path != null) OSUtil.get().showInFolder(path);
                } else {
                    new DlgActivityLog(GUI.get().sh(), idx).openDialog();
                }
            }
        });
    }

    protected void addVersionHistoryMenuItem(TrayMenuPopulator trayMenuPopulator)
    {
        trayMenuPopulator.addMenuItem("Sync History...", new AbstractListener(null) {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgHistory(GUI.get().sh()).openDialog();
            }
        });
    }

    protected void addPreferencesMenuItem(TrayMenuPopulator trayMenuPopulator)
    {
        trayMenuPopulator.addPreferencesMenuItem(new AbstractListener(PREFERENCES)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                openDlgPreferences();
            }
        });
    }

    private void openDlgPreferences()
    {
        if (_dlgPreferences == null || _dlgPreferences.isDisposed()) {
            _dlgPreferences = preferencesDialog();
            _dlgPreferences.openDialog();
        } else {
            _dlgPreferences.forceActive();
        }
    }

    @Override
    public void addListener(ITrayMenuListener listener)
    {
        _trayMenuListeners.add(listener);
        // Why notify the added listener immediately?  Because the menu already has some state
        // that the listener would like to know about.
        listener.onTrayMenuChange(_lastMenu);
    }

    private void notifyListeners(Menu newMenu)
    {
        for (ITrayMenuListener l : _trayMenuListeners) {
            l.onTrayMenuChange(newMenu);
        }
    }

    @Override
    public void onTrayMenuComponentChange()
    {
        switch(_rebuildDisposition) {
        case REBUILD:
            rebuildMenu();
            break;
        case REUSE:
            GUI.get().exec(new Runnable()
            {
                @Override
                public void run()
                {
                    updateLastMenuInPlace();
                }
            });
            break;
        }
    }

    @Override
    public void setVisible(boolean b)
    {
        _lastMenu.setVisible(b);
    }

    @Override
    public void enable()
    {
        _enabled = true;
        rebuildMenu();
    }

    @Override
    public void dispose()
    {
        _trayMenuListeners.clear();

        _transferTrayMenuSection.dispose();
    }
}
