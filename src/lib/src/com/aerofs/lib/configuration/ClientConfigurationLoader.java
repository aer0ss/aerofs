package com.aerofs.lib.configuration;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.base.config.PropertiesHelper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The responsibility of this class is to execute the loading logic/policy.
 *
 * The class loads configuration in the following order:
 * - static configuration, provided at build time, at $approot/configuration.properties
 * - if the property "config.loader.is_private_deployment" is present and true, it
 *   loads the following:
 *   - site configuration, provided by installer, at $approot/site-config.properties
 *   - http configuration based on "config.loader.configuration_service_url"
 *     and "config.loader.base_ca_certificate" properties
 *
 * Sources are considered in the order they are added, the first source to supply a
 *   property defines the value of that property.
 */
public class ClientConfigurationLoader
{
    private static final Logger LOGGER = Loggers.getLogger(ClientConfigurationLoader.class);

    public static final String PROPERTY_IS_PRIVATE_DEPLOYMENT
            = "config.loader.is_private_deployment";
    public static final String PROPERTY_CONFIG_SERVICE_URL
            = "config.loader.configuration_service_url";
    public static final String PROPERTY_BASE_CA_CERT
            = "config.loader.base_ca_certificate";

    static final String STATIC_CONFIG_FILE = "configuration.properties";
    static final String SITE_CONFIG_FILE = "site-config.properties";
    static final String HTTP_CONFIG_CACHE = "config-service-cache.properties";

    private final HttpsDownloader _downloader;
    private final PropertiesHelper _propertiesHelper = new PropertiesHelper();

    private final String _approot;
    private final String _rtroot;

    public ClientConfigurationLoader(HttpsDownloader downloader, String approot, String rtroot)
    {
        _downloader = downloader;
        _approot = approot;
        _rtroot = rtroot;
    }

    /**
     * loads configuration from multiple configuration sources based on the class logic.
     *
     * Throws ConfigurationException if this is a private deployment and we are unable to load
     *   site config or http config.
     */
    public Properties loadConfiguration()
            throws ConfigurationException
    {
        try {
            Properties siteConfigProperties = new Properties();
            Properties httpProperties = new Properties();
            Properties staticProperties = getStaticProperties();

            File siteConfigFile = getSiteConfigFile();

            if (staticProperties.getProperty(PROPERTY_IS_PRIVATE_DEPLOYMENT, "false").equals(
                    "true")) {
                boolean loaded = false;
                // Load site configuration file, failing if it doesn't exist.
                // Avoiding using OSUtil because static initializers suck.
                // We do crazy stuff on OSX because codesigning is mean, but not mean enough.
                if (System.getProperty("os.name").startsWith("Mac OS X")) {
                    // We need to read the config from Resources/site-config.lproj/locversion.plist,
                    // which is conveniently omitted from the codesigning seal.
                    // construct relative path from approot:
                    File codesigningEvadingFile = new File(
                            new File(
                                    new File(_approot).getParentFile(),
                                    "site-config.lproj"
                            ),
                            "locversion.plist"
                    );
                    if (codesigningEvadingFile.isFile()) {
                        loadPropertiesFromFile(siteConfigProperties, codesigningEvadingFile);
                        loaded = true;
                    }
                }

                // Load config from site-config.properties, if it exists
                if (siteConfigFile.isFile()) {
                    loadPropertiesFromFile(siteConfigProperties, siteConfigFile);
                    loaded = true;
                }

                if (!loaded) throw new FileNotFoundException("Missing " + siteConfigFile);

                siteConfigProperties = _propertiesHelper.parseProperties(siteConfigProperties);

                // Load HTTP configuration, warning if it cannot be loaded.
                File httpConfigFile = getHttpConfigFile();
                downloadHttpConfig(staticProperties, siteConfigProperties, httpConfigFile);
                // N.B. we load the http config properties from cache even when we've failed download http config from
                // the config server; this is intended as a fail-safe mechanism in case the client cannot reach the
                // config server.
                // However, this also opens up a hole where an adversary can edit the http config cache and then
                // launch the client with no internet connection to configure the client with arbitrary configuration.
                loadPropertiesFromFile(httpProperties, httpConfigFile);
                httpProperties = _propertiesHelper.parseProperties(httpProperties);

            } else if (siteConfigFile.exists()) {
                // This ignores the repackaged OSX case, but I'm okay with this
                throw new IncompatibleModeException();
            }

            // Join all properties together, logging a warning if any property is specified twice.
            return _propertiesHelper.mergeProperties(staticProperties,
                    siteConfigProperties, httpProperties);
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    protected void loadPropertiesFromFile(Properties properties, File file)
            throws IOException
    {
        try (InputStream in = new FileInputStream(file)) {
            properties.load(in);
        }
    }

    protected File getSiteConfigFile()
    {
        return new File(_approot, SITE_CONFIG_FILE);
    }

    protected File getHttpConfigFile()
    {
        return new File(_rtroot, HTTP_CONFIG_CACHE);
    }

    /**
     * Download http configuration from the configuration server. And suppress any exception
     *   because we'll be falling back to the cache file if this fails.
     */
    protected void downloadHttpConfig(Properties staticProperties,
            Properties siteConfigProperties, File httpConfigFile)
    {
        Properties preHttpProperties = new Properties();
        preHttpProperties.putAll(staticProperties);
        preHttpProperties.putAll(siteConfigProperties);

        String url = null;
        try {
            url = preHttpProperties.getProperty(PROPERTY_CONFIG_SERVICE_URL);
            String certificate = preHttpProperties.getProperty(PROPERTY_BASE_CA_CERT, "");
            ICertificateProvider certificateProvider = StringUtils.isBlank(certificate) ? null
                    : new StringBasedCertificateProvider(certificate);

            _downloader.download(url, certificateProvider, httpConfigFile);
        } catch (Throwable t) {
            // N.B. the best we can do is log the occurrence because if at this stage, none of the
            // services are available and there's no way to report the defect back home.
            LOGGER.warn("Failed to download http configuration at: {}", url, t);
        }
    }

    /**
     * Reads the static configuration file. First look in the classpath then in the current
     * directory.
     *
     * @return An InputStream corresponding to the static configuration file.
     * @throws java.io.FileNotFoundException if the static configuration file could not be found.
     */
    private InputStream getStaticConfigInputStream()
            throws FileNotFoundException
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // Try context classloader.
        if (classLoader != null) {
            InputStream resource = classLoader.getResourceAsStream(STATIC_CONFIG_FILE);
            if (resource != null) return resource;
        }

        throw new FileNotFoundException(STATIC_CONFIG_FILE);
    }

    protected Properties getStaticProperties()
            throws ExBadArgs
    {
        Properties staticProperties = new Properties();

        // Load static configuration file, ignoring it if it doesn't exist.
        try {
            InputStream inputStream = getStaticConfigInputStream();
            staticProperties.load(inputStream);
        } catch (IOException e) {
            LOGGER.warn("Failed to open {}. Assuming no staticProperties.", STATIC_CONFIG_FILE, e);
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
