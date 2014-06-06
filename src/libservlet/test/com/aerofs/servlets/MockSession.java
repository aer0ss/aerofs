/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets;

import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.lib.user.ISession;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertFalse;

/**
 * A mock wrapper of HttpSession "user id" that ignores the concept of sessions. N.B. unlike with
 * the ThreadLocalHttpSessionUser, there are *not* thread-local user ids. The tests that use this
 * class should be single-threaded, so this isn't a problem. Intended use is in {@code
 * LocalSPServiceReactorCaller}.
 */
public class MockSession implements ISession
{
    private User _user;

    @Override
    public boolean exists()
    {
        return _user != null;
    }

    @Override
    public @Nonnull User getUser()
            throws ExNotAuthenticated
    {
        if (_user == null) throw new ExNotAuthenticated();
        return _user;
    }

    @Override
    public void setUser(@Nonnull User userID)
    {
        _user = userID;
        assertFalse(_user.id().getString().isEmpty());
    }

    @Override
    public void remove()
    {
        _user = null;
    }

    @Override
    public String id()
    {
        // Doesn't matter.
        return "";
    }
}
