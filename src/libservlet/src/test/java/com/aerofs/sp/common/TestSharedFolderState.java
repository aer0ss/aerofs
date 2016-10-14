/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.common;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.proto.Sp.PBSharedFolderState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSharedFolderState
{
    @Test
    public void fromPB_shouldConvertAsExpected()
            throws ExBadArgs
    {
        assertEquals(SharedFolderState.fromPB(PBSharedFolderState.PENDING), SharedFolderState.PENDING);
        assertEquals(SharedFolderState.fromPB(PBSharedFolderState.JOINED), SharedFolderState.JOINED);
        assertEquals(SharedFolderState.fromPB(PBSharedFolderState.LEFT),
                SharedFolderState.LEFT);
    }

    @Test
    public void fromOrdinal_shouldConvertAsExpected()
            throws ExBadArgs
    {
        // DO keep these hard-coded numbers (rather than changing them to use
        // SharedFolderState.LEFT.ordinal(), since our database saves the numbers persistently.
        // This test guarantee these persistently stored numbers matches the ordinals.
        assertEquals(SharedFolderState.fromOrdinal(2), SharedFolderState.LEFT);
        assertEquals(SharedFolderState.fromOrdinal(1), SharedFolderState.PENDING);
        assertEquals(SharedFolderState.fromOrdinal(0), SharedFolderState.JOINED);
    }

    @Test(expected = AssertionError.class)
    public void fromOrdinal_shouldThrowOnUnknownOrdinal()
            throws ExBadArgs
    {
        SharedFolderState.fromOrdinal(100);
    }
}