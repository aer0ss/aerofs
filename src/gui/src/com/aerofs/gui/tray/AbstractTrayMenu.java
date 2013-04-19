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
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.sharing.DlgManageSharedFolders;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.slf4j.Logger;

public abstract class AbstractTrayMenu implements ITrayMenu
{
    protected static final Logger l = Loggers.getLogger(AbstractTrayMenu.class);

    private static final ClickEvent OPEN_AEROFS_FOLDER
            = new ClickEvent(Action.OPEN_AEROFS_FOLDER, Source.TASKBAR);
    private static final ClickEvent PREFERENCES
            = new ClickEvent(Action.PREFERENCES, Source.TASKBAR);
    private static final ClickEvent MANAGE_SHARED_FOLDER
            = new ClickEvent(Action.MANAGE_SHARED_FOLDER, Source.TASKBAR);

    protected final Menu _menu;
    protected final TrayIcon _icon;

    protected final IndexingPoller _indexingPoller;

    protected final IndexingTrayMenuSection _indexingTrayMenuSection;
    protected final TransferTrayMenuSection _transferTrayMenuSection;
    protected boolean _enabled;
    protected final TrayMenuPopulator _populator;

    private AeroFSDialog _dlgPreferences;


    public AbstractTrayMenu(TrayIcon icon)
    {
        _icon = icon;

        _menu = new Menu(GUI.get().sh(), SWT.POP_UP);

        _populator = new TrayMenuPopulator(_menu);
        _transferTrayMenuSection = new TransferTrayMenuSection(_populator);

        _menu.addMenuListener(new MenuListener() {
            @Override
            public void menuShown(MenuEvent event)
            {
                _populator.clearAllMenuItems();
                loadMenu();
            }

            @Override
            public void menuHidden(MenuEvent arg0)
            {
                _icon.clearNotifications();
            }
        });

        if (Cfg.storageType() == StorageType.LINKED) {
            _indexingPoller = new IndexingPoller(UI.scheduler());


            _indexingTrayMenuSection = new IndexingTrayMenuSection(_menu, _populator,
                    _indexingPoller);
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

    protected abstract void loadMenu();
    protected abstract AeroFSDialog preferencesDialog();

    protected void addOpenFolderMenuItem()
    {
        _populator.addMenuItem("Open " + L.brand() + " Folder",
                new AbstractListener(OPEN_AEROFS_FOLDER)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        GUIUtil.launch(Cfg.absDefaultRootAnchor());
                    }
                });
    }

    protected void createSharedFoldersMenu()
    {
        _populator.addMenuItem("Shared Folders...", new AbstractListener(MANAGE_SHARED_FOLDER) {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgManageSharedFolders(GUI.get().sh()).openDialog();
            }
        });
    }

    protected void addPreferencesMenuItem()
    {
        _populator.addPreferencesMenuItem(new AbstractListener(PREFERENCES) {
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
        _populator.dispose();

        _transferTrayMenuSection.dispose();
    }
}
