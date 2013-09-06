package com.aerofs.daemon.rest.jersey;

import com.aerofs.base.id.UserID;

public class UserIDParam extends AbstractParam<UserID>
{
    public UserIDParam(String input)
    {
        super(input);
    }

    @Override
    protected UserID parse(String input) throws Exception
    {
        return UserID.fromExternal(input);
    }
}
