package com.aerofs.havre;

import com.aerofs.oauth.AuthenticatedPrincipal;

public interface Authenticator
{
    public static class UnauthorizedUserException extends Exception
    {
        private static final long serialVersionUID = 0L;
    }

    /**
     * Authenticate a request
     *
     * @param token Auth token extracted from request
     * @return user associated with request
     * @throws UnauthorizedUserException
     */
    AuthenticatedPrincipal authenticate(String token) throws UnauthorizedUserException;
}
