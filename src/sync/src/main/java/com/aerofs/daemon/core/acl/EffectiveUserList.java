package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
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
    private final List<UserID> _users;
    private final List<IUserListChangeListener> _listeners;

    @Inject
    public EffectiveUserList(IMapSIndex2SID sidx2sid, LocalACL lacl) {
        _sidx2sid = sidx2sid;
        _lacl = lacl;
        _users = new CopyOnWriteArrayList<>();
        _listeners = Lists.newArrayList();
    }

    public List<UserID> getEffectiveList() {
        return _users;
    }

    public void storeAdded_(SIndex sidx) throws SQLException {
        SID sid = _sidx2sid.get_(sidx);
        if (!sid.isUserRoot()) return;
        Iterator<UserID> users = _lacl.get_(sidx).keySet().stream().filter(u -> !u.isTeamServerID()).iterator();
        checkState(users.hasNext());
        UserID user = users.next();
        _users.add(user);
        l.info("Effective user added: {}", user);
        checkState(!users.hasNext());
        _listeners.forEach(IUserListChangeListener::onUserListChanged);
    }

    public void storeRemoved_(SIndex sidx) throws SQLException {
        SID sid = _sidx2sid.get_(sidx);
        if (!sid.isUserRoot()) return;

        // we cannot rely on ACL entries here as they may have been removed already
        // instead, we go through the current effective user list, looking for a matching root SID
        for (UserID user : _users) {
            if (SID.rootSID(user).equals(sid)) {
                _users.remove(user);
                l.info("Effective user removed: {}", user);
                _listeners.forEach(IUserListChangeListener::onUserListChanged);
                break;
            }
        }
    }

    public void addListener(IUserListChangeListener listener) {
        _listeners.add(listener);
    }

    public static interface IUserListChangeListener {
        void onUserListChanged();
    }
}
