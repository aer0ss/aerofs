/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

public class SystemTray
{
    private final TrayIcon _icon;
    private final Balloons _bm;
    private final Progresses _progs;
    private final ITrayMenu _menu;

    public SystemTray(IMenuProvider menuProvider)
    {
        _icon = new TrayIcon(this);
        _progs = new Progresses(this);
        _bm = new Balloons(_icon);
        _menu = menuProvider.createMenu(_icon);
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
        _progs.removeAllProgresses();
    }

    public Progresses getProgs()
    {
        return _progs;
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
