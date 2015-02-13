/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.lib.AppRoot;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static java.lang.String.format;

/**
 * a bunch of provider methods all crunched into one utility class
 */
public class DryadClientUtil
{
    private DryadClientUtil()
    {
        // prevents instantiation
    }

    public static DryadClient createPublicDryadClient()
            throws IOException, GeneralSecurityException
    {
        return new DryadClient(
                getStringProperty("lib.dryad.url", "https://dryad.aerofs.com"),
                createPublicDryadSSLContext());
    }

    public static DryadClient createPrivateDryadClient(String hostname, int port, String certData)
            throws IOException, GeneralSecurityException
    {
        return new DryadClient(
                "https://" + hostname + ":" + port,
                createPrivateDryadSSLContext(certData));
    }

    private static SSLContext createPublicDryadSSLContext()
            throws IOException, GeneralSecurityException
    {
        // FIXME (AT): remove overwriting cacert.pem, delete aerofs_public_cacert.pem, and then
        //   just read from cacert.pem
        return createSSLContext(new FileBasedCertificateProvider(
                new File(AppRoot.abs(), "aerofs_public_cacert.pem").getAbsolutePath()));
    }

    private static SSLContext createPrivateDryadSSLContext(String certData)
            throws IOException, GeneralSecurityException
    {
        return createSSLContext(new StringBasedCertificateProvider(certData));
    }

    private static SSLContext createSSLContext(ICertificateProvider provider)
            throws IOException, GeneralSecurityException
    {
        return new SSLEngineFactory(Mode.Client, Platform.Desktop, null, provider, null)
                .getSSLContext();
    }

    public static String createDefectLogsResource(String defectID, UserID userID, DID deviceID)
    {
        return format("/v1.0/defects/%s/client/%s/%s",
                defectID, userID.getString(), deviceID.toStringFormal());
    }

    public static String createArchivedLogsResource(UserID userID, DID deviceID, String filename)
    {
        return format("/v1.0/archived/%s/%s/%s",
                userID.getString(), deviceID.toStringFormal(), filename);
    }
}
