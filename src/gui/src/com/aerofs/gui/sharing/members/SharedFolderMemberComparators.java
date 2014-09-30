/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.Viewer;

import static com.aerofs.gui.GUIUtil.aggregateComparisonResults;
import static com.aerofs.gui.GUIUtil.compare;
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
                    return aggregateComparisonResults(
                            compareByIsLocalUser(m1, m2),
                            compareByState(m1, m2),
                            compareByHavingNames(m1, m2),
                            compareByLabel(m1, m2));
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
                    return aggregateComparisonResults(
                            compareByIsLocalUser(m1, m2),
                            compareByState(m1, m2),
                            compareByRole(m1, m2),
                            compareByHavingNames(m1, m2),
                            compareByLabel(m1, m2));
                } else {
                    return 0;
                }
            }
        };
    }

    // local user < non-local users
    private static int compareByIsLocalUser(SharedFolderMember a, SharedFolderMember b)
    {
        return compare(a.isLocalUser(), b.isLocalUser());
    }

    // joined members < pending members | left members
    private static int compareByState(SharedFolderMember a, SharedFolderMember b)
    {
        return compare(a._state == JOINED, b._state == JOINED);
    }

    // members with names < members with only emails (hasn't signed up)
    private static int compareByHavingNames(SharedFolderMember a, SharedFolderMember b)
    {
        return compare(a.hasName(), b.hasName());
    }

    // alphabetical order of the label
    private static int compareByLabel(SharedFolderMember a, SharedFolderMember b)
    {
        return a.getLabel().compareTo(b.getLabel());
    }

    // owner < editor < viewer
    private static int compareByRole(SharedFolderMember a, SharedFolderMember b)
    {
        return a._permissions.compareTo(b._permissions);
    }
}
