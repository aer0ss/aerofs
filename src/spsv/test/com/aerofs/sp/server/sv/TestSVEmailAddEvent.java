/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;

import org.junit.Test;

import java.sql.SQLException;

public class TestSVEmailAddEvent extends AbstractSVReactorTest
{
    @Test
    public void shouldAddEventToDatabase()
            throws SQLException
    {
        // TODO This test only verifies that no exceptions are thrown, not that the data is actually
        // properly inputted
        _transaction.begin();
        db.addEmailEvent("test@test.com", "subscribe", null, "test_category", Long.valueOf(0));
        _transaction.commit();
    }
}
