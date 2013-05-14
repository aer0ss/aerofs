/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.tray;

import com.aerofs.gui.tray.IMenuProvider;
import com.aerofs.gui.tray.ITrayMenu;
import com.aerofs.gui.tray.RebuildDisposition;
import com.aerofs.gui.tray.TrayIcon;

public class MultiuserMenuProvider implements IMenuProvider
{
    @Override
    public ITrayMenu createMenu(TrayIcon icon, RebuildDisposition disposition)
    {
        return new MultiuserTrayMenu(icon, disposition);
    }
}
