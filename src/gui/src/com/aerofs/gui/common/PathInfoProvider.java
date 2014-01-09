/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.common;

import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nullable;
import java.util.Map;

public class PathInfoProvider
{
    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    public PathInfoProvider(Widget parent)
    {
        // auto-dispose
        parent.addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent disposeEvent)
            {
                dispose();
            }
        });
    }

    public void dispose()
    {
        for (Image image : _iconCache.values()) image.dispose();
        _iconCache.clear();
    }

    public String getFilename(@Nullable Path path)
    {
        return path == null ? "" : UIUtil.getPrintablePath(path.last());
    }

    public Image getFileIcon(@Nullable Path path)
    {
        return path == null || UIUtil.isSystemFile(path.toPB())
                ? Images.get(Images.ICON_METADATA)
                : Images.getFileIcon(path.last(), _iconCache);
    }
}
