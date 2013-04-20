/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.sharing.DlgManageSharedFolders;
import com.aerofs.gui.tray.IndexingPoller.IIndexingCompletionListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.UbuntuTrayItem;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractTrayMenu implements ITrayMenu, ITrayMenuComponentListener
{
    private static final ClickEvent OPEN_AEROFS_FOLDER
            = new ClickEvent(Action.OPEN_AEROFS_FOLDER, Source.TASKBAR);
    private static final ClickEvent PREFERENCES
            = new ClickEvent(Action.PREFERENCES, Source.TASKBAR);
    private static final ClickEvent MANAGE_SHARED_FOLDER
            = new ClickEvent(Action.MANAGE_SHARED_FOLDER, Source.TASKBAR);

    protected final TrayIcon _icon;

    protected final @Nullable IndexingPoller _indexingPoller;
    protected final @Nullable IndexingTrayMenuSection _indexingTrayMenuSection;

    protected final TransferTrayMenuSection _transferTrayMenuSection;
    protected final RebuildDisposition _rebuildDisposition;
    private boolean rebuilding = false;
    private int rebuilds = 0;
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

        _transferTrayMenuSection = new TransferTrayMenuSection(UI.scheduler());
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
            _indexingPoller = new IndexingPoller(UI.scheduler());
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

        UI.rnc().addListener(new IListener() {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType().getNumber()) {
                case Type.DOWNLOAD_VALUE:
                case Type.UPLOAD_VALUE:
                    _transferTrayMenuSection.update(pb);
                    break;
                case Type.ROOTS_CHANGED_VALUE:
                    GUI.get().shellext().notifyRootAnchor();
                    break;
                default:
                    break;
                }
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
        Menu newMenu = populateNewMenu();

        boolean enabled = _lastMenu.isEnabled();
        boolean visible = _lastMenu.isVisible();
        newMenu.setEnabled(enabled);
        newMenu.setVisible(visible);
        _lastMenu.setEnabled(false);
        _lastMenu.setVisible(false);
        notifyListeners(newMenu);
        _lastMenu.dispose();
        _lastMenu = newMenu;
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
                new AbstractListener(OPEN_AEROFS_FOLDER)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        GUIUtil.launch(Cfg.absDefaultRootAnchor());
                    }
                });
    }

    protected void createSharedFoldersMenu(Menu menu)
    {
        TrayMenuPopulator populator = new TrayMenuPopulator(menu);
        populator.addMenuItem("Shared Folders...", new AbstractListener(MANAGE_SHARED_FOLDER)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgManageSharedFolders(GUI.get().sh()).openDialog();
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
