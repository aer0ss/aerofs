/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import org.junit.Test;

public class TestUserID
{
    @Test(expected = ExInvalidID.class)
    public void fromExternal_shouldThrowOnEmptyID()
            throws ExInvalidID
    {
        UserID.fromExternal("");
    }
}
