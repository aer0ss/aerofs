/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.utils;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.*;
import java.security.cert.CertificateException;

public class CertificateUtils
{
    public static PKCS10CertificationRequest pemToCSR(byte[] pem)
            throws IOException
    {
        PEMParser parser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(pem)));
        try {
            Object parsed = parser.readObject();
            if (parsed instanceof PKCS10CertificationRequest) {
                return (PKCS10CertificationRequest) parsed;
            } else {
                throw new IOException("PEM encoded object was not a pkcs10 csr");
            }
        } finally {
            parser.close();
        }
    }

    public static byte[] x509ToPEM(X509CertificateHolder cert)
            throws IOException, CertificateException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(bytes));
        try {
            writer.writeObject(cert);
            writer.flush();
            return bytes.toByteArray();
        } finally {
            writer.close();
        }
    }

    public static byte[] csrToPEM(PKCS10CertificationRequest csr)
            throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(bytes));
        try {
            writer.writeObject(csr);
            writer.flush();
            return bytes.toByteArray();
        } finally {
            writer.close();
        }
    }

    public static X509CertificateHolder pemToCert(byte[] pem)
            throws IOException
    {
        PEMParser parser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(pem)));
        try {
            Object parsed = parser.readObject();
            if (parsed instanceof X509CertificateHolder) {
                return (X509CertificateHolder) parsed;
            } else {
                throw new IOException("PEM encoded object was not a x509 cert");
            }
        } finally {
            parser.close();
        }
    }
}
