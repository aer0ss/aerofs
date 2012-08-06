package com.aerofs.gui.tray;

public class SystemTray
{
    private final ISystemTray _st;
    private final IIcon _icon;
    private final IMenu _menu;
    private final Balloons _bm;
    private final Progresses _progs;

    public SystemTray()
    {
        _st = new SystemTrayImplSWT(this);
        _icon = _st.getTrayIcon();
        _progs = new Progresses(this);
        _bm = new Balloons(_icon);
        _menu = new MenuImplSWT(_icon);

        _icon.attachMenu(this, _menu);
    }

    public Balloons getBalloons()
    {
        return _bm;
    }

    public IIcon getIcon()
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

    public IMenu getMenu()
    {
        return _menu;
    }
}
