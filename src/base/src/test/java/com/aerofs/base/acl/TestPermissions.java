/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.acl;

import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.proto.Common.PBPermission;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static com.aerofs.base.acl.Permissions.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPermissions
{
    @Before
    public void setUp()
    {
        ConfigurationProperties.setProperties(new Properties());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromPermissions_shouldThrowOnUnknownFlag()
    {
        Permissions.fromBitmask(4);
    }

    @Test
    public void fromBitmaskAndAllOfShouldBeEquivalent()
    {
        assertEquals(allOf(Permission.WRITE),
                Permissions.fromBitmask(Permission.WRITE.flag()));
        assertEquals(allOf(Permission.MANAGE),
                Permissions.fromBitmask(Permission.MANAGE.flag()));
        assertEquals(allOf(Permission.WRITE, Permission.MANAGE),
                Permissions.fromBitmask(Permission.WRITE.flag() | Permission.MANAGE.flag()));
    }

    @Test
    public void bitmasksShouldBeAsExpected()
    {
        assertEquals(1 << PBPermission.WRITE_VALUE, allOf(Permission.WRITE).bitmask());
        assertEquals(1 << PBPermission.MANAGE_VALUE, allOf(Permission.MANAGE).bitmask());
        assertEquals(0, allOf().bitmask());
        assertEquals(1, allOf(Permission.WRITE).bitmask());
        assertEquals(2, allOf(Permission.MANAGE).bitmask());
        assertEquals(3, allOf(Permission.WRITE, Permission.MANAGE).bitmask());
    }

    @Test
    public void coversShouldReturnExpectedOrder()
    {
        assertTrue(allOf(Permission.WRITE, Permission.MANAGE)
                .covers(allOf(Permission.MANAGE, Permission.WRITE)));
        assertTrue(allOf(Permission.WRITE, Permission.MANAGE)
                .covers(Permission.MANAGE));
        assertTrue(allOf(Permission.WRITE, Permission.MANAGE)
                .covers(Permission.WRITE));
        assertTrue(allOf(Permission.WRITE, Permission.MANAGE)
                .covers(allOf()));

        assertFalse(allOf(Permission.WRITE)
                .covers(allOf(Permission.MANAGE, Permission.WRITE)));
        assertFalse(allOf(Permission.WRITE)
                .covers(Permission.MANAGE));
        assertTrue(allOf(Permission.WRITE)
                .covers(Permission.WRITE));
        assertTrue(allOf(Permission.WRITE)
                .covers(allOf()));

        assertFalse(allOf()
                .covers(allOf(Permission.MANAGE, Permission.WRITE)));
        assertFalse(allOf()
                .covers(Permission.MANAGE));
        assertFalse(allOf()
                .covers(Permission.WRITE));
        assertTrue(allOf()
                .covers(allOf()));
    }
}
