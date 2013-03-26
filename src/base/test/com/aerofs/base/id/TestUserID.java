/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.id;

import com.aerofs.base.ex.ExEmptyEmailAddress;
import org.junit.Test;

public class TestUserID
{
    @Test(expected = ExEmptyEmailAddress.class)
    public void fromExternal_shouldThrowOnEmptyID()
            throws ExEmptyEmailAddress
    {
        UserID.fromExternal("");
    }
}
