/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.gui.sharing.members.SharedFolderMember.User;
import com.aerofs.sp.common.SharedFolderState;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;

/**
 * This is the base-class for a number of label providers used in a table to show the list of
 * members in a shared folder. It takes care of switching colour and showing tooltip for pending
 * users.
 *
 * N.B. there's no need to override other tooltip-related methods because the default impls
 * do a pretty good job already.
 */
public class SharingLabelProvider extends ColumnLabelProvider
{
    @Override
    public Color getForeground(Object element)
    {
        return SWTResourceManager.getColor(isPendingUser(element) ?
                SWT.COLOR_DARK_GRAY :
                SWT.COLOR_BLACK);
    }

    @Override
    public String getToolTipText(Object element)
    {
        if (element instanceof User) {
            return ((User)element)._userID.getString() +
                    (isPendingUser(element) ? "\nThis user hasn't accepted the invitation yet." : "");
        } else {
            return "";
        }
    }

    private boolean isPendingUser(Object element)
    {
        return element instanceof SharedFolderMember
                && ((SharedFolderMember)element)._state == SharedFolderState.PENDING;
    }
}
