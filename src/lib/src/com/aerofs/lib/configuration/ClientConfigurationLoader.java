package com.aerofs.lib.configuration;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.PropertiesHelper;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.google.common.collect.Range;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * The responsibility of this class is to execute the loading logic/policy.
 *
 * The class loads configuration in the following order:
 * - if the property "config.loader.is_private_deployment" is present and true, it
 *   loads the following:
 *   - site configuration, provided by installer, at $approot/site-config.properties
 *   - http configuration based on "config.loader.configuration_service_url"
 *     and "config.loader.base_ca_certificate" properties in site-config.
 *
 * Sources are considered in the order they are added, the last source to supply a
 *   property defines the value of that property.
 */
public class ClientConfigurationLoader
{
    private static final Logger LOGGER = Loggers.getLogger(ClientConfigurationLoader.class);

    static final String PROPERTY_CONFIG_SERVICE_URL
            = "config.loader.configuration_service_url";
    public static final String PROPERTY_BASE_CA_CERT
            = "config.loader.base_ca_certificate";
    static final String PROPERTY_BASE_HOST
            = "base.host.unified";

    static final String SITE_CONFIG_FILE = "site-config.properties";
    static final String HTTP_CONFIG_CACHE = "config-service-cache.properties";

    private static final int CONNECTION_TIMEOUT = (int)(3 * C.SEC);
    private static final int READ_TIMEOUT = (int)(3 * C.SEC);

    private final String            _approot;
    private final String            _rtroot;
    private final PropertiesHelper  _helper;


    public ClientConfigurationLoader(String approot, String rtroot, PropertiesHelper helper)
    {
        _approot = approot;
        _rtroot = rtroot;
        _helper = helper;
    }

    /**
     * Loads configuration properties from multiple sources based on the class logic.
     *
     * @return fully composed and rendered properties to use for the desktop client
     * @throws SiteConfigException - failed to load site configuration.
     * @throws HttpConfigException - failed to load http configuration.
     * @throws RenderConfigException - failed to render configuration.
     */
    public Properties loadConfiguration()
            throws SiteConfigException, HttpConfigException, RenderConfigException
    {
        Properties properties = new Properties();
        Properties siteConfig, httpConfig;

        try {
            siteConfig = getSiteConfig();
        } catch (Exception e) {
            throw new SiteConfigException(e);
        }

        try {
            httpConfig = getHttpConfig(siteConfig);
        } catch (Exception e) {
            throw new HttpConfigException(e);
        }

        try {
            properties.putAll(httpConfig);
            properties.putAll(siteConfig);
            properties = _helper.parseProperties(properties);
        } catch (Exception e) {
            throw new RenderConfigException(e);
        }

        return properties;
    }

    /**
     * @throws Exception if we failed to load site configuration (sanity check included).
     */
    private Properties getSiteConfig() throws Exception
    {
        Properties properties = new Properties();

        // TODO (AT): I prefer to load site config entirely from one source (assuming valid)
        // instead of composing from two sources. However, the author expected clobbering and it
        // was unclear if we rely on this behaviour anywhere.
        //
        // Avoiding using OSUtil because static initializers suck.
        // We do crazy stuff on OSX because codesigning is mean, but not mean enough.
        if (System.getProperty("os.name").startsWith("Mac OS X")) {
            // We need to read the config from Resources/site-config.lproj/locversion.plist,
            // which is conveniently omitted from the codesigning seal.
            // construct relative path from approot:
            File codesigningEvadingFile = getOSXSiteConfigFile();

            if (codesigningEvadingFile.isFile()) {
                LOGGER.info("Loading OS-X specific site configuration.");
                loadPropertiesFromFile(properties, codesigningEvadingFile);
            }
        }

        // also load from the default site config and clobber properties from OS X-specific site
        // config if applicable.
        File siteConfigFile = getDefaultSiteConfigFile();

        if (siteConfigFile.isFile()) {
            LOGGER.info("Loading default site configuration.");
            loadPropertiesFromFile(properties, siteConfigFile);
        }

        // we expect site configuration to include at least these 2 properties.
        //
        // N.B. it's possible that we have not loaded _any_ site config at this point because
        // neither sources exist. In which case, we will certainly failed these checkes.
        checkState(properties.containsKey(PROPERTY_CONFIG_SERVICE_URL)
                && isNotBlank(properties.getProperty(PROPERTY_CONFIG_SERVICE_URL)));
        checkState(properties.containsKey(PROPERTY_BASE_CA_CERT)
                && isNotBlank(properties.getProperty(PROPERTY_BASE_CA_CERT)));

        return properties;
    }

    /**
     * @throws Exception if we failed to load http configuration (sanity check included).
     */
    private Properties getHttpConfig(Properties siteConfig)
            throws Exception
    {
        Properties properties;

        try {
            properties = getRemoteHttpConfig(siteConfig);
        } catch (Exception e) {
            LOGGER.warn("Failed to load remote http config; proceed with local http config.");
            LOGGER.warn("Cause: {}", e.getMessage());
            return getLocalHttpConfig();
        }

        // at this point, we know we've succeeded in loading the http config from remote and we
        // simply need to save it to local cache for the future.
        try (OutputStream out = new FileOutputStream(getLocalHttpConfigFile())) {
            properties.store(out, "Http config downloaded from " +
                    siteConfig.getProperty(PROPERTY_CONFIG_SERVICE_URL));
        } catch (IOException e) {
            LOGGER.warn("Failed to save remote http config to local; ignored.");
            LOGGER.warn("Cause: {}", e.getMessage());
        }

        return properties;
    }

    /**
     * @throws Exception if we failed to load http config from remote or if the http config we
     *   have loaded failed the sanity check.
     */
    @SuppressWarnings("try")
    private Properties getRemoteHttpConfig(Properties siteConfig)
            throws Exception
    {
        String url = siteConfig.getProperty(PROPERTY_CONFIG_SERVICE_URL);
        String cert = siteConfig.getProperty(PROPERTY_BASE_CA_CERT);

        HttpsURLConnection conn = createConnection(url, new StringBasedCertificateProvider(cert));

        try (Closeable ignored = conn::disconnect) {
            conn.connect();

            checkState(Range.closedOpen(200, 300).contains(conn.getResponseCode()));

            Properties properties = new Properties();

            try (InputStream in = conn.getInputStream()) {
                properties.load(in);
            }

            // it's important to perform sanity check now because if we fail here, we can still
            // fallback to local cache.
            checkHttpConfig(properties);

            return properties;
        }
    }

    /**
     * @throws Exception if we failed to load http config from local cache or if the http config
     *   we have loaded failed the sanity check.
     */
    private Properties getLocalHttpConfig()
            throws Exception
    {
        Properties properties = new Properties();

        loadPropertiesFromFile(properties, getLocalHttpConfigFile());
        checkHttpConfig(properties);

        return properties;
    }

    /**
     * @throws Exception if the supplied http config failed the sanity check.
     */
    private void checkHttpConfig(Properties httpConfig)
            throws Exception
    {
        checkState(httpConfig.containsKey(PROPERTY_BASE_HOST)
                && isNotBlank(httpConfig.getProperty(PROPERTY_BASE_HOST)));
    }

    // this is protected so we can mock it in unit tests
    protected HttpsURLConnection createConnection(String url, ICertificateProvider provider)
            throws GeneralSecurityException, IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

        conn.setSSLSocketFactory(new SSLEngineFactory(Mode.Client, Platform.Desktop, null,
                provider, null).getSSLContext().getSocketFactory());
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        return conn;
    }

    private void loadPropertiesFromFile(Properties properties, File file)
            throws IOException
    {
        try (InputStream in = new FileInputStream(file)) {
            properties.load(in);
        }
    }

    protected File getOSXSiteConfigFile()
    {
        return new File(
                new File(
                        new File(_approot).getParentFile(),
                        "site-config.lproj"
                ),
                "locversion.plist"
        );
    }

    protected File getDefaultSiteConfigFile()
    {
        return new File(_approot, SITE_CONFIG_FILE);
    }

    protected File getLocalHttpConfigFile()
    {
        return new File(_rtroot, HTTP_CONFIG_CACHE);
    }

    public static class SiteConfigException extends Exception
    {
        private static final long serialVersionUID = 0;

        public SiteConfigException(Exception cause) { super(cause); }
    }

    public static class HttpConfigException extends Exception
    {
        private static final long serialVersionUID = 0;

        public HttpConfigException(Exception cause) { super(cause); }
    }

    public static class RenderConfigException extends Exception
    {
        private static final long serialVersionUID = 0;

        public RenderConfigException(Exception cause) { super(cause); }
    }
}
