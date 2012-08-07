/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.srvlib.sp.user.ISessionUserID;

import static org.junit.Assert.assertFalse;

/**
 * A mock wrapper of HttpSession "user id" that ignores the concept of sessions.
 * N.B. unlike with the ThreadLocalHttpSessionUser, there are *not* thread-local user ids. The
 * tests that use this class should be single-threaded, so this isn't a problem.
 * Intended use is in {@code LocalSPServiceReactorCaller}
 */
public class MockSessionUserID implements ISessionUserID
{
    private String _userId;

    @Override
    public String getUser()
            throws ExNoPerm
    {
        if (_userId == null) throw new ExNoPerm();
        return _userId;
    }

    @Override
    public void setUser(String userId)
    {
        _userId = userId;
        assertFalse(_userId.isEmpty());
    }

    @Override
    public void removeUser()
    {
        _userId = null;
    }
}
