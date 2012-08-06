package com.aerofs.lib;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.lib.id.DID;
import com.aerofs.proto.Common.PBVer;
import com.google.common.collect.Maps;

public class Version
{
    private final Map<DID, Tick> _map;

    public Version()
    {
        _map = Maps.newHashMap();
    }

    public Version(Version v)
    {
        _map = Maps.newHashMap(v._map);
    }

    public Version(PBVer pb)
    {
        this();

        assert pb.getDeviceIdCount() == pb.getTickCount();

        for (int i = 0; i < pb.getDeviceIdCount(); i++) {
            DID did = new DID(pb.getDeviceId(i));
            set_(did, pb.getTick(i));
        }
    }

    public Version set_(DID did, Tick tk)
    {
        assert !tk.equals(Tick.ZERO);
        _map.put(did, tk);
        return this;
    }

    /**
     * @return Tick.ZERO if not found did in the entries
     */
    public Tick get_(DID did)
    {
        Tick tk = _map.get(did);
        return tk == null ? Tick.ZERO : tk;
    }

    public Version set_(DID did, long l)
    {
        set_(did, new Tick(l));
        return this;
    }

    // return a read-only view of the map
    public Map<DID, Tick> getAll_()
    {
        return Collections.unmodifiableMap(_map);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("{");

        int idx = _map.size();
        for (Entry<DID, Tick> en : _map.entrySet()) {
            sb.append(en.getKey());
            sb.append(en.getValue());
            if (--idx > 0) sb.append(',');
        }
        sb.append('}');

        return sb.toString();
    }

    public PBVer toPB_()
    {
        PBVer.Builder builder = PBVer.newBuilder();

        for (Entry<DID, Tick> en : _map.entrySet()) {
            builder.addDeviceId(en.getKey().toPB());
            builder.addTick(en.getValue().getLong());
        }

        return builder.build();
    }

    public boolean isZero_()
    {
        for (Tick tk : _map.values()) if (tk.getLong() != 0) return false;
        return true;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || _map.equals(((Version) o)._map);
    }

    @Override
    public int hashCode()
    {
        return _map.hashCode();
    }

    /**
     * @return a new Version object containing the union of the two operands.
     *
     * N.B. Existing objects are not modified.
     */
    public Version add_(Version v)
    {
        Version ret = new Version(this);

        for (Entry<DID, Tick> en : v._map.entrySet()) {
            Tick his = en.getValue();
            Tick mine = _map.get(en.getKey());
            if (mine == null || mine.getLong() < his.getLong()) {
                ret.set_(en.getKey(), his);
            }
        }
        return ret;
    }

    /**
     * @return a new Version object containing the diff of the two operands.
     *
     * N.B. Existing objects are not modified.
     */
    public Version sub_(Version v)
    {
        Version ret = new Version();
        for (Entry<DID, Tick> en : this._map.entrySet()) {
            Tick mine = en.getValue();
            Tick his = v._map.get(en.getKey());
            if (his == null || mine.getLong() > his.getLong()) {
                ret.set_(en.getKey(), mine);
            }
        }
        return ret;
    }

    /**
     * @return this - (this - v). Visually, the return value is the part of this 'shadowed' by v.
     */
    public Version shadowedBy_(Version v)
    {
        return sub_(sub_(v));
    }

    /**
     * @return a new version object, keeping only this Version's non-alias ticks
     */
    public Version withoutAliasTicks_()
    {
        Version ret = new Version();

        for (Entry<DID, Tick> en : _map.entrySet()) {
            Tick t = en.getValue();
            if (!t.isAlias()) ret.set_(en.getKey(), t);
        }

        return ret;
    }
}
