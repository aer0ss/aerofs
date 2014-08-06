/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestGroup_modify extends AbstractBusinessObjectTest
{
    @Test
    public void shouldModifyExistingGroupName()
            throws Exception
    {
        String originalCommonName = "Orignal Common Name";
        String newCommonName = "New Common Name";

        OrganizationID orgID = new OrganizationID(1);
        factOrg.save(orgID);
        Group group = factGroup.save(originalCommonName, orgID, null);

        // Assertions.
        group.setCommonName(newCommonName);
        assertEquals(newCommonName, group.getCommonName());
    }

    @Test
    public void shouldNotDuplicateGroupsOnUpdate()
            throws Exception
    {
        OrganizationID orgID = new OrganizationID(1);
        Organization org = factOrg.save(orgID);
        Group group = factGroup.save("A", orgID, null);

        // Assertions.
        group.setCommonName("B");
        assertEquals(1, org.listGroups(10, 0).size());
    }
}