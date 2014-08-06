/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.group.Group;
import org.junit.Test;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

import java.sql.SQLException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestGroup_save extends AbstractBusinessObjectTest
{
    private byte[] hexToBytes(String hexString)
    {
        HexBinaryAdapter adapter = new HexBinaryAdapter();
        return adapter.unmarshal(hexString);
    }

    @Test
    public void shouldCreateLocalGroupSuccessfully()
            throws Exception
    {
        String commonName = "My Awesome Group";
        OrganizationID orgID = new OrganizationID(1);

        factOrg.save(orgID);
        Group group = factGroup.save("My Awesome Group", orgID, null);

        // Assertions.
        assertTrue(group.exists());
        assertTrue(group.isLocallyManaged());
        assertEquals(commonName, group.getCommonName());
        assertEquals(orgID, group.getOrganization().id());
    }

    @Test
    public void shouldCreateExternalGroupSuccessfully()
            throws Exception
    {
        String commonName = "My Awesome Group";
        byte[] externalID = hexToBytes("5f7e3ec337f61f285e81d0b23acdffd9c19f30ab");
        OrganizationID orgID = new OrganizationID(1);

        factOrg.save(orgID);
        Group group = factGroup.save("My Awesome Group", orgID, externalID);

        // Assertions.
        assertTrue(group.exists());
        assertTrue(group.isExternallyManaged());
        assertEquals(commonName, group.getCommonName());
        assertEquals(orgID, group.getOrganization().id());
        assertArrayEquals(externalID, group.getExternalIdNullable());
    }

    @Test
    public void shouldReportMissingGroupAsNotExisting()
            throws SQLException
    {
        Group group = factGroup.create(new GroupID(666));
        assertFalse(group.exists());
    }
}
