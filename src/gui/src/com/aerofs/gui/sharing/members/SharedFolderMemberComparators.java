/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.Viewer;

/**
 * comparators provided in this class are used to sort shared folder member entries in the
 * shared folder members table.
 *
 * N.B. if the objects are not _both_ SharedFolderMembers, they are considered equivalent.
 */
public class SharedFolderMemberComparators
{
    private SharedFolderMemberComparators()
    {
        // private to prevent instantiation
    }

    public static ViewerComparator bySubject()
    {
        return new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2)
            {
                if (o1 instanceof SharedFolderMember && o2 instanceof SharedFolderMember) {
                    return ((SharedFolderMember)o1).compareToBySubject((SharedFolderMember)o2);
                } else {
                    return 0;
                }
            }
        };
    }

    public static ViewerComparator byRole()
    {
        return new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2)
            {
                if (o1 instanceof SharedFolderMember && o2 instanceof SharedFolderMember) {
                    return ((SharedFolderMember)o1).compareToByRole((SharedFolderMember)o2);
                } else {
                    return 0;
                }
            }
        };
    }
}
