/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.common;

import com.aerofs.gui.Images;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * Note that this component doesn't support multiuser because it
 * doesn't plays well with Block Storage.
 */
public class PathInfoProvider
{
    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    public PathInfoProvider(Widget parent)
    {
        checkState(!L.isMultiuser());

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
        if (path == null) return "";
        return UIUtil.getPrintablePath(
                path.isEmpty() ? UIUtil.sharedFolderName(path, "") : path.last());
    }

    public Image getFileIcon(@Nullable Path path)
    {
        if (path == null || UIUtil.isSystemFile(path.toPB())) return Images.get(Images.ICON_METADATA);
        if (isDirectory(path)) return Images.getFolderIcon();
        if (path.isEmpty()) return Images.get(Images.ICON_LOGO32);
        return Images.getFileIcon(path.last(), _iconCache);
    }

    // N.B. this detection code reports false negatives when block storage is used.
    private boolean isDirectory(@Nullable Path path)
    {
        String absPath = UIUtil.absPathNullable(path);
        return absPath != null && new File(absPath).isDirectory();
    }
}
