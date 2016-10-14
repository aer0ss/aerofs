package com.aerofs.rest.auth;

import com.aerofs.ids.SID;
import com.aerofs.oauth.Scope;

import static com.google.common.base.Preconditions.checkState;

public class ServiceToken implements IAuthToken {
    private final String _service;

    ServiceToken(String service)
    {
        _service = service;
    }

    public String service()
    {
        return _service;
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
