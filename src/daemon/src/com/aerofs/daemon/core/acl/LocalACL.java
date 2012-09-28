package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Map;

/**
 * ACL: Access Control List. AeroFS uses Discretionary ACL.
 *
 * This class manipulates and accesses the local database, whereas ACLSynchronizer keeps local ACL in
 * sync with the central ACL database.
 */
public class LocalACL
{
    private final CfgLocalUser _cfgLocalUser;
    private final DirectoryService _ds;
    private final IACLDatabase _adb;
    private final IStores _ss;

    // mapping: store -> (subject -> role). It's an in-memory cache of the ACL table in the db.
    private final Map<SIndex, ImmutableMap<String, Role>> _cache = Maps.newHashMap();

    @Inject
    public LocalACL(CfgLocalUser cfgLocalUser,  DirectoryService ds, TransManager tm, IStores ss,
            IACLDatabase adb)
    {
        _ds = ds;
        _adb = adb;
        _cfgLocalUser = cfgLocalUser;
        _ss = ss;

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
     * @return the SOID corresponding to the specified path
     */
    public @Nonnull SOID checkThrows_(String subject, Path path, Role role)
            throws ExNotFound, SQLException, ExNoPerm, ExExpelled
    {
        SOID soid = _ds.resolveThrows_(path);
        OA oa = _ds.getOAThrows_(soid);
        if (oa.isAnchor()) soid = _ds.followAnchorThrows_(oa);
        checkThrows_(subject, soid.sidx(), role);
        return soid;
    }

    /**
     * @return the SOID corresponding to the specified path. Do not follow anchor if the resolved
     * object is an anchor.
     */
    public @Nonnull SOID checkNoFollowAnchorThrows_(String subject, Path path, Role role)
            throws ExNotFound, SQLException, ExNoPerm
    {
        SOID soid = _ds.resolveThrows_(path);
        checkThrows_(subject, soid.sidx(), role);
        return soid;
    }

    public void checkThrows_(String subject, SIndex sidx, Role role)
            throws SQLException, ExNoPerm, ExNotFound
    {
        if (!check_(subject, sidx, role)) throw new ExNoPerm(subject + ", " + role + ", " + sidx);
    }

    /**
     * @return whether the {@code subject}'s role allows operations that are allowed by
     * {@code role}. operations on the subjects' root store are always allowed.
     */
    public boolean check_(String subject, SIndex sidx, Role role) throws ExNotFound, SQLException
    {
        Role roleActual = get_(sidx).get(subject);
        boolean allowed = roleActual != null && roleActual.covers(role);

        if (!allowed) {
            // always allow the local user to operate on the root store, so that newly installed
            // devices can instantly start syncing with other devices without waiting for ACL to be
            // downloaded.
            allowed = subject.equals(_cfgLocalUser.get()) && sidx.equals(_ss.getRoot_());
        }
        return allowed;
    }

    /**
     * @return the map of subjects to their permissions for a given store
     */
    public @Nonnull ImmutableMap<String, Role> get_(SIndex sidx)
            throws ExNotFound, SQLException
    {
        ImmutableMap<String, Role> subject2role = _cache.get(sidx);
        if (subject2role == null) {
            subject2role = readFromDB_(sidx);
            _cache.put(sidx, subject2role);
        }
        return subject2role;
    }

    /**
     * Read the subject-to-role mapping for the given store from the database.
     */
    private @Nonnull ImmutableMap<String, Role> readFromDB_(SIndex sidx)
            throws SQLException, ExNotFound
    {
        ImmutableMap.Builder<String, Role> builder = ImmutableMap.builder();
        IDBIterator<Map.Entry<String, Role>> iter = _adb.get_(sidx);
        try {
            while (iter.next_()) {
                Map.Entry<String, Role> srp = iter.get_();
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
    void set_(SIndex sidx, Map<String, Role> subject2role, Trans t)
            throws SQLException, ExNotFound
    {
        _adb.set_(sidx, subject2role, t);

        invalidate_(sidx);
    }

    /**
     * Internal use only. Clients should use {@link ACLSynchronizer}.
     */
    void delete_(SIndex sidx, Iterable<String> subjects, Trans t)
            throws SQLException, ExNotFound
    {
        _adb.delete_(sidx, subjects, t);

        invalidate_(sidx);
    }

    /**
     * Internal use only. Clients should use {@link ACLSynchronizer}.
     */
    void clear_(Trans t) throws SQLException, ExNotFound
    {
        _adb.clear_(t);
        invalidateAll_();
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
