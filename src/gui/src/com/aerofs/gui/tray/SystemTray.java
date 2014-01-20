/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

import org.eclipse.swt.widgets.UbuntuTrayItem;

public class SystemTray
{
    private final TrayIcon _icon;
    private final Balloons _bm;
    private final ITrayMenu _menu;

    public SystemTray(IMenuProvider menuProvider)
    {
        _icon = new TrayIcon(this);
        _bm = new Balloons(_icon);
        _menu = menuProvider.createMenu(_icon, UbuntuTrayItem.supported() ?
                RebuildDisposition.REBUILD : RebuildDisposition.REUSE);
        _menu.addListener(_icon);
     }

    public Balloons getBalloons()
    {
        return _bm;
    }

    public TrayIcon getIcon()
    {
        return _icon;
    }

    public void dispose()
    {
        _bm.dispose();
        if (_menu != null) _menu.dispose();
        _icon.dispose();
    }

    public void enableMenu()
    {
        if (_menu != null) _menu.enable();
    }

    public void setMenuVisible(boolean b)
    {
        if (_menu != null) _menu.setVisible(b);
    }
}
