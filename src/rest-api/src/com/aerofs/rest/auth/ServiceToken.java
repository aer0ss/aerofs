package com.aerofs.rest.auth;

import com.aerofs.base.id.SID;
import com.aerofs.oauth.Scope;

import static com.google.common.base.Preconditions.checkState;

public class ServiceToken implements IAuthToken
{
    private final String service;

    public ServiceToken(String service)
    {
        this.service = service;
    }

    public String service()
    {
        return service;
    }

    @Override
    public boolean hasPermission(Scope scope) {
        return true;
    }

    @Override
    public boolean hasFolderPermission(Scope scope, SID sid) {
        checkState(Scope.isQualifiable(scope));
        return true;
    }
}
