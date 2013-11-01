/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.manage;

import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.Viewer;

// used to sort shared folder member entries in the members table
public class SharedFolderMemberComparator extends ViewerComparator
{
    @Override
    public int compare(Viewer v, Object e1, Object e2)
    {
        if (e1 instanceof SharedFolderMember && e2 instanceof SharedFolderMember) {
            return ((SharedFolderMember)e1).compareTo((SharedFolderMember)e2);
        } else {
            // don't try to sort unless both entries are PBSharedFolderMembers
            return 0;
        }
    }
}
