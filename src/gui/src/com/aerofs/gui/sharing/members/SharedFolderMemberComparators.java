/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.gui.sharing.SharedFolderMember;
import com.aerofs.gui.sharing.Subject;
import com.aerofs.gui.sharing.Subject.Group;
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
                    Subject s1 = m1.getSubject();
                    Subject s2 = m2.getSubject();

                    return ComparisonChain.start()
                            .compareTrueFirst(s1.isLocalUser(), s2.isLocalUser())
                            .compareTrueFirst(m1.getState() == JOINED, m2.getState() == JOINED)
                            .compareTrueFirst(s1 instanceof Group, s2 instanceof Group)
                            .compareTrueFirst(s1.hasName(), s2.hasName())
                            .compare(s1.getLabel(), s2.getLabel())
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
                    Subject s1 = m1.getSubject();
                    Subject s2 = m2.getSubject();

                    return ComparisonChain.start()
                            .compareTrueFirst(s1.isLocalUser(), s2.isLocalUser())
                            .compareTrueFirst(m1.getState() == JOINED, m2.getState() == JOINED)
                            .compareTrueFirst(s1 instanceof Group, s2 instanceof Group)
                            // we want to sort the permissions such that Pa < Pb iff Pb is a subset
                            // of Pa. This order is opposite from the order defined in Permissions,
                            // thus we reverse the comparison order
                            .compare(m2.getPermissions(), m1.getPermissions())
                            .compareTrueFirst(s1.hasName(), s2.hasName())
                            .compare(s1.getLabel(), s2.getLabel())
                            .result();
                } else {
                    return 0;
                }
            }
        };
    }
}
