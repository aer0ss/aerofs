/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.auth.IUserAuthToken;

import static com.aerofs.oauth.Scope.*;
import static com.google.common.base.Preconditions.checkArgument;

public class CertAuthToken implements IUserAuthToken
{
    private UserID _userID;
    private DID _deviceID;

    public CertAuthToken(UserID user, DID device)
    {
        _userID = user;
        _deviceID = device;
    }

    @Override
    public UserID user()
    {
        return _userID;
    }

    @Override
    public UserID issuer()
    {
        return _userID;
    }

    @Override
    public UniqueID uniqueId()
    {
        return _deviceID;
    }

    @Override
    public boolean hasPermission(Scope scope)
    {
        if (scope == ORG_ADMIN) return _userID.isTeamServerID();
        return true;
    }

    @Override
    public boolean hasFolderPermission(Scope scope, SID sid)
    {
        checkArgument(Scope.isQualifiable(scope));
        return true;
    }
}
