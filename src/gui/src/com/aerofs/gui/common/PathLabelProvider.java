/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.common;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;

import java.util.Map;

import static com.aerofs.ui.UIUtil.isSystemFile;

/**
 * Features:
 * - Produces text based on path.
 * - Shortens text based on column width, refreshes on resize.
 * - Produces images based on file type, self-manages image cache and the disposal of.
 */
public class PathLabelProvider extends ColumnLabelProvider
{
    private final TableViewerColumn _column;

    private final GC _gc;
    private final Map<Program, Image> _iconCache;

    public PathLabelProvider(TableViewerColumn column)
    {
        _column = column;

        _gc = new GC(_column.getColumn().getParent());
        _iconCache = Maps.newHashMap();

        _column.getColumn().addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent e)
            {
                _column.getViewer().refresh();
            }
        });

        _column.getColumn().addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent disposeEvent)
            {
                for (Image image : _iconCache.values()) image.dispose();
                _iconCache.clear();
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
        Path path = getPathNullable(element);
        String text = path == null ? "" : UIUtil.getPrintablePath(path.last());
        return GUIUtil.shortenText(_gc, text, _column.getColumn(), true);
    }

    @Override
    public Image getImage(Object element)
    {
        Path path = getPathNullable(element);
        return path == null || isSystemFile(path.toPB())
                ? Images.get(Images.ICON_METADATA) : Images.getFileIcon(path.last(), _iconCache);
    }
}
