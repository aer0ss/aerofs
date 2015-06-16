package com.aerofs.lib.configuration;

import com.aerofs.base.config.PropertiesHelper;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ssl.ICertificateProvider;
import com.google.common.io.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;

import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;
import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * This test covers the code path ClientConfigurationLoader executes in the event of various
 * failures.
 *
 * I've decided to do white-box testing so that I can simulate and cover various failure cases
 * without creating a number of trivial classes for the purpose of mocking.
 */
public class TestClientConfigurationLoader
{
    // this is the mock URL for the configuration service, must be non-blank and different from
    // CONFIG_SERVICE_URL
    static final String BAD_CONFIG_SERVICE_URL = "https://bad.fake.url";

    // this is the mock URL for the configuration service, must be non-blank.
    static final String CONFIG_SERVICE_URL = "https://really.fake.url";

    // this value is used as content, must be non-blank.
    static final String BASE_CA_CERT = "FAKE_CERT_DATA";

    // this value is used to perform sanity check, must be non-blank.
    static final String BASE_HOST = "really.fake.url";

    static final String OS_OSX = "Mac OS X 10.7";
    static final String OS_NON_OSX = "Windows 7";

    @Rule public TemporaryFolder _approotParentFolder;
    File _approotFolder;
    String _approot;

    @Rule public TemporaryFolder _rtrootFolder;
    String _rtroot;

    ClientConfigurationLoader _loader;
    PropertiesHelper _helper;
    HttpsURLConnection _conn;

    @Before
    public void setup()
            throws Exception
    {
        _approotParentFolder = new TemporaryFolder();
        _approotParentFolder.create();
        _approotFolder = _approotParentFolder.newFolder("approot");
        _approotFolder.mkdirs();
        _approot = _approotFolder.getAbsolutePath();

        _rtrootFolder = new TemporaryFolder();
        _rtrootFolder.create();
        _rtroot = _rtrootFolder.getRoot().getAbsolutePath();

        _helper = spy(new PropertiesHelper());
        _loader = spy(new ClientConfigurationLoader(_approot, _rtroot, _helper));
        _conn = mock(HttpsURLConnection.class);

        doReturn(_conn).when(_loader).createConnection(matches(CONFIG_SERVICE_URL),
                any(ICertificateProvider.class));
        doThrow(IOException.class).when(_loader).createConnection(not(matches(CONFIG_SERVICE_URL)),
                any(ICertificateProvider.class));

        // defaults to 404 until the actual content is mocked
        doReturn(404).when(_conn).getResponseCode();
    }

    private AutoCloseable mockOS(String osName)
    {
        String currOsName = System.getProperty("os.name");
        System.setProperty("os.name", osName);
        return () -> System.setProperty("os.name", currOsName);
    }

    private void mockOSXSiteConfig(String content)
            throws IOException
    {
        _loader.getOSXSiteConfigFile().getParentFile().mkdirs();
        Files.write(content, _loader.getOSXSiteConfigFile(), Charset.defaultCharset());
    }

    private void mockDefaultSiteConfig(String content)
            throws IOException
    {
        Files.write(content, _loader.getDefaultSiteConfigFile(), Charset.defaultCharset());
    }

    private String formatSiteConfig(String url, String cert)
    {
        return PROPERTY_CONFIG_SERVICE_URL + "=" + url + "\n" +
                PROPERTY_BASE_CA_CERT + "=" + cert + "\n";
    }

    private void mockLocalHttpConfig(String content)
            throws IOException
    {
        Files.write(content, _loader.getLocalHttpConfigFile(), Charset.defaultCharset());
    }

    private void mockRemoteHttpConfig(String content)
            throws Exception
    {
        doReturn(200).when(_conn).getResponseCode();
        doReturn(new ByteArrayInputStream(content.getBytes())).when(_conn).getInputStream();
    }

    private String formatHttpConfig(String baseHost)
    {
        return PROPERTY_BASE_HOST + "=" + baseHost + "\n";
    }

    @Test
    public void shouldSucceed()
            throws Exception
    {
        // ensure all sources are available
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));

        _loader.loadConfiguration();
    }

    @Test
    public void shouldThrowWhenSiteConfigIsNotFound()
            throws Exception
    {
        try {
            _loader.loadConfiguration();
        } catch (SiteConfigException e) {
            return; // expected
        }

        fail();
    }

    @Test
    public void shouldThrowWhenSiteConfigIsInvalid()
            throws Exception
    {
        mockDefaultSiteConfig("");

        try {
            _loader.loadConfiguration();
        } catch (SiteConfigException e) {
            return; // expected
        }

        fail();
    }

    @Test
    @SuppressWarnings("try")
    public void shouldPassWithOnlyOSXSiteConfigOnOSX()
            throws Exception
    {
        try (AutoCloseable ignored = mockOS(OS_OSX)) {
            mockOSXSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
            mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));

            _loader.loadConfiguration();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void shouldThrowWithOnlyOSXSiteConfigOnNonOSX()
            throws Exception
    {
        try (AutoCloseable ignored = mockOS(OS_NON_OSX)) {
            mockOSXSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
            mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));

            try {
                _loader.loadConfiguration();
            } catch (SiteConfigException e) {
                return; // expected
            }

            fail();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void shouldPassWithBothSiteConfigsOnOSX()
            throws Exception
    {
        try (AutoCloseable ignored = mockOS(OS_OSX)) {
            mockOSXSiteConfig(formatSiteConfig(BAD_CONFIG_SERVICE_URL, BASE_CA_CERT));
            mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
            // also verifies that the default site config takes precedence on conflicts
            mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));

            _loader.loadConfiguration();
        }
    }

    @Test
    public void shouldThrowWhenHttpConfigIsNotAvailable()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));

        mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));
        // do ensure content is good and then mock the response code to ensure the failure is
        // caused by bad response code instead of bad content
        doReturn(500).when(_conn).getResponseCode();

        try {
            _loader.loadConfiguration();
        } catch (HttpConfigException e) {
            return; // expected
        }

        fail();
    }

    @Test
    public void shouldThrowWithBadConfigServiceURL()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(BAD_CONFIG_SERVICE_URL, BASE_CA_CERT));
        // it is necessary to mock remote http config here to verify a bad url will cause the
        // loader to not get good config
        mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));

        try {
            _loader.loadConfiguration();
        } catch (HttpConfigException e) {
            return; // expected
        }

        fail();
    }

    @Test
    public void shouldPassWithOnlyLocalHttpConfig()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockLocalHttpConfig(formatHttpConfig(BASE_HOST));

        _loader.loadConfiguration();
    }

    @Test
    public void shouldPersistRemoteHttpConfigToLocal()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockRemoteHttpConfig(formatHttpConfig(BASE_HOST));
        // mock local to verify that remote takes precedence over local
        mockLocalHttpConfig("");

        _loader.loadConfiguration();

        assertTrue(_loader.getLocalHttpConfigFile().isFile());
        // intentionally mock the remote config to fail to force loading from local
        doReturn(404).when(_conn).getResponseCode();

        _loader.loadConfiguration();
    }

    @Test
    public void shouldFallbackToLocalWhenRemoteIsInvalid()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockRemoteHttpConfig("");
        mockLocalHttpConfig(formatHttpConfig(BASE_HOST));

        _loader.loadConfiguration();
    }

    @Test
    public void shouldThrowWhenHttpConfigInvalid()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        // make sure both http config are invalid because invalid remote will cause loader to
        // fall back to local
        mockRemoteHttpConfig("");
        mockLocalHttpConfig("");

        try {
            _loader.loadConfiguration();
        } catch (HttpConfigException e) {
            return; // expected
        }

        fail();
    }

    @Test
    public void shouldComposeConflictingProperties()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT) +
                        "conflict1=site\n" +
                        "conflict2=site\n");
        mockLocalHttpConfig(formatHttpConfig(BASE_HOST) +
                        "conflict1=http\n" +
                        "conflict3=http\n");

        Properties properties = _loader.loadConfiguration();

        // verify that http > site > static
        assertEquals("site", properties.getProperty("conflict1"));
        assertEquals("site", properties.getProperty("conflict2"));
        assertEquals("http", properties.getProperty("conflict3"));
    }

    @Test
    public void shouldRenderProperties()
            throws Exception
    {
        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockLocalHttpConfig(formatHttpConfig(BASE_HOST) +
                        "clone=${" + PROPERTY_BASE_HOST + "}/clone\n");

        Properties properties = _loader.loadConfiguration();

        assertEquals(BASE_HOST + "/clone", properties.getProperty("clone"));
    }

    @Test
    public void shouldThrowWhenFailingToRender()
            throws Exception
    {
        doThrow(ExBadArgs.class).when(_helper).parseProperties(any(Properties.class));

        mockDefaultSiteConfig(formatSiteConfig(CONFIG_SERVICE_URL, BASE_CA_CERT));
        mockLocalHttpConfig(formatHttpConfig(BASE_HOST));

        try {
            _loader.loadConfiguration();
        } catch (RenderConfigException e) {
            return; // expected
        }

        fail();
    }
}
