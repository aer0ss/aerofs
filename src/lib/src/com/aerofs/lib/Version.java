package com.aerofs.lib;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.base.id.DID;
import com.aerofs.proto.Common.PBVer;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Objects.firstNonNull;

public class Version
{
    private static final Version ZERO = empty();

    private final Map<DID, Tick> _map;

    private Version()
    {
        _map = Maps.newHashMap();
    }

    private Version(Map<DID, Tick> map)
    {
        _map = map;
    }

    public static Version empty()
    {
        return new Version();
    }

    public static Version copyOf(Version v)
    {
        return new Version(Maps.newHashMap(v._map));
    }

    public static Version fromPB(PBVer pb)
    {
        Version v = empty();
        assert pb.getDeviceIdCount() == pb.getTickCount();

        for (int i = 0; i < pb.getDeviceIdCount(); i++) {
            DID did = new DID(pb.getDeviceId(i));
            v.set_(did, pb.getTick(i));
        }
        return v;
    }

    /**
     * A Guava-like static factory method
     */
    public static Version of(DID did, Tick tk)
    {
        return empty().set_(did, tk);
    }

    public static Version of(DID did, long tk)
    {
        return empty().set_(did, tk);
    }

    private Version set_(DID did, Tick tk)
    {
        assert !tk.equals(Tick.ZERO);
        _map.put(did, tk);
        return this;
    }

    /**
     * @return Tick.ZERO if not found did in the entries
     */
    public @Nonnull Tick get_(DID did)
    {
        Tick tk = _map.get(did);
        return firstNonNull(tk, Tick.ZERO);
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
        return this.equals(Version.ZERO);
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
        Version ret = copyOf(this);

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
    public Version sub_(@Nonnull Version v)
    {
        if (v.isZero_()) return copyOf(this);

        Version ret = empty();
        for (Entry<DID, Tick> en : this._map.entrySet()) {
            Tick mine = en.getValue();
            Tick his = v._map.get(en.getKey());
            if (his == null || mine.getLong() > his.getLong()) {
                ret.set_(en.getKey(), mine);
            }
        }
        return ret;
    }

    // equivalent to this.sub_(v).isZero_()
    public boolean isDominatedBy_(@Nonnull Version v)
    {
        for (Entry<DID, Tick> en : _map.entrySet()) {
            Tick mine = en.getValue();
            Tick his = v._map.get(en.getKey());
            if (his == null || mine.getLong() > his.getLong()) return false;
        }
        return true;
    }

    /**
     * @return this - (this - v). Visually, the return value is the part of this 'shadowed' by v.
     */
    public Version shadowedBy_(Version v)
    {
        if (v.isZero_()) return empty();
        return sub_(sub_(v));
    }

    /**
     * @return true iff *this* version is entirely shadowed by v
     */
    public boolean isEntirelyShadowedBy_(Version v)
    {
        return this.shadowedBy_(v).equals(this);
    }

    /**
     * @return whether the version vector only contains alias ticks
     *
     * NB: return true for empty vector
     */
    public boolean isAliasOnly_()
    {
        for (Tick t : _map.values()) {
            if (!t.isAlias()) return false;
        }
        return true;
    }

    /**
     * @return whether the version vector only contains regular ticks
     *
     * NB: return true for empty vector
     */
    public boolean isNonAliasOnly_()
    {
        for (Tick t : _map.values()) {
            if (t.isAlias()) return false;
        }
        return true;
    }

    /**
     * Regular and alias ticks should NEVER be mixed in the same version vector
     *
     * @return whether all ticks in the vector belong in the same tick space (non-alias / alias)
     */
    public boolean isHomogeneous_()
    {
        if (_map.isEmpty()) return true;
        Iterator<Tick> it = _map.values().iterator();
        boolean v = it.next().isAlias();
        while (it.hasNext()) {
            if (it.next().isAlias() != v) return false;
        }
        return true;
    }

    /**
     * @return a new version object, keeping only this Version's non-alias ticks
     */
    public Version nonAliasTicks_()
    {
        Version ret = empty();
        for (Entry<DID, Tick> en : _map.entrySet()) {
            Tick t = en.getValue();
            if (!t.isAlias()) ret.set_(en.getKey(), t);
        }
        return ret;
    }

    /**
     * @return null if this Version is empty
     */
    public @Nullable DID findLargestDID()
    {
        DID didMax = null;
        for (DID did : getAll_().keySet()) {
            if (didMax == null || didMax.compareTo(did) < 0) didMax = did;
        }
        return didMax;
    }
}
