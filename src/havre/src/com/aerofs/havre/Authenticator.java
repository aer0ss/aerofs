package com.aerofs.havre;

import com.aerofs.oauth.AuthenticatedPrincipal;
import org.jboss.netty.handler.codec.http.HttpRequest;

public interface Authenticator
{
    public static class UnauthorizedUserException extends Exception
    {
        private static final long serialVersionUID = 0L;
    }

    /**
     * Authenticate a request
     *
     * @param request HttpRequest to authenticate
     * @return user associated with request
     * @throws UnauthorizedUserException
     */
    AuthenticatedPrincipal authenticate(HttpRequest request) throws UnauthorizedUserException;
}
