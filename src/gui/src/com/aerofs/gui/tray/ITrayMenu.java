/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

public interface ITrayMenu
{
    void setVisible(boolean b);
    void enable();
    void dispose();
    void addListener(ITrayMenuListener l);
}
