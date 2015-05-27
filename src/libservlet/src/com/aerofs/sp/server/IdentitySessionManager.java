/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPooledConnection;

/**
 * A facade for setting, querying, and invalidating authentication sessions.
 * An "authentication session" is the context for a single signin procedure, from initial auth
 * request through to approval, and may involve two client-side actors: the actual client and the
 * User-Agent.
 *
 * FIX THIS: I would like to uses some typeinfo to avoid accidental confusion between
 * Session nonces and DeviceAuthorization nonces. Or perhaps extract some common functions
 * into generics and provide two different views of this class.
 *
 * --
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
    @Inject
    public IdentitySessionManager(PooledJedisConnectionProvider provider)
    {
        _jedisConProvider = provider;
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
     * Use a session nonce to get the user attributes provided by the OpenID delegate flow.
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
     *  - throws ExExternalAuthFailure: bad sessionNonce.
     *
     */
    IdentitySessionAttributes getSession(String sessionNonce) throws ExExternalAuthFailure
    {
        String session = getAndDelete(getSessionKey(sessionNonce));
        return (session == null) ? null : unmarshallUser(session);
    }

    /**
     * Mark a particular session as authenticated. This can only succeed once for a given delegate
     * @throws ExExternalAuthFailure the delegate nonce is invalid. Give up and don't try again.
     */
    void authenticateSession(String delegateNonce, int timeoutSecs, IdentitySessionAttributes attrs)
            throws ExExternalAuthFailure
    {
        JedisPooledConnection connection = _jedisConProvider.getConnection();

        try {
            String session = connection.getSet(getDelegateKey(delegateNonce), "");

            if (session == null || session.isEmpty()) {
                // Could be a bogus nonce, or expired, or a replay attack...
                l.warn("Failed to authenticate delegate nonce {}", delegateNonce);
                throw new ExExternalAuthFailure();
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
     * The Redis missing method: get the value of key and then delete it.
     * Throws an exception if the key is unset or already deleted.
     *
     * Returns null if the key is associated with an empty string.
     */
    private String getAndDelete(String key) throws ExExternalAuthFailure
    {
        JedisPooledConnection connection = _jedisConProvider.getConnection();

        try {
            // just an anonymous scope block. You know, for style.
            {
                String val = connection.get(key);

                if ((val == null) || val.equals(REDIS_DELETING)) {
                    l.warn("Invalid nonce {}");
                    throw new ExExternalAuthFailure();
                }

                if (val.isEmpty()) return null;
            }

            // DOUBLE-CHECKED LOCK: aha! So the nonce is associated. Can we own it?
            String val = connection.getSet(key, REDIS_DELETING);
            if (val == null || val.equals(REDIS_DELETING)) {
                throw new ExExternalAuthFailure();
            }
            assert val.isEmpty() == false : "Impossible state";

            // delete the nonce, it has now been used once...
            connection.del(val);
            return val;
        } finally { connection.returnResource(); }
    }

    /** Generate a securely-random string for use as a nonce. */
    private String generateNonce() {return UniqueID.generate().toStringFormal();}

    /**
     * Create a device-authorization record for this user.
     *
     * For locality, we count on the caller to validate that the user in question actually exists.
     * If you send me a user that doesn't exist, you are a bad person.
     *
     * Returns a self-destructing nonce that can be used to authorize a single device.
     */
    String createDeviceAuthorizationNonce(User user, int lifetimeSecs)
    {
        String deviceAuthNonce = generateNonce();

        JedisPooledConnection connection = _jedisConProvider.getConnection();
        connection.setex(toDeviceAuthKey(deviceAuthNonce), lifetimeSecs, user.id().getString());
        connection.returnResource();

        return deviceAuthNonce;
    }

    /**
     * For a given device authorization, return the user that auth'ed the device and remove
     * the authorization nonce. This throws an exception if the nonce is invalid.
     * @throws ExExternalAuthFailure the given authorization nonce is not valid for
     * authorizing devices.
     */
    public UserID getAuthorizedDevice(String deviceAuthNonce)
            throws ExExternalAuthFailure, ExInvalidID
    {
        String userid = getAndDelete(toDeviceAuthKey(deviceAuthNonce));
        assert userid != null : "Impossible backing-store state";

        return UserID.fromExternal(userid);
    }

    private static String toDeviceAuthKey(final String val) { return PREFIX_DEVICEAUTH + val; }
    private static String getSessionKey(final String val) { return PREFIX_SESSION + val; }
    private static String getDelegateKey(final String val) { return PREFIX_DELEGATE + val; }

    protected PooledJedisConnectionProvider _jedisConProvider;

    private static final Logger l = Loggers.getLogger(IdentitySessionManager.class);
    private static final String PREFIX_SESSION    = "sp:op:s:/";
    private static final String PREFIX_DELEGATE   = "sp:op:d:/";
    private static final String PREFIX_DEVICEAUTH = "sp:op:a:/";
    private static final String REDIS_DELETING    = "d";
}
