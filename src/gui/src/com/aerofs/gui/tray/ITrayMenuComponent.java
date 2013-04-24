/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import org.eclipse.swt.widgets.Menu;

public interface ITrayMenuComponent
{
    public void addListener(ITrayMenuComponentListener l);
    public void populateMenu(Menu menu);
    public void updateInPlace();
}
