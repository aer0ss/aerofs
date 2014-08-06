/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class TestOrganization_ListGroups extends AbstractBusinessObjectTest
{
    private final OrganizationID orgId = new OrganizationID(1);
    Organization org = factOrg.create(orgId);

    private final int NUMBER_OF_GROUPS_WITH_PREFIX_1 = 10;
    private final int NUMBER_OF_GROUPS_WITH_PREFIX_2 = 5;
    private final int TOTAL_GROUPS =
            NUMBER_OF_GROUPS_WITH_PREFIX_1 + NUMBER_OF_GROUPS_WITH_PREFIX_2;

    private final String PREFIX_1 = "Marketing";
    private final String PREFIX_2 = "Sales";

    @Before
    public void setup()
            throws Exception
    {
        odb.insert(orgId);

        for (int i = 0; i < NUMBER_OF_GROUPS_WITH_PREFIX_1; i++) {
            factGroup.save(PREFIX_1 + " " + i, orgId, null);
        }

        for (int i = 0; i < NUMBER_OF_GROUPS_WITH_PREFIX_2; i++) {
            factGroup.save(PREFIX_2 + " " + i, orgId, null);
        }
    }

    @Test
    public void shouldListAllGroupsForListGroups()
            throws Exception
    {
        Collection<Group> groups = org.listGroups(TOTAL_GROUPS, 0, PREFIX_1);
        assertEquals(NUMBER_OF_GROUPS_WITH_PREFIX_1, groups.size());
    }
}