/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.dyuproject.openid.Association;
import com.dyuproject.openid.Constants;
import com.dyuproject.openid.DiffieHellmanAssociation;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.OpenIdUserManager;
import com.dyuproject.util.DiffieHellman;
import com.dyuproject.util.http.HttpConnector.Response;
import com.dyuproject.util.http.UrlEncodedParameterMap;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPooledConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A facade for setting, querying, and invalidating authentication sessions.
 * An "authentication session" is the context for a single signin procedure, from initial auth
 * request through to approval, and may involve two client-side actors: the actual client and the
 * User-Agent.
 *
 * The session is identified by two nonces:
 *
 * Session nonce can be queried many times. It can successfully get user attributes at most once.
 *
 * Delegate nonce can not be queried. It can be used - once - to authenticate a session nonce,
 * i.e., mark that session as authenticated and record the user attributes.
 *
 * User attributes are: Email, First Name, and Last Name.
 *
*/
public class IdentitySessionManager
{
    public IdentitySessionManager()
    {
        _jedisConProvider = new PooledJedisConnectionProvider();
        // TODO: configure this better: could be a transient redis install
        _jedisConProvider.init_(
                REDIS.ADDRESS.get().getHostName(),
                (short)REDIS.ADDRESS.get().getPort());
    }

    String createSession(int lifetimeSecs)
    {
        String sessionNonce = UniqueID.generate().toStringFormal();

        JedisPooledConnection connection = _jedisConProvider.getConnection();
        connection.setex(getSessionKey(sessionNonce), lifetimeSecs, "");
        connection.returnResource();

        return sessionNonce;
    }

    String createDelegate(String sessionNonce, int lifetimeSecs)
    {
        String delegateNonce = UniqueID.generate().toStringFormal();

        JedisPooledConnection connection = _jedisConProvider.getConnection();
        connection.setex(getDelegateKey(delegateNonce), lifetimeSecs, sessionNonce);
        connection.returnResource();

        return delegateNonce;
    }

    /**
     * Use a session nonce to get the user attributes provided by the OpenId delegate flow.
     *
     * This can only succeed (return user attributes) once; note the double-checked lock here. No
     * need for a multi-step redis transaction.
     *
     * Three possible outcomes:
     *
     *  - valid IdentitySessionAttributes object : account is authenticated; key will be atomically deprecated.
     *
     *  - null : session nonce exists but is not yet authenticated
     *
     *  - throws ExBadCredential: bad sessionNonce.
     *
     */
    IdentitySessionAttributes getSession(String sessionNonce) throws ExBadCredential
    {
        JedisPooledConnection connection = _jedisConProvider.getConnection();

        try {
            // just an anonymous scope block. You know, for style.
            {
                String session = connection.get(getSessionKey(sessionNonce));

                if ((session == null) || session.equals(REDIS_DELETING)) {
                    l.warn("Invalid session nonce {}", sessionNonce);
                    throw new ExBadCredential("No such session nonce");
                }

                if (session.isEmpty()) { // Valid but unauthenticated
                    return null;
                }
            }

            // DOUBLE-CHECKED LOCK: aha! So the session is authenticated. Can we own it?
            String session = connection.getSet(getSessionKey(sessionNonce), REDIS_DELETING);
            if (session == null || session.equals(REDIS_DELETING)) {
                throw new ExBadCredential("No such session nonce");
            }
            assert session.isEmpty() == false : "Impossible state";

            // delete the session nonce, it has now been used once...
            connection.del(getSessionKey(sessionNonce));

            return unmarshallUser(session);
        } finally { connection.returnResource(); }
    }

    /**
     * Mark a particular session as authenticated. This can only succeed once for a given delegate
     * @throws ExBadCredential the delegate nonce is invalid. Give up and don't try again.
     */
    void authenticateSession(String delegateNonce, int timeoutSecs, IdentitySessionAttributes attrs)
            throws ExBadCredential
    {
        JedisPooledConnection connection = _jedisConProvider.getConnection();

        try {
            String session = connection.getSet(getDelegateKey(delegateNonce), "");

            if (session == null || session.isEmpty()) {
                // Could be a bogus nonce, or expired, or a replay attack...
                l.warn("Failed to authenticate delegate nonce {}", delegateNonce);
                throw new ExBadCredential("Failed to authenticate delegate");
            }

            // from here on, we are the only actor with the session key...
            connection.del(getDelegateKey(delegateNonce));
            connection.setex(getSessionKey(session), timeoutSecs, marshallUser(attrs));

        } finally { connection.returnResource(); }
    }

    /**
     * Marshall the user attributes into a string (so we can use getset). Really stupid.
     */
    private String marshallUser(IdentitySessionAttributes user)
    {
        return user.getEmail() + '\n' + user.getFirstName() + '\n' + user.getLastName();
    }

    /**
     * Unmarshall a Redis string into user attributes. Pretty stupid.
     */
    private IdentitySessionAttributes unmarshallUser(String userStr)
    {
        String[] userElems = userStr.split("\n");
        assert userElems.length == 3 : "Marshalling error";
        return new IdentitySessionAttributes(userElems[0], userElems[1], userElems[2]);
    }

    /**
     * Store and retrieve OpenIdUser objects, keyed by update tokens.
     *
     * Note that the User object holds the association data for the OpenId provider, so
     * we do keep the instance around for the auth response flow.
     *
     * NOTE: This code expects the OPENID_DELEGATE_NONCE *attribute* in the HttpServletRequest.
     *
     * TODO: OpenIdUser is serializable, so we could actually store the User object in a db...
     */
    static class UserManager implements OpenIdUserManager
    {
        @Override
        public void init(Properties properties) { }

        @Override
        public OpenIdUser getUser(HttpServletRequest request) throws IOException
        {
            String token = (String)request.getAttribute(OpenId.OPENID_DELEGATE_NONCE);
            return (token == null) ? null : _users.get(token);
        }

        /** return false if the token is missing or the key was already present */
        @Override
        public boolean saveUser(OpenIdUser user,
                HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            String token = (String)request.getAttribute(OpenId.OPENID_DELEGATE_NONCE);
            return (token != null) && (_users.putIfAbsent(token, user) == null);
        }

        /** return false if the token is missing or the key was not present */
        @Override
        public boolean invalidate(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            String token = (String)request.getAttribute(OpenId.OPENID_DELEGATE_NONCE);
            return (token != null) && (_users.remove(token) != null);
        }

        private ConcurrentMap<String, OpenIdUser> _users = new ConcurrentHashMap<String, OpenIdUser>();
    }

    // This class is used in place of DiffieHelman for OpenId servers that are too...
    // primitive? Dumb? to use the required association model.
    // On "associate" we simply say "yes" and hack up the user association; without this,
    // the dyu library will fail saying that the user is not associated.
    // On "verify" we create a dumb-mode identification request to the server.
    static class DumbAssociation implements Association
    {
        @Override
        public boolean associate(OpenIdUser user, OpenIdContext context) throws Exception
        {
            // Ugly. We can't do the equivalent of setAssocHandle() any other way than the following
            Map<String, Object> hashMap = new HashMap<String, Object>();
            hashMap.put("a", user.getClaimedId());
            hashMap.put("c", Constants.Assoc.ASSOC_HANDLE);
            hashMap.put("d", new HashMap());
            hashMap.put("e", user.getOpenIdServer());

            user.fromJSON(hashMap);

            return true;
        }

        @Override
        public boolean verifyAuth(OpenIdUser user, Map<String, String> authRedirect, OpenIdContext context)
                throws Exception
        {
            if(!Constants.Mode.ID_RES.equals(authRedirect.get(Constants.OPENID_MODE))) {
                return false;
            }

            l.debug("OpenId using stateless auth-verify mode");

            // Build our new request by starting with everything from the authRedirect map
            UrlEncodedParameterMap map = new UrlEncodedParameterMap(user.getOpenIdServer());

            map.putAll(authRedirect);
            map.remove(Constants.OPENID_MODE);
            map.put(Constants.OPENID_MODE, "check_authentication");

            Response response = context.getHttpConnector().doPOST(
                    user.getOpenIdServer(), (Map<?,?>)null,
                    map, Constants.DEFAULT_ENCODING);

            BufferedReader br = null;
            Map<String, Object> results = new HashMap<String, Object>();
            try
            {
                br = new BufferedReader(new InputStreamReader(
                        response.getInputStream(), Constants.DEFAULT_ENCODING), 1024);
                DiffieHellmanAssociation.parseInputByLineSeparator(br, ':', results);
            }
            finally
            {
                if(br!=null)
                    br.close();
            }

            if (results.containsKey("is_valid")) {
                String isValid = results.get("is_valid").toString();
                if (isValid.toLowerCase().equals("true")) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String getSessionKey(final String val) { return PREFIX_SESSION + val; }
    private static String getDelegateKey(final String val) { return PREFIX_DELEGATE + val; }

    private PooledJedisConnectionProvider _jedisConProvider;

    private static final Logger l = Loggers.getLogger(IdentitySessionManager.class);
    private static final String PREFIX_SESSION    = "sp:op:s:/";
    private static final String PREFIX_DELEGATE   = "sp:op:d:/";
    private static final String REDIS_DELETING    = "d";
}