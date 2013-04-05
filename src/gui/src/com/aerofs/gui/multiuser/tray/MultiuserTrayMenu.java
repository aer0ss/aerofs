/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.tray;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.multiuser.preferences.MultiuserDlgPreferences;
import com.aerofs.gui.tray.ITrayMenu;
import com.aerofs.gui.tray.TransferTrayMenuSection;
import com.aerofs.gui.tray.TrayIcon;
import com.aerofs.gui.tray.TrayMenuPopulator;
import com.aerofs.labeling.L;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import static com.aerofs.proto.Sv.PBSVEvent.Type.CLICKED_TASKBAR_MANAGE_TEAM;
import static com.aerofs.proto.Sv.PBSVEvent.Type.CLICKED_TASKBAR_PREFERENCES;

public class MultiuserTrayMenu implements ITrayMenu
{
    private final Menu _menu;
    private final TrayIcon _icon;

    private final TransferTrayMenuSection _transferTrayMenuSection;
    private boolean _enabled;
    public final TrayMenuPopulator _populator;

    public MultiuserTrayMenu(TrayIcon icon)
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

        UI.rnc().addListener(new IListener()
        {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                switch (pb.getType().getNumber()) {
                case Type.DOWNLOAD_VALUE:
                case Type.UPLOAD_VALUE:
                    _transferTrayMenuSection.update(pb);
                    break;
                default:
                    break;
                }
            }
        });
    }

    public void loadMenu()
    {

        if (UI.updater().getUpdateStatus() == Status.APPLY) {
            _populator.addApplyUpdateMenuItem();
        }

        addManageTeamMenuItem();

        _populator.addMenuSeparator();

        if (!_enabled) {
            _populator.addLaunchingMenuItem();

            _populator.addMenuSeparator();

        } else {
            _transferTrayMenuSection.populate();

            _populator.addMenuSeparator();

            addPreferencesMenuItem();
        }

        createHelpMenu();
        _populator.addExitMenuItem(L.PRODUCT);
    }

    public void createHelpMenu()
    {
        Menu helpMenu = _populator.createHelpMenu();
        TrayMenuPopulator helpTrayMenuPopulator = new TrayMenuPopulator(helpMenu);
        helpTrayMenuPopulator.addHelpMenuItems();
    }

    private MultiuserDlgPreferences _dlgPreferences;

    public void openDlgPreferences()
    {
        if (_dlgPreferences == null || _dlgPreferences.isDisposed()) {
            _dlgPreferences = new MultiuserDlgPreferences(GUI.get().sh());
            _dlgPreferences.openDialog();
        } else {
            _dlgPreferences.forceActive();
        }
    }

    public void addPreferencesMenuItem()
    {
        _populator.addPreferencesMenuItem(new AbstractListener(CLICKED_TASKBAR_PREFERENCES)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                openDlgPreferences();
            }
        });
    }

    private void addManageTeamMenuItem()
    {
        _populator.addMenuItem("Manage Team", new AbstractListener(CLICKED_TASKBAR_MANAGE_TEAM)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch(WWW.TEAM_MANAGEMENT_URL);
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
        _populator.dispose();
    }
}
