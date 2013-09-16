/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package aerofs.base.acl;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Common.PBRole;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRole
{
    @Test
    public void fromString_shouldConvertAsExpected()
            throws ExBadArgs
    {
        assertEquals(Role.fromString("OWNER"), Role.OWNER);
        assertEquals(Role.fromString("EDITOR"), Role.EDITOR);
        assertEquals(Role.fromString("VIEWER"), Role.VIEWER);
    }

    @Test(expected = ExBadArgs.class)
    public void fromString_shouldThrowOnUnknownRole()
            throws ExBadArgs
    {
        Role.fromString("OWNER2");
    }

    @Test
    public void fromPB_shouldConvertAsExpected()
            throws ExBadArgs
    {
        assertEquals(Role.fromPB(PBRole.OWNER), Role.OWNER);
        assertEquals(Role.fromPB(PBRole.EDITOR), Role.EDITOR);
        assertEquals(Role.fromPB(PBRole.VIEWER), Role.VIEWER);
    }

    @Test
    public void fromOrdinal_shouldConvertAsExpected()
            throws ExBadArgs
    {
        // DO keep these hard-coded numbers (rather than changing them to use Role.OWNER.ordinal(),
        // since our database saves the numbers persistently. This test guarantee these persistently
        // stored numbers matches the ordinals.
        assertEquals(Role.fromOrdinal(2), Role.OWNER);
        assertEquals(Role.fromOrdinal(1), Role.EDITOR);
        assertEquals(Role.fromOrdinal(0), Role.VIEWER);
    }

    @Test(expected = AssertionError.class)
    public void fromOrdinal_shouldThrowOnUnknownOrdinal()
            throws ExBadArgs
    {
        Role.fromOrdinal(100);
    }

    @Test
    public void covers_shouldReturnExpectedOrder()
    {
        assertTrue(Role.OWNER.covers(Role.OWNER));
        assertTrue(Role.OWNER.covers(Role.EDITOR));
        assertTrue(Role.OWNER.covers(Role.VIEWER));

        assertFalse(Role.EDITOR.covers(Role.OWNER));
        assertTrue(Role.EDITOR.covers(Role.EDITOR));
        assertTrue(Role.EDITOR.covers(Role.VIEWER));

        assertFalse(Role.VIEWER.covers(Role.OWNER));
        assertFalse(Role.VIEWER.covers(Role.EDITOR));
        assertTrue(Role.VIEWER.covers(Role.VIEWER));
    }

    @Test
    public void getDescription_shouldReturneExpectedStrings()
    {
        assertEquals(Role.OWNER.getDescription(), "OWNER");
        assertEquals(Role.EDITOR.getDescription(), "EDITOR");
        assertEquals(Role.VIEWER.getDescription(), "VIEWER");
    }
}
