/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.device;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificateGenerationResult;
import com.aerofs.sp.server.lib.device.DeviceDatabase.ExDeviceNameAlreadyExist;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import sun.security.pkcs.PKCS10;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.SQLException;

public class Device
{
    public static class Factory
    {
        private DeviceDatabase _db;
        private CertificateDatabase _certdb;
        private CertificateGenerator _certgen;

        private User.Factory _factUser;
        private Certificate.Factory _factCert;

        @Inject
        public void inject(DeviceDatabase db, CertificateDatabase certdb,
                CertificateGenerator certgen, User.Factory factUser, Certificate.Factory factCert)
        {
            _db = db;
            _certdb = certdb;
            _certgen = certgen;
            _factUser = factUser;
            _factCert = factCert;
        }

        public Device create(@Nonnull DID did)
        {
            return new Device(this, did);
        }

        public Device create(@Nonnull ByteString did)
        {
            return create(new DID(did));
        }
    }

    private final Factory _f;
    private final DID _id;

    private Device(Factory f, DID id)
    {
        _f = f;
        _id = id;
    }

    public DID id()
    {
        return _id;
    }

    public boolean exists()
            throws SQLException
    {
        return _f._db.hasDevice(_id);
    }

    /**
     * @throws ExNotFound if the device (not the user) is not found
     */
    public User getOwner()
            throws ExNotFound, SQLException
    {
        // Do not cache the created object in memory to avoid db/mem inconsistency
        return _f._factUser.create(_f._db.getOwnerID(_id));
    }

    public String getName()
            throws ExNotFound, SQLException
    {
        return _f._db.getName(_id);
    }

    public Certificate getCertificate()
            throws ExNotFound, SQLException
    {
        long serial = _f._certdb.getSerial(_id);
        return _f._factCert.create(serial);
    }

    /**
     * Set the device name. Rename using Util.nextName() if a device with the same name exists.
     */
    public void setName(String name)
            throws SQLException, ExNotFound
    {
        while (true) {
            try {
                _f._db.setName(_id, name);
                break;
            } catch (ExDeviceNameAlreadyExist e) {
                name = Util.nextName(name, "");
            }
        }
    }

    @Override
    public int hashCode()
    {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _id.equals(((Device) o)._id));
    }

    @Override
    public String toString()
    {
        return "device " + _id;
    }

    /**
     * Add the device to the db, rename using Util.nextName() if a device with the same name exists.
     */
    public void createNewDevice(User owner, String name)
            throws SQLException, ExDeviceIDAlreadyExists
    {
        while (true) {
            try {
                _f._db.addDevice(_id, owner.id(), name);
                break;
            } catch (ExDeviceNameAlreadyExist e) {
                name = Util.nextName(name, "");
            }
        }
    }

    /**
     * Issue a device certificate
     *
     * @throws ExNotFound if the device doesn't exist
     */
    public CertificateGenerationResult certify(PKCS10 csr)
            throws IOException, ExNotFound, SQLException, ExBadArgs, ExAlreadyExist,
            SignatureException, CertificateException
    {
        // Verify the device ID and user ID matches what is specified in CSR.
        String cname = csr.getSubjectName().getCommonName();

        User owner = getOwner();

        if (!cname.equals(SecUtil.getCertificateCName(owner.id(), _id))) {
            throw new ExBadArgs("cname doesn't match: hash(" + owner + " + " +
                    _id.toStringFormal() + ") != " + cname);
        }

        CertificateGenerationResult cert = _f._certgen.generateCertificate(owner.id(), _id, csr);

        // Create the required entry in the certificate table. If this operation fails then
        // the CA will still have a record of the certificate, but we will not return it.
        // This is okay, since the DRL (device revocation list) is maintained by the SP and
        // not the CA anyway.
        _f._certdb.addCertificate(cert.getSerial(), _id, cert.getExpiry());

        Util.l(this).info("created certificate for " + _id.toStringFormal() + " with serial " +
                cert.getSerial() + " (expires on " + cert.getExpiry() + ")");

        return cert;
    }
}
