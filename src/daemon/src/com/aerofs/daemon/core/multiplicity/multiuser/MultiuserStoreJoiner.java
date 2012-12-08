/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.UserID;
import com.google.inject.Inject;

import java.util.Map;
import java.util.Map.Entry;

public class MultiuserStoreJoiner implements IStoreJoiner
{
    private final StoreCreator _sc;

    @Inject
    public MultiuserStoreJoiner(StoreCreator sc)
    {
        _sc = sc;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, Map<UserID, Role> newRoles,
            Trans t) throws Exception
    {
        if (isRootStore(sid, newRoles)) {
            _sc.createRootStore_(sid, MultiuserPathResolver.getStorePath(sid), t);
        }
    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Map<UserID, Role> newRoles, Trans t)
            throws Exception
    {
        if (isRootStore(sid, newRoles)) {
            // TODO:
        }
    }

    /**
     * Return whether the SID identifies a root store by examing the users in its ACL.
     *
     * An SID is a root store if and only if 1) there is a single non-team-server user in the ACL,
     * and 2) the user is an owner, and 3) the SID is that user's root store ID.
     */
    private boolean isRootStore(SID sid, Map<UserID, Role> roles)
    {
        boolean hasMatchingUserID = false;
        int nonTeamServerUserCount = 0;
        for (Entry<UserID, Role> en : roles.entrySet()) {
            UserID userID = en.getKey();
            // ignore team server IDs
            if (userID.isTeamServerID()) continue;

            // can't have more than one non-team-server users in the ACL
            if (nonTeamServerUserCount++ != 0) return false;

            if (en.getValue() == Role.OWNER && SID.rootSID(userID).equals(sid)) {
                hasMatchingUserID = true;
            }
        }

        return hasMatchingUserID;
    }
}
