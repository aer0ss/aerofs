/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui;

import com.aerofs.gui.GUI;

/**
 * The only purpose of this class is to hold the IUI singleton object. Do not add other
 * responsibilities to this class.
 */
public class UI
{
    // this will be checked to verify if _ui is set.
    private static IUI _ui = null;

    public static void set(IUI ui)
    {
        _ui = ui;
    }

    public static IUI get()
    {
        return _ui;
    }

    public static boolean isGUI()
    {
        return _ui instanceof GUI;
    }
}
