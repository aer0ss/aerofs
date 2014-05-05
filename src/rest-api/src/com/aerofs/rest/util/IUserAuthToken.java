/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.util;

import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;

public interface IUserAuthToken extends IAuthToken
{
    public UserID user();
    public UserID issuer();
    public UniqueID uniqueId();
}
