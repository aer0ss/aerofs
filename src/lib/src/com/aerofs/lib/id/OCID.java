package com.aerofs.lib.id;

import com.aerofs.ids.OID;

/** Object id, Component id */
public class OCID implements Comparable<OCID>
{
    final OID _oid;
    final CID _cid;

    public OCID(OID oid, CID cid)
    {
        _oid = oid;
        _cid = cid;
    }

    @Override
    public int compareTo(OCID arg0)
    {
        int comp = _oid.compareTo(arg0._oid);
        if (comp != 0) return comp;
        return _cid.compareTo(arg0._cid);
    }

    public OID oid()
    {
        return _oid;
    }

    public CID cid()
    {
        return _cid;
    }

    @Override
    public String toString()
    {
        return _oid.toString() + _cid;
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _oid.equals(((OCID) o)._oid)
                && _cid.equals(((OCID) o)._cid));
    }

    @Override
    public int hashCode()
    {
        return _oid.hashCode() + _cid.hashCode();
    }
}
