package com.aerofs.lib.configuration;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.config.DynamicConfiguration;
import com.google.common.io.Files;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;
import static org.mockito.Mockito.spy;

/**
 * This test covers the code path ClientConfigurationLoader executes in the event of various
 *   failures.
 */
public class TestClientConfigurationLoader
{
    // this is the mock URL for the configuration service, the actual value does not matter
    static final String CONFIG_SERVICE_URL = "https://really.fake.url";

    // this is another mock URL, the actual value does not matter as long as it's different
    //   from CONFIG_SERVICE_URL
    static final String BAD_URL = "https://really.bad.url";

    // this value is used as the content of the property file. This needs to be valid
    //   certificate data ( or blank ), but the actual certificate does not matter
    static final String CERT = "";

    @Rule public TemporaryFolder _approotFolder;
    String _approot;

    MockHttpsDownloader _downloader;
    ClientConfigurationLoader _loader;

    @Before
    public void setup()
            throws Exception
    {
        _approotFolder = new TemporaryFolder();
        _approotFolder.create();
        _approot = _approotFolder.getRoot().getAbsolutePath();

        _downloader = spy(new MockHttpsDownloader());
        _loader = new ClientConfigurationLoader(_downloader);
    }

    @Test
    public void shouldEvaluateConflictingPropertiesCorrectly()
            throws Exception
    {
        File staticConfigFile = createStaticConfigFile(true);
        Files.append("conflict=static", staticConfigFile, Charset.defaultCharset());
        File siteConfigFile = createSiteConfigFile(CONFIG_SERVICE_URL, CERT);
        Files.append("conflict=site\nconflict2=site\n",
                siteConfigFile, Charset.defaultCharset());

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assertEquals("static", config.getString("conflict"));
        assertEquals("site", config.getString("conflict2"));
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
        createSiteConfigFile(CONFIG_SERVICE_URL, CERT);
        createHttpConfigCache("is_cache=true");

        DynamicConfiguration config = _loader.loadConfiguration(_approot);

        assertFalse(config.containsKey("is_cache"));
    }

    @Test
    public void shouldLoadFromCacheWhenConfigServiceIsNotAvailableAndHasCache()
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
        createSiteConfigFile(CONFIG_SERVICE_URL, CERT);

        _loader.loadConfiguration(_approot);
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
                PROPERTY_BASE_CA_CERT + '=' + cert + '\n',
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

    public class MockHttpsDownloader extends HttpsDownloader
    {
        public void download(String url, @Nullable ICertificateProvider certificateProvider, File file)
                throws GeneralSecurityException, IOException
        {
            if (!url.equals(CONFIG_SERVICE_URL)) throw new IOException();

            String content = "config.service.available=true\n" +
                    "conflict=http\n" +
                    "conflict2=http";

            Files.write(content, file, Charset.defaultCharset());
        }
    }
}
