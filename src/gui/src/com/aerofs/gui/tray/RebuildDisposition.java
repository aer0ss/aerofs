/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

/**
 * This enum exists because of the way SWT, gtk, and libappindicator (Ubuntu tray items) interact.
 *
 * We do things that update the tray menu in-place.  These things do not work for libappindicator.
 * libappindicator has a single setMenu() call, which parses a GtkMenu structure and builds a menu
 * which it exposes over DBus.  However, there appears to be a bug or poor interaction in
 * libappindicator which breaks the following approach:
 *
 * 1) setMenu() some GtkMenu (SWT's native widget for an SWT Menu)
 * 2) change the menu (say, update the transfers section)
 * 3) call setMenu() again on the same GtkMenu
 *
 * The call at 3) will free the "old" underlying GtkMenu and all its children before trying to
 * parse the structure of the new menu, even if they are in the new GtkMenu that you passed in.
 * This makes the new menu invalid, and then libappindicator will show NO MENU AT ALL.
 *
 * So the workaround is to construct a complete new menu every time we want to refresh the UI when
 * using libappindicator.
 *
 * Unfortunately, that doesn't play nicely with the other platforms.
 * On OSX, rebuilding while the menu is displayed causes all menu items to be no longer activatable
 * as the SWT widget has been disposed.
 *
 * So we require that all tray menu-related things be able to both rebuild a whole new menu (for
 * Ubuntu's libappindicator integration) and be able to update a menu in place (for classic SWT
 * menu structures).
 */
public enum RebuildDisposition
{
    REBUILD,
    REUSE
}
