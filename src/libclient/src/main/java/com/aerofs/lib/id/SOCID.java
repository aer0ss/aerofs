package com.aerofs.lib.id;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.OID;
import com.aerofs.proto.RitualNotifications.PBSOCID;

/** Store, Object, Component id */
public class SOCID implements Comparable<SOCID> {

    private final SOID _soid;
    private final CID _cid;

    public SOCID(PBSOCID pb)
    {
        this(new SIndex(pb.getSidx()), new OID(BaseUtil.fromPB(pb.getOid())), new CID(pb.getCid()));
    }


    public SOCID(SIndex sidx, OID oid, CID cid)
    {
        _soid = new SOID(sidx, oid);
        _cid = cid;
    }

    public SOCID(SIndex sidx, OCID ocid)
    {
        _soid = new SOID(sidx, ocid.oid());
        _cid = ocid.cid();
    }

    public SOCID(SOID soid, CID cid)
    {
        _soid = soid;
        _cid = cid;
    }

    @Override
    public int compareTo(SOCID socid)
    {
        int comp = soid().compareTo(socid.soid());
        if (comp == 0) comp = cid().compareTo(socid.cid());
        return comp;
    }

    @Override
    public String toString()
    {
        return soid().toString() + cid().toString();
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

    public CID cid()
    {
        return _cid;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;

        SOCID socid = (SOCID)o;
        return sidx().equals(socid.sidx()) &&
            cid().equals(socid.cid()) &&
            oid().equals(socid.oid());
    }

    @Override
    public int hashCode()
    {
        return oid().hashCode();
    }
}
