package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

/**
 *
 */
public class Store implements Comparable<Store>, IDumpStatMisc, PauseSync.Listener
{
    protected final SIndex _sidx;

    // For debugging.
    // The idea is that when this.deletePersistentData_ is called, this object
    // is effectively unusable, so no callers should invoke any of its methods (except toString
    // which is harmless).  If anyone knows of a design pattern that would better fit this model
    // please change it!
    protected boolean _isDeleted;

    private final ImmutableMap<Class<?>, Object> _ifaces;

    public interface Factory
    {
        Store create_(SIndex sidx) throws SQLException;
    }

    protected Store(SIndex sidx, ImmutableMap<Class<?>, Object> ifaces)
    {
        _sidx = sidx;
        _ifaces = ifaces;
    }

    public SIndex sidx()
    {
        checkState(!_isDeleted);
        return _sidx;
    }

    @Override
    public int compareTo(@Nonnull Store o)
    {
        checkState(!_isDeleted);
        checkState(!o._isDeleted);
        return _sidx.compareTo(o._sidx);
    }

    @Override
    public String toString()
    {
        return _sidx.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        checkState(!_isDeleted);
        return this == o || (o != null && o instanceof Store && _sidx.equals(((Store) o)._sidx));
    }

    @Override
    public int hashCode()
    {
        checkState(!_isDeleted);
        return _sidx.hashCode();
    }

    @SuppressWarnings("unchecked")
    public <T> T iface(Class<T> c)
    {
        checkState(!_isDeleted);
        return (T)_ifaces.get(c);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps) {}

    @Override
    public void onPauseSync_() {}

    @Override
    public void onResumeSync_() {}

    // called if ACL for the store are received *after* the store is created
    public void accessible_() {}

    /** Notifier called when a device becomes potentially online for this store. */
    public void notifyDeviceOnline_(DID did) {}

    /** Notifier called when a device becomes offline for this store. */
    public void notifyDeviceOffline_(DID did) {}

    /**
     * Called after the Store is created.
     * If there are no OPM devices for this store, do nothing.
     */
    void postCreate_() {}

    /**
     * Pre-deletion trigger. Before we remove a Store from the map, mark the devices offline
     * for this store.
     */
    void preDelete_() {}

    public void deletePersistentData_(Trans t) throws SQLException {
        _isDeleted = true;
    }
}
