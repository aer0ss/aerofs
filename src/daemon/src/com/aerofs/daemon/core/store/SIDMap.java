package com.aerofs.daemon.core.store;

import java.sql.SQLException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;

/**
 * This class is a wrapper of ISIDDatabase
 */
public class SIDMap implements IMapSIndex2SID, IMapSID2SIndex
{
    private final ISIDDatabase _db;

    Map<SID, SIndex> _sid2sidx = Maps.newHashMap();
    Map<SIndex, SID> _sidx2sid = Maps.newHashMap();

    @Inject
    public SIDMap(ISIDDatabase db)
    {
        _db = db;
    }

    @Override
    public @Nullable SIndex getNullable_(SID sid)
    {
        return _sid2sidx.get(sid);
    }

    @Override
    public @Nonnull SIndex get_(SID sid)
    {
        SIndex sidx = getNullable_(sid);
        assert sidx != null : sid;
        return sidx;
    }

    @Override
    public @Nullable SID getNullable_(SIndex sidx)
    {
        return _sidx2sid.get(sidx);
    }

    @Override
    public @Nonnull SID get_(SIndex sidx)
    {
        SID sid = getNullable_(sidx);
        assert sid != null : sidx;
        return sid;
    }

    @Override
    public @Nonnull SID getThrows_(SIndex sidx) throws ExNotFound
    {
        SID sid = getNullable_(sidx);
        if (sid == null) throw new ExNotFound("sid for " + sidx);
        return sid;
    }

    @Override
    public @Nonnull SID getAbsent_(SIndex sidx) throws SQLException
    {
        assert getNullable_(sidx) == null : sidx;
        return _db.getSID_(sidx);
    }

    @Override
    public @Nullable SIndex getLocalOrAbsentNullable_(SID sid) throws SQLException
    {
        return _db.getSIndex_(sid);
    }

    @Override
    public @Nonnull SIndex getAbsent_(SID sid, Trans t) throws SQLException
    {
        assert getNullable_(sid) == null : sid;
        SIndex sidx = getLocalOrAbsentNullable_(sid);
        if (sidx == null) sidx = _db.addSID_(sid, t);
        return sidx;
    }

    void add_(SIndex sidx) throws SQLException
    {
        SID sid = _db.getSID_(sidx);
        Util.verify(_sid2sidx.put(sid, sidx) == null);
        Util.verify(_sidx2sid.put(sidx, sid) == null);
    }

    SID delete_(SIndex sidx)
    {
        SID sid = _sidx2sid.remove(sidx);
        assert sid != null : sidx;
        Util.verify(_sid2sidx.remove(sid));
        return sid;
    }
}
