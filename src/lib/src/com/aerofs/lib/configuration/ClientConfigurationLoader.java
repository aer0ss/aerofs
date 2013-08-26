package com.aerofs.lib.configuration;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.base.config.PropertiesHelper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
    private PropertiesHelper _propertiesHelper = new PropertiesHelper();

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
    public Properties loadConfiguration(String approot)
            throws ConfigurationException
    {
        Properties compositeProperties;

        try {
            Properties staticProperties;
            Properties siteConfigProperties = new Properties();
            Properties httpProperties = new Properties();

            staticProperties = getStaticProperties();

            if (staticProperties.getProperty(PROPERTY_IS_ENTERPRISE_DEPLOYMENT, "false").equals(
                    "true")) {
                // Load site configuration file, failing if it doesn't exist.
                siteConfigProperties.load(new FileInputStream(new File(approot, SITE_CONFIG_FILE)));
                siteConfigProperties = _propertiesHelper.parseProperties(siteConfigProperties);

                // Load HTTP configuration, failing if it cannot be loaded.
                downloadHttpConfig(approot, staticProperties, siteConfigProperties);
                httpProperties.load(new FileInputStream(new File(approot, HTTP_CONFIG_CACHE)));
                httpProperties = _propertiesHelper.parseProperties(httpProperties);

            } else if (new File(approot, SITE_CONFIG_FILE).exists()) {
                throw new IncompatibleModeException();
            }

            // Join all properties together, logging a warning if any property is specified twice.
            compositeProperties = _propertiesHelper.unionOfThreeProperties(staticProperties,
                    siteConfigProperties, httpProperties);
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }

        return compositeProperties;
    }

    /**
     * Download http configuration from the configuration server. And surpress any exception
     *   because we'll be falling back to the cache file if this fails.
     */
    protected void downloadHttpConfig(String approot, Properties staticProperties,
            Properties siteConfigProperties)
    {
        Properties preHttpProperties = new Properties();
        preHttpProperties.putAll(staticProperties);
        preHttpProperties.putAll(siteConfigProperties);

        try {
            String url = preHttpProperties.getProperty(PROPERTY_CONFIG_SERVICE_URL);
            String certificate = preHttpProperties.getProperty(PROPERTY_BASE_CA_CERT, "");
            ICertificateProvider certificateProvider = StringUtils.isBlank(certificate) ? null
                    : new StringBasedCertificateProvider(certificate);
            File cache = new File(approot, HTTP_CONFIG_CACHE);

            _downloader.download(url, certificateProvider, cache);
        } catch (Throwable t) {
            LOGGER.debug("Failed to download http configuration", t);
        }
    }

    /**
     * Reads the static configuration file. First look in the classpath then in the current
     * directory.
     *
     * @return An InputStream corresponding to the static configuration file. null if the static
     * configuration file could not be found.
     */
    protected @Nullable
    InputStream getStaticConfigInputStream()
    {
        InputStream inputStream = null;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // Try context classloader.
        if (classLoader != null) {
            inputStream = classLoader.getResourceAsStream(STATIC_CONFIG_FILE);
        }
        return inputStream;
    }

    protected Properties getStaticProperties() throws IOException
    {
        Properties staticProperties = new Properties();

        // Load static configuration file, ignoring it if it doesn't exist.
        try {
            InputStream inputStream = getStaticConfigInputStream();
            staticProperties.load(inputStream);
        } catch (IOException e) {
            LOGGER.warn("Failed to open: " + STATIC_CONFIG_FILE + " with error {}. Assuming no " +
                    "staticProperties.", e.toString());
            staticProperties = new Properties();
        }
        return _propertiesHelper.parseProperties(staticProperties);
    }

    public static class ConfigurationException extends Exception
    {
        private static final long serialVersionUID = 1L;
        public ConfigurationException(Exception e) { super(e); }
    }

    public static class IncompatibleModeException extends Exception
    {
        // N.B. the serialVersionUID is necessary because Exception implements Serializable
        //   and not providing serialVersionUID will throw a warning at compile time
        //   causing CI to fail.
        private static final long serialVersionUID = 1594312114711899559L;
    }
}
