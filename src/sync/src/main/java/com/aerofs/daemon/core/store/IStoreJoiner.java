package com.aerofs.daemon.core.store;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;

import java.util.Set;

public interface IStoreJoiner
{
    public static class StoreInfo
    {
        public final String _name;
        public final boolean _external;
        public final ImmutableMap<UserID, Permissions> _roles;
        public final Set<UserID> _externalMembers;

        // See docs/design/sharing_and_migration.txt for information about the external flag.
        public StoreInfo(String name, boolean external, ImmutableMap<UserID, Permissions> roles,
                         Set<UserID> externalMembers)
        {
            _name = name;
            _external = external;
            _roles = roles;
            _externalMembers = externalMembers;
        }
    }

    /**
     * Create logical/physical objects as needed when gaining access to a store.
     */
    void joinStore_(SIndex sidx, SID sid, StoreInfo info, Trans t) throws Exception;

    /**
     * Remove logical/physical objects as needed when losing access to a store.
     */
    void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception;

    /**
     * React to changes to member list (other than the local user)
     */
    boolean onMembershipChange_(SIndex sidx, StoreInfo info) throws Exception;

}
