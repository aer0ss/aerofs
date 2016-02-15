package com.aerofs.lib.id;


import com.aerofs.ids.OID;

/** Store index, Object id, KIndex */
public class SOKID implements Comparable<SOKID> {

    private final SOID _soid;
    private final KIndex _kidx;

    public SOKID(SIndex sidx, OID oid, KIndex kidx)
    {
        this(new SOID(sidx, oid), kidx);
    }

    public SOKID(SOID soid, KIndex kidx)
    {
        _soid = soid;
        _kidx = kidx;
    }

    @Override
    public int compareTo(SOKID o)
    {
        int comp = soid().compareTo(o.soid());
        if (comp == 0) comp = kidx().compareTo(o.kidx());
        return comp;
    }

    @Override
    public String toString()
    {
        return soid() + "k" + kidx();
    }

    public SOID soid()
    {
        return _soid;
    }

    public SIndex sidx()
    {
        return _soid.sidx();
    }

    public OID oid()
    {
        return _soid.oid();
    }

    public KIndex kidx()
    {
        return _kidx;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;

        SOKID socid = (SOKID)o;
        return sidx().equals(socid.sidx()) &&
            kidx().equals(socid.kidx()) &&
            oid().equals(socid.oid());
    }

    @Override
    public int hashCode()
    {
        return oid().hashCode();
    }
}
