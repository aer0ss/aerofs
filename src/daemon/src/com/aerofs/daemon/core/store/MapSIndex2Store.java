package com.aerofs.daemon.core.store;

import java.sql.SQLException;
import java.util.Map;

import javax.inject.Inject;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;

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
    public Store getNullable_(SIndex sidx)
    {
        return _sidx2s.get(sidx);
    }

    /**
     * @return the store object corresponding to the sidx. Assertion failure if not found.
     */
    public Store get_(SIndex sidx)
    {
        Store s = getNullable_(sidx);
        assert s != null;
        return s;
    }

    /**
     * @return always a valid store object corresponding to the sidx
     * @throws ExNotFound if there is no corresponding store
     */
    public Store getThrows_(SIndex sidx) throws ExNotFound
    {
        Store s = getNullable_(sidx);
        if (s == null) throw new ExNotFound("store " + sidx);
        return s;
    }

    public void add_(final SIndex sidx) throws SQLException
    {
        assert !_sidx2s.containsKey(sidx);

        Store s = _factStore.create_(sidx);
        assert s.sidx().equals(sidx);

        Util.verify(_sidx2s.put(sidx, s) == null);
    }

    public void delete_(final SIndex sidx)
    {
        Util.verify(_sidx2s.remove(sidx) != null);
    }
}
