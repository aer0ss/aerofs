package com.aerofs.lib.configuration;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ssl.ICertificateProvider;
import com.google.common.io.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Properties;

import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
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

    @Rule public TemporaryFolder _rtrootFolder;
    String _rtroot;

    MockHttpsDownloader _downloader;
    ClientConfigurationLoader _loader;

    @Before
    public void setup()
            throws Exception
    {
        _approotFolder = new TemporaryFolder();
        _approotFolder.create();
        _approot = _approotFolder.getRoot().getAbsolutePath();

        _rtrootFolder = new TemporaryFolder();
        _rtrootFolder.create();
        _rtroot = _rtrootFolder.getRoot().getAbsolutePath();

        _downloader = new MockHttpsDownloader();
        _loader = spy(new ClientConfigurationLoader(_downloader, _approot, _rtroot));
    }

    @Test
    public void shouldCorrectlyDealWithConflictingProperties()
            throws Exception
    {
        Properties staticProperties = setupStaticProperties(true);
        staticProperties.setProperty("conflict", "static");
        staticProperties.setProperty("conflict3", "static");

        File siteConfigFile = createSiteConfigFile(CONFIG_SERVICE_URL, CERT);
        Files.append("conflict=site\nconflict2=site\nconflict3=site",
                siteConfigFile, Charset.defaultCharset());

        Properties properties = _loader.loadConfiguration();

        assertEquals("http", properties.getProperty("conflict"));
        assertEquals("http", properties.getProperty("conflict2"));
        assertEquals("site", properties.getProperty("conflict3"));
    }

    @Test
    public void shouldSucceedWhenIsNotEnterpriseDeployment()
            throws Exception
    {
        setupStaticProperties(false);

        _loader.loadConfiguration();
    }

    @Test
    public void shouldThrowFileNotFoundExceptionWhenSiteConfigIsNotAvailable()
            throws Exception
    {
        setupStaticProperties(true);

        try {
            _loader.loadConfiguration();
            fail("Expected exception.");
        } catch (ConfigurationException e) {
            assert(e.getCause().getClass() == FileNotFoundException.class);
        }
    }

    @Test
    public void shouldThrowFileNotFoundExceptionWhenConfigServiceIsNotAvailableAndHasNoCache()
            throws Exception
    {
        setupStaticProperties(true);
        createSiteConfigFile(BAD_URL, CERT);

        try {
            _loader.loadConfiguration();
            fail("Expected exception.");
        } catch (ConfigurationException e) {
            assert(e.getCause().getClass() == FileNotFoundException.class);
        }
    }

    @Test
    public void shouldSucceedAndUpdateCacheWhenEverythingIsAvailable()
            throws Exception
    {
        setupStaticProperties(true);
        createSiteConfigFile(CONFIG_SERVICE_URL, CERT);
        createHttpConfigCache("is_cache=true");

        Properties config = _loader.loadConfiguration();

        // the end configuration should use the downloaded http config over the config persisted in the cache
        assertFalse(config.containsKey("is_cache"));

        Properties cachedConfig = new Properties();
        _loader.loadPropertiesFromFile(cachedConfig, _loader.getHttpConfigFile());

        // the cache should be updated to match the downloaded http config
        assertFalse(cachedConfig.containsKey("is_cache"));
    }

    @Test
    public void shouldLoadFromCacheWhenConfigServiceIsNotAvailableAndHasCache()
            throws Exception
    {
        setupStaticProperties(true);
        createSiteConfigFile(BAD_URL, CERT);
        createHttpConfigCache("is_cache=true");

        Properties config = _loader.loadConfiguration();

        assert config.containsKey("is_cache") && config.getProperty("is_cache").equals("true");
    }

    @Test
    public void shouldThrowIncompatibleModeExceptionWhenIsNotPrivateDeploymentAndSiteConfigExists()
            throws Exception
    {
        setupStaticProperties(false);
        createSiteConfigFile(CONFIG_SERVICE_URL, CERT);

        try {
            _loader.loadConfiguration();
            fail("Expected exception.");
        } catch (ConfigurationException e) {
            assert(e.getCause().getClass() == IncompatibleModeException.class);
        }
    }

    @Test
    public void shouldUseCorrectSiteConfigFile()
    {
        assertEquals(new File(_approot, SITE_CONFIG_FILE).getAbsolutePath(),
                _loader.getSiteConfigFile().getAbsolutePath());
    }

    @Test
    public void shouldUseCorrectHttpConfigFile()
    {
        assertEquals(new File(_rtroot, HTTP_CONFIG_CACHE).getAbsolutePath(),
                _loader.getHttpConfigFile().getAbsolutePath());

    }

    protected Properties setupStaticProperties(boolean httpConfigRequired)
            throws IOException, ExBadArgs
    {
        Properties mockStaticProperties = new Properties();
        mockStaticProperties.setProperty(PROPERTY_IS_PRIVATE_DEPLOYMENT,
                String.valueOf(httpConfigRequired));
        doReturn(mockStaticProperties).when(_loader).getStaticProperties();

        return mockStaticProperties;
    }

    protected File createSiteConfigFile(String url, String cert)
            throws IOException
    {
        cert = cert.replace("\n", "\\n");

        File siteConfigFile = _loader.getSiteConfigFile();
        Files.write(PROPERTY_CONFIG_SERVICE_URL + '=' + url + '\n' +
                PROPERTY_BASE_CA_CERT + '=' + cert + '\n',
                siteConfigFile, Charset.defaultCharset());
        return siteConfigFile;
    }

    protected File createHttpConfigCache(String content)
            throws IOException
    {
        File cache = _loader.getHttpConfigFile();
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
