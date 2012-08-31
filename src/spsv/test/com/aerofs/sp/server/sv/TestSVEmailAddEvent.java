/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import com.aerofs.lib.spsv.sendgrid.Event;

import java.sql.SQLException;

public class TestSVEmailAddEvent extends AbstractSVReactorTest
{
    private static final String EMAIL = "test@test.com";
    private static final Event EVENT = Event.UNSUBSCRIBE;
    private static final String DESC = "description";
    private static final String CATEGORY = "test_category";
    private static final Long TIMESTAMP = Long.valueOf(0);

    @Test
    public void shouldAddEventToDatabase()
            throws SQLException
    {
        EmailEvent event = new EmailEvent(EMAIL,
                EVENT,
                DESC,
                CATEGORY,
                TIMESTAMP);

        _transaction.begin();

        int id = db.addEmailEvent(event);
        EmailEvent ee = db.getEmailEvent(id);

        _transaction.commit();

        assertEquals(ee, event);
    }
}
