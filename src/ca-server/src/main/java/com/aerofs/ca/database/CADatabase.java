package com.aerofs.ca.database;

import com.aerofs.ca.utils.KeyUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

// used to bundle up multiple sql statements into one transaction, and return more useful objects than provided by mysql
public class CADatabase
{
    // package private so tester can access it
    final DBI _dbi;

    public CADatabase(DBI dbi)
    {
        this._dbi = dbi;
    }

    // returns if successfully inserts a row into signed_certificates with this serial number
    public boolean takeSerialNumberIfUnused(long serial)
    {
        try {
            return _dbi.inTransaction((conn, status) -> {
                ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

                throwIfNotSetUp(caData);

                if (caData.serialNumberTaken(serial)) {
                    return false;
                }
                // if taking the serial number somehow doesn't affect a row, throw an exception and rollback
                throwIfDidNotUpdateOneRow(caData.takeSerialNumber(serial));
                return true;
            });
        } catch (CallbackFailedException e) {
            // could happen if another thread sets the exact same value between this thread checking and setting
            return false;
        }
    }

    public void addSignedCertificate(long serial, X509CertificateHolder cert)
    {
        _dbi.inTransaction((conn, status) -> {
            ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

            throwIfNotSetUp(caData);

            throwIfDidNotUpdateOneRow(caData.addSignedCertificate(serial, cert.getEncoded()));
            return null;
        });
    }

    public KeyPair getKeyPair()
            throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException
    {
        return KeyUtils.decodeKeyPair(_dbi.inTransaction((conn, status) -> {
            ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

            throwIfNotSetUp(caData);

            return caData.getPrivateKey();
        }));
    }

    public X509CertificateHolder getCACert()
            throws IOException
    {
        return new X509CertificateHolder(_dbi.<byte[]>inTransaction((conn, status) -> {
            ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

            throwIfNotSetUp(caData);

            return caData.getCACert();
        }));
    }


    public void setKeyAndCert(PrivateKey privateKey, X509CertificateHolder cert)
    {
        _dbi.inTransaction((conn, status) -> {
            ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

            caData.clearServerConfig();
            // if we're setting a new CA, we don't need to hold onto the old signed certificates
            caData.clearSignedCerts();
            throwIfDidNotUpdateOneRow(caData.setKeyAndCert(privateKey.getEncoded(), cert.getEncoded()));
            throwIfDidNotUpdateOneRow(caData.takeSerialNumber(cert.getSerialNumber().longValue()));
            throwIfDidNotUpdateOneRow(caData.addSignedCertificate(cert.getSerialNumber().longValue(), cert.getEncoded()));

            return null;
        });
    }

    public boolean initialized()
    {
        return _dbi.inTransaction((conn, status) -> {
            ServerConfigDAO caData = conn.attach(ServerConfigDAO.class);

            return caData.getNumRows() == 1;
        });
    }

    private static void throwIfNotSetUp(ServerConfigDAO database)
            throws Exception
    {
        if (database.getNumRows() != 1) {
            throw new Exception("CA not set up before querying");
        }
    }

    private static void throwIfDidNotUpdateOneRow(int updated)
            throws Exception
    {
        if (updated != 1) {
            throw new Exception("SQL Statement did not update one row as expected");
        }
    }
}
