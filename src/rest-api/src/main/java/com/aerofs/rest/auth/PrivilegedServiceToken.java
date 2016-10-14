package com.aerofs.rest.auth;

/**
 * A token type that indicates *strictly* system-level authorization for a call.
 * A route which requires this type be injected with an @Auth annotation ensures that
 * it will only be callable by clients with system-level authorization.
 */
public class PrivilegedServiceToken extends ServiceToken implements IAuthToken
{
    public PrivilegedServiceToken(String service)
    {
        super(service);
    }
}
