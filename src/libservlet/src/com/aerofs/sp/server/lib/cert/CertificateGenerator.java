package com.aerofs.sp.server.lib.cert;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import sun.security.pkcs.PKCS10;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.DataInputStream;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * This class is a wrapper for the certificate generation entity (the CA
 * server).
 */
public class CertificateGenerator implements ICertificateGenerator
{
    /**
     * This class encapsulates certificates that have been generated by the AeroFS certificate
     * authority.
     */
    public class CertificationResult
    {
        // The raw certificate bytes.
        private byte[] _certificate;

        // The certificate in x509 format.
        private X509Certificate _x509;

        public CertificationResult(byte[] certificate)
                throws CertificateException
        {
            this._certificate = certificate;
            InputStream in = new ByteArrayInputStream(_certificate);

            this._x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }

        /**
         * @return the string representation of the certificate.
         */
        public String toString()
        {
            return new String(_certificate);
        }

        /**
         * @return the serial number of the certificate as generated by the certificate authority.
         */
        public long getSerial()
        {
            return _x509.getSerialNumber().longValue();
        }

        /**
         * @return the expire timestamp of this certificate.
         */
        public Date getExpiry()
        {
            return _x509.getNotAfter();
        }
    }

    private String _caURL;

    /**
     * Create a certificate for the given user, device ID and CSR bytes.
     *
     * This class is really just a wrapper for the HTTP interface of the CA server.
     *
     * The user ID and the device ID are only used in the URL, which is in turn used by the CA to
     * choose the filename of the new certificate (certs are saved on the remote system in case
     * we ever need them in the future). This function doesn't perform any business logic checks
     * (ex: the session user matches what is in the CSR; such checks should be performed by the
     * caller).
    */
    @Override
    public CertificationResult generateCertificate(UserID userId, DID did, PKCS10 csr)
        throws IOException, SignatureException, CertificateException
    {
        // Convert the CSR into string.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        csr.print(ps);
        String strCSR = bos.toString();

        // Call the CA. Use the user_id + '-' + device_id as the URL string. This string is used
        // only as the certificate's file names stored on the CA server, so the actual value of the
        // string is not important.

        URLConnection conn;
        try {
            conn = (new URL(_caURL + "?" + userId + '-' + did.toStringFormal())).openConnection();
        }
        catch (MalformedURLException e) {
            // Wrap in malformed URL exceptions, re-throw as IO exception so that we can cleanly
            // implement the certificate generator interface.
            throw new IOException(e.toString());
        }

        // 5 sec timeout should be more than enough. The default is way too long.
        Long timeout = 5 * C.SEC;
        conn.setConnectTimeout(timeout.intValue());
        conn.setDoOutput(true);
        conn.connect();

        OutputStream os = conn.getOutputStream();
        try {
            os.write(BaseUtil.string2utf(strCSR));
        } finally {
            os.close();
        }

        if (conn.getContentLength() <= 0) {
            throw new IOException("Cannot parse CA reply. Content length = " + conn
                    .getContentLength());
        }

        InputStream is = conn.getInputStream();
        DataInputStream dis = new DataInputStream(is);

        byte[] bs;
        try {
            bs = new byte[conn.getContentLength()];
            dis.readFully(bs);
        } finally {
            // This will also close the input stream 'is'.
            dis.close();
        }

        // If bs is invalid a certificate exception will be thrown.
        return new CertificationResult(bs);
    }

    public void setCAURL_(String caURL)
    {
        _caURL = caURL;
    }
}
