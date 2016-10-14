/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.auth;

import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;

public interface IUserAuthToken extends IAuthToken
{
    public UserID user();
    public UserID issuer();
    public UniqueID uniqueId();
}
