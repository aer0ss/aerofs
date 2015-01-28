/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.sharing.SharedFolderMember;
import com.aerofs.gui.sharing.SharedFolderMember.SharedFolderMemberWithPermissions;
import com.aerofs.gui.sharing.Subject.User;
import com.aerofs.sp.common.SharedFolderState;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

/**
 * This is the base-class for a number of label providers used in a table to show the list of
 * members in a shared folder. It takes care of switching colour and showing tooltip for pending
 * users.
 *
 * N.B. there's no need to override other tooltip-related methods because the default impls
 * do a pretty good job already.
 */
public class SharingLabelProviders
{
    public static ColumnLabelProvider forSubject()
    {
        return new SubjectLabelProvider();
    }

    public static ColumnLabelProvider forRole()
    {
        return new RoleLabelProvider();
    }

    public static ColumnLabelProvider forActions(CompUserList parent)
    {
        return new ArrowLabelProvider(parent);
    }

    private static class SharingLabelProvider extends ColumnLabelProvider
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
            if (element instanceof SharedFolderMember) {
                return ((SharedFolderMember)element).getDescription() +
                        (isPendingUser(element) ? "\nThis user hasn't accepted the invitation yet."
                                 : "");
            } else {
                return "";
            }
        }

        private boolean isPendingUser(Object element)
        {
            // check for users because we don't count pending groups
            return element instanceof SharedFolderMember
                    && ((SharedFolderMember)element).getSubject() instanceof User
                    && ((SharedFolderMember)element).getState() == SharedFolderState.PENDING;
        }
    }

    private static class SubjectLabelProvider extends SharingLabelProvider
    {
        @Override
        public Image getImage(Object element)
        {
            if (element instanceof Exception) {
                return Images.get(Images.ICON_WARNING);
            } else if (element instanceof SharedFolderMember) {
                return ((SharedFolderMember)element).getImage();
            } else {
                return null;
            }
        }

        @Override
        public String getText(Object element)
        {
            if (element instanceof Exception) {
                return ((Exception) element).getMessage();
            } else if (element instanceof SharedFolderMember) {
                return ((SharedFolderMember)element).getLabel();
            } else {
                return element.toString();
            }
        }
    }

    private static class RoleLabelProvider extends SharingLabelProvider
    {
        @Override
        public String getText(Object element)
        {
            if (element instanceof SharedFolderMemberWithPermissions) {
                return ((SharedFolderMemberWithPermissions)element).getPermissions().roleName();
            } else {
                return "";
            }
        }
    }

    private static class ArrowLabelProvider extends ColumnLabelProvider
    {
        private final CompUserList _parent;

        public ArrowLabelProvider(CompUserList parent)
        {
            _parent = parent;
        }

        @Override
        public String getText(Object element)
        {
            return element instanceof SharedFolderMember &&
                    SharedFolderMemberMenu.get(_parent._isPrivileged,
                            (SharedFolderMember)element).hasContextMenu()
                    ? "Actions " + GUIUtil.TRIANGLE_DOWNWARD
                    : "";
        }
    }
}
