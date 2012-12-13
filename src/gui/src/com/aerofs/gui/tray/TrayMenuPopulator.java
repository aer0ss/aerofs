/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.misc.DlgAbout;
import com.aerofs.gui.misc.DlgDefect;
import com.aerofs.lib.S;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import static com.aerofs.proto.Sv.PBSVEvent.Type.CLICKED_TASKBAR_APPLY_UPDATE;
import static com.aerofs.proto.Sv.PBSVEvent.Type.CLICKED_TASKBAR_EXIT;

public class TrayMenuPopulator
{
    private final Menu _rootMenu;

    public TrayMenuPopulator(Menu rootMenu)
    {
        _rootMenu = rootMenu;
    }

    public void clearAllMenuItems()
    {
        for (MenuItem mi : _rootMenu.getItems()) mi.dispose();
    }

    public MenuItem addMenuItem(String text, int index, AbstractListener la)
    {
        MenuItem mi = new MenuItem(_rootMenu, SWT.PUSH, index);
        mi.setText(text);
        if (la != null) mi.addListener(SWT.Selection, la);
        return mi;
    }

    public MenuItem addMenuItem(String text, AbstractListener la)
    {
        MenuItem mi = new MenuItem(_rootMenu, SWT.PUSH);
        mi.setText(text);
        if (la != null) mi.addListener(SWT.Selection, la);
        return mi;
    }

    public void addWarningMenuItem(String text, AbstractListener la)
    {
        String label;
        Image image;
        if (OSUtil.isLinux()) {
            label = "\u26A0 " + text;
            image = null;
        } else {
            label = text;
            image = Images.get(Images.ICON_WARNING);
        }
        addMenuItem(label, la).setImage(image);
    }

    public void addErrorMenuItem(String text)
    {
        MenuItem mi = addMenuItem(text, null);
        mi.setImage(Images.get(Images.ICON_ERROR));
        mi.setEnabled(false);
    }

    public void addHelpMenuItems()
    {
        addMenuItem(S.REPORT_A_PROBLEM, new AbstractListener(null)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgDefect(GUI.get().sh()).open();
            }
        });

        addMenuItem(S.REQUEST_A_FEATURE, new AbstractListener(null)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch("http://support.aerofs.com/forums/67721-feature-requests");
            }
        });

        addMenuItem("Support Center", new AbstractListener(null)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                GUIUtil.launch("http://support.aerofs.com");
            }
        });

        addMenuSeparator();

        addMenuItem("About " + S.PRODUCT, new AbstractListener(null)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                new DlgAbout(GUI.get().sh()).openDialog();
            }
        });
    }

    public void addApplyUpdateMenuItem()
    {
        addWarningMenuItem(S.BTN_APPLY_UPDATE,
                new AbstractListener(CLICKED_TASKBAR_APPLY_UPDATE)
                {
                    @Override
                    protected void handleEventImpl(Event event)
                    {
                        UI.updater().execUpdateFromMenu();
                    }
                });
    }

    public void addLaunchingMenuItem()
    {
        addMenuItem("Launching...", null)
                .setEnabled(false);
    }

    public void addExitMenuItem(String fullProductName)
    {
        addMenuItem((OSUtil.isWindows() ? "Exit" : "Quit") + " " +
               fullProductName, new AbstractListener(CLICKED_TASKBAR_EXIT)
        {
            @Override
            protected void handleEventImpl(Event event)
            {
                UI.get().shutdown();
                System.exit(0);
            }
        });
    }

    public void addMenuSeparator()
    {
        new MenuItem(_rootMenu, SWT.SEPARATOR);
    }

    public Menu createHelpMenu()
    {
        MenuItem miHelp = new MenuItem(_rootMenu, SWT.CASCADE);
        miHelp.setText("Help");
        Menu menuHelp = new Menu(_rootMenu.getShell(), SWT.DROP_DOWN);
        miHelp.setMenu(menuHelp);
        return menuHelp;
    }

    public void addPreferencesMenuItem(AbstractListener listener)
    {
        addMenuItem(S.PREFERENCES + "...", listener);
    }

    public void dispose()
    {
        _rootMenu.dispose();
    }
}
