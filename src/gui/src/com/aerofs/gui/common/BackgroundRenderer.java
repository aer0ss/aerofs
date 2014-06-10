/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.common;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;

/**
 * A class that renders the table items background in the Shared Folder and Selective Sync Dialog.
 * In the Shared Folder dialog, we use this class just to render the separator. At some point, we
 * really should try using something other than SWT or using user draw because drawing native
 * controls over TableItems really really messes up
 * (example: http://www.eclipse.org/forums/index.php/t/25319/) on Windows and Linux and we have to
 * have classes such as this to make the UI look presentable. This is rather undesirable
 * Everything works as expected on OSX (Thanks Stevie J and Stevie W).
 */
public class BackgroundRenderer implements Listener
{
    private final Composite _parent;
    private final String _data;
    // This boolean helps to distinguish between Shared Folder dialog and Selective Sync dialog box.
    // It is rather unfortunate that we have to distinguish between them but on Windows and Linux
    // drawing controls over a TableItem results in a hover box being drawn over the table item.
    // This is okay in the Shared Folder dialog since we don't draw anything on top of the table
    // item but in the Selective Sync dialog we have to draw a checkbox which makes the UI look
    // rather ugly. So, the solution is to disable selection and hovering in the Selective Sync
    // dialog box because it is not needed anyways.
    private final boolean _hideSelectionAndHoverBox;

    public BackgroundRenderer(Composite c, String data, boolean hideSelectionAndHoverBox) {
        _parent = c;
        _data = data;
        _hideSelectionAndHoverBox = hideSelectionAndHoverBox;
    }

    @Override
    public void handleEvent(Event event)
    {
        int clientWidth = _parent.getClientArea().width;
        GC gc = event.gc;
        gc.setForeground(event.display.getSystemColor(SWT.COLOR_BLACK));
        if (_hideSelectionAndHoverBox) {
            // This is needed to disable hovering over the table items. On windows, hovering
            // over the table items somehow pops up a small hover box over the table items.
            event.detail &= ~SWT.HOT;
            event.detail &= ~SWT.SELECTED;
            gc.setBackground(event.display.getSystemColor(SWT.COLOR_WHITE));
            gc.fillRectangle(0, event.y, clientWidth, event.height);
        }
        if (event.item.getData(_data) == null) {
            event.detail &= ~SWT.HOT;
            event.detail &= ~SWT.SELECTED;
            // This helps to make no visual changes to the separator when its selected. All
            // though its still logically selected, the user won't get to see the selection.
            gc.setBackground(event.display.getSystemColor(SWT.COLOR_GRAY));
            gc.fillRectangle(0, event.y, clientWidth, event.height);
        }

    }
}