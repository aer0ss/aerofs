/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.group.Group;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestGroup_delete extends AbstractBusinessObjectTest
{
    @Test
    public void shouldSuccessfullyDeleteGroup()
            throws Exception
    {
        OrganizationID orgID = new OrganizationID(123);
        factOrg.save(orgID);
        Group group = factGroup.save("Marketing Team", orgID, null);

        // Assertions.
        assertTrue(group.exists());
        group.delete();
        assertFalse(group.exists());
    }
}