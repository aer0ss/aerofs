package com.aerofs.lib.configuration;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.config.DynamicConfiguration;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.File;

/**
 * The responsibility of this class is to execute the loading logic/policy.
 *
 * The class loads configuration in the following order:
 * - static configuration, provided at build time, at $approot/configuration.properties
 * - if the property "config.loader.is_enterprise_deployment" is present and true, it
 *   loads the following:
 *   - site configuration, provided by installer, at $approot/site-config.properties
 *   - http configuration based on "config.loader.configuration_service_url"
 *     and "config.loader.enterprise_customer_certificate" properties
 *
 * Sources are considered in the order they are added, the first source to supply a
 *   property defines the value of that property.
 */
public class ClientConfigurationLoader
{
    private static final Logger LOGGER = Loggers.getLogger(ClientConfigurationLoader.class);

    public static final String PROPERTY_IS_ENTERPRISE_DEPLOYMENT
            = "config.loader.is_enterprise_deployment";
    public static final String PROPERTY_CONFIG_SERVICE_URL
            = "config.loader.configuration_service_url";
    public static final String PROPERTY_BASE_CA_CERT
            = "config.loader.base_ca_certificate";

    static final String STATIC_CONFIG_FILE = "configuration.properties";
    static final String SITE_CONFIG_FILE = "site-config.properties";
    static final String HTTP_CONFIG_CACHE = "config-service-cache.properties";

    private HttpsDownloader _downloader;

    public ClientConfigurationLoader(HttpsDownloader downloader)
    {
        _downloader = downloader;
    }

    /**
     * loads configuration from multiple configuration sources based on the class logic.
     *
     * Throws ConfigurationException if this is a private deployment and we are unable to load
     *   site config or http config.
     */
    public DynamicConfiguration loadConfiguration(String approot)
            throws ConfigurationException, IncompatibleModeException
    {
        DynamicConfiguration.Builder builder = DynamicConfiguration.builder();

        /**
         * There is a bug with DynamicConfiguration.Builder in that it adds configuration
         *   by alphabetical order of configuration names instead of the order configurations
         *   are added.
         *
         * FIXME import the latest dynamic configuration jar with the fix and
         *   remove numeral prefix in configuration names
         */
        AbstractConfiguration staticConfig = new PropertiesConfiguration(STATIC_CONFIG_FILE);
        builder.addConfiguration(staticConfig, "1static");

        if (staticConfig.getBoolean(PROPERTY_IS_ENTERPRISE_DEPLOYMENT, false)) {
            PropertiesConfiguration siteConfig = new PropertiesConfiguration();
            siteConfig.load(new File(approot, SITE_CONFIG_FILE));

            downloadHttpConfig(approot, siteConfig);

            PropertiesConfiguration httpConfig = new PropertiesConfiguration();
            httpConfig.load(new File(approot, HTTP_CONFIG_CACHE));

            builder.addConfiguration(siteConfig, "2site")
                    .addConfiguration(httpConfig, "3http");
        } else if (new File(approot, SITE_CONFIG_FILE).exists()) {
            throw new IncompatibleModeException();
        }

        return builder.build();
    }

    /**
     * Download http configuration from the configuration server. And surpress any exception
     *   because we'll be falling back to the cache file if this fails.
     */
    protected void downloadHttpConfig(String approot, AbstractConfiguration siteConfig)
    {
        try {
            String url = siteConfig.getString(PROPERTY_CONFIG_SERVICE_URL);
            String certificate = siteConfig.getString(PROPERTY_BASE_CA_CERT, "");
            ICertificateProvider certificateProvider = StringUtils.isBlank(certificate) ? null
                    : new StringBasedCertificateProvider(certificate);
            File cache = new File(approot, HTTP_CONFIG_CACHE);

            _downloader.download(url, certificateProvider, cache);
        } catch (Throwable t) {
            LOGGER.debug("Failed to download http configuration", t);
        }
    }

    public static class IncompatibleModeException extends Exception
    {
        // N.B. the serialVersionUID is necessary because Exception implements Serializable
        //   and not providing serialVersionUID will throw a warning at compile time
        //   causing CI to fail.
        private static final long serialVersionUID = 1594312114711899559L;
    }
}
