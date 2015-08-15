package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkState;

public class EffectiveUserList {
    private static final Logger l = LoggerFactory.getLogger(EffectiveUserList.class);

    private final IMapSIndex2SID _sidx2sid;
    private final LocalACL _lacl;
    private List<UserID> _users;

    @Inject
    public EffectiveUserList(IMapSIndex2SID sidx2sid, LocalACL lacl) {
        _sidx2sid = sidx2sid;
        _lacl = lacl;
        _users = new CopyOnWriteArrayList<>();
    }

    public List<UserID> getEffectiveList() {
        return _users;
    }

    public void storeAdded_(SIndex sidx) throws SQLException {
        // Add the user of the store to the effective list.
        SID sid = _sidx2sid.get_(sidx);
        if (!sid.isUserRoot()) return;
        Iterator<UserID> users = _lacl.get_(sidx).keySet().stream().filter(u -> !u.isTeamServerID()).iterator();
        checkState(users.hasNext());
        UserID user = users.next();
        _users.add(user);
        l.info("User {} is added to the effective user list", user);
        checkState(!users.hasNext());
    }

    public void storeRemoved_(SIndex sidx) throws SQLException {
        // Remove the user of the store from the effective list before the store is deleted.
        SID sid = _sidx2sid.get_(sidx);
        if (!sid.isUserRoot()) return;
        Iterator<UserID> users = _lacl.get_(sidx).keySet().stream().filter(u -> !u.isTeamServerID()).iterator();
        checkState(users.hasNext());
        UserID user = users.next();
        _users.remove(user);
        l.info("User {} is removed from the effective user list.", user);
        checkState(!users.hasNext());
    }
}
