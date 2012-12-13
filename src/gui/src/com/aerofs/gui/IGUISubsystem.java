package com.aerofs.gui;

import com.aerofs.gui.tray.IMenuProvider;

public interface IGUISubsystem
{
    IMenuProvider getMenuProvider();

    boolean shellExtensionShouldBeInstalled();
}
