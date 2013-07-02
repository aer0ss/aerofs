package com.aerofs.lib.configuration;

import com.aerofs.config.DynamicConfiguration;
import com.google.common.io.Files;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.aerofs.lib.configuration.ConfigurationTestConstants.*;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;

/**
 * This test suite depends on ConfigurationTestConstants and tests against the local
 *   production environment with a custom self-signed certificate.
 */
public class TestClientConfigurationLoader
{
    @Rule public TemporaryFolder _approotFolder;
    String _approot;

    ClientConfigurationLoader _loader;

    @Before
    public void setup()
            throws Exception
    {
        _approotFolder = new TemporaryFolder();
        _approotFolder.create();
        _approot = _approotFolder.getRoot().getAbsolutePath();

        _loader = new ClientConfigurationLoader(new HttpsDownloader());
    }

    @Test
    public void shouldEvaluateConflictingPropertiesCorrectly()
            throws Exception
    {
        File staticConfigFile = createStaticConfigFile(true);
        Files.append("conflict=static", staticConfigFile, Charset.defaultCharset());
        File siteConfigFile = createSiteConfigFile(URL, CERT);
        Files.append("conflict=site\nupdater.version.url=asdf\n",
                siteConfigFile, Charset.defaultCharset());

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assertEquals("static", config.getString("conflict"));
        assertEquals("asdf", config.getString("updater.version.url"));
        assertEquals(TEST_PROP_VALUE, config.getString(TEST_PROP));
    }

    @Test
    public void shouldSucceedWhenIsNotEnterpriseDeployment()
            throws Exception
    {
        createStaticConfigFile(false);

        _loader.loadConfiguration(_approot);
    }

    @Test(expected = ConfigurationException.class)
    public void shouldThrowConfigurationExceptionWhenSiteConfigIsNotAvailable()
            throws Exception
    {
        createStaticConfigFile(true);

        _loader.loadConfiguration(_approot);
    }

    @Test(expected = ConfigurationException.class)
    public void shouldThrowConfigurationExceptionWhenConfigServiceIsNotAvailableAndHasNoCache()
            throws Exception
    {
        createStaticConfigFile(true);
        createSiteConfigFile(BAD_URL, CERT);

        _loader.loadConfiguration(_approot);
    }

    @Test
    public void shouldSucceedAndUpdateCacheWhenEverythingIsAvailable()
            throws Exception
    {
        createStaticConfigFile(true);
        createSiteConfigFile(URL, CERT);
        createHttpConfigCache("is_cache=true");

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assertFalse(config.containsKey("is_cache"));
    }

    @Test
    public void shouldLoadFromCacheWhenConfigServiceInNotAvailableAndHasCache()
            throws Exception
    {
        createStaticConfigFile(true);
        createSiteConfigFile(BAD_URL, CERT);
        createHttpConfigCache("is_cache=true");

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assert config.containsKey("is_cache") && config.getBoolean("is_cache");
    }

    @Test(expected = IncompatibleModeException.class)
    public void shouldThrowIncompatibleModeExceptionWhenIsNotPrivateDeploymentAndSiteConfigExists()
            throws Exception
    {
        createStaticConfigFile(false);
        createSiteConfigFile(URL, CERT);

        _loader.loadConfiguration(_approot);
    }

    /**
     * This test is only valid when the local production server is using a certificate signed
     *   by a trusted CA.
     */
    @Ignore("Run this test case manually") @Test
    public void shouldSucceedWhenEnterpriseConfigIsBlank()
            throws Exception
    {
        createStaticConfigFile(true);
        createSiteConfigFile(URL, "");

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assertEquals(TEST_PROP_VALUE, config.getString(TEST_PROP));
    }

    protected File getStaticConfigFile()
            throws IOException
    {
        return new File(ClientConfigurationLoader.STATIC_CONFIG_FILE);
    }

    protected File getSiteConfigFile()
            throws IOException
    {
        return new File(_approot, ClientConfigurationLoader.SITE_CONFIG_FILE);
    }

    protected File getHttpConfigCache()
            throws IOException
    {
        return new File(_approot, ClientConfigurationLoader.HTTP_CONFIG_CACHE);
    }

    protected File createStaticConfigFile(boolean httpConfigRequired)
            throws IOException
    {
        File staticConfigFile = getStaticConfigFile();
        Files.write(PROPERTY_IS_ENTERPRISE_DEPLOYMENT + '=' + httpConfigRequired + '\n',
                staticConfigFile, Charset.defaultCharset());
        staticConfigFile.deleteOnExit();
        return staticConfigFile;
    }

    protected File createSiteConfigFile(String url, String cert)
            throws IOException
    {
        cert = cert.replace("\n", "\\n");

        File siteConfigFile = getSiteConfigFile();
        Files.write(PROPERTY_CONFIG_SERVICE_URL + '=' + url + '\n' +
                PROPERTY_ENTERPRISE_CUSTOMER_CERT + '=' + cert + '\n',
                siteConfigFile, Charset.defaultCharset());
        return siteConfigFile;
    }

    protected File createHttpConfigCache(String content)
            throws IOException
    {
        File cache = getHttpConfigCache();
        Files.write(content, cache, Charset.defaultCharset());
        return cache;
    }
}
