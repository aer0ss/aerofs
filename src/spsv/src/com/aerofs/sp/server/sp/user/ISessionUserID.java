/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp.user;

import com.aerofs.lib.ex.ExNoPerm;

/**
 * This interace is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISessionUserID
{
    /**
     * @return the user ID of this session
     * @throws ExNoPerm if no user has been set for the session
     */
    String getUser() throws ExNoPerm;

    /**
     * Set the session user ID.
     */
    void setUser(String userId);

    /**
     * Remove the user ID from this session. A subsequent call to getUser() should throw ExNoPerm,
     * unless the ID has been set again.
     */
    void removeUser();
}
