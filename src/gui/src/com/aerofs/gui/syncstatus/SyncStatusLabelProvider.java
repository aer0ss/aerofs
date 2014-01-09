/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatus;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatusEntry;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;

import static com.aerofs.gui.syncstatus.SyncStatusContentProvider.isSectionHeader;
import static com.aerofs.gui.syncstatus.SyncStatusContentProvider.throwOnInvalidElement;

public class SyncStatusLabelProvider extends ColumnLabelProvider
{
    private final Control _control;

    public SyncStatusLabelProvider(Control control)
    {
        _control = control;
    }

    @Override
    public Font getFont(Object element)
    {
        throwOnInvalidElement(element);
        return isSectionHeader(element)
                ? GUIUtil.makeBold(_control.getFont())
                : super.getFont(element);
    }

    @Override
    public Image getImage(Object element)
    {
        throwOnInvalidElement(element);
        return element instanceof SyncStatusEntry
                ? getStatusIcon(((SyncStatusEntry)element)._status)
                : super.getImage(element);
    }

    @Override
    public String getText(Object element)
    {
        throwOnInvalidElement(element);
        return element instanceof SyncStatusEntry
                ? ((SyncStatusEntry)element).getDisplayName()
                : (String)element;
    }

    @Override
    public String getToolTipText(Object element)
    {
        throwOnInvalidElement(element);
        return element instanceof SyncStatusEntry
                ? getToolTipText((SyncStatusEntry)element)
                : super.getToolTipText(element);
    }

    @Override
    public Color getForeground(Object element)
    {
        throwOnInvalidElement(element);
        return element instanceof SyncStatusEntry
                && ((SyncStatusEntry)element)._status == SyncStatus.OFFLINE
                ? SWTResourceManager.getColor(SWT.COLOR_DARK_GRAY)
                : super.getForeground(element);
    }

    @Override
    public Color getBackground(Object element)
    {
        throwOnInvalidElement(element);
        return isSectionHeader(element)
                ? SWTResourceManager.getColor(0xF0, 0xF0, 0xF0)
                : super.getBackground(element);
    }

    private Image getStatusIcon(SyncStatus status)
    {
        switch (status) {
        case IN_SYNC:       return Images.get(Images.SS_IN_SYNC);
        case IN_PROGRESS:   return Images.get(Images.SS_IN_PROGRESS);
        case OFFLINE:       return Images.get(Images.SS_OFFLINE_NOSYNC);
        }

        throw new IllegalArgumentException("Invalid sync status value.");
    }

    private String getToolTipText(SyncStatusEntry entry)
    {
        switch (entry._status) {
        case IN_SYNC:       return "Synced to " + entry.getDisplayName();
        case IN_PROGRESS:   return "Syncing to " + entry.getDisplayName() + " is in progress";
        case OFFLINE:       return entry.getDisplayName() + " is currently offline";
        }

        throw new IllegalArgumentException("Invalid sync status value.");
    }
}
