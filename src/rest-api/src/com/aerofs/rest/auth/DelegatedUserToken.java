package com.aerofs.rest.auth;

import com.aerofs.ids.UserID;

public class DelegatedUserToken extends ServiceToken implements IAuthToken {
    private UserID user;

    public DelegatedUserToken(String service, UserID user)
    {
        super(service);
        this.user = user;
    }

    public UserID getUser()
    {
        return user;
    }
}
