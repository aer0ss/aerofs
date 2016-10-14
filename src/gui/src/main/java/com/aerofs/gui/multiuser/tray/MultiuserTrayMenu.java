/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.tray;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.multiuser.preferences.MultiuserDlgPreferences;
import com.aerofs.gui.tray.*;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.update.Updater.Status;
import com.google.common.base.Preconditions;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class MultiuserTrayMenu extends AbstractTrayMenu implements ITrayMenu, ITrayMenuComponentListener
{
    public MultiuserTrayMenu(TrayIcon icon, RebuildDisposition buildOrReuse)
    {
        super(icon, buildOrReuse);

        // It's critical that we rebuild the menu here, since otherwise Ubuntu won't
        // have a menu to display until one of the menu items needs to be updated.
        rebuildMenu();
    }

    @Override
    protected void updateLastMenuInPlace()
    {
        if (_indexingTrayMenuSection != null) {
            _indexingTrayMenuSection.updateInPlace();
        }
        _transferTrayMenuSection.updateInPlace();
    }

    @Override
    protected void populateMenu(Menu menu)
    {
        TrayMenuPopulator populator = new TrayMenuPopulator(menu);
        // Apply updates
        if (UIGlobals.updater().getUpdateStatus() == Status.APPLY) {
            populator.addApplyUpdateMenuItem();
            populator.addMenuSeparator();
        }

        // Open AeroFS folder
        if (Cfg.storageType() == StorageType.LINKED) {
            addOpenFolderMenuItem(populator);
            populator.addMenuSeparator();
        }

        populator.addInviteCoworkerMenuItem();
        populator.addMenuSeparator();

        // Manage Team online
        addManageTeamMenuItem(populator);
        populator.addMenuSeparator();

        // Either "launching", "indexing", or (maybe) shared folders + transfers + preferences
        if (!_enabled) {
            populator.addLaunchingMenuItem();
            populator.addMenuSeparator();
        } else if (_indexingPoller != null && !_indexingPoller.isIndexingDone()) {
            // until indexing is done, all Ritual calls will fail, therefore there's no point
            // displaying shared folders and transfers entries
            Preconditions.checkNotNull(_indexingTrayMenuSection);
            _indexingTrayMenuSection.populateMenu(menu);
            populator.addMenuSeparator();
        } else {
            createSharedFoldersMenu(populator);
            createRecentActivitesMenu(menu);
            addVersionHistoryMenuItem(populator);
            populator.addMenuSeparator();

            _transferTrayMenuSection.populateMenu(menu);
            populator.addMenuSeparator();
            addPreferencesMenuItem(populator);
        }

        // Help
        createHelpMenu(populator);
        populator.addMenuSeparator();
        // Exit
        populator.addExitMenuItem(L.product());
    }

    @Override
    protected AeroFSDialog preferencesDialog()
    {
        return new MultiuserDlgPreferences(GUI.get().sh());
    }

    private void createHelpMenu(TrayMenuPopulator trayMenuPopulator)
    {
        Menu helpMenu = trayMenuPopulator.createHelpMenu();
        TrayMenuPopulator helpTrayMenuPopulator = new TrayMenuPopulator(helpMenu);
        helpTrayMenuPopulator.addHelpMenuItems(null);
    }

    private void addManageTeamMenuItem(TrayMenuPopulator trayMenuPopulator)
    {
        String organizationUsersUrl = getStringProperty("base.www.organization_users_url");

        trayMenuPopulator.addMenuItem("Manage Organization", new AbstractListener()
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch(organizationUsersUrl);
            }
        });
    }
}
