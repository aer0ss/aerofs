package com.aerofs.lib.id;


import com.aerofs.ids.OID;

/** Store index, Object id */
public class SOID implements Comparable<SOID> {

    private final SIndex _sidx;
    private final OID _oid;

    public SOID(SIndex sidx, OID oid)
    {
        _sidx = sidx;
        _oid = oid;
    }

    @Override
    public int compareTo(SOID soid)
    {
        int comp = oid().compareTo(soid.oid());
        if (comp == 0) comp = sidx().compareTo(soid.sidx());
        return comp;
    }

    public SIndex sidx()
    {
        return _sidx;
    }

    public OID oid()
    {
        return _oid;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;
        SOID o2 = (SOID) o;
        return _oid.equals(o2._oid) && _sidx.equals(o2._sidx);
    }

    @Override
    public int hashCode()
    {
        return _oid.hashCode();
    }

    @Override
    public String toString()
    {
        return sidx().toString() + oid().toString();
    }
}
