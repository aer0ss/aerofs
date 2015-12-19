package com.aerofs.rest.auth;

import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;

public class DelegatedUserDeviceToken extends ServiceToken implements IUserAuthToken
{
    private final UserID user;
    private final UniqueID did;

    public DelegatedUserDeviceToken(String service, UserID user, UniqueID did)
    {
        super(service);
        this.user = user;
        this.did = did;
    }

    @Override
    public UserID user() {
        return user;
    }

    @Override
    public UserID issuer() {
        return user;
    }

    @Override
    public UniqueID uniqueId() {
        return did;
    }
}
