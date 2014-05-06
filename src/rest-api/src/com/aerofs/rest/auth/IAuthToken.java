/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.auth;

import com.aerofs.base.id.SID;
import com.aerofs.oauth.Scope;

public interface IAuthToken
{
    public boolean hasPermission(Scope scope);
    public boolean hasFolderPermission(Scope scope, SID sid);
}
