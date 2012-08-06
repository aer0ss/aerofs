package com.aerofs.gui.tray;

public interface IIcon {

    public abstract void dispose();

    public abstract boolean isDisposed();

    public abstract void setToolTipText(String str);

    void setSpin(boolean spin);

    void attachMenu(SystemTray st, IMenu menu);

    void showNotification(boolean b);

}
