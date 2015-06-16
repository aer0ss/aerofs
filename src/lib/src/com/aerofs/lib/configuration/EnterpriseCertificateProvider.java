package com.aerofs.lib.configuration;

import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.lib.LibParam.DeploymentConfig;

/**
 * N.B. this class depends on enterprise deployment and shoud not be used if the enterprise
 *   mode is not enabled.
 */
public class EnterpriseCertificateProvider extends StringBasedCertificateProvider
{
    public EnterpriseCertificateProvider()
    {
        super(DeploymentConfig.BASE_CA_CERTIFICATE);
    }
}
