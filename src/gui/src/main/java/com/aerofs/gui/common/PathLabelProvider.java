/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.common;

import com.aerofs.lib.Path;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.TableColumn;

import static com.aerofs.gui.GUIUtil.shortenText;

/**
 * Note that this component doesn't support multiuser because
 * PathInfoProvider doesn't play well with Block Storage.
 */
public class PathLabelProvider extends ColumnLabelProvider
{
    private final TableColumn _column;
    private final GC _gc;
    private final PathInfoProvider _provider;

    public PathLabelProvider(final TableViewerColumn column)
    {
        _column = column.getColumn();
        _gc = new GC(_column.getParent());
        _provider = new PathInfoProvider(_column);

        _column.addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent e)
            {
                column.getViewer().refresh();
            }
        });
    }

    protected Path getPathNullable(Object element)
    {
        return null;
    }

    @Override
    public String getText(Object element)
    {
        return shortenText(_gc, _provider.getFilename(getPathNullable(element)), _column, true);
    }

    @Override
    public Image getImage(Object element)
    {
        return _provider.getFileIcon(getPathNullable(element));
    }
}
