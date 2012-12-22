/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.UserID;

import javax.annotation.Nonnull;

/**
 * This interace is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISessionUser
{
    /**
     * @return the user of this session
     * @throws ExNoPerm if no user has been set for the session
     */
    @Nonnull User get() throws ExNoPerm;

    /**
     * DEPRECATED. Use get() instead
     * @return the user ID of this session
     * @throws ExNoPerm if no user has been set for the session
     */
    @Nonnull UserID getID() throws ExNoPerm;

    /**
     * Set the session user
     */
    void set(User user);

    /**
     * Remove the user ID from this session. A subsequent call to getUserNullable() should throw
     * ExNoPerm, unless the ID has been set again.
     */
    void remove();
}
