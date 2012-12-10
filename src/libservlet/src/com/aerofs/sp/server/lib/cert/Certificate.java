package com.aerofs.sp.server.lib.cert;

import com.google.inject.Inject;

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

        public Certificate create(long serial)
        {
            return new Certificate(this, serial);
        }
    }

    private final Factory _f;
    private final long _serial;

    public Certificate(Factory f, long serial)
    {
        _f = f;
        _serial = serial;
    }

    public long serial()
    {
        return _serial;
    }

    public boolean isRevoked()
            throws SQLException
    {
        return _f._certdb.isRevoked(serial());
    }

    public void revoke()
            throws SQLException
    {
        _f._certdb.revokeCertificateBySerial(serial());
    }
}
