package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class Certificate
{
    public static class Factory
    {
        private final CertificateDatabase _certdb;

        @Inject
        public Factory(CertificateDatabase certdb)
        {
            _certdb = certdb;
        }

        public ImmutableList<Certificate> list(DID did)
                throws SQLException, ExNotFound, ExInvalidID
        {
            List<Long> serials = _certdb.getAllSerialsIssuedFor(did);
            Builder<Certificate> builder = ImmutableList.builder();
            for (long s: serials) {
                builder.add(new Certificate(this, _certdb.getDid(s), s));
            }
            return builder.build();
        }
    }

    private final Factory _f;
    private final DID _did;
    private final long  _serial;

    public Certificate(Factory f, DID did, long serial)
    {
        _f = f;
        _did = did;
        _serial = serial;
    }

    public DID did()
    {
        return _did;
    }

    public long serial()
    {
        return _serial;
    }

    public boolean isRevoked()
            throws SQLException, ExNotFound
    {
        return _f._certdb.isRevoked(_serial);
    }

    public void revoke()
            throws SQLException, ExNotFound
    {
        _f._certdb.revokeCertificate(_serial);
    }
}
