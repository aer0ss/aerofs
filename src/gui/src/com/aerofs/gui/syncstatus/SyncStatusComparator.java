/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatus;
import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatusEntry;import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import static com.aerofs.gui.syncstatus.SyncStatusContentProvider.throwOnInvalidElement;

public class SyncStatusComparator extends ViewerComparator
{
    @Override
    public int compare(Viewer viewer, Object element1, Object element2)
    {
        throwOnInvalidElement(element1);
        throwOnInvalidElement(element2);

        return GUIUtil.aggregateComparisonResults(
                compareByType(element1, element2),
                compareByStatus(element1, element2),
                compareByDisplayName(element1, element2));
    }

    private int compareByType(Object element1, Object element2)
    {
        return getTypeOrdinal(element1) - getTypeOrdinal(element2);
    }

    private int getTypeOrdinal(Object element)
    {
        if (element == SyncStatusContentProvider.ID_DEVICES)            return 0;
        else if (element == SyncStatusContentProvider.ID_NO_DEVICES)    return 1;
        else if (element instanceof SyncStatusEntry
                && ((SyncStatusEntry)element).isLocalUser())            return 2;
        else if (element == SyncStatusContentProvider.ID_USERS)         return 3;
        else if (element == SyncStatusContentProvider.ID_NO_USERS)      return 4;
        else                                                            return 5;
    }

    private int compareByStatus(Object element1, Object element2)
    {
        if (element1 instanceof SyncStatusEntry && element2 instanceof SyncStatusEntry) {
            return getStatusOrdinal(((SyncStatusEntry)element1)._status)
                    - getStatusOrdinal(((SyncStatusEntry)element2)._status);
        } else {
            return 0; // incomparable
        }
    }

    private int getStatusOrdinal(SyncStatus status)
    {
        switch (status) {
        case IN_SYNC:       return 0;
        case IN_PROGRESS:   return 1;
        case OFFLINE:       return 2;
        }

        throw new IllegalArgumentException("Invalid sync status value.");
    }

    private int compareByDisplayName(Object element1, Object element2)
    {
        if (element1 instanceof SyncStatusEntry && element2 instanceof SyncStatusEntry) {
            return ((SyncStatusEntry)element1)._displayName
                    .compareTo(((SyncStatusEntry)element2)._displayName);
        } else {
            return 0; // incomparable
        }
    }
}
