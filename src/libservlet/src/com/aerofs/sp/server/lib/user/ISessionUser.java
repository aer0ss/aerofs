/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.ex.ExNoPerm;

import javax.annotation.Nonnull;

/**
 * This interace is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISessionUser
{
    /**
     * @return the user of this session
     * @throws ExNoPerm if no user has been set for the session (i.e. does not exist).
     */
    @Nonnull User get() throws ExNoPerm;

    /**
     * Return whether set() has been called
     */
    boolean exists();

    /**
     * Set the session user
     */
    void set(User user);

    /**
     * Remove the user ID from this session. A subsequent call to getUserNullable() should throw
     * ExNoPerm, unless the ID has been set again.
     */
    void remove();

    /**
     * Get the tomcat session ID associated with this session user.
     */
    String getSessionID();
}
