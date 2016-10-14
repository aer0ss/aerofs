/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.session;

import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * An object that keeps track of active SP user sessions. Maintains a mapping from user ID to a set
 * of tomcat session ID strings.
 */
public class SPActiveUserSessionTracker
{
    private static final Logger l = Loggers.getLogger(SPActiveUserSessionTracker.class);

    // TODO (WW) use UserID as the map kep.
    private final Map<String, Set<String>> _userMap = Maps.newHashMap();

    public synchronized void signIn(UserID userID, String sessionID)
    {
        l.debug("Sign in: " + userID + " " + sessionID);

        if (_userMap.containsKey(userID.getString())) {
            Set<String> sessionSet = _userMap.get(userID.getString());
            sessionSet.add(sessionID);
        } else {
            Set<String> sessionSet = Sets.newHashSet();
            sessionSet.add(sessionID);
            _userMap.put(userID.getString(), sessionSet);
        }
    }

    /**
     * Remove a specific session belonging to a specific user.
     */
    public synchronized void signOut(UserID userID, String sessionID)
    {
        l.debug("Sign out: " + userID + " " + sessionID);

        Set<String> sessionSet = _userMap.get(userID.getString());

        if (sessionSet != null) {
            sessionSet.remove(sessionID);
        }
    }

    public synchronized Set<String> sessionsForUser(UserID userid)
    {
        return _userMap.get(userid.getString());
    }

    /**
     * Remove all sessions belonging to a given user.
     */
    public synchronized @Nullable Set<String> signOutAll(UserID userID)
    {
        l.info("Sign out all: " + userID);

        Set<String> sessionSet = _userMap.get(userID.getString());
        _userMap.remove(userID.getString());
        return sessionSet;
    }
}
