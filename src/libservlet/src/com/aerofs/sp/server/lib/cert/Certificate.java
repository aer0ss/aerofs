package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;

import javax.inject.Inject;
import java.sql.SQLException;

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

        public Certificate create(DID did)
        {
            return new Certificate(this, did);
        }
    }

    private final Factory _f;
    private final DID _did;

    public Certificate(Factory f, DID did)
    {
        _f = f;
        _did = did;
    }

    public DID did()
    {
        return _did;
    }

    public long serial()
            throws SQLException, ExNotFound
    {
        return _f._certdb.getSerial(_did);
    }

    public boolean exists()
            throws SQLException
    {
        return _f._certdb.exists(_did);
    }

    public boolean isRevoked()
            throws SQLException, ExNotFound
    {
        return _f._certdb.isRevoked(_did);
    }

    public void revoke()
            throws SQLException, ExNotFound
    {
        _f._certdb.revokeCertificate(_did);
    }
}
