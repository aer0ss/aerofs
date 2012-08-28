package com.aerofs.gui.tray;

public class SystemTray
{
    private final TrayIcon _icon;
    private final TrayMenu _menu;
    private final Balloons _bm;
    private final Progresses _progs;

    public SystemTray()
    {
        _icon = new TrayIcon(this);
        _progs = new Progresses(this);
        _bm = new Balloons(_icon);
        _menu = new TrayMenu(_icon);
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
        _menu.dispose();
        _icon.dispose();
        _progs.removeAllProgresses();
    }

    public Progresses getProgs()
    {
        return _progs;
    }

    public TrayMenu getMenu()
    {
        return _menu;
    }
}
