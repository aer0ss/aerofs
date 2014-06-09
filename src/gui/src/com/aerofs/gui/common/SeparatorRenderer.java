/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.common;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;

/**
 * A class that renders the separator in the Shared Folder and Selective Sync Dialog.
 */
public class SeparatorRenderer implements Listener
{
    private final Table _parentTable;
    private final String _data;
    private final Display _display;

    public SeparatorRenderer(Display display, Table parentTable, String data) {
        _parentTable = parentTable;
        _data = data;
        _display = display;
    }

    @Override
    public void handleEvent(Event event)
    {
        int clientWidth = _parentTable.getClientArea().width;
        GC gc = event.gc;
        // This is needed to disable hovering over the table items. On windows, hovering
        // over the table items somehow pops up a small hover box over the table items.
        if((event.detail & SWT.HOT) != 0 ){
            event.detail &= ~SWT.HOT;
        }
        if (event.item.getData(_data) == null) {
            event.detail &= ~SWT.SELECTED;
            // This helps to make no visual changes to the separator when its selected. All
            // though its still logically selected, the user won't get to see the selection.
            gc.setBackground(_display.getSystemColor(SWT.COLOR_GRAY));
            gc.setForeground(_display.getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(0, event.y, clientWidth, event.height);
        }
    }
}