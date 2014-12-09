/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.google.common.collect.ComparisonChain;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.Viewer;

import static com.aerofs.sp.common.SharedFolderState.JOINED;

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
                    SharedFolderMember m1 = (SharedFolderMember)o1;
                    SharedFolderMember m2 = (SharedFolderMember)o2;

                    return ComparisonChain.start()
                            .compareTrueFirst(m1.isLocalUser(), m2.isLocalUser())
                            .compareTrueFirst(m1._state == JOINED, m2._state == JOINED)
                            .compareTrueFirst(m1.hasName(), m2.hasName())
                            .compare(m1.getLabel(), m2.getLabel())
                            .result();
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
                    SharedFolderMember m1 = (SharedFolderMember)o1;
                    SharedFolderMember m2 = (SharedFolderMember)o2;

                    return ComparisonChain.start()
                            .compareTrueFirst(m1.isLocalUser(), m2.isLocalUser())
                            .compareTrueFirst(m1._state == JOINED, m2._state == JOINED)
                            // we want to sort the permissions such that Pa < Pb iff Pb is a subset
                            // of Pa. This order is opposite from the order defined in Permissions,
                            // thus we reverse the comparison order
                            .compare(m2._permissions, m1._permissions)
                            .compareTrueFirst(m1.hasName(), m2.hasName())
                            .compare(m1.getLabel(), m2.getLabel())
                            .result();
                } else {
                    return 0;
                }
            }
        };
    }
}
