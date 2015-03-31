/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.database;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import javax.annotation.Nullable;

public interface ServerConfigDAO
{
    @SqlQuery("select count(*) from server_configuration")
    public int getNumRows();

    // return DER encoding of a private key
    @Nullable
    @SqlQuery("select ca_key from server_configuration")
    public byte[] getPrivateKey();

    @SqlUpdate("delete from server_configuration")
    public int clearServerConfig();

    @SqlUpdate("delete from signed_certificates")
    public int clearSignedCerts();

    // store DER encoding of a private key and x509 certificate
    // BouncyCastle uses DER encoding as its default, so key.getEncoded() satisfies this
    @SqlUpdate("insert into server_configuration(ca_key, ca_cert) values(:key, :cert)")
    public int setKeyAndCert(@Bind("key") byte[] privateKey, @Bind("cert") byte[] caCert);

    // return DER encoding of a x509 certificate
    @Nullable
    @SqlQuery("select ca_cert from server_configuration")
    public byte[] getCACert();

    @SqlQuery("select exists(select * from signed_certificates where serial_number = :serial)")
    public boolean serialNumberTaken(@Bind("serial") long serialNumber);

    @SqlUpdate("insert into signed_certificates(serial_number, certificate) values(:serial, 'NOT A CERT')")
    public int takeSerialNumber(@Bind("serial") long serialNumber);

    @SqlUpdate("update signed_certificates set certificate = :cert where serial_number = :serial")
    public int addSignedCertificate(@Bind("serial") long serialNumber, @Bind("cert") byte[] cert);
}
