/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.ex.ExNotAuthenticated;

import javax.annotation.Nonnull;

/**
 * This interface is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISession
{
    /**
     * @return the user of this session
     * @throws ExNotAuthenticated if no user has been setUser for the session (i.e. does not exist).
     */
    @Nonnull User getUser() throws ExNotAuthenticated;

    /**
     * Return whether setUser() has been called
     */
    boolean exists();

    /**
     * Return whether the current session is authenticated.
     */
    // TODO: isAuthenticated();

    /**
     * Set the session user.
     */
    void setUser(User user);

    /**
     * Remove the user ID from this session. A subsequent call to getUserNullable() should throw
     * ExNoPerm, unless the ID has been set again.
     */
    void remove();

    /**
     * Get unique identifier associated with this session.
     */
    String id();
}
