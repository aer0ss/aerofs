/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.tray;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.multiuser.preferences.MultiuserDlgPreferences;
import com.aerofs.gui.tray.AbstractTrayMenu;
import com.aerofs.gui.tray.TrayIcon;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import com.aerofs.ui.UI;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;


public class MultiuserTrayMenu extends AbstractTrayMenu
{
    private static final ClickEvent MANAGE_TEAM_CLICKED
            = new ClickEvent(Action.MANAGE_TEAM, Source.TASKBAR);

    public MultiuserTrayMenu(TrayIcon icon)
    {
        super(icon);
    }

    @Override
    public void loadMenu()
    {
        if (UI.updater().getUpdateStatus() == Status.APPLY) {
            _populator.addApplyUpdateMenuItem();
            _populator.addMenuSeparator();
        }

        if (Cfg.storageType() == StorageType.LINKED) {
            addOpenFolderMenuItem();
            _populator.addMenuSeparator();
        }

        addManageTeamMenuItem();

        if (!_enabled) {
            _populator.addMenuSeparator();

            _populator.addLaunchingMenuItem();
            _populator.addMenuSeparator();
        } else if (_indexingPoller != null && !_indexingPoller.isIndexingDone()) {
            _populator.addMenuSeparator();

            _indexingTrayMenuSection.populate();
            _populator.addMenuSeparator();
        } else {
            createSharedFoldersMenu();
            _populator.addMenuSeparator();

            _transferTrayMenuSection.populate();

            _populator.addMenuSeparator();

            addPreferencesMenuItem();
        }

        createHelpMenu();
        _populator.addExitMenuItem(L.product());
    }

    @Override
    protected AeroFSDialog preferencesDialog()
    {
        return new MultiuserDlgPreferences(GUI.get().sh());
    }

    public void createHelpMenu()
    {
        Menu helpMenu = _populator.createHelpMenu();
        TrayMenuPopulator helpTrayMenuPopulator = new TrayMenuPopulator(helpMenu);
        helpTrayMenuPopulator.addHelpMenuItems();
    }

    private void addManageTeamMenuItem()
    {
        _populator.addMenuItem("Manage Team", new AbstractListener(MANAGE_TEAM_CLICKED)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch(WWW.TEAM_MEMBERS_URL.get());
            }
        });
    }
}
