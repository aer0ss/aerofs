package com.aerofs.daemon.core.store;

import java.sql.SQLException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class maintains the mapping from SIndex to Store objects, which represent locally present
 * stores.
 */
public class MapSIndex2Store
{
    private Store.Factory _factStore;

    private final Map<SIndex, Store> _sidx2s = Maps.newHashMap();

    @Inject
    public void inject_(Store.Factory factStore)
    {
        _factStore = factStore;
    }

    /**
     * @return the store object corresponding to the sidx, null if not found
     */
    public @Nullable Store getNullable_(SIndex sidx)
    {
        return _sidx2s.get(sidx);
    }

    /**
     * @return the store object corresponding to the sidx. Assertion failure if not found.
     */
    public @Nonnull Store get_(SIndex sidx)
    {
        Store s = getNullable_(sidx);
        return checkNotNull(s, sidx);
    }

    /**
     * @return always a valid store object corresponding to the sidx
     * @throws ExNotFound if there is no corresponding store
     */
    public @Nonnull Store getThrows_(SIndex sidx) throws ExNotFound
    {
        Store s = getNullable_(sidx);
        if (s == null) throw new ExNotFound("store " + sidx);
        return s;
    }

    // FIXME: SO WEIRD. Why do we do the creation of the store in the add()?
    Store add_(final SIndex sidx) throws SQLException
    {
        assert !_sidx2s.containsKey(sidx);

        Store s = _factStore.create_(sidx);
        assert s.sidx().equals(sidx) : s.sidx() + "!=" + sidx;

        Util.verify(_sidx2s.put(sidx, s) == null);
        return s;
    }

    public void delete_(final SIndex sidx)
    {
        Util.verify(_sidx2s.remove(sidx) != null);
    }
}
