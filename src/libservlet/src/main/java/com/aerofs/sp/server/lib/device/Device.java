/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.device;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExDeviceIDAlreadyExists;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ParamFactory;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.SQLException;

public class Device
{
    private static final Logger l = Loggers.getLogger(Device.class);

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
            return create(new DID(BaseUtil.fromPB(did)));
        }

        @ParamFactory
        public Device _create(String s)
        {
            try {
                return create(new DID(UniqueID.fromStringFormal(s)));
            } catch (ExInvalidID e) {
                throw new IllegalArgumentException("Invalid DID");
            }
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

    public String getOSFamily()
            throws SQLException, ExNotFound
    {
        return _f._db.getOSFamily(_id);
    }

    public String getOSName()
            throws SQLException, ExNotFound
    {
        return _f._db.getOSName(_id);
    }

    public long getInstallDate() throws SQLException, ExNotFound
    {
        return _f._db.getInstallDate(_id);
    }

    public ImmutableList<Certificate> certificates()
            throws SQLException, ExNotFound, ExInvalidID
    {
        return _f._factCert.list(_id);
    }

    public boolean isUnlinked()
            throws ExInvalidID, SQLException, ExNotFound
    {
        return _f._db.isUnlinked(_id);
    }

    public void setName(String name)
            throws SQLException, ExNotFound
    {
        _f._db.setName(_id, name);
    }

    public void throwIfNotFound()
            throws ExNotFound, SQLException
    {
        if (!exists()) {
            throw new ExNotFound();
        }
    }

    // Throw the same exception as above to avoid brute force discovery of devices.
    public void throwIfNotOwner(User owner)
            throws ExNotFound, SQLException
    {
        if (!owner.equals(getOwner())) {
            throw new ExNotFound();
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
     * Add the device to the db
     * @return this device
     */
    public Device save(User owner, String osFamily, String osName, String deviceName)
            throws SQLException, ExDeviceIDAlreadyExists
    {
        _f._db.insertDevice(_id, owner.id(), osFamily, osName, deviceName);
        return this;
    }

    /**
     * @return serial number(s) of revoked certificate(s), if any
     */
    public ImmutableSet<Long> delete()
            throws SQLException, ExNotFound, ExInvalidID
    {
        if (!exists()) {
            throw new ExNotFound();
        }

        ImmutableSet.Builder<Long> serials = ImmutableSet.builder();

        for (Certificate cert : certificates()) {
            if (!cert.isRevoked()) {
                cert.revoke();
                serials.add(cert.serial());
            }
        }

        _f._db.markUnlinked(_id);

        return serials.build();
    }

    /**
     * add certificate for device
     *
     * @throws ExNotFound if the device doesn't exist
     */
    public void addCertificate(CertificationResult cert)
            throws IOException, ExNotFound, SQLException, ExBadArgs, ExAlreadyExist,
            SignatureException, CertificateException
    {
        // Create the required entry in the certificate table. If this operation fails then
        // the CA will still have a record of the certificate, but we will not return it.
        // This is okay, since the DRL (device revocation list) is maintained by the SP and
        // not the CA anyway.
        _f._certdb.insertCertificate(cert.getSerial(), _id, cert.getExpiry());

        l.info("created certificate for " + getOwner() + ":" + _id.toStringFormal() +
                " with serial " + cert.getSerial() + " (expires on " + cert.getExpiry() + ")");
    }

    /**
     * Generate a certificate for device. This method does not require a db transaction.
     */
    public CertificationResult certify(PKCS10CertificationRequest csr, User owner)
            throws IOException, SQLException, ExBadArgs, ExNotFound, CertificateException,
            SignatureException
    {
        // Verify the device ID and user ID matches what is specified in CSR.
        String cname = getCN(csr);

        if (!cname.equals(BaseSecUtil.getCertificateCName(owner.id(), _id))) {
            throw new ExBadArgs("cname doesn't match: hash(" + owner + " + " +
                    _id.toStringFormal() + ") != " + cname);
        }

        return _f._certgen.generateCertificate(owner.id(), _id, csr);
    }

    private String getCN(PKCS10CertificationRequest csr)
            throws ExBadArgs
    {
        X500Name subject = csr.getSubject();
        // This is the PKIX object identifier for the CN field of a PKCS10 request
        ASN1ObjectIdentifier cnOID = new ASN1ObjectIdentifier("2.5.4.3");
        // This is all just dealing with ASN1 being super general, extracting the actual string
        // of interest
        RDN[] rdns = subject.getRDNs(cnOID);
        if (rdns.length != 1) {
            throw new ExBadArgs("certificate had too many/few subjects: " + rdns.length);
        }
        if (rdns[0].isMultiValued()) {
            throw new ExBadArgs();
        }
        return rdns[0].getFirst().getValue().toString();
    }

    public void setOSFamilyAndName(String osFamily, String osName)
            throws SQLException, ExNotFound
    {
        _f._db.setOSFamilyAndName(_id, osFamily, osName);
    }
}
