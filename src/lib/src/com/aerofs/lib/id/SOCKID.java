package com.aerofs.lib.id;

import com.aerofs.ids.OID;

/** Store, Object, Component, KIndex id */
public class SOCKID implements Comparable<SOCKID> {

    private final SOCID _socid;
    private final KIndex _kidx;

    public SOCKID(SIndex sidx, OID oid, CID cid, KIndex kidx)
    {
        this(new SOCID(sidx, oid, cid), kidx);
    }

    public SOCKID(SOID soid, CID cid, KIndex kidx)
    {
        this(new SOCID(soid, cid), kidx);
    }

    public SOCKID(SOCID socid)
    {
        this(socid, KIndex.MASTER);
    }

    public SOCKID(SIndex sidx, OCID ocid)
    {
        this(new SOCID(sidx, ocid.oid(), ocid.cid()));
    }

    public SOCKID(SIndex sidx, OID oid, CID cid)
    {
        this(new SOCID(sidx, oid, cid));
    }

    public SOCKID(SOID soid, CID cid)
    {
        this(new SOCID(soid, cid));
    }

    public SOCKID(SOKID sokid, CID cid)
    {
        this(new SOCID(sokid.soid(), cid), sokid.kidx());
    }

    public SOCKID(SOCID socid, KIndex xidx)
    {
        // we don't support conflicting metadata for the moment
        assert !socid.cid().isMeta() || xidx.equals(KIndex.MASTER);
        _socid = socid;
        _kidx = xidx;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(soid().toString());
        sb.append(cid().toString());
        if (!kidx().equals(KIndex.MASTER)) {
            sb.append('k');
            sb.append(kidx().toString());
        }
        return sb.toString();
    }

    public SIndex sidx()
    {
        return _socid.sidx();
    }

    public OID oid()
    {
        return _socid.oid();
    }

    public CID cid()
    {
        return _socid.cid();
    }

    public KIndex kidx()
    {
        return _kidx;
    }

    public SOCID socid()
    {
        return _socid;
    }

    public SOID soid()
    {
        return _socid.soid();
    }

    public SOKID sokid()
    {
        return new SOKID(soid(), kidx());
    }

    @Override
    public int compareTo(SOCKID k)
    {
        int comp = socid().compareTo(k.socid());
        if (comp == 0) comp = kidx().compareTo(k.kidx());
        return comp;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        SOCKID k = (SOCKID)o;
        return socid().equals(k.socid()) && kidx().equals(k.kidx());
    }

    @Override
    public int hashCode()
    {
        return oid().hashCode();
    }
}
