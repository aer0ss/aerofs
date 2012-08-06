package com.aerofs.gui.tray;

// LEGAL: code partially copied from http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet143.java?view=co
//
public class SystemTrayImplSWT implements ISystemTray {

    private final IconImplSWT _icon;

    // throw ExNotFound if system tray is not supported
    public SystemTrayImplSWT(SystemTray st)
    {
        _icon = new IconImplSWT(st);
    }

    @Override
    public IIcon getTrayIcon()
    {
        return _icon;
    }
}
