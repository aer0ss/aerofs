/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.user.ISessionUser;
import com.aerofs.sp.server.lib.user.User;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertFalse;

/**
 * A mock wrapper of HttpSession "user id" that ignores the concept of sessions. N.B. unlike with
 * the ThreadLocalHttpSessionUser, there are *not* thread-local user ids. The tests that use this
 * class should be single-threaded, so this isn't a problem. Intended use is in {@code
 * LocalSPServiceReactorCaller}.
 */
public class MockSessionUser implements ISessionUser
{
    private User _user;

    @Override
    public @Nonnull UserID getID()
            throws ExNoPerm
    {
        return get().id();
    }

    @Override
    public boolean exists()
    {
        return _user != null;
    }

    @Override
    public @Nonnull User get()
            throws ExNoPerm
    {
        if (_user == null) throw new ExNoPerm();
        return _user;
    }

    @Override
    public void set(@Nonnull User user)
    {
        _user = user;
        assertFalse(_user.toString().isEmpty());
    }

    @Override
    public void remove()
    {
        _user = null;
    }
}
