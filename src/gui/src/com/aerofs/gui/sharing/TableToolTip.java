/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

/**
 * Helper class to add custom dynamic tooltip to each item of a Table
 */
class TableToolTip implements Listener
{
    private Shell _tip = null;
    private final Table _table;
    private final String _toolTipData;

    TableToolTip(Table table, String toolTipData)
    {
        _table = table;
        _toolTipData = toolTipData;

        _table.setToolTipText("");
        _table.addListener(SWT.Dispose, this);
        _table.addListener(SWT.KeyDown, this);
        _table.addListener(SWT.MouseMove, this);
        _table.addListener(SWT.MouseHover, this);
    }

    @Override
    public void handleEvent(Event event) {
        switch (event.type) {
        case SWT.Dispose:
        case SWT.KeyDown:
        case SWT.MouseMove:
            if (_tip == null) break;
            _tip.dispose();
            _tip = null;
            break;
        case SWT.MouseHover:
            Display display = _table.getDisplay();
            TableItem item = _table.getItem(new Point(event.x, event.y));
            if (item != null) {
                if (_tip != null  && !_tip.isDisposed ()) _tip.dispose();
                _tip = new Shell(_table.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
                _tip.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));

                FillLayout layout = new FillLayout();
                layout.marginWidth = 2;
                _tip.setLayout(layout);

                Label label = new Label(_tip, SWT.NONE);
                label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
                label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                label.setData("_TABLEITEM", item);
                label.setText((String)item.getData(_toolTipData));
                label.addListener(SWT.MouseExit, labelListener);
                label.addListener(SWT.MouseDown, labelListener);

                Point size = _tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                Rectangle rect = item.getBounds(0);
                Point pt = _table.toDisplay(rect.x, rect.y);
                _tip.setBounds(pt.x, pt.y, size.x, size.y);
                _tip.setVisible(true);
            }
            break;
        default:
            break;
        }
    }

    private final Listener labelListener = new Listener () {
        @Override
        public void handleEvent(Event event) {
            Label label = (Label)event.widget;
            switch (event.type) {
            case SWT.MouseDown:
                Event e = new Event();
                e.item = (TableItem)label.getData("_TABLEITEM");
                // Assuming table is single select, set the selection as if
                // the mouse down event went through to the table
                _table.setSelection(new TableItem [] {(TableItem) e.item});
                _table.notifyListeners(SWT.Selection, e);
                _tip.dispose();
                _table.setFocus();
                break;
            case SWT.MouseExit:
                _tip.dispose();
                break;
            }
        }
    };
}
