/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.user.ISessionUser;

import static org.junit.Assert.assertFalse;

/**
 * A mock wrapper of HttpSession "user id" that ignores the concept of sessions. N.B. unlike with
 * the ThreadLocalHttpSessionUser, there are *not* thread-local user ids. The tests that use this
 * class should be single-threaded, so this isn't a problem. Intended use is in {@code
 * LocalSPServiceReactorCaller}.
 */
public class MockSessionUser implements ISessionUser
{
    private UserID _userId;

    @Override
    public UserID getID()
            throws ExNoPerm
    {
        if (_userId == null) throw new ExNoPerm();
        return _userId;
    }

    @Override
    public void setID(UserID userId)
    {
        _userId = userId;
        assertFalse(_userId.toString().isEmpty());
    }

    @Override
    public void remove()
    {
        _userId = null;
    }
}
