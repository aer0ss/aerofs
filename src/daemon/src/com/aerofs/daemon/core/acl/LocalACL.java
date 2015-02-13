package com.aerofs.daemon.core.acl;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ids.UserID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 * ACL: Access Control List. AeroFS uses Discretionary ACL.
 *
 * This class manipulates and accesses the local database, whereas ACLSynchronizer keeps local ACL in
 * sync with the central ACL database.
 */
public class LocalACL
{
    private final CfgLocalUser _cfgLocalUser;
    private final IACLDatabase _adb;
    private final IMapSIndex2SID _sidx2sid;
    private final DirectoryService _ds;

    // mapping: store -> (subject -> role). It's an in-memory cache of the ACL table in the db.
    private final Map<SIndex, ImmutableMap<UserID, Permissions>> _cache = Maps.newHashMap();

    @Inject
    public LocalACL(CfgLocalUser cfgLocalUser, TransManager tm, IMapSIndex2SID sidx2sid,
            IACLDatabase adb, DirectoryService ds)
    {
        _adb = adb;
        _cfgLocalUser = cfgLocalUser;
        _sidx2sid = sidx2sid;
        _ds = ds;

        // clear the cache when a transaction is aborted
        tm.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                invalidateAll_();
            }
        });
    }

    /**
     * @return whether the {@code subject}'s role allows operations that are allowed by
     * {@code role}. operations on the subjects' root store are always allowed.
     */
    public boolean check_(UserID subject, SIndex sidx, Permissions permissions) throws SQLException
    {
        Permissions permissionsActual = get_(sidx).get(subject);
        boolean allowed = permissionsActual != null && permissionsActual.covers(permissions);

        // always allow the local user to operate on root stores, so that newly installed
        // devices can instantly start syncing with other devices without waiting for ACL to be
        // downloaded.
        return allowed || (subject.equals(_cfgLocalUser.get())
                                   && _sidx2sid.get_(sidx).equals(SID.rootSID(subject)));
    }

    /**
     *
     * @return the SOID corresponding to the specified path. Follow anchor if the resolved object is
     * an anchor.
     */
    public @Nonnull SOID checkThrows_(UserID subject, Path path)
            throws ExNotFound, SQLException, ExNoPerm, ExExpelled
    {
        SOID soid = _ds.resolveThrows_(path);
        OA oa = _ds.getOAThrows_(soid);
        if (oa.isAnchor()) soid = _ds.followAnchorThrows_(oa);
        checkThrows_(subject, soid.sidx(), Permissions.VIEWER);
        return soid;
    }

    public void checkThrows_(UserID subject, SIndex sidx, Permissions permissions)
            throws SQLException, ExNoPerm, ExNotFound
    {
        if (!check_(subject, sidx, permissions)) {
            throw new ExNoPerm(subject + ", " + permissions + ", " + sidx);
        }
    }

    /**
     * @return the map of subjects to their permissions for a given store
     * If the sidx is not in the LocalACL table, returns an empty map.
     */
    public @Nonnull ImmutableMap<UserID, Permissions> get_(SIndex sidx)
            throws SQLException
    {
        ImmutableMap<UserID, Permissions> subject2role = _cache.get(sidx);
        if (subject2role == null) {
            subject2role = readFromDB_(sidx);
            _cache.put(sidx, subject2role);
        }
        return subject2role;
    }

    /**
     * Read the subject-to-role mapping for the given store from the database.
     * If the sidx is not available locally, returns an empty map.
     */
    private @Nonnull ImmutableMap<UserID, Permissions> readFromDB_(SIndex sidx)
            throws SQLException
    {
        ImmutableMap.Builder<UserID, Permissions> builder = ImmutableMap.builder();
        IDBIterator<Map.Entry<UserID, Permissions>> iter = _adb.get_(sidx);
        try {
            while (iter.next_()) {
                Map.Entry<UserID, Permissions> srp = iter.get_();
                builder.put(srp.getKey(), srp.getValue());
            }
        } finally {
            iter.close_();
        }

        return builder.build();
    }

    /**
     * Internal use only. Clients should use {@link ACLSynchronizer}.
     */
    void set_(SIndex sidx, Map<UserID, Permissions> subject2role, Trans t)
            throws SQLException
    {
        _adb.set_(sidx, subject2role, t);

        invalidate_(sidx);
    }

    /**
     * Internal use only. Clients should use {@link ACLSynchronizer}.
     */
    void delete_(SIndex sidx, UserID subject, Trans t)
            throws SQLException, ExNotFound
    {
        _adb.delete_(sidx, subject, t);

        invalidate_(sidx);
    }

    /**
     * Internal use only. Clients should use {@link ACLSynchronizer}.
     */
    void clear_(SIndex sidx, Trans t) throws SQLException
    {
        _adb.clear_(sidx, t);
        invalidate_(sidx);
    }

    /**
     * This method gets all accessible stores for the local user as stored in the LocalACL.
     */
    public Set<SIndex> getAccessibleStores_() throws SQLException
    {
        Set<SIndex> s = Sets.newHashSet();
        IDBIterator<SIndex> it = _adb.getAccessibleStores_(_cfgLocalUser.get());
        try {
            while (it.next_()) s.add(it.get_());
        } finally {
            it.close_();
        }
        return s;
    }

    private void invalidate_(SIndex sidx)
    {
        _cache.remove(sidx);
    }

    private void invalidateAll_()
    {
        _cache.clear();
    }
}
