package com.aerofs.sp.server;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.OpenId;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.OpenIdUserManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Store and retrieve OpenIdUser objects, keyed by update tokens.
 *
 * Note that the User object holds the association data for the OpenID provider, so
 * we do keep the instance around for the auth response flow.
 *
 * NOTE: This code expects the DELEGATE_NONCE *attribute* in the HttpServletRequest.
 *
 * TODO: OpenIdUser is serializable, so we could actually store the User object in a db...
 */
public class UserManager implements OpenIdUserManager
{
    @Override
    public void init(Properties properties) { }

    @Override
    public OpenIdUser getUser(HttpServletRequest request) throws IOException
    {
        String token = (String)request.getAttribute(LibParam.Identity.DELEGATE_NONCE);
        return (token == null) ? null : _users.get(token);
    }

    /** return false if the token is missing or the key was already present */
    @Override
    public boolean saveUser(OpenIdUser user,
            HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        String token = (String)request.getAttribute(LibParam.Identity.DELEGATE_NONCE);
        return (token != null) && (_users.putIfAbsent(token, user) == null);
    }

    /** return false if the token is missing or the key was not present */
    @Override
    public boolean invalidate(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        String token = (String)request.getAttribute(LibParam.Identity.DELEGATE_NONCE);
        return (token != null) && (_users.remove(token) != null);
    }

    private ConcurrentMap<String, OpenIdUser> _users = new ConcurrentHashMap<String, OpenIdUser>();
}
