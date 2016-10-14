/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

public interface IMenuProvider
{
    public ITrayMenu createMenu(TrayIcon icon, RebuildDisposition disposition);
}
