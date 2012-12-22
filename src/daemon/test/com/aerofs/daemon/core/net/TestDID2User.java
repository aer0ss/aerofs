/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.DID2UserDatabase;
import com.aerofs.lib.db.InMemorySQLiteDBCW;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;

public class TestDID2User extends AbstractTest
{
    InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();
    DID2UserDatabase db = new DID2UserDatabase(dbcw.getCoreDBCW());
    DID2User did2User;

    DID did = new DID(UniqueID.generate());

    @Before
    public void setup()
            throws SQLException
    {
        dbcw.init_();
        did2User = new DID2User(null, null, db, null);
    }

    ////////
    // enforcement testing

    @Test(expected = SQLException.class)
    public void shouldFailOnDuplicateAdditions()
            throws SQLException
    {
        did2User.addToLocal_(did, UserID.fromInternal("hohoho"), null);
        did2User.addToLocal_(did, UserID.fromInternal("hohoho"), null);
    }

    ////////
    // logic testing

    @Test
    public void shouldReturnNullForNonExistentDID()
            throws SQLException
    {
        assertEquals(did2User.getFromLocalNullable_(did), null);
    }

    @Test
    public void shouldReturnAddedMapping()
            throws SQLException
    {
        UserID userId = UserID.fromInternal("hohoho");
        did2User.addToLocal_(did, userId, null);
        assertEquals(did2User.getFromLocalNullable_(did), userId);
    }
}
