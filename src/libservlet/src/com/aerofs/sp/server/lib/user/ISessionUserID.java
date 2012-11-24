/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;

/**
 * This interace is only necessary to enable mocking of the ThreadLocalHttpSessionUser in SPService
 */
public interface ISessionUserID
{
    /**
     * @return the user ID of this session
     * @throws ExNoPerm if no user has been set for the session
     */
    UserID get() throws ExNoPerm;

    /**
     * Set the session user ID.
     */
    void set(UserID userId);

    /**
     * Remove the user ID from this session. A subsequent call to getUserNullable() should throw
     * ExNoPerm, unless the ID has been set again.
     */
    void remove();
}
